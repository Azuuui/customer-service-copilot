"""检索投影重建；只更新分词、短语与 Embedding，不处理业务生命周期。"""

import json
from collections import Counter

from app.embedding import BgeM3Embedder
from app.importer import compose_search_text
from app.repository import _connect
from app.text import normalize_query, tokenize_search_text


class KnowledgeIndexService:
    def __init__(self, database_url, embedder=None):
        self.database_url = database_url
        self.embedder = embedder or BgeM3Embedder()

    def reindex(self, source_key):
        with _connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(
                """SELECT id, source_key, standard_question, category, user_questions,
                          keywords, original_answer
                   FROM knowledge_items WHERE source_key = %s""",
                (source_key,),
            )
            row = cursor.fetchone()
            if row is None:
                raise KeyError("knowledge_not_found")
            record = {
                "standard_question": row["standard_question"],
                "category": row["category"],
                "user_questions": row["user_questions"],
                "keywords": row["keywords"],
                "original_reply": row["original_answer"],
            }
            search_text = compose_search_text(record)
            tokens = list(tokenize_search_text(search_text))
            vector = self.embedder.embed(search_text)
            metadata = self.embedder.metadata()
            cursor.execute(
                """UPDATE knowledge_items SET search_text=%s, search_tokens=%s::jsonb,
                       term_frequencies=%s::jsonb, document_length=%s,
                       search_vector=to_tsvector('simple', %s), embedding=%s::vector,
                       embedding_model=%s, embedding_model_version=%s, embedding_dimension=%s,
                       embedding_generated_at=%s, updated_at=now() WHERE id=%s""",
                (search_text, json.dumps(tokens, ensure_ascii=False),
                 json.dumps(dict(Counter(tokens)), ensure_ascii=False), len(tokens), " ".join(tokens),
                 json.dumps(vector), metadata["model"], metadata["version"], metadata["dimension"],
                 metadata["generated_at"], row["id"]),
            )
            cursor.execute("DELETE FROM knowledge_phrases WHERE knowledge_id=%s", (row["id"],))
            phrases = [("standard", row["standard_question"])]
            phrases.extend(("user_question", value) for value in row["user_questions"])
            phrases.extend(("keyword", value) for value in row["keywords"])
            phrases.extend(("keyword", line.strip()) for line in row["original_answer"].splitlines() if line.strip())
            for position, (phrase_type, value) in enumerate(phrases):
                cursor.execute(
                    """INSERT INTO knowledge_phrases(knowledge_id, phrase_type, value, normalized_value, position)
                       VALUES (%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING""",
                    (row["id"], phrase_type, value, normalize_query(value), position),
                )
            cursor.execute(
                """UPDATE knowledge_reindex_events SET event_status='completed', completed_at=now()
                   WHERE source_key=%s AND event_status='pending'""",
                (source_key,),
            )
            connection.commit()
        return {"source_key": source_key, "embedding_dimension": len(vector), "status": "ready"}
