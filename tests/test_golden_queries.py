import json
import unittest
from pathlib import Path

from app.search_service import HybridSearchService
from app.text import configure_dictionary, normalize_query, tokenize_search_text


class JsonRepository:
    def __init__(self, records):
        self.docs = {}
        for record in records:
            tokens = tokenize_search_text(" ".join([record["standard_question"], *record["user_questions"], *record["keywords"], record["category"]]))
            from collections import Counter
            self.docs[record["id"]] = {**record, "id": record["id"], "tokens": tokens, "term_frequencies": dict(Counter(tokens)), "document_length": len(tokens)}

    def exact_matches(self, query):
        rows = []
        for item_id, doc in self.docs.items():
            phrases = [doc["standard_question"], *doc["user_questions"], *doc["keywords"]]
            for index, phrase in enumerate(phrases):
                if normalize_query(phrase) == query:
                    rows.append((item_id, "standard" if index == 0 else "user_question" if index == 1 else "keyword"))
                    break
        return rows

    def active_documents(self):
        return list(self.docs.values())

    def get_documents(self, ids):
        return [self.docs[item_id] for item_id in ids if item_id in self.docs]


class GoldenQueryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        records = json.loads((Path(__file__).parents[1] / "data/demo-knowledge.json").read_text(encoding="utf-8"))
        words = [r["standard_question"] for r in records] + [k for r in records for k in r["keywords"]]
        configure_dictionary(words)
        cls.service = HybridSearchService(JsonRepository(records))

    def test_business_golden_queries_return_a_result(self):
        for query in ("垃圾处理器", "粉碎机", "智能锁", "灯具维修"):
            with self.subTest(query=query):
                response = self.service.search(query)
                self.assertTrue(response["results"], query)
                self.assertNotIn("match_type", response["results"][0])
                self.assertIn("match_methods", response["results"][0])


if __name__ == "__main__":
    unittest.main()
