import hashlib
import json
import pathlib
import tempfile
import unittest

import check_content

ASSISTANT_HASH = "a" * 64
SENTENCE_HASH = "b" * 64


class ContentCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.assets = self.root / "assets"
        self.symbols = self.root / "symbols"
        self.docs_dir = self.root / "docs"
        (self.assets / "cards").mkdir(parents=True)
        (self.symbols / "icons").mkdir(parents=True)
        self.docs_dir.mkdir()
        (self.assets / "cards" / "yes.webp").write_bytes(b"webp")
        (self.symbols / "icons" / "yes.svg").write_text("<svg/>", encoding="utf-8")
        (self.symbols / "map.json").write_text(json.dumps({"yes": "icons/yes.svg"}), encoding="utf-8")
        self.model = self.root / "LocalModel.kt"
        self.docs = {"KNOWN_MODELS": self.docs_dir / "10-on-device-assistant.md",
                     "KNOWN_SENTENCE_MODELS": self.docs_dir / "15-gloss-to-text.md"}
        self.write("languages.json", {"languages": [{"code": "en"}, {"code": "hi"}]})
        self.write("cards.json", {
            "categories": [{"id": "answers", "label": {"en": "Answers", "hi": "जवाब"}}],
            "cards": [{"id": "yes", "category": "answers", "label": {"en": "Yes", "hi": "हाँ"}}],
        })
        self.write("triage.json", {
            "groups": [{"id": "safety", "label": {"en": "Safety", "hi": "सुरक्षा"}}],
            "questions": [{"id": "q1", "group": "safety", "text": {"en": "Are you hurt?", "hi": "क्या आपको चोट है?"},
                           "cards": ["yes"], "gesture": "yes.ok"}],
        })
        self.write("gestures.json", {"gestures": [{"id": "yes.ok"}]})
        self.write("signs.json", {"gestures": []})
        self.write("isl_signs.json", {"gestures": []})
        self.write("words.json", {"words": [{"w": ["yes"], "sign": "yes.ok", "card": "yes"}]})
        self.write("phrases.json", {"phrases": [{
            "id": "want", "label": {"en": "Do you want …?", "hi": "क्या आपको … चाहिए?"},
            "signs": ["yes.ok", "$"],
            "slots": [{"card": "yes", "sign": "yes.ok", "text": {"en": "Do you want water?", "hi": "क्या पानी चाहिए?"}}],
        }]})
        self.write("subject_ui.json", {"is_this_it": {"en": "Is this it?", "hi": "क्या यही है?"}})
        self.write_allow_list(ASSISTANT_HASH, SENTENCE_HASH)
        self.write_docs(ASSISTANT_HASH, SENTENCE_HASH)
        self.write_manifest()

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name: str, value) -> None:
        (self.assets / name).write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def read(self, name: str):
        return json.loads((self.assets / name).read_text(encoding="utf-8"))

    def write_allow_list(self, assistant: str, sentence: str) -> None:
        self.model.write_text(
            "val KNOWN_SENTENCE_MODELS = setOf(\n"
            "    // Qwen2.5 (Apache-2.0), from a repository.\n"
            f'    "{sentence}",\n'
            ")\n"
            "val KNOWN_MODELS = setOf(\n"
            f'    "{assistant}",\n'
            ")\n",
            encoding="utf-8")

    def write_docs(self, assistant: str, sentence: str) -> None:
        self.docs["KNOWN_MODELS"].write_text(f"| model | `{assistant}` |\n", encoding="utf-8")
        self.docs["KNOWN_SENTENCE_MODELS"].write_text(f"| helper | `{sentence}` |\n", encoding="utf-8")

    def write_manifest(self) -> None:
        files = {p.relative_to(self.assets).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in sorted(self.assets.rglob("*")) if p.is_file()}
        (self.assets / "manifest.json").write_text(json.dumps({"version": 1, "files": files}), encoding="utf-8")

    def check(self):
        return check_content.check(self.assets, self.symbols, self.model, self.docs)

    def test_a_consistent_bundle_has_no_errors(self):
        self.assertEqual(self.check(), [])

    def test_a_missing_card_image_is_reported(self):
        (self.assets / "cards" / "yes.webp").unlink()
        self.assertIn("card yes: missing cards/yes.webp", self.check())

    def test_unknown_and_duplicate_references_are_reported(self):
        triage = self.read("triage.json")
        triage["questions"][0]["cards"] = ["ghost"]
        triage["questions"][0]["gesture"] = "ghost.sign"
        triage["questions"].append({"id": "q1", "group": "nowhere", "text": {"en": "x", "hi": "x"}})
        self.write("triage.json", triage)
        errors = self.check()
        self.assertTrue(any("unknown card 'ghost'" in e for e in errors))
        self.assertTrue(any("unknown gesture 'ghost.sign'" in e for e in errors))
        self.assertTrue(any("unknown group 'nowhere'" in e for e in errors))
        self.assertTrue(any("duplicate ids ['q1']" in e for e in errors))

    def test_a_missing_language_in_a_subject_facing_label_is_reported(self):
        cards = self.read("cards.json")
        cards["cards"][0]["label"].pop("hi")
        self.write("cards.json", cards)
        self.assertIn("card yes: missing languages ['hi']", self.check())

    def test_follow_up_questions_must_name_a_real_parent(self):
        triage = self.read("triage.json")
        triage["questions"][0]["after"] = "missing:yes"
        self.write("triage.json", triage)
        self.assertTrue(any("unknown after question 'missing'" in e for e in self.check()))

        triage["questions"][0]["after"] = "q1"
        self.write("triage.json", triage)
        self.assertTrue(any("malformed after 'q1'" in e for e in self.check()))

    def test_a_phrase_slot_must_point_at_a_real_card_and_sign(self):
        phrases = self.read("phrases.json")
        phrases["phrases"][0]["signs"] = ["ghost.sign", "$"]
        phrases["phrases"][0]["slots"][0]["card"] = "ghost"
        self.write("phrases.json", phrases)
        errors = self.check()
        self.assertTrue(any("unknown sign 'ghost.sign'" in e for e in errors))
        self.assertTrue(any("unknown slot card 'ghost'" in e for e in errors))

    def test_a_card_without_a_symbol_map_entry_is_reported(self):
        (self.symbols / "map.json").write_text("{}", encoding="utf-8")
        self.assertIn("card yes: no entry in content/symbols/map.json", self.check())

    def test_an_orphan_symbol_is_reported(self):
        (self.symbols / "icons" / "extra.svg").write_text("<svg/>", encoding="utf-8")
        self.assertIn("content/symbols/icons/extra.svg: orphan, no card in map.json uses it", self.check())

    def test_a_card_image_whose_hash_differs_from_the_manifest_is_reported(self):
        manifest = self.read("manifest.json")
        manifest["files"]["cards/yes.webp"] = "0" * 64
        self.write("manifest.json", manifest)
        errors = self.check()
        self.assertTrue(any(e.startswith("cards/yes.webp: sha256 ")
                            and e.endswith("manifest.json records " + "0" * 64) for e in errors))

    def test_the_manifest_must_list_exactly_the_files_present(self):
        manifest = self.read("manifest.json")
        manifest["files"]["cards/gone.webp"] = "0" * 64
        self.write("manifest.json", manifest)
        self.assertIn("manifest.json: lists cards/gone.webp, which is absent from the bundle", self.check())

        (self.assets / "stray.json").write_text("{}", encoding="utf-8")
        self.assertIn("stray.json: present in the bundle, not listed in manifest.json", self.check())

    def test_an_allow_list_hash_missing_from_its_doc_is_reported(self):
        self.write_docs(ASSISTANT_HASH, "c" * 64)
        errors = self.check()
        self.assertTrue(any(SENTENCE_HASH in e and "15-gloss-to-text.md" in e for e in errors))

    def test_a_malformed_allow_list_hash_is_reported(self):
        self.write_allow_list(ASSISTANT_HASH, "abc")
        errors = self.check()
        self.assertTrue(any("'abc' is not 64 lowercase hex characters" in e for e in errors))

    def test_the_real_repository_passes(self):
        self.assertEqual(check_content.check(), [])


if __name__ == "__main__":
    unittest.main()
