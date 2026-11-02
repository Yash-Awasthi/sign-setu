"""The app's language codes and the FLORES-200 codes the translation models use."""

FLORES = {
    "en": "eng_Latn", "hi": "hin_Deva", "bn": "ben_Beng", "ta": "tam_Taml", "te": "tel_Telu", "mr": "mar_Deva",
    "gu": "guj_Gujr", "kn": "kan_Knda", "ml": "mal_Mlym", "or": "ory_Orya", "pa": "pan_Guru", "as": "asm_Beng",
    "ne": "npi_Deva", "ur": "urd_Arab",
    # NLLB-200 only; IndicTrans2 does not cover Chhattisgarhi.
    "hne": "hne_Deva",
}

# IndicTrans2 covers the 22 scheduled languages; of the app's languages it lacks Chhattisgarhi, Gondi and Halbi.
INDICTRANS2 = {k for k in FLORES if k != "hne"}

# Whisper's own codes for speech recognition hints; languages it does not know are recognised without a hint.
WHISPER = {"en", "hi", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "as", "ne", "ur"}


def flores(code: str) -> str:
    try:
        return FLORES[code]
    except KeyError:
        raise ValueError(f"no translation for language {code!r}") from None
