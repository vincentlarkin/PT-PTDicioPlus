"""English Wiktionary (Kaikki) English entries -> EN->PT translations and English inflection map."""
from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass

from .common import REGION_TAGS, raw

KEEP_LABELS = set(REGION_TAGS) | {
    "masculine", "feminine", "colloquial", "informal", "slang", "formal", "vulgar", "dated", "archaic", "obsolete", "rare"}


@dataclass
class Translation:
    en: str        # English headword
    en_pos: str
    sense: str     # English sense description from the translation table
    pt: str        # Portuguese word/phrase
    labels: str    # e.g. "Portugal", "Brazil", "Madeira", "masculine"
    sense_ord: int  # 0 = the English word's first sense that has translations


def _lines_containing(path, needle: str):
    # grep is ~20x faster than Python at discarding the 1.4M irrelevant lines.
    proc = subprocess.Popen(["grep", "-F", needle, str(path)], stdout=subprocess.PIPE, text=True, encoding="utf-8")
    yield from proc.stdout
    proc.wait()


def parse_translations() -> list[Translation]:
    path = raw("kaikki-en-english", "*.jsonl")
    out: list[Translation] = []
    for line in _lines_containing(path, '"lang": "Portuguese"'):
        e = json.loads(line)
        if e.get("lang_code") != "en":
            continue
        groups = []
        for s in e.get("senses", []):
            g = s.get("glosses") or []
            if s.get("translations"):
                groups.append((s["translations"], g[-1] if g else ""))
        if e.get("translations"):
            groups.append((e["translations"], ""))
        for order, (ts, gloss) in enumerate(groups):
            for t in ts:
                if t.get("lang_code") != "pt" or not t.get("word"):
                    continue
                labels = [x for x in t.get("tags", []) + t.get("raw_tags", []) if x in KEEP_LABELS]
                out.append(Translation(e["word"], e.get("pos", ""), t.get("sense") or gloss, t["word"].strip(),
                                       ",".join(labels), order))
    return out


def parse_english_inflections() -> dict[str, set[str]]:
    """English form -> set of lemmas (went -> go, mice -> mouse, better -> good)."""
    path = raw("kaikki-en-english", "*.jsonl")
    out: dict[str, set[str]] = {}
    for line in _lines_containing(path, '"form_of"'):
        e = json.loads(line)
        if e.get("lang_code") != "en":
            continue
        w = e["word"].lower()
        for s in e.get("senses", []):
            tags = set(s.get("tags", []))
            if tags & {"misspelling", "obsolete", "archaic", "nonstandard", "dialectal", "rare"}:
                continue
            for fo in s.get("form_of", []):
                lemma = fo.get("word", "").lower()
                if lemma and lemma != w and " " not in w:
                    out.setdefault(w, set()).add(lemma)
    return out
