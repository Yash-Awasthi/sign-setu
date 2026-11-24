import unittest

import seal_agreement


class FieldsTest(unittest.TestCase):
    SEAL = "SS1|abc|3|head|root|9474-ca87-91d8-0b9d|sig|key"

    def test_names_the_fields_both_readers_share(self):
        self.assertEqual(
            {"session": "abc", "count": "3", "head": "head", "root": "root", "digest": "9474-ca87-91d8-0b9d"},
            seal_agreement.fields_of(self.SEAL),
        )

    def test_a_payload_that_is_not_a_seal_is_refused(self):
        with self.assertRaises(ValueError):
            seal_agreement.fields_of("nonsense")


class CompareTest(unittest.TestCase):
    APP = {
        "verdict": "MATCHES_DEVICE",
        "session": "abc",
        "count": 3,
        "head": "head",
        "root": "root",
        "digest": "9474-ca87-91d8-0b9d",
    }
    TOOL = {"session": "abc", "count": "3", "head": "head", "root": "root", "digest": "9474-ca87-91d8-0b9d"}

    def test_agreement_is_silent(self):
        self.assertEqual([], seal_agreement.compare(self.APP, self.TOOL, "valid"))

    def test_a_different_chain_head_is_reported(self):
        problems = seal_agreement.compare(self.APP, dict(self.TOOL, head="other"), "valid")
        self.assertEqual(1, len(problems))
        self.assertIn("head", problems[0])

    def test_a_different_entry_count_is_reported(self):
        problems = seal_agreement.compare(self.APP, dict(self.TOOL, count="4"), "valid")
        self.assertEqual(1, len(problems))
        self.assertIn("count", problems[0])

    def test_the_off_phone_check_saying_invalid_against_a_matching_app_is_a_disagreement(self):
        problems = seal_agreement.compare(self.APP, self.TOOL, "invalid")
        self.assertEqual(1, len(problems))
        self.assertIn("invalid", problems[0])

    def test_an_unsigned_seal_the_off_phone_check_accepts_still_disagrees_with_a_matching_app(self):
        problems = seal_agreement.compare(dict(self.APP, verdict="UNSIGNED"), self.TOOL, "valid")
        self.assertEqual(1, len(problems))
        self.assertIn("UNSIGNED", problems[0])

    def test_the_app_finding_differences_where_the_seal_holds_is_a_disagreement(self):
        problems = seal_agreement.compare(dict(self.APP, verdict="DIFFERS_FROM_DEVICE"), self.TOOL, "valid")
        self.assertEqual(1, len(problems))

    def test_a_signature_valid_verdict_holds_without_the_record(self):
        self.assertEqual([], seal_agreement.compare(dict(self.APP, verdict="SIGNATURE_VALID"), self.TOOL, "valid"))


if __name__ == "__main__":
    unittest.main()
