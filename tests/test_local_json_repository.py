import unittest
from pathlib import Path

from app.json_repository import JsonKnowledgeRepository


class JsonKnowledgeRepositoryTests(unittest.TestCase):
    def test_local_repository_returns_original_excel_answer(self):
        repository = JsonKnowledgeRepository(Path(__file__).parents[1] / "data/demo-knowledge.json")
        item_id, _ = repository.exact_matches("购买灯具")[0]
        document = repository.get_documents([item_id])[0]
        self.assertEqual(document["original_answer"], document["original_reply"])
        self.assertTrue(document["tokens"])


if __name__ == "__main__":
    unittest.main()
