-- 企业知识主体、不可变版本、三级类目、标签和预约发布。

CREATE TABLE IF NOT EXISTS knowledge_categories (
    id bigserial PRIMARY KEY,
    parent_id bigint REFERENCES knowledge_categories(id),
    name text NOT NULL,
    normalized_name text NOT NULL,
    depth smallint NOT NULL CHECK (depth BETWEEN 1 AND 3),
    sort_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_by bigint REFERENCES users(id),
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (parent_id, normalized_name),
    CHECK (parent_id IS NOT NULL OR depth = 1)
);

CREATE OR REPLACE FUNCTION validate_knowledge_category_depth()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_depth smallint;
BEGIN
    IF NEW.parent_id IS NULL THEN
        IF NEW.depth <> 1 THEN
            RAISE EXCEPTION '根类目深度必须为 1';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.id IS NOT NULL AND NEW.parent_id = NEW.id THEN
        RAISE EXCEPTION '类目不能作为自己的父类目';
    END IF;
    IF NEW.id IS NOT NULL AND EXISTS (
        WITH RECURSIVE category_ancestors AS (
            SELECT id, parent_id FROM knowledge_categories WHERE id = NEW.parent_id
            UNION ALL
            SELECT category.id, category.parent_id
            FROM knowledge_categories category
            JOIN category_ancestors ancestor ON category.id = ancestor.parent_id
        )
        SELECT 1 FROM category_ancestors WHERE id = NEW.id
    ) THEN
        RAISE EXCEPTION '类目不能形成循环';
    END IF;
    SELECT depth INTO parent_depth FROM knowledge_categories WHERE id = NEW.parent_id;
    IF parent_depth IS NULL THEN
        RAISE EXCEPTION '父类目不存在';
    END IF;
    IF parent_depth >= 3 OR NEW.depth <> parent_depth + 1 THEN
        RAISE EXCEPTION '类目最多三级且深度必须连续';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_knowledge_category_depth
BEFORE INSERT OR UPDATE OF parent_id, depth ON knowledge_categories
FOR EACH ROW EXECUTE FUNCTION validate_knowledge_category_depth();

CREATE TABLE IF NOT EXISTS knowledge_tags (
    id bigserial PRIMARY KEY,
    name text NOT NULL,
    normalized_name text NOT NULL UNIQUE,
    color text,
    is_active boolean NOT NULL DEFAULT true,
    created_by bigint REFERENCES users(id),
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_entries (
    id bigserial PRIMARY KEY,
    category_id bigint NOT NULL REFERENCES knowledge_categories(id),
    standard_question text NOT NULL,
    standard_question_normalized text NOT NULL,
    source_key text UNIQUE,
    lifecycle_status text NOT NULL DEFAULT 'active'
        CHECK (lifecycle_status IN ('active', 'disabled')),
    current_version_id bigint,
    created_by bigint REFERENCES users(id),
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (category_id, standard_question_normalized)
);

CREATE TABLE IF NOT EXISTS knowledge_versions (
    id bigserial PRIMARY KEY,
    knowledge_entry_id bigint NOT NULL REFERENCES knowledge_entries(id),
    version_number integer NOT NULL CHECK (version_number > 0),
    standard_question text NOT NULL,
    user_questions jsonb NOT NULL DEFAULT '[]'::jsonb,
    keywords jsonb NOT NULL DEFAULT '[]'::jsonb,
    related_question_refs jsonb NOT NULL DEFAULT '[]'::jsonb,
    answer_type text NOT NULL DEFAULT 'NORMAL' CHECK (answer_type IN ('NORMAL', 'JSON')),
    service_decision jsonb,
    original_answer text NOT NULL,
    answer_blocks jsonb NOT NULL DEFAULT '[]'::jsonb,
    search_text text NOT NULL,
    search_tokens jsonb NOT NULL DEFAULT '[]'::jsonb,
    term_frequencies jsonb NOT NULL DEFAULT '{}'::jsonb,
    document_length integer NOT NULL DEFAULT 0 CHECK (document_length >= 0),
    search_vector tsvector NOT NULL,
    embedding vector(1024),
    embedding_model text,
    embedding_model_version text,
    embedding_dimension integer CHECK (embedding_dimension IS NULL OR embedding_dimension = 1024),
    embedding_generated_at timestamptz,
    valid_from timestamptz,
    valid_to timestamptz,
    content_hash text NOT NULL,
    change_reason text NOT NULL,
    created_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (knowledge_entry_id, version_number),
    UNIQUE (knowledge_entry_id, content_hash),
    CHECK (jsonb_typeof(user_questions) = 'array'),
    CHECK (jsonb_typeof(keywords) = 'array'),
    CHECK (jsonb_typeof(answer_blocks) = 'array'),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);

ALTER TABLE knowledge_entries
    ADD CONSTRAINT fk_knowledge_entries_current_version
    FOREIGN KEY (current_version_id) REFERENCES knowledge_versions(id);

CREATE OR REPLACE FUNCTION prevent_knowledge_version_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '知识版本为不可变快照，请创建新版本';
END;
$$;

CREATE TRIGGER trg_prevent_knowledge_version_mutation
BEFORE UPDATE OR DELETE ON knowledge_versions
FOR EACH ROW EXECUTE FUNCTION prevent_knowledge_version_mutation();

CREATE TABLE IF NOT EXISTS knowledge_version_tags (
    knowledge_version_id bigint NOT NULL REFERENCES knowledge_versions(id),
    tag_id bigint NOT NULL REFERENCES knowledge_tags(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (knowledge_version_id, tag_id)
);

CREATE TABLE IF NOT EXISTS knowledge_relations (
    source_entry_id bigint NOT NULL REFERENCES knowledge_entries(id),
    target_entry_id bigint NOT NULL REFERENCES knowledge_entries(id),
    relation_type text NOT NULL DEFAULT 'related_question'
        CHECK (relation_type IN ('related_question')),
    created_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (source_entry_id, target_entry_id, relation_type),
    CHECK (source_entry_id <> target_entry_id)
);

CREATE TABLE IF NOT EXISTS knowledge_publication_schedule (
    id bigserial PRIMARY KEY,
    knowledge_version_id bigint NOT NULL UNIQUE REFERENCES knowledge_versions(id),
    publication_status text NOT NULL
        CHECK (publication_status IN ('scheduled', 'published', 'retired', 'cancelled')),
    publish_at timestamptz NOT NULL,
    retire_at timestamptz,
    published_at timestamptz,
    retired_at timestamptz,
    scheduled_by bigint REFERENCES users(id),
    cancelled_by bigint REFERENCES users(id),
    cancellation_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (retire_at IS NULL OR retire_at > publish_at)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_categories_parent_sort
    ON knowledge_categories(parent_id, sort_order, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_categories_unique_path
    ON knowledge_categories(COALESCE(parent_id, 0), normalized_name);
CREATE INDEX IF NOT EXISTS idx_knowledge_entries_status_category
    ON knowledge_entries(lifecycle_status, category_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_versions_entry_created
    ON knowledge_versions(knowledge_entry_id, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_versions_search_vector
    ON knowledge_versions USING gin(search_vector);
CREATE INDEX IF NOT EXISTS idx_knowledge_versions_embedding_hnsw
    ON knowledge_versions USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_knowledge_publication_due
    ON knowledge_publication_schedule(publication_status, publish_at);
