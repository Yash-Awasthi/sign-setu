import json
import tempfile
import unittest
from pathlib import Path

import check_lint


def sarif(*results, rules=None) -> dict:
    rules = rules or {}
    return {
        "runs": [
            {
                "tool": {"driver": {"rules": [{"id": k, "defaultConfiguration": {"level": v}} for k, v in rules.items()]}},
                "results": [
                    {
                        "ruleId": rule,
                        "locations": [{"physicalLocation": {"artifactLocation": {"uri": f}}}],
                    }
                    for rule, f in results
                ],
            }
        ]
    }


class FindingsTest(unittest.TestCase):
    def test_reads_rule_file_and_severity(self):
        found = check_lint.findings(sarif(("UnusedResources", "app/src/main/res/x.xml"), rules={"UnusedResources": "warning"}))
        self.assertEqual([{"rule": "UnusedResources", "file": "app/src/main/res/x.xml", "level": "warning"}], found)

    def test_a_finding_without_a_file_is_still_a_finding(self):
        run = {"runs": [{"tool": {"driver": {"rules": []}}, "results": [{"ruleId": "GradleDependency", "locations": []}]}]}
        self.assertEqual([{"rule": "GradleDependency", "file": "", "level": "warning"}], check_lint.findings(run))

    def test_counts_by_rule_and_file_so_line_moves_do_not_matter(self):
        found = check_lint.findings(sarif(("Typos", "a.md"), ("Typos", "a.md"), ("Typos", "b.md")))
        self.assertEqual({("Typos", "a.md"): 2, ("Typos", "b.md"): 1}, check_lint.keyed(found))


class CompareTest(unittest.TestCase):
    known = {("UnusedResources", "app/x.xml"): 1, ("Typos", "docs/a.md"): 2}

    def test_a_run_within_the_list_is_quiet(self):
        found = [{"rule": "UnusedResources", "file": "app/x.xml", "level": "warning"}, {"rule": "Typos", "file": "docs/a.md", "level": "warning"}]
        # Typo counts: the list allows two, this run has one, so the entry is stale and must be struck.
        self.assertEqual(1, len(check_lint.compare(found, self.known)))

    def test_a_new_finding_fails(self):
        problems = check_lint.compare([{"rule": "HardcodedDebugMode", "file": "app/y.kt", "level": "warning"}], self.known)
        self.assertTrue(any("new finding: HardcodedDebugMode" in p for p in problems), problems)

    def test_a_finding_that_grew_fails(self):
        found = [{"rule": "Typos", "file": "docs/a.md", "level": "warning"}] * 3
        problems = check_lint.compare(found, self.known)
        self.assertTrue(any("now occurs 3 times, the list allows 2" in p for p in problems), problems)

    def test_a_stale_entry_fails_so_the_list_can_only_shrink(self):
        problems = check_lint.compare([], self.known)
        self.assertTrue(any("stale entry" in p for p in problems), problems)

    def test_an_error_is_a_blocker_whatever_the_list_says(self):
        found = [{"rule": "UnusedResources", "file": "app/x.xml", "level": "error"}]
        problems = check_lint.compare(found, self.known)
        self.assertTrue(any("ERROR" in p and "UnusedResources" in p for p in problems), problems)


class KnownFileTest(unittest.TestCase):
    def test_the_list_round_trips(self):
        found = check_lint.findings(sarif(("UnusedResources", "app/x.xml"), ("UnusedResources", "app/x.xml")))
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "known.json"
            check_lint.write_known(found, path)
            data = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(1, len(data["known"]))
            self.assertEqual(2, data["known"][0]["count"])
            self.assertEqual({("UnusedResources", "app/x.xml"): 2}, check_lint.load_known(path))

    def test_a_missing_list_holds_nothing(self):
        self.assertEqual({}, check_lint.load_known(Path("does-not-exist.json")))

    def test_the_repository_list_is_not_empty_and_has_no_errors(self):
        known = check_lint.load_known()
        self.assertTrue(known, "the list should record today's findings")
        self.assertFalse([k for k in known if not k[0] or not isinstance(k[1], str)])


if __name__ == "__main__":
    unittest.main()
