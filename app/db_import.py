"""知识库事务导入。"""

import json

from app.repository import _connect
from app.text import normalize_query


UPSERT_SQL = """
INSERT INTO knowledge_items (
    source_key, standard_question, standard_question_normalized, category,
    user_questions, keywords, scenarios, original_answer, search_text,
    search_tokens, term_frequencies, document_length, search_vector,
    is_active, valid_from, valid_to, source_created_by, source_updated_by,
    source_created_at, source_updated_at, content_hash, current_version,
    embedding, embedding_model, embedding_model_version, embedding_dimension,
    embedding_generated_at, imported_at, updated_at
) VALUES (
    %(source_key)s, %(standard_question)s, %(standard_question_normalized)s, %(category)s,
    %(user_questions)s::jsonb, %(keywords)s::jsonb, %(scenarios)s::jsonb, %(original_answer)s,
    %(search_text)s, %(search_tokens)s::jsonb, %(term_frequencies)s::jsonb, %(document_length)s,
    to_tsvector('simple', %(token_text)s), %(is_active)s, %(valid_from)s, %(valid_to)s,
    %(source_created_by)s, %(source_updated_by)s, %(source_created_at)s, %(source_updated_at)s,
    %(content_hash)s, 1, %(embedding)s::vector, %(embedding_model)s,
    %(embedding_model_version)s, %(embedding_dimension)s, %(embedding_generated_at)s, now(), now()
)
ON CONFLICT (source_key) DO UPDATE SET
    standard_question = EXCLUDED.standard_question,
    standard_question_normalized = EXCLUDED.standard_question_normalized,
    category = EXCLUDED.category, user_questions = EXCLUDED.user_questions,
    keywords = EXCLUDED.keywords, scenarios = EXCLUDED.scenarios,
    original_answer = EXCLUDED.original_answer, search_text = EXCLUDED.search_text,
    search_tokens = EXCLUDED.search_tokens, term_frequencies = EXCLUDED.term_frequencies,
    document_length = EXCLUDED.document_length, search_vector = EXCLUDED.search_vector,
    is_active = EXCLUDED.is_active, valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    source_created_by = EXCLUDED.source_created_by, source_updated_by = EXCLUDED.source_updated_by,
    source_created_at = EXCLUDED.source_created_at, source_updated_at = EXCLUDED.source_updated_at,
    current_version = CASE WHEN knowledge_items.content_hash <> EXCLUDED.content_hash
        THEN knowledge_items.current_version + 1 ELSE knowledge_items.current_version END,
    content_hash = EXCLUDED.content_hash,
    embedding = CASE WHEN knowledge_items.content_hash <> EXCLUDED.content_hash
        THEN EXCLUDED.embedding ELSE COALESCE(EXCLUDED.embedding, knowledge_items.embedding) END,
    embedding_model = CASE WHEN knowledge_items.content_hash <> EXCLUDED.content_hash
        THEN EXCLUDED.embedding_model ELSE COALESCE(EXCLUDED.embedding_model, knowledge_items.embedding_model) END,
    embedding_model_version = CASE WHEN knowledge_items.content_hash <> EXCLUDED.content_hash
        THEN EXCLUDED.embedding_model_version ELSE COALESCE(EXCLUDED.embedding_model_version, knowledge_items.embedding_model_version) END,
    embedding_dimension = CASE WHEN knowledge_items.content_hash <> EXCLUDED.content_hash
        THEN EXCLUDED.embedding_dimension ELSE COALESCE(EXCLUDED.embedding_dimension, knowledge_items.embedding_dimension) END,
    embedding_generated_at = CASE WHEN knowledge_items.content_hash <> EXCLUDED.content_hash
        THEN EXCLUDED.embedding_generated_at ELSE COALESCE(EXCLUDED.embedding_generated_at, knowledge_items.embedding_generated_at) END,
    imported_at = now(), updated_at = now()
RETURNING id, current_version
"""


def _json(value):
    return json.dumps(value, ensure_ascii=False, default=str)


def embeddings_are_current(records, database_url):
    """确认数据库中每条来源、内容摘要和真实向量均与本次导入一致。"""
    expected = {record["source_key"]: record["content_hash"] for record in records}
    if not expected:
        return True
    with _connect(database_url) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT source_key, content_hash FROM knowledge_items WHERE embedding IS NOT NULL"
            )
            actual = {row["source_key"]: row["content_hash"] for row in cursor.fetchall()}
    return expected == actual


def import_records(records, database_url, embedding_metadata=None):
    """在单个事务中幂等导入；相同内容不会产生重复历史版本。"""
    with _connect(database_url) as connection:
        with connection.cursor() as cursor:
            for record in records:
                params = dict(record)
                for field in ("user_questions", "keywords", "scenarios", "search_tokens", "term_frequencies"):
                    params[field] = _json(params[field])
                params["token_text"] = " ".join(record["search_tokens"])
                params["embedding"] = _json(record["embedding"]) if record.get("embedding") is not None else None
                metadata = embedding_metadata or {}
                params["embedding_model"] = metadata.get("model")
                params["embedding_model_version"] = metadata.get("version")
                params["embedding_dimension"] = metadata.get("dimension")
                params["embedding_generated_at"] = metadata.get("generated_at")
                cursor.execute(UPSERT_SQL, params)
                row = cursor.fetchone()
                knowledge_id, version = row["id"], row["current_version"]
                cursor.execute("DELETE FROM knowledge_phrases WHERE knowledge_id = %s", (knowledge_id,))
                for phrase in record["phrases"]:
                    cursor.execute(
                        """INSERT INTO knowledge_phrases
                           (knowledge_id, phrase_type, value, normalized_value, position)
                           VALUES (%s, %s, %s, %s, %s)
                           ON CONFLICT (knowledge_id, phrase_type, normalized_value) DO NOTHING""",
                        (knowledge_id, phrase["type"], phrase["value"], normalize_query(phrase["value"]), phrase["position"]),
                    )
                snapshot = {key: value for key, value in record.items() if key not in {"embedding", "phrases"}}
                cursor.execute(
                    """INSERT INTO knowledge_item_versions
                       (knowledge_id, version, content_hash, snapshot, source_created_by,
                        source_updated_by, source_created_at, source_updated_at)
                       VALUES (%s, %s, %s, %s::jsonb, %s, %s, %s, %s)
                       ON CONFLICT (knowledge_id, content_hash) DO NOTHING""",
                    (knowledge_id, version, record["content_hash"], _json(snapshot),
                     record["source_created_by"], record["source_updated_by"],
                     record["source_created_at"], record["source_updated_at"]),
                )
        connection.commit()
    return len(records)
