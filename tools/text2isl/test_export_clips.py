import json
import tempfile
import unittest
from pathlib import Path

import export_clips


class ExportClipsTest(unittest.TestCase):
    def test_reads_the_viewer_file_and_keeps_only_coordinates(self):
        frame = {"frame": 0, "blendshapes": [0.5] * 52, "coordinates": [0.12345] * 144, "quaternions": [0.0] * 144}
        path = Path(tempfile.mkdtemp()) / "prediction.js"
        path.write_text('window.predictionDatabase = {"hi there": {"frames": [' + json.dumps(frame) + "]}};\n"
                        "window.predictionData = window.predictionDatabase['hi there'];", encoding="utf-8")
        [clip] = export_clips.clips(export_clips.read_predictions(path))
        self.assertEqual("hi there", clip["sentence"])
        self.assertEqual([[0.123] * 144], clip["frames"])
        self.assertEqual(25, clip["fps"])  # every ISL-CSLTR video is 25 fps

    def test_a_later_file_replaces_the_same_sentence(self):
        d = Path(tempfile.mkdtemp())
        for name, value in (("a.json", 0.1), ("b.json", 0.2)):
            (d / name).write_text(json.dumps({"hi ": {"frames": [{"coordinates": [value] * 144}]}}), encoding="utf-8")
        export_clips.OUT = d / "clips.json"
        export_clips.main(str(d / "a.json"), str(d / "b.json"))
        [clip] = json.loads(export_clips.OUT.read_text(encoding="utf-8"))["clips"]
        self.assertEqual(("hi", [[0.2] * 144]), (clip["sentence"], clip["frames"]))

    def test_skips_a_clip_with_short_frames(self):
        self.assertEqual([], export_clips.clips({"x": {"frames": [{"coordinates": [0.0] * 10}]}}))


if __name__ == "__main__":
    unittest.main()
