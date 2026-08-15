"""数据库不可用时使用项目内知识 JSON 的只读检索仓储。"""

import json
from collections import Counter
from pathlib import Path

from app.text import configure_dictionary, normalize_query, tokenize_search_text


class JsonKnowledgeRepository:
    def __init__(self, path: Path):
        records = json.loads(path.read_text(encoding="utf-8"))
        configure_dictionary([record["standard_question"] for record in records])
        self.documents = {}
        for record in records:
            search_text = " ".join([
                record["standard_question"],
                *record.get("user_questions", []),
                *record.get("keywords", []),
                record.get("category", ""),
                record.get("original_reply", ""),
            ])
            tokens = tokenize_search_text(search_text)
            self.documents[record["id"]] = {
                **record,
                "original_answer": record.get("original_reply", ""),
                "tokens": tokens,
                "term_frequencies": dict(Counter(tokens)),
                "document_length": len(tokens),
            }

    def exact_matches(self, normalized_query):
        matches = []
        for item_id, document in self.documents.items():
            phrase_groups = (
                ("standard", [document["standard_question"]]),
                ("user_question", document.get("user_questions", [])),
                ("keyword", document.get("keywords", [])),
            )
            for phrase_type, phrases in phrase_groups:
                normalized_phrases = [normalize_query(phrase) for phrase in phrases]
                if normalized_query in normalized_phrases:
                    matches.append((item_id, phrase_type))
                    break
                if any(normalized_query in phrase for phrase in normalized_phrases):
                    matches.append((item_id, "keyword"))
                    break
        return matches

    def active_documents(self):
        return list(self.documents.values())

    def vector_search(self, _vector, _limit):
        return []

    def get_documents(self, ids):
        return [self.documents[item_id] for item_id in ids if item_id in self.documents]

    def health(self):
        return {"active_count": len(self.documents), "embedded_count": 0, "total_count": len(self.documents)}
