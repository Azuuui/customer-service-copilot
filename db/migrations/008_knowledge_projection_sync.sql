-- 将正式检索投影同步到知识治理的不可变版本体系，兼容首次导入与后续修改。

CREATE TABLE IF NOT EXISTS knowledge_reindex_events (
    id bigserial PRIMARY KEY,
    source_key text NOT NULL,
    content_hash text NOT NULL,
    event_status text NOT NULL DEFAULT 'pending'
        CHECK (event_status IN ('pending', 'completed', 'failed')),
    failure_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_knowledge_reindex_pending
    ON knowledge_reindex_events(event_status, created_at) WHERE event_status = 'pending';

ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS change_reason text;

CREATE OR REPLACE FUNCTION ensure_knowledge_category(category_path text)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    segments text[] := regexp_split_to_array(category_path, '/');
    start_index integer;
    part text;
    category_id bigint;
    parent_category_id bigint := NULL;
    category_depth smallint := 1;
BEGIN
    start_index := GREATEST(1, array_length(segments, 1) - 2);
    FOREACH part IN ARRAY segments[start_index:array_length(segments, 1)] LOOP
        SELECT id INTO category_id FROM knowledge_categories
         WHERE parent_id IS NOT DISTINCT FROM parent_category_id AND normalized_name = lower(trim(part));
        IF category_id IS NULL THEN
            INSERT INTO knowledge_categories(parent_id, name, normalized_name, depth)
            VALUES (parent_category_id, trim(part), lower(trim(part)), category_depth)
            RETURNING id INTO category_id;
        END IF;
        parent_category_id := category_id;
        category_depth := category_depth + 1;
    END LOOP;
    RETURN parent_category_id;
END;
$$;

CREATE OR REPLACE FUNCTION sync_knowledge_item_to_governance()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    category_id_value bigint;
    entry_id_value bigint;
    version_id_value bigint;
    next_version integer;
BEGIN
    category_id_value := ensure_knowledge_category(NEW.category);
    INSERT INTO knowledge_entries(category_id, standard_question, standard_question_normalized,
                                  source_key, lifecycle_status)
    VALUES (category_id_value, NEW.standard_question, NEW.standard_question_normalized,
            NEW.source_key, CASE WHEN NEW.is_active THEN 'active' ELSE 'disabled' END)
    ON CONFLICT (source_key) DO UPDATE SET
        category_id = excluded.category_id,
        standard_question = excluded.standard_question,
        standard_question_normalized = excluded.standard_question_normalized,
        lifecycle_status = excluded.lifecycle_status,
        updated_at = now()
    RETURNING id INTO entry_id_value;

    SELECT id INTO version_id_value FROM knowledge_versions
     WHERE knowledge_entry_id = entry_id_value AND content_hash = NEW.content_hash;
    IF version_id_value IS NULL THEN
        SELECT COALESCE(max(version_number), 0) + 1 INTO next_version
          FROM knowledge_versions WHERE knowledge_entry_id = entry_id_value;
        INSERT INTO knowledge_versions(
            knowledge_entry_id, version_number, standard_question, user_questions, keywords,
            original_answer, answer_blocks, search_text, search_tokens, term_frequencies,
            document_length, search_vector, embedding, embedding_model, embedding_model_version,
            embedding_dimension, embedding_generated_at, valid_from, valid_to, content_hash,
            change_reason
        ) VALUES (
            entry_id_value, next_version, NEW.standard_question, NEW.user_questions, NEW.keywords,
            NEW.original_answer, NEW.scenarios, NEW.search_text, NEW.search_tokens,
            NEW.term_frequencies, NEW.document_length, NEW.search_vector, NEW.embedding,
            NEW.embedding_model, NEW.embedding_model_version, NEW.embedding_dimension,
            NEW.embedding_generated_at, NEW.valid_from, NEW.valid_to, NEW.content_hash,
            COALESCE(NULLIF(NEW.change_reason, ''), CASE WHEN TG_OP = 'INSERT' THEN '初始导入' ELSE '知识内容更新' END)
        ) RETURNING id INTO version_id_value;
    END IF;
    UPDATE knowledge_entries SET current_version_id = version_id_value, updated_at = now()
     WHERE id = entry_id_value;

    IF NEW.embedding IS NULL AND (TG_OP = 'INSERT' OR OLD.content_hash IS DISTINCT FROM NEW.content_hash) THEN
        INSERT INTO knowledge_reindex_events(source_key, content_hash)
        VALUES (NEW.source_key, NEW.content_hash);
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_knowledge_item_to_governance ON knowledge_items;
CREATE TRIGGER trg_sync_knowledge_item_to_governance
AFTER INSERT OR UPDATE OF standard_question, category, user_questions, keywords, original_answer,
    search_text, is_active, valid_from, valid_to, content_hash, embedding
ON knowledge_items FOR EACH ROW EXECUTE FUNCTION sync_knowledge_item_to_governance();

-- 迁移前已存在的知识执行一次无内容变化更新，触发治理数据回填。
UPDATE knowledge_items SET standard_question = standard_question;
