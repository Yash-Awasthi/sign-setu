import contextlib
import io
import json
import pathlib
import tempfile
import unittest

import check_dependencies

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = ROOT / "app" / "gradle.lockfile"
SNAPSHOT = ROOT / "tools" / "security" / "data" / "osv-snapshot-2026-11-27.json"

FIXED_VULN = {
    "id": "GHSA-test-0000-0000",
    "summary": "test deserialization issue",
    "severity": [{"type": "CVSS_V3", "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H"}],
    "aliases": ["CVE-2026-0001"],
    "database_specific": {"severity": "HIGH"},
    "affected": [
        {
            "package": {"ecosystem": "Maven", "name": "com.example:widget"},
            "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "1.0"}, {"fixed": "2.0.0"}]}],
        },
        {
            "package": {"ecosystem": "PyPI", "name": "widget"},
            "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}, {"fixed": "9.9"}]}],
        },
    ],
}

UNFIXED_VULN = {
    "id": "GHSA-test-1111-1111",
    "summary": "test issue with no fix",
    "database_specific": {"severity": "LOW"},
    "affected": [
        {
            "package": {"ecosystem": "Maven", "name": "com.example:widget"},
            "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}]}],
        }
    ],
}


def snapshot(pairs, vulns=None):
    """A snapshot document shaped like the committed one, with no network involved."""
    vulns = vulns or {}
    wanted = {}
    for vid, vuln in vulns.items():
        for affected in vuln["affected"]:
            if affected["package"]["ecosystem"] == "Maven":
                wanted.setdefault(affected["package"]["name"], []).append(vid)
    return {
        "generated": "2026-11-27",
        "ecosystem": "Maven",
        "source": "https://api.osv.dev/v1",
        "lockfile": {"path": "app/gradle.lockfile", "sha256": "0" * 64},
        "components": [{"package": package, "version": version} for package, version in pairs],
        "results": [
            {"package": package, "version": version, "vuln_ids": sorted(wanted.get(package, []))}
            for package, version in pairs
        ],
        "vulns": vulns,
    }


class OfflineGateTest(unittest.TestCase):
    def run_gate(self, pairs, data):
        with tempfile.TemporaryDirectory() as tmp:
            lock = pathlib.Path(tmp) / "gradle.lockfile"
            lock.write_text("".join(f"{package}:{version}=standardReleaseRuntimeClasspath\n" for package, version in pairs),
                            encoding="utf-8")
            path = pathlib.Path(tmp) / "snapshot.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            out, err = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = check_dependencies.main(["--offline", "--lock", str(lock), "--snapshot", str(path)])
            return code, out.getvalue(), err.getvalue()

    def test_a_vulnerable_triple_below_its_fix_fails(self):
        pairs = [("com.example:widget", "1.5.0")]
        code, out, _ = self.run_gate(pairs, snapshot(pairs, {FIXED_VULN["id"]: FIXED_VULN}))
        self.assertEqual(code, 1)
        self.assertIn("GHSA-test-0000-0000 (CVE-2026-0001) HIGH com.example:widget:1.5.0 fixed-in 2.0.0", out)

    def test_a_vulnerable_triple_at_or_above_its_fix_passes(self):
        pairs = [("com.example:widget", "2.0.0")]
        code, out, _ = self.run_gate(pairs, snapshot(pairs, {FIXED_VULN["id"]: FIXED_VULN}))
        self.assertEqual(code, 0)
        self.assertIn("1 vulnerabilities, 0 below a fixed release", out)

    def test_a_vulnerability_with_no_fix_is_printed_but_passes(self):
        pairs = [("com.example:widget", "1.5.0")]
        code, out, _ = self.run_gate(pairs, snapshot(pairs, {UNFIXED_VULN["id"]: UNFIXED_VULN}))
        self.assertEqual(code, 0)
        self.assertIn("fixed-in no fixed release", out)

    def test_an_empty_batch_passes(self):
        code, out, _ = self.run_gate([], snapshot([]))
        self.assertEqual(code, 0)
        self.assertIn("0 components, 0 vulnerabilities, 0 below a fixed release", out)

    def test_a_triple_the_snapshot_never_queried_fails(self):
        data = snapshot([("com.example:widget", "1.5.0")])
        code, _, err = self.run_gate([("com.example:widget", "1.5.0"), ("com.example:extra", "1.0")], data)
        self.assertEqual(code, 1)
        self.assertIn("snapshot does not cover com.example:extra:1.0", err)


