import pathlib
import tempfile
import unittest
from unittest import mock

import check_notices
import licences
import notices
from sbom import flavoured_lines, shipped_lines

LOCK = notices.LOCK
VERSION = "0.8.52"


class RenderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lines = LOCK.read_text(encoding="utf-8").splitlines()
        cls.map = licences.load()
        cls.text = notices.render(cls.lines, cls.map, VERSION)

    def test_the_component_list_is_the_flavoured_set_not_the_retired_union(self):
        self.assertLess(len(flavoured_lines(self.lines)), len(shipped_lines(self.lines)))
        self.assertIn(f"bundles the {len(flavoured_lines(self.lines))} third-party components",
                      self.text)

    def test_every_flavoured_component_is_named_exactly_once(self):
        rows = notices.component_rows(self.lines, self.map)
        self.assertEqual(len(rows), len(flavoured_lines(self.lines)))
        for licence_key, group, artifact, version, _, _ in rows:
            self.assertEqual(self.text.count(f"| `{group}:{artifact}`"), 1,
                             f"{group}:{artifact} appears more than once ({licence_key})")

    def test_components_are_grouped_by_licence_with_the_obligation(self):
        rows = notices.component_rows(self.lines, self.map)
        for licence_key in ("Apache-2.0", "BSD-3-Clause", "Android SDK License"):
            count = sum(1 for row in rows if row[0] == licence_key)
            self.assertGreater(count, 0)
            self.assertIn(f"### {licence_key} ({count} component", self.text)
        self.assertIn("keep the copyright notice and this permission notice", self.text)

    def test_the_dual_licence_elections_are_binding_sentences(self):
        self.assertIn("`net.java.dev.jna:jna` is distributed under **Apache-2.0**", self.text)
        self.assertIn("JNA is not used under the LGPL", self.text)
        self.assertIn("`org.checkerframework:checker-compat-qual` is distributed under **MIT**",
                      self.text)
        self.assertIn("(elected grant: Apache-2.0)", self.text)
        self.assertIn("(elected grant: MIT)", self.text)

    def test_the_full_licence_texts_are_in_the_document(self):
        for fragment in ("Apache License", "Redistribution and use in source and binary forms",
                         "Permission is hereby granted, free of charge"):
            self.assertIn(fragment, self.text)

    def test_a_component_with_no_resolved_licence_stops_the_render(self):
        licence_map = dict(self.map)
        coord = licences.coordinate(flavoured_lines(self.lines)[0])
        licence_map[coord] = {"licences": [], "source": "unresolved"}
        with self.assertRaises(SystemExit):
            notices.render(self.lines, licence_map, VERSION)

    def test_the_render_is_stable(self):
        again = notices.render(self.lines, self.map, VERSION)
        self.assertEqual(self.text, again)


class CheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = pathlib.Path(self.tmp.name) / "THIRD-PARTY-NOTICES.md"
        self.lines = LOCK.read_text(encoding="utf-8").splitlines()
        self.good = notices.render(self.lines, licences.load(), VERSION)
        self.path.write_text(self.good, encoding="utf-8", newline="\n")

    def tearDown(self):
        self.tmp.cleanup()

    def test_a_current_file_has_no_drift(self):
        self.assertEqual(check_notices.check(self.path, version=VERSION), [])

    def test_a_mutated_file_reports_drift_and_the_cli_exits_1(self):
        self.path.write_text(self.good.replace("Apache-2.0", "Apache-2.0 ", 1),
                             encoding="utf-8", newline="\n")
        self.assertTrue(check_notices.check(self.path, version=VERSION))
        with mock.patch("sys.stderr"), mock.patch("sys.stdout"):
            self.assertEqual(check_notices.main(["--notices", str(self.path)]), 1)

    def test_a_missing_file_is_reported(self):
        self.path.unlink()
        self.assertTrue(check_notices.check(self.path, version=VERSION))

    def test_the_committed_file_is_current(self):
        self.assertEqual(check_notices.check(notices.OUT, version=VERSION), [])


if __name__ == "__main__":
    unittest.main()
