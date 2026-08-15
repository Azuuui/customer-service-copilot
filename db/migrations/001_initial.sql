CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS schema_migrations (
    version text PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_items (
    id bigserial PRIMARY KEY,
    source_key text NOT NULL UNIQUE,
    standard_question text NOT NULL,
    standard_question_normalized text NOT NULL,
    category text NOT NULL,
    user_questions jsonb NOT NULL DEFAULT '[]'::jsonb,
    keywords jsonb NOT NULL DEFAULT '[]'::jsonb,
    scenarios jsonb NOT NULL DEFAULT '[]'::jsonb,
    original_answer text NOT NULL DEFAULT '',
    search_text text NOT NULL,
    search_tokens jsonb NOT NULL DEFAULT '[]'::jsonb,
    term_frequencies jsonb NOT NULL DEFAULT '{}'::jsonb,
    document_length integer NOT NULL DEFAULT 0,
    search_vector tsvector NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    valid_from timestamptz,
    valid_to timestamptz,
    source_created_by text,
    source_updated_by text,
    source_created_at timestamptz,
    source_updated_at timestamptz,
    content_hash text NOT NULL,
    current_version integer NOT NULL DEFAULT 1,
    embedding vector(1024),
    embedding_model text,
    embedding_model_version text,
    embedding_dimension integer,
    embedding_generated_at timestamptz,
    imported_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (embedding_dimension IS NULL OR embedding_dimension = 1024),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);

CREATE TABLE IF NOT EXISTS knowledge_phrases (
    id bigserial PRIMARY KEY,
    knowledge_id bigint NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    phrase_type text NOT NULL CHECK (phrase_type IN ('standard', 'user_question', 'keyword')),
    value text NOT NULL,
    normalized_value text NOT NULL,
    position integer NOT NULL DEFAULT 0,
    UNIQUE (knowledge_id, phrase_type, normalized_value)
);

CREATE TABLE IF NOT EXISTS knowledge_item_versions (
    id bigserial PRIMARY KEY,
    knowledge_id bigint NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    version integer NOT NULL,
    content_hash text NOT NULL,
    snapshot jsonb NOT NULL,
    change_type text NOT NULL DEFAULT 'import',
    source_created_by text,
    source_updated_by text,
    source_created_at timestamptz,
    source_updated_at timestamptz,
    imported_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (knowledge_id, content_hash),
    UNIQUE (knowledge_id, version)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_standard_normalized
    ON knowledge_items (standard_question_normalized);
CREATE INDEX IF NOT EXISTS idx_knowledge_active_validity
    ON knowledge_items (is_active, valid_from, valid_to);
CREATE INDEX IF NOT EXISTS idx_knowledge_phrase_exact
    ON knowledge_phrases (phrase_type, normalized_value);
CREATE INDEX IF NOT EXISTS idx_knowledge_search_vector
    ON knowledge_items USING gin (search_vector);
CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_hnsw
    ON knowledge_items USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL AND is_active = true;
