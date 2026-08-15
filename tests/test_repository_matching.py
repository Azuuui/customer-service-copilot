import unittest

from app.repository import _one_edit_apart


class RepositoryMatchingTests(unittest.TestCase):
    def test_one_character_omission_is_tolerated(self):
        self.assertTrue(_one_edit_apart("电热水头", "电热水龙头"))

    def test_multiple_differences_are_rejected(self):
        self.assertFalse(_one_edit_apart("电热水头", "空气开关"))


if __name__ == "__main__":
    unittest.main()
