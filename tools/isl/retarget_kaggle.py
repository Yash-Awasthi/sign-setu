"""Signs for the word reader's labels that neither INCLUDE nor the ISLRTC dictionary covers, retargeted from the Kaggle
Indian Sign Language video dataset (Prasad Shet, MIT; the reader's own training videos) with the app's Holistic model.
Needs the MediaPipe venv. Run from the repository root, then refresh the content manifest:

    python -s tools/isl/retarget_kaggle.py [data_dir]   # videos in <data_dir>/isl_kaggle60, caches in <data_dir>/isl_more/retarget
    python tools/content/build_content.py --manifest-only
"""
import concurrent.futures
import json
import os
import pathlib
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import retarget as rt  # noqa: E402
import retarget_islrtc as islrtc  # noqa: E402

DATA_ROOT = pathlib.Path(os.environ.get("SIGNSETU_DATA", "ml-data"))
READER = rt.ROOT / "app" / "src" / "main" / "assets" / "content" / "models" / "sign_reader.json"
CATEGORY = "Kaggle"
TAKES = 6  # clips per word to choose the cleanest from
MAX_S = 8.0  # these takes hold the sign longer than the dictionary does
# The words.json Hindi where the word has an entry; these words are new to the app.
HINDI = {
    "bear": "भालू", "brinjal": "बैंगन", "budget": "बजट", "busy": "व्यस्त", "cabbage": "पत्तागोभी", "carrot": "गाजर",
    "cauliflower": "फूलगोभी", "chilli": "मिर्च", "close": "बंद करो", "cook": "खाना बनाना", "crocodile": "मगरमच्छ",
    "cry": "रोना", "cucumber": "खीरा", "deer": "हिरण", "drink": "पीना", "elephant": "हाथी", "exam": "परीक्षा",
    "fedup": "परेशान", "fever": "बुखार", "giraffe": "जिराफ़", "give": "देना", "hug": "गले लगाना", "injury": "चोट",
    "interview": "साक्षात्कार", "jump": "कूदना", "karnataka": "कर्नाटक", "lemon": "नींबू", "lion": "शेर",
    "maybe": "शायद", "monkey": "बंदर", "onion": "प्याज़", "peacock": "मोर", "pigeon": "कबूतर", "pour": "डालना",
    "radish": "मूली", "sparrow": "गौरैया", "still": "अभी भी", "switch": "स्विच", "tea": "चाय", "tiger": "बाघ",
    "turtle": "कछुआ", "umbrella": "छाता", "vegetables": "सब्ज़ियाँ", "volcano": "ज्वालामुखी",
    "what is your name": "आपका नाम क्या है", "writer": "लेखक", "wrong": "ग़लत",
}


def cached(job: tuple[pathlib.Path, pathlib.Path]) -> pathlib.Path:
    video, cache = job
    if not cache.exists():
        rec, fps = islrtc.landmarks(video)
        np.savez_compressed(cache, fps=fps, **rec)
    return cache


def main(data: pathlib.Path) -> None:
    videos = data / "isl_kaggle60" / "Video_Dataset" / "Video_Dataset"
    caches = data / "isl_more" / "retarget"
    caches.mkdir(parents=True, exist_ok=True)
    signs = json.loads(rt.OUT.read_text(encoding="utf-8"))
    have = {g["word"] for g in signs["gestures"] if not g.get("rec", "").startswith("Kaggle/")}
    folders = {f.name.lower(): f for f in videos.iterdir() if f.is_dir()}
    words = [w for w in json.loads(READER.read_text(encoding="utf-8"))["labels"] if w not in have and w in folders]
    jobs = {w: [(v, caches / f"{folders[w].name}__{v.stem}.npz") for v in sorted(folders[w].glob("*.mp4")) if "tilt" not in v.stem][:TAKES]
            for w in words}
    with concurrent.futures.ProcessPoolExecutor(6) as pool:
        list(pool.map(cached, [j for js in jobs.values() for j in js]))
    made = []
    for word in words:
        recs = []
        for video, cache in jobs[word]:
            z = np.load(cache)
            rec = {k: z[k] for k in z.files if k != "fps"}
            if len(rec["pose"]) >= 5 and rec["has_pose"].mean() > 0.8:
                recs.append((f"{folders[word].name}/{video.name}", rec, float(z["fps"])))
        if not recs:
            print("skip", word)
            continue
        rid, _, hands = rt.pick([(r[0], r[1]) for r in recs])
        # The cleanest take first, then the others: a take whose signing is cut too short or runs on is passed over.
        order = sorted(recs, key=lambda r: r[0] != rid)
        rid, keys = next(((i, k) for i, rec, fps in order for k in (rt.retarget(rec, hands, fps, first=True),
                          rt.retarget(rec, hands, fps, first=True, visible=True)) if islrtc.MIN_S <= k[-1]["t"] <= MAX_S),
                         (None, None))
        if keys is None:
            print("skip", word)
            continue
        made.append({"id": f"isl.{word}", "word": word, "hi": HINDI.get(word, ""), "category": CATEGORY, "rec": f"Kaggle/{rid}", "keys": keys})
        print(word, len(keys), "keys", flush=True)
    signs["gestures"] = [g for g in signs["gestures"] if not g.get("rec", "").startswith("Kaggle/")] + made
    signs["source"] = islrtc.SOURCE
    signs["categories"][CATEGORY] = {"en": "More words (recorded)", "hi": "और शब्द (रिकॉर्ड किए गए)"}
    rt.write(signs)
    print(f"Kaggle words: {len(made)}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DATA_ROOT)
