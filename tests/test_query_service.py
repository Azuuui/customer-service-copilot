import unittest

from app.search_service import HybridSearchService


class FakeEmbedder:
    model_name = 'BAAI/bge-m3'
    model_version = 'test-fixture'
    dimension = 3

    def embed(self, text):
        return [1.0, 0.0, 0.0]


class FakeRepository:
    def __init__(self):
        self.docs = {
            'lock': {
                'id': 'lock', 'standard_question': '智能锁维修/安装',
                'standard_question_normalized': '智能锁维修安装',
                'user_questions': ['智能锁坏了怎么修'], 'keywords': ['智能锁', '密码锁'],
                'category': '客服中心知识库/产品分类/修锁换锁',
                'original_answer': '录入智能锁开锁/安装', 'scenarios': [],
                'tokens': ['智能锁', '维修', '安装'],
                'term_frequencies': {'智能锁': 1, '维修': 1, '安装': 1}, 'document_length': 3,
            },
            'lamp': {
                'id': 'lamp', 'standard_question': '购买灯具',
                'standard_question_normalized': '购买灯具',
                'user_questions': ['灯具可以买吗'], 'keywords': ['灯具'],
                'category': '客服中心知识库/产品分类/水电维修',
                'original_answer': '需要确认后录入灯具销售', 'scenarios': [],
                'tokens': ['灯具', '销售'],
                'term_frequencies': {'灯具': 1, '销售': 1}, 'document_length': 2,
            },
        }

    def exact_matches(self, normalized_query):
        return [('lock', 'standard')] if normalized_query == '智能锁维修 安装' else []

    def active_documents(self):
        return list(self.docs.values())

    def vector_search(self, vector, limit):
        return [('lamp', 0.1)]

    def get_documents(self, ids):
        return [self.docs[item_id] for item_id in ids if item_id in self.docs]


class SearchServiceTests(unittest.TestCase):
    def test_exact_standard_match_is_first_and_keeps_raw_answer(self):
        service = HybridSearchService(FakeRepository(), FakeEmbedder())
        response = service.search('智能锁维修/安装')
        self.assertEqual(response['results'][0]['id'], 'lock')
        self.assertEqual(response['results'][0]['original_answer'], '录入智能锁开锁/安装')
        self.assertNotIn('match_type', response['results'][0])

    def test_empty_query_returns_explicit_error_contract(self):
        service = HybridSearchService(FakeRepository(), FakeEmbedder())
        with self.assertRaises(ValueError):
            service.search('  ')


if __name__ == '__main__':
    unittest.main()
