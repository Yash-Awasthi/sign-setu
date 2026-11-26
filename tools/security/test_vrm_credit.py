import pathlib
import tempfile
import unittest

import vrm_credit

MODEL = vrm_credit.MODEL
CREDIT = vrm_credit.OUT
SETTINGS = CREDIT.parent / "SettingsDialogs.kt"

# The bytes the credit was written for. A replaced model fails here on purpose: its metadata, and
# so its credit and permission, must be read again before it ships.
SHIPPED_SHA256 = "9d6f561af051cdc5375a8849579dd330370a0a07c02205a0344dd44110405ad1"


class MetaTest(unittest.TestCase):
    def test_the_shipped_model_is_the_one_the_credit_was_written_for(self):
        self.assertEqual(vrm_credit.digest_of(MODEL), SHIPPED_SHA256,
                         "assets/avatar/model.vrm changed: regenerate AvatarCredit.kt and re-read "
                         "the model's licence before shipping it")

    def test_the_metadata_states_every_field_the_credit_needs(self):
        meta = vrm_credit.meta_of()
        for field in ("name", "authors", "licenseUrl", "creditNotation"):
            self.assertTrue(meta.get(field), f"VRM meta has no {field}; credit cannot be generated")
        self.assertEqual(meta["authors"], ["Leo"])
        self.assertEqual(meta["name"], "ISL Sample 1")
        self.assertEqual(meta["licenseUrl"], "https://vrm.dev/licenses/1.0/")

    def test_the_model_requires_credit_so_the_app_must_carry_one(self):
        meta = vrm_credit.meta_of()
        self.assertEqual(meta["creditNotation"], "required")
        fields = vrm_credit.credit_fields(meta)
        self.assertTrue(fields["credit_required"])
        self.assertIn(fields["title"], vrm_credit.entry_name(fields))
        self.assertIn(fields["author"], vrm_credit.entry_name(fields))
        self.assertIn(fields["licence_url"], vrm_credit.text(fields))

    def test_a_missing_meta_field_is_an_error_not_a_guess(self):
        meta = vrm_credit.meta_of()
        for field in ("name", "authors", "licenseUrl", "creditNotation"):
            broken = dict(meta)
            broken.pop(field)
            with self.assertRaisesRegex(ValueError, field):
                vrm_credit.credit_fields(broken)

    def test_an_unknown_licence_url_is_an_error(self):
        meta = dict(vrm_credit.meta_of(), licenseUrl="https://example.invalid/terms")
        with self.assertRaisesRegex(ValueError, "example.invalid"):
            vrm_credit.credit_fields(meta)


class GeneratedFileTest(unittest.TestCase):
    def test_the_committed_kotlin_is_what_the_model_renders(self):
        expected = vrm_credit.render(vrm_credit.meta_of())
        self.assertEqual(CREDIT.read_text(encoding="utf-8"), expected,
                         "run python tools/security/vrm_credit.py --write")

    def test_no_meta_field_is_typed_by_hand_into_the_kotlin(self):
        source = CREDIT.read_text(encoding="utf-8")
        self.assertIn(vrm_credit.kotlin_string("Leo"), source)
        self.assertIn(vrm_credit.kotlin_string("https://vrm.dev/licenses/1.0/"), source)

    def test_the_licences_dialog_uses_the_generated_entry(self):
        settings = SETTINGS.read_text(encoding="utf-8")
        self.assertIn("AvatarCredit.ENTRY_NAME to AvatarCredit.TEXT", settings)
        self.assertIn("internal val LICENCES", settings)

    def test_rendering_the_same_meta_twice_is_identical(self):
        meta = vrm_credit.meta_of()
        self.assertEqual(vrm_credit.render(meta), vrm_credit.render(meta))


class CliTest(unittest.TestCase):
    def test_check_mode_exits_0_on_the_committed_file_and_1_on_a_changed_one(self):
        self.assertEqual(vrm_credit.main([]), 0)
        with tempfile.TemporaryDirectory() as tmp:
            out = pathlib.Path(tmp) / "AvatarCredit.kt"
            out.write_text("package app.signsetu.ui.settings\n", encoding="utf-8")
            self.assertEqual(vrm_credit.main(["--out", str(out)]), 1)
            self.assertEqual(vrm_credit.main(["--out", str(out), "--write"]), 0)
            self.assertEqual(vrm_credit.main(["--out", str(out)]), 0)


if __name__ == "__main__":
    unittest.main()
