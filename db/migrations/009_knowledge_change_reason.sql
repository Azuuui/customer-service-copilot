-- 为已执行 008 的环境补充版本变更原因，并刷新同步函数。
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS change_reason text;

CREATE OR REPLACE FUNCTION sync_knowledge_item_to_governance()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    category_id_value bigint;
    entry_id_value bigint;
    version_id_value bigint;
    next_version integer;
BEGIN
    category_id_value := ensure_knowledge_category(NEW.category);
    INSERT INTO knowledge_entries(category_id, standard_question, standard_question_normalized, source_key, lifecycle_status)
    VALUES (category_id_value, NEW.standard_question, NEW.standard_question_normalized, NEW.source_key,
            CASE WHEN NEW.is_active THEN 'active' ELSE 'disabled' END)
    ON CONFLICT (source_key) DO UPDATE SET category_id=excluded.category_id,
        standard_question=excluded.standard_question, standard_question_normalized=excluded.standard_question_normalized,
        lifecycle_status=excluded.lifecycle_status, updated_at=now()
    RETURNING id INTO entry_id_value;
    SELECT id INTO version_id_value FROM knowledge_versions
     WHERE knowledge_entry_id=entry_id_value AND content_hash=NEW.content_hash ORDER BY version_number DESC LIMIT 1;
    IF version_id_value IS NULL THEN
        SELECT COALESCE(max(version_number),0)+1 INTO next_version FROM knowledge_versions WHERE knowledge_entry_id=entry_id_value;
        INSERT INTO knowledge_versions(knowledge_entry_id,version_number,standard_question,user_questions,keywords,
            original_answer,answer_blocks,search_text,search_tokens,term_frequencies,document_length,search_vector,
            embedding,embedding_model,embedding_model_version,embedding_dimension,embedding_generated_at,
            valid_from,valid_to,content_hash,change_reason)
        VALUES(entry_id_value,next_version,NEW.standard_question,NEW.user_questions,NEW.keywords,NEW.original_answer,
            NEW.scenarios,NEW.search_text,NEW.search_tokens,NEW.term_frequencies,NEW.document_length,NEW.search_vector,
            NEW.embedding,NEW.embedding_model,NEW.embedding_model_version,NEW.embedding_dimension,NEW.embedding_generated_at,
            NEW.valid_from,NEW.valid_to,NEW.content_hash,
            COALESCE(NULLIF(NEW.change_reason,''),CASE WHEN TG_OP='INSERT' THEN '初始导入' ELSE '知识内容更新' END))
        RETURNING id INTO version_id_value;
    END IF;
    UPDATE knowledge_entries SET current_version_id=version_id_value,updated_at=now() WHERE id=entry_id_value;
    IF NEW.embedding IS NULL AND (TG_OP='INSERT' OR OLD.content_hash IS DISTINCT FROM NEW.content_hash) THEN
        INSERT INTO knowledge_reindex_events(source_key,content_hash) VALUES(NEW.source_key,NEW.content_hash);
    END IF;
    RETURN NEW;
END;
$$;
