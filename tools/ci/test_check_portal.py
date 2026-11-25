"""The portal job keeps running check, test and build, and the portal keeps no write path."""

import pathlib
import shutil
import tempfile
import unittest

import check_portal

WORKFLOW = """name: ci
on:
  pull_request:

jobs:
  portal:
    runs-on: ubuntu-24.04
    defaults:
      run:
        working-directory: portal
    steps:
      - uses: actions/checkout@v5
      - run: npm ci --prefix ../avatar
      - run: npm ci
      - run: npx vp check
      - run: npx vp test
      - run: npx vp build

  tools:
    runs-on: ubuntu-24.04
"""

REVIEWS = """// Verdicts live in this browser only; the JSON export is the review record.
const STORE = "signsetu.reviews.v1";

export function save(all: Record<string, string>) {
  localStorage.setItem(STORE, JSON.stringify(all));
}
"""

APP = """export const App = () => null;
"""


class CheckPortalTest(unittest.TestCase):
    def setUp(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        self.src = self.root / "portal" / "src"
        self.src.mkdir(parents=True)
        (self.src / "reviews.ts").write_text(REVIEWS, encoding="utf-8")
        self.app = self.src / "App.tsx"
        self.app.write_text(APP, encoding="utf-8")
        self.workflow = self.root / "ci.yml"
        self.workflow.write_text(WORKFLOW, encoding="utf-8")

    def check(self):
        return check_portal.check(self.workflow, self.src)

    def test_a_clean_portal_passes(self):
        self.assertEqual([], self.check())

    def test_a_fetch_call_fails(self):
        self.app.write_text(APP + 'export const load = () => fetch("/session");\n', encoding="utf-8")
        self.assertTrue(any("fetch(" in e for e in self.check()))

    def test_a_write_method_fails(self):
        self.app.write_text(APP + 'export const send = { method: "POST" };\n', encoding="utf-8")
        self.assertTrue(any("method: POST/PUT/PATCH/DELETE" in e for e in self.check()))

    def test_the_test_step_cannot_be_dropped(self):
        self.workflow.write_text(WORKFLOW.replace("      - run: npx vp test\n", ""), encoding="utf-8")
        self.assertTrue(any("npx vp test" in e for e in self.check()))

    def test_an_import_of_app_session_code_fails(self):
        self.app.write_text(APP + 'import record from "../../app/src/main/java/SessionRecord";\n', encoding="utf-8")
        self.assertTrue(any("app/src/main/java" in e for e in self.check()))

    def test_a_storage_write_outside_reviews_fails(self):
        self.app.write_text(APP + 'localStorage.setItem("copy", "x");\n', encoding="utf-8")
        self.assertTrue(any("localStorage.setItem outside reviews.ts" in e for e in self.check()))

    def test_a_changed_review_key_fails(self):
        (self.src / "reviews.ts").write_text(REVIEWS.replace("signsetu.reviews.v1", "other"), encoding="utf-8")
        self.assertTrue(any("signsetu.reviews.v1" in e for e in self.check()))


class RepositoryPortalTest(unittest.TestCase):
    def test_the_repository_portal_is_gated(self):
        self.assertEqual([], check_portal.check())


if __name__ == "__main__":
    unittest.main()
