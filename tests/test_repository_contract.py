import unittest

from app.repository import ACTIVE_SQL


class RepositoryContractTests(unittest.TestCase):
    def test_formal_query_filter_excludes_inactive_and_expired_rows(self):
        self.assertIn("is_active = true", ACTIVE_SQL)
        self.assertIn("valid_from IS NULL OR valid_from <= now()", ACTIVE_SQL)
        self.assertIn("valid_to IS NULL OR valid_to > now()", ACTIVE_SQL)


if __name__ == "__main__":
    unittest.main()