class ParsingTest(unittest.TestCase):
    def test_only_release_runtime_lines_are_components(self):
        lines = [
            "# a comment",
            "a.first:alpha:1.0=standardReleaseRuntimeClasspath,debugRuntimeClasspath",
            "a.first:alpha:1.0=nearbyReleaseRuntimeClasspath",
            "b.tool:lint:9.9=releaseLintChecksClasspath",
            "c.test:unit:1.1=standardDebugUnitTestRuntimeClasspath",
        ]
        self.assertEqual(check_dependencies.components(lines), [("a.first:alpha", "1.0")])

    def test_versions_order_numerically_not_as_text(self):
        self.assertEqual(check_dependencies.compare_versions("33.3.1-android", "33.7.2"), -1)
        self.assertEqual(check_dependencies.compare_versions("4.26.1", "4.27.5"), -1)
        self.assertEqual(check_dependencies.compare_versions("4.0.0", "4.0.0-rc-1"), 1)
        self.assertEqual(check_dependencies.compare_versions("1.0", "1.0"), 0)

    def test_fixed_after_ignores_fixes_below_the_pin_and_other_ecosystems(self):
        vuln = {
            "affected": [
                {"package": {"ecosystem": "Maven", "name": "g:a"}, "ranges": [{"events": [{"fixed": "3.25.5"}]}]},
                {"package": {"ecosystem": "Maven", "name": "g:a"}, "ranges": [{"events": [{"fixed": "4.27.5"}]}]},
                {"package": {"ecosystem": "PyPI", "name": "g:a"}, "ranges": [{"events": [{"fixed": "9.9"}]}]},
            ]
        }
        self.assertEqual(check_dependencies.fixed_after(vuln, "g:a", "4.26.1"), "4.27.5")
        self.assertIsNone(check_dependencies.fixed_after(vuln, "g:a", "4.28.0"))

    def test_query_ids_follows_the_page_token_of_a_single_query(self):
        calls = []

        def post(body):
            calls.append(body)
            queries = body["queries"]
            results = []
            for query in queries:
                if "page_token" not in query:
                    results.append({"vulns": [{"id": "OSV-1"}], "next_page_token": "more"})
                else:
                    results.append({"vulns": [{"id": "OSV-2"}]})
            return {"results": results}

        rows = check_dependencies.query_ids([("g:a", "1.0")], post=post)
        self.assertEqual(rows, [{"package": "g:a", "version": "1.0", "vuln_ids": ["OSV-1", "OSV-2"]}])
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[1]["queries"][0]["page_token"], "more")

    def test_the_committed_snapshot_queries_every_shipped_triple(self):
        data = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
        pairs = check_dependencies.components(LOCK.read_text(encoding="utf-8").splitlines())
        self.assertEqual(len(pairs), 276)
        self.assertEqual([(row["package"], row["version"]) for row in data["results"]], pairs)
        self.assertEqual([(row["package"], row["version"]) for row in data["components"]], pairs)
        queried = {(row["package"], row["version"]): row["vuln_ids"] for row in data["results"]}
        self.assertEqual(queried[("com.google.guava:guava", "33.3.1-android")], ["GHSA-xxph-c9ww-hj94"])
        self.assertEqual(queried[("com.google.protobuf:protobuf-javalite", "4.26.1")], ["GHSA-735f-pc8j-v9w8"])
        self.assertEqual(queried[("com.google.guava:guava", "33.7.2-android")], [])


if __name__ == "__main__":
    unittest.main()
