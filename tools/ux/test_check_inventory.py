import unittest

import check_inventory

GOOD = "| `StartKey` | Home | Start; Language; Tour | Back closes the app |\n"


class InventoryTest(unittest.TestCase):
    def test_a_complete_row_passes(self):
        self.assertEqual([], check_inventory.problems(GOOD, {"StartKey"}))

    def test_a_route_without_a_row_is_reported(self):
        self.assertEqual(
            ["SettingsKey: opens in the app but has no row"],
            check_inventory.problems(GOOD, {"StartKey", "SettingsKey"}),
        )

    def test_a_row_without_a_route_is_reported(self):
        self.assertEqual(
            [
                "SettingsKey: opens in the app but has no row",
                "StartKey: has a row but no entry in AppRoot.kt",
            ],
            check_inventory.problems(GOOD, {"SettingsKey"}),
        )

    def test_two_actions_are_not_three(self):
        row = "| `StartKey` | Home | Start; Language | Back closes the app |\n"
        self.assertEqual(
            ["StartKey: needs three primary actions separated by ';'"],
            check_inventory.problems(row, {"StartKey"}),
        )

    def test_the_real_document_matches_the_navigation(self):
        self.assertEqual([], check_inventory.problems(check_inventory.DOC.read_text(encoding="utf-8"), check_inventory.routes()))


if __name__ == "__main__":
    unittest.main()
