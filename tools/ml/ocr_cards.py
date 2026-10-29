"""Sentence-card OCR for the C sentence proxy: rapidocr on a few frames of each clip, one JSON line per frame
(clip, frame time, text, confidence). Runs from its own venv (C:\\work\\venvs\\ocr) so its onnxruntime and OpenCV
pins never meet MediaPipe's.

    python ocr_cards.py LIST OUT.jsonl [--frames 0.4,0.55,0.7,0.85] [--save DIR]

LIST holds clip paths under C:\\work\\ml-data, one per line; clips already in OUT are skipped.
"""
import json
import pathlib
import sys

import cv2
from rapidocr_onnxruntime import RapidOCR
from guard import DATA



def main(a):
    lst, out = pathlib.Path(a[0]), pathlib.Path(a[1])
    fracs = [float(x) for x in (a[a.index("--frames") + 1] if "--frames" in a else "0.4,0.55,0.7,0.85").split(",")]
    save = pathlib.Path(a[a.index("--save") + 1]) if "--save" in a else None
    done = set()
    if out.exists():
        done = {json.loads(l)["clip"] for l in out.read_text(encoding="utf-8").splitlines() if l.strip()}
    ocr = RapidOCR()
    with open(out, "a", encoding="utf-8") as f:
        for n, clip in enumerate(c for c in lst.read_text(encoding="utf-8").splitlines() if c and c not in done):
            cap = cv2.VideoCapture(str(DATA / clip))
            dur = cap.get(cv2.CAP_PROP_FRAME_COUNT) / (cap.get(cv2.CAP_PROP_FPS) or 25) * 1000
            for q in fracs:
                cap.set(cv2.CAP_PROP_POS_MSEC, dur * q)
                ok, img = cap.read()
                if not ok:
                    continue
                res, _ = ocr(img)
                lines = [(r[1], float(r[2])) for r in (res or [])]
                text = " ".join(t for t, _ in lines)
                conf = min((c for _, c in lines), default=0.0)
                f.write(json.dumps({"clip": clip, "ms": int(dur * q), "text": text, "confidence": round(conf, 3)},
                                   ensure_ascii=False) + "\n")
                if save is not None:
                    save.mkdir(parents=True, exist_ok=True)
                    cv2.imwrite(str(save / f"{n:03d}_{int(q * 100)}.jpg"), cv2.resize(img, None, fx=0.5, fy=0.5))
            f.flush()
            cap.release()


if __name__ == "__main__":
    main(sys.argv[1:])
