"""PostgreSQL 数据访问层。"""

import json

from app.config import DATABASE_URL


ACTIVE_SQL = """
is_active = true
AND (valid_from IS NULL OR valid_from <= now())
AND (valid_to IS NULL OR valid_to > now())
"""


def _connect(database_url=DATABASE_URL):
    try:
        import psycopg
        from psycopg.rows import dict_row
    except ImportError as exc:
        raise RuntimeError("缺少 psycopg，请先安装 requirements.txt") from exc
    return psycopg.connect(database_url, row_factory=dict_row)


def _one_edit_apart(left, right):
    """短业务词容错：只接受一次插入、删除或替换，避免宽泛模糊命中。"""
    if abs(len(left) - len(right)) > 1:
        return False
    if len(left) > len(right):
        left, right = right, left
    if len(left) == len(right):
        return sum(a != b for a, b in zip(left, right)) == 1
    index = differences = 0
    for char in right:
        if index < len(left) and left[index] == char:
            index += 1
        else:
            differences += 1
            if differences > 1:
                return False
    return True


class PostgresKnowledgeRepository:
    def __init__(self, database_url=DATABASE_URL):
        self.database_url = database_url

    def exact_matches(self, normalized_query):
        sql = f"""
            WITH candidates AS (
                SELECT ki.id::text AS id,
                       CASE WHEN kp.normalized_value = %s THEN kp.phrase_type ELSE 'keyword' END AS phrase_type,
                       CASE WHEN kp.normalized_value = %s THEN 0 ELSE 1 END AS match_rank,
                       CASE kp.phrase_type WHEN 'standard' THEN 1 WHEN 'user_question' THEN 2 ELSE 3 END AS phrase_rank,
                       row_number() OVER (
                           PARTITION BY ki.id
                           ORDER BY CASE WHEN kp.normalized_value = %s THEN 0 ELSE 1 END,
                                    CASE kp.phrase_type WHEN 'standard' THEN 1 WHEN 'user_question' THEN 2 ELSE 3 END
                       ) AS candidate_rank
                FROM knowledge_phrases kp
                JOIN knowledge_items ki ON ki.id = kp.knowledge_id
                WHERE (kp.normalized_value = %s OR kp.normalized_value LIKE '%%' || %s || '%%')
                  AND {ACTIVE_SQL}
            )
            SELECT id, phrase_type FROM candidates WHERE candidate_rank = 1
            ORDER BY match_rank, phrase_rank, id::bigint
        """
        fuzzy_rows = []
        with _connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(sql, (normalized_query,) * 5)
            rows = cursor.fetchall()
            if len(normalized_query) >= 4:
                cursor.execute(
                    f"""SELECT ki.id::text AS id, kp.normalized_value
                        FROM knowledge_phrases kp JOIN knowledge_items ki ON ki.id = kp.knowledge_id
                        WHERE length(kp.normalized_value) BETWEEN %s AND %s
                          AND left(kp.normalized_value, 1) = %s AND right(kp.normalized_value, 1) = %s
                          AND {ACTIVE_SQL}""",
                    (len(normalized_query) - 1, len(normalized_query) + 1,
                     normalized_query[0], normalized_query[-1]),
                )
                fuzzy_rows = cursor.fetchall()
        matches = [(row["id"], row["phrase_type"]) for row in rows]
        seen = {item_id for item_id, _ in matches}
        for row in fuzzy_rows:
            if row["id"] not in seen and _one_edit_apart(normalized_query, row["normalized_value"]):
                matches.append((row["id"], "keyword"))
                seen.add(row["id"])
        return matches

    def active_documents(self):
        sql = f"""
            SELECT id::text AS id, search_tokens AS tokens,
                   term_frequencies, document_length
            FROM knowledge_items WHERE {ACTIVE_SQL}
        """
        with _connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(sql)
            return list(cursor.fetchall())

    def vector_search(self, vector, limit):
        sql = f"""
            SELECT id::text AS id, 1 - (embedding <=> %s::vector) AS score
            FROM knowledge_items
            WHERE embedding IS NOT NULL AND {ACTIVE_SQL}
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """
        encoded = json.dumps(vector)
        with _connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(sql, (encoded, encoded, limit))
            return [(row["id"], float(row["score"])) for row in cursor.fetchall()]

    def get_documents(self, ids):
        if not ids:
            return []
        sql = f"""
            SELECT id::text AS id, source_key, standard_question, category,
                   user_questions, keywords, scenarios, original_answer,
                   valid_from, valid_to
            FROM knowledge_items
            WHERE id = ANY(%s::bigint[]) AND {ACTIVE_SQL}
        """
        with _connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(sql, ([int(item_id) for item_id in ids],))
            return list(cursor.fetchall())

    def health(self):
        sql = f"""
            SELECT count(*) FILTER (WHERE {ACTIVE_SQL}) AS active_count,
                   count(*) FILTER (WHERE embedding IS NOT NULL AND {ACTIVE_SQL}) AS embedded_count,
                   count(*) AS total_count
            FROM knowledge_items
        """
        with _connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(sql)
            return dict(cursor.fetchone())
