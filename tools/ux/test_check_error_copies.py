import unittest

import check_error_copies as check

GOOD = """
<string name="settings_assistant_missing">Not installed. Import the model file your department gives you.</string>
<string name="sign_to_type_no_camera">The camera is not available. Allow the camera in the phone's settings.</string>
<string name="voice_no_permission">Allow the microphone to use speech.</string>
<string name="second_screen_not_listed">Pair the two devices once in Bluetooth settings, then come back.</string>
<string name="export_failed">The phone may be full. Free some space and try again; the session is unchanged.</string>
"""


class ErrorCopyTest(unittest.TestCase):
    def test_each_state_names_a_fix(self):
        self.assertEqual([], check.problems(GOOD))

    def test_a_bare_failure_is_reported(self):
        bad = check.problems(GOOD.replace("Allow the camera in the phone's settings.", ""))
        self.assertEqual(["no camera: sign_to_type_no_camera names no fix"], bad)

    def test_an_error_code_is_reported(self):
        bad = check.problems(GOOD.replace("Free some space and try again", "Error 0x5"))
        self.assertIn("storage full: export_failed shows an error code", bad)

    def test_the_real_strings_pass(self):
        self.assertEqual([], check.problems(check.STRINGS.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
