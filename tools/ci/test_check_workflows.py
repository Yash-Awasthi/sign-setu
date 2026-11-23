"""The emulator job stays wide: every instrumented class it can run, on every app pull request."""

import pathlib
import shutil
import tempfile
import unittest

import check_workflows

WORKFLOW = """name: ci
on:
  pull_request:

jobs:
  app-changed:
    runs-on: ubuntu-24.04
    outputs:
      any: ${{ steps.f.outputs.any }}
    steps:
      - run: |
          if git diff --name-only BASE HEAD | grep -qE '^(app/|gradle/|settings\\.gradle\\.kts|build\\.gradle\\.kts|\\.github/workflows/ci\\.yml)'; then echo any=true; fi

  instrumented:
    needs: [version, app-changed]
    if: ${{ !cancelled() && (needs.app-changed.outputs.any == 'true' || needs.version.outputs.new == 'true' || github.event_name == 'workflow_dispatch') }}
    runs-on: ubuntu-24.04
    timeout-minutes: 45
    steps:
      - run: for c in AlphaTest BetaTest; do ./gradlew :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu.$c; done

  gitleaks:
    runs-on: ubuntu-24.04
"""

TEST_SOURCE = "import org.junit.Test\n\nclass {name} {{\n    @Test fun one() = Unit\n}}\n"


class CheckWorkflowsTest(unittest.TestCase):
    def setUp(self):
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, root, ignore_errors=True)
        self.tests = root / "androidTest"
        self.tests.mkdir()
        for name in ("AlphaTest", "BetaTest"):
            (self.tests / f"{name}.kt").write_text(TEST_SOURCE.format(name=name), encoding="utf-8")
        self.workflow = root / "ci.yml"
        self.workflow.write_text(WORKFLOW, encoding="utf-8")

    def check(self):
        return check_workflows.check(self.workflow, self.tests)

    def test_a_new_class_must_join_the_job(self):
        (self.tests / "GammaTest.kt").write_text(TEST_SOURCE.format(name="GammaTest"), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("GammaTest" in e for e in errors), errors)

    def test_a_device_only_class_is_allowed_to_stay_out(self):
        (self.tests / "SlowTest.kt").write_text(TEST_SOURCE.format(name="SlowTest"), encoding="utf-8")
        self.workflow.write_text(WORKFLOW.replace("AlphaTest BetaTest", "AlphaTest BetaTest SlowTest"), encoding="utf-8")
        errors = check_workflows.check(self.workflow, self.tests)
        self.assertFalse(any("SlowTest" in e for e in errors), errors)

    def test_the_job_must_need_the_changed_filter(self):
        self.workflow.write_text(WORKFLOW.replace("needs: [version, app-changed]", "needs: version"), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("app-changed" in e for e in errors), errors)

    def test_the_job_must_run_on_a_pull_request(self):
        self.workflow.write_text(
            WORKFLOW.replace("needs.app-changed.outputs.any == 'true' || ", ""), encoding="utf-8"
        )
        errors = self.check()
        self.assertTrue(any("pull request" in e for e in errors), errors)

    def test_the_timeout_is_kept(self):
        self.workflow.write_text(WORKFLOW.replace("timeout-minutes: 45", "timeout-minutes: 90"), encoding="utf-8")
        self.assertTrue(any("45-minute" in e for e in self.check()))

    def test_a_named_class_that_is_not_a_test_fails(self):
        self.workflow.write_text(WORKFLOW.replace("AlphaTest BetaTest", "AlphaTest BetaTest GhostTest"), encoding="utf-8")
        errors = self.check()
        self.assertTrue(any("GhostTest" in e for e in errors), errors)


class RepositoryWorkflowTest(unittest.TestCase):
    def test_the_repository_workflow_is_wide_enough(self):
        self.assertEqual([], check_workflows.check())


if __name__ == "__main__":
    unittest.main()
