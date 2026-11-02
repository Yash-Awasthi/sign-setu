"""Downloads the brain's models once, so it can then run with no internet.

    python -m brain.fetch [--asr small] [--translator indictrans2|nllb|both]

Models go to HF_HOME (Hugging Face's cache folder). IndicTrans2 is gated on Hugging Face: accept its licence on the model
pages with the department's account, then log in once (`hf auth login`) or set HF_TOKEN before running this. NLLB-200
needs no login. Each model prints "ok" or "FAIL" with the reason, and the exit status is the number of failures.
"""
import argparse
import sys

INDICTRANS2 = ["ai4bharat/indictrans2-en-indic-dist-200M", "ai4bharat/indictrans2-indic-en-dist-200M"]
NLLB = "facebook/nllb-200-distilled-600M"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asr", default="small", help="faster-whisper size")
    ap.add_argument("--translator", default="both", choices=["indictrans2", "nllb", "both"])
    a = ap.parse_args()
    failed = 0

    def step(name, fn):
        nonlocal failed
        try:
            fn()
            print(f"ok   {name}", flush=True)
        except Exception as e:
            failed += 1
            print(f"FAIL {name}: {type(e).__name__}: {e}", flush=True)

    def whisper():
        from faster_whisper import WhisperModel

        WhisperModel(a.asr, device="cpu", compute_type="int8")

    def seq2seq(repo):
        # Loading once both downloads the files and proves they load, including IndicTrans2's own model code.
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        trust = repo.startswith("ai4bharat/")
        AutoTokenizer.from_pretrained(repo, trust_remote_code=trust)
        AutoModelForSeq2SeqLM.from_pretrained(repo, trust_remote_code=trust)

    step(f"speech to text (faster-whisper {a.asr})", whisper)
    if a.translator in ("indictrans2", "both"):
        for repo in INDICTRANS2:
            step(repo, lambda r=repo: seq2seq(r))
    if a.translator in ("nllb", "both"):
        step(NLLB, lambda: seq2seq(NLLB))
    sys.exit(failed)


if __name__ == "__main__":
    main()
