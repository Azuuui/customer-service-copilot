CREATE TABLE IF NOT EXISTS knowledge_bulk_operations (
    id bigserial PRIMARY KEY,
    operation_type text NOT NULL CHECK (operation_type IN ('move_category','replace_tags')),
    snapshot jsonb NOT NULL,
    created_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    undone_at timestamptz,
    undone_by bigint REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_bulk_operations_created
    ON knowledge_bulk_operations(created_at DESC);
