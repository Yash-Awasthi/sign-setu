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

    def test_skips_a_clip_with_short_frames(self):
        self.assertEqual([], export_clips.clips({"x": {"frames": [{"coordinates": [0.0] * 10}]}}))


if __name__ == "__main__":
    unittest.main()
