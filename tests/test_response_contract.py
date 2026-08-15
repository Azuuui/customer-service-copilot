import unittest

from app.response_contract import build_search_response


class ResponseContractTests(unittest.TestCase):
    def test_pagination_returns_four_results_from_offset_without_reordering(self):
        rows = [{'id': str(i), 'standard_question': f'问题{i}', 'category': '测试', 'original_answer': str(i), 'scenarios': []} for i in range(8)]
        response = build_search_response('测试', rows, limit=4, offset=4)
        self.assertEqual([item['id'] for item in response['results']], ['4', '5', '6', '7'])
        self.assertEqual(response['offset'], 4)

    def test_response_exposes_business_fields_without_generated_answer(self):
        response = build_search_response(
            query='智能锁维修',
            results=[{
                'id': 'lock',
                'standard_question': '智能锁维修/安装',
                'category': '客服中心知识库/产品分类/修锁换锁',
                'original_answer': '录入智能锁开锁/安装',
                'scenarios': [{'raw_text': '录入智能锁开锁/安装', 'serviceability': '可承接', 'ticket_recommendations': ['智能锁开锁/安装'], 'tags': [], 'notes': []}],
                'match_methods': ['direct'],
                'match_type': '直接命中',
                'rrf_score': 0.0,
            }],
        )
        self.assertEqual(response['results'][0]['original_answer'], '录入智能锁开锁/安装')
        self.assertNotIn('generated_answer', response['results'][0])
        self.assertNotIn('serviceability', response['results'][0])
        self.assertNotIn('scenarios', response['results'][0])
        self.assertEqual(response['results'][0]['match_methods'], ['direct'])
        self.assertEqual(response['results'][0]['answer_blocks'][0]['text'], '录入智能锁开锁/安装')
        self.assertNotIn('serviceability', response['results'][0]['answer_blocks'][0])
        self.assertEqual(response['limit'], 5)

    def test_multiline_original_answer_has_one_display_block_per_line(self):
        response = build_search_response('漏水', [{
            'id': 'leak', 'standard_question': '漏水录入条件', 'category': '水电',
            'original_answer': '明管问题→录入水管维修\n模糊表述→录入精准测漏',
            'scenarios': [], 'match_methods': ['direct'], 'match_type': '直接命中',
        }])
        self.assertEqual([block['text'] for block in response['results'][0]['answer_blocks']], [
            '明管问题→录入水管维修', '模糊表述→录入精准测漏',
        ])


if __name__ == '__main__':
    unittest.main()
