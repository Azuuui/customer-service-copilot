import unittest

from app.bm25 import BM25Index, tokenize_search_text
from app.rrf import reciprocal_rank_fusion
from app.text import configure_dictionary, normalize_query


class RetrievalAlgorithmTests(unittest.TestCase):
    def test_chinese_tokenizer_uses_words_not_character_ngrams(self):
        configure_dictionary(['智能锁'])
        tokens = tokenize_search_text('智能锁维修 安装')
        self.assertIn('智能锁', tokens)
        self.assertIn('维修', tokens)
        self.assertNotIn('能锁', tokens)

    def test_bm25_ranks_exact_business_terms_first(self):
        docs = [
            {'id': 'lock', 'tokens': ['智能锁', '维修', '安装'], 'term_frequencies': {'智能锁': 1, '维修': 1, '安装': 1}, 'document_length': 3},
            {'id': 'lamp', 'tokens': ['灯具', '销售'], 'term_frequencies': {'灯具': 1, '销售': 1}, 'document_length': 2},
        ]
        ranked = BM25Index(docs).rank('智能锁维修')
        self.assertEqual(ranked[0][0], 'lock')
        self.assertGreater(ranked[0][1], ranked[1][1])

    def test_rrf_merges_two_ranked_lists_without_raw_score_addition(self):
        merged = reciprocal_rank_fusion([
            ['standard', 'shared', 'bm25-only'],
            ['vector-only', 'shared', 'standard'],
        ])
        self.assertEqual(merged[0][0], 'standard')
        self.assertEqual(merged[1][0], 'shared')
        self.assertIn('vector-only', [item[0] for item in merged])

    def test_query_normalization_is_stable_for_exact_matching(self):
        self.assertEqual(normalize_query('  智能锁，维修！ '), '智能锁 维修')


if __name__ == '__main__':
    unittest.main()
