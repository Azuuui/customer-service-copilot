import unittest
from pathlib import Path


class WebContractTests(unittest.TestCase):
    def test_page_uses_original_answer_blocks_without_serviceability_badges(self):
        root = Path(__file__).parents[1]
        javascript = (root / "web/app.js").read_text(encoding="utf-8")
        html = (root / "web/index.html").read_text(encoding="utf-8")
        self.assertIn("answer_blocks", javascript)
        self.assertNotIn("scenario.serviceability", javascript)
        self.assertNotIn("承接规则", html)

    def test_submit_handler_does_not_shadow_search_function_with_query_text(self):
        javascript = (Path(__file__).parents[1] / "web/app.js").read_text(encoding="utf-8")
        self.assertNotIn("const query = input.value.trim()", javascript)
        self.assertIn("app.js?v=", (Path(__file__).parents[1] / "web/index.html").read_text(encoding="utf-8"))

    def test_collapsed_result_uses_three_line_preview_and_hides_full_blocks(self):
        javascript = (Path(__file__).parents[1] / "web/app.js").read_text(encoding="utf-8")
        self.assertIn("answer-preview", javascript)
        self.assertIn("answer-full", javascript)
        self.assertIn("slice(0, 3)", javascript)


if __name__ == "__main__":
    unittest.main()
