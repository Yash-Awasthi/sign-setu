import importlib.util
import pathlib
import unittest

SPEC = importlib.util.spec_from_file_location("build_natural_audio", pathlib.Path(__file__).resolve().parent / "build_natural_audio.py")
natural = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(natural)


class NaturalAudioTest(unittest.TestCase):
    def test_english_and_hindi_use_elevenlabs_and_the_rest_cartesia(self):
        self.assertEqual("elevenlabs", natural.provider("en"))
        self.assertEqual("elevenlabs", natural.provider("hi"))
        self.assertEqual("cartesia", natural.provider("ta"))
        self.assertEqual("cartesia", natural.provider("hne"))

    def test_languages_without_a_voice_keep_parler(self):
        for code in ("as", "ne", "or", "ur", "gon", "hlb"):
            self.assertIsNone(natural.provider(code))

    def test_each_language_has_one_voice(self):
        voiced_here = {c for c in natural.CARTESIA_VOICES} | natural.ELEVENLABS
        self.assertEqual(voiced_here, natural.NATURAL_ONLY)


if __name__ == "__main__":
    unittest.main()
