"""Stand-ins for what identifies a person, put in before text goes to a model outside the station and taken out of its reply.

The phone names the person's names and details it knows (PersonalDetails.kt); numbers, emails, links and ID numbers
are found here. The same rules as the phone's masking for the gateway (assistant/Pseudonymizer.kt).
"""
import re

STAND_INS = ["Ravi", "Arun", "Kiran", "Gopal", "Mohan"]
MIN_DIGITS = 6
PATTERNS = [
    (re.compile(r"[\w.%+-]+@[\w.-]+\.[^\W\d_]{2,}"), "email"),
    (re.compile(r"(?i)\b(?:https?://|www\.)\S+"), "link"),
    (re.compile(r"\b[A-Z]{5}\d{4}[A-Z]\b"), "id"),
    (re.compile(r"(?<![^\W\d_])[+(]?\d(?:[\d ()\-]{4,}\d)"), "number"),
]


def word(text: str, flags: int = 0) -> re.Pattern:
    return re.compile(r"(?<!\w)" + re.escape(text) + r"(?!\w)", flags)


class Mask:
    def __init__(self, names: list[str], details: list[str]):
        self.names = list(dict.fromkeys(n.strip() for n in names if len(n.strip()) >= 2))
        self.details = list(dict.fromkeys(d.strip() for d in details if len(d.strip()) >= 3 and any(c.isalpha() for c in d) and d.strip() not in self.names))
        self.back: dict[str, str] = {}
        self.forward: list[tuple[re.Pattern, str]] | None = None

    def _build(self, text: str) -> list[tuple[re.Pattern, str]]:
        base = next((s for s in STAND_INS if not word(s).search(text)), STAND_INS[0])
        stand_in: dict[str, str] = {}
        for i, full in enumerate(self.names):
            tag = base if i == 0 else f"{base} {i + 1}"
            self.back[tag] = full
            for part in [full] + full.split():
                if len(part) >= 2:
                    stand_in.setdefault(part, tag)
        for i, value in enumerate(self.details):
            tag = f"[detail {i + 1}]"
            self.back[tag] = value
            stand_in.setdefault(value, tag)
        # Longest first, so a full name goes before its parts.
        return [(word(k, re.I), v) for k, v in sorted(stand_in.items(), key=lambda kv: -len(kv[0]))]

    def mask(self, text: str) -> str:
        if self.forward is None:
            self.forward = self._build(text)
        for pattern, tag in self.forward:
            text = pattern.sub(tag, text)
        for pattern, label in PATTERNS:
            def swap(m: re.Match) -> str:
                if label == "number" and sum(c.isdigit() for c in m.group(0)) < MIN_DIGITS:
                    return m.group(0)
                tag = f"[{label} {len(self.back) + 1}]"
                self.back[tag] = m.group(0)
                return tag
            text = pattern.sub(swap, text)
        return text

    def restore(self, text: str) -> str:
        for tag in sorted(self.back, key=len, reverse=True):
            text = word(tag).sub(lambda _m, real=self.back[tag]: real, text)
        return text


class Masked:
    """[llm] with every message masked and every reply restored."""

    remote = True

    def __init__(self, llm, mask: Mask):
        self.llm, self.m = llm, mask
        self.model = llm.model

    def available(self) -> bool:
        return self.llm.available()

    def chat(self, messages: list[dict], *args, **kwargs) -> str:
        return self.m.restore(self.llm.chat([{**x, "content": self.m.mask(x["content"])} for x in messages], *args, **kwargs))


def guard(llm, names: list[str] | None = None, details: list[str] | None = None):
    """The model as a route should use it: as it is on this computer, masked when it is behind an API."""
    return Masked(llm, Mask(names or [], details or [])) if getattr(llm, "remote", False) else llm
