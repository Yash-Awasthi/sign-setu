import json
import pathlib
import tempfile
import unittest
from unittest import mock

import sbom
import sbom_lockfile

LOCK = pathlib.Path(__file__).resolve().parents[2] / "app" / "gradle.lockfile"
LINES = [
    "z.last:thing:2.0=releaseRuntimeClasspath",
    "a.first:alpha:1.0=standardReleaseRuntimeClasspath,debugRuntimeClasspath",
    "m.mid:kernel:3.1=nearbyReleaseRuntimeClasspath",
    "t.tool:lint:9.9=lintClassPath",
    "u.unit:test:1.1=standardReleaseCompileClasspath",
]


class DocumentTest(unittest.TestCase):
    def test_only_release_runtime_lines_become_components_sorted_by_purl(self):
        doc = sbom_lockfile.document(LINES, commit="abc123", project="p", version="1.0")
        self.assertEqual(doc["bomFormat"], "CycloneDX")
        self.assertEqual(doc["specVersion"], "1.5")
        self.assertEqual([c["purl"] for c in doc["components"]], [
            "pkg:maven/a.first/alpha@1.0",
            "pkg:maven/m.mid/kernel@3.1",
            "pkg:maven/z.last/thing@2.0",
        ])
        self.assertEqual(doc["components"][0]["group"], "a.first")
        self.assertEqual(doc["components"][0]["name"], "alpha")
        self.assertEqual(doc["components"][0]["version"], "1.0")

    def test_metadata_names_the_project_and_the_commit(self):
        doc = sbom_lockfile.document(LINES, commit="abc123", project="p", version="1.0")
        component = doc["metadata"]["component"]
        self.assertEqual((component["name"], component["version"]), ("p", "1.0"))
        self.assertIn({"name": "sign-setu:commit", "value": "abc123"}, component["properties"])

    def test_serial_number_is_stable_for_the_same_inputs_and_moves_with_the_commit(self):
        first = sbom_lockfile.document(LINES, commit="abc123", project="p", version="1.0")["serialNumber"]
        again = sbom_lockfile.document(LINES, commit="abc123", project="p", version="1.0")["serialNumber"]
        moved = sbom_lockfile.document(LINES, commit="def456", project="p", version="1.0")["serialNumber"]
        self.assertEqual(first, again)
        self.assertNotEqual(first, moved)

    def test_real_lockfile_matches_the_filter_count_and_every_purl_is_maven(self):
        lines = LOCK.read_text(encoding="utf-8").splitlines()
        doc = sbom_lockfile.document(lines, commit="abc123")
        shipped = sbom.shipped_lines(lines)
        self.assertEqual(len(doc["components"]), len(shipped))
        self.assertTrue(all(c["purl"].startswith("pkg:maven/") for c in doc["components"]))
        self.assertEqual(len({c["purl"] for c in doc["components"]}), len(doc["components"]))

    def test_main_writes_the_document_and_reports_the_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            lock = pathlib.Path(tmp) / "gradle.lockfile"
            lock.write_text("\n".join(LINES) + "\n", encoding="utf-8")
            out = pathlib.Path(tmp) / "sbom.json"
            with mock.patch("sys.stdout"):
                self.assertEqual(sbom_lockfile.main(lock, out, "abc123"), 0)
            doc = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(len(doc["components"]), 3)
            self.assertEqual(doc["serialNumber"], sbom_lockfile.document(LINES, "abc123")["serialNumber"])

    def test_commit_falls_back_to_unknown_when_git_is_unavailable(self):
        with mock.patch.dict("os.environ", {}, clear=True), \
                mock.patch("subprocess.run", side_effect=OSError("no git")):
            self.assertEqual(sbom_lockfile.commit_sha(), "unknown")


if __name__ == "__main__":
    unittest.main()



