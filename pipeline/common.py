"""Shared helpers for the dictionary build pipeline (stdlib only)."""
from __future__ import annotations

import json
import pathlib
import re
import time
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = ROOT / "data" / "raw"
BUILD = ROOT / "data" / "build"


def raw(dataset_id: str, pattern: str = "*") -> pathlib.Path:
    """Return the single file in data/raw/<id>/ matching pattern (excluding source.json)."""
    hits = [p for p in sorted((RAW / dataset_id).glob(pattern)) if p.name != "source.json"]
    if not hits:
        raise FileNotFoundError(f"{dataset_id}: nothing matches {pattern} - run tools/fetch_datasets.py {dataset_id}")
    return hits[0]


def source_meta(dataset_id: str) -> dict:
    return json.loads((RAW / dataset_id / "source.json").read_text())


_COMBINING = re.compile(r"[̀-ͯ]")
_SPACES = re.compile(r"\s+")


def norm(s: str) -> str:
    """Lookup key: lowercase, accents stripped (ç -> c), whitespace collapsed.

    MUST stay identical to PortugueseNormalizer.key() in dictionary-core.
    """
    s = unicodedata.normalize("NFD", s.strip().lower())
    s = _COMBINING.sub("", s)
    s = s.replace("’", "'").replace("‐", "-").replace("–", "-")
    return _SPACES.sub(" ", s)


class Timer:
    def __init__(self, label: str):
        self.label = label

    def __enter__(self):
        self.t = time.perf_counter()
        print(f"[{self.label}] ...", flush=True)
        return self

    def __exit__(self, *exc):
        print(f"[{self.label}] done in {time.perf_counter() - self.t:.1f}s", flush=True)


# Wiktionary sense/form tags that describe region or register. Anything here is shown as a label.
REGION_TAGS = {
    "Portugal": "PT", "European-Portuguese": "PT", "Brazil": "BR", "Brazilian-Portuguese": "BR",
    "Angola": "AO", "Mozambique": "MZ", "Cape-Verde": "CV", "Guinea-Bissau": "GW",
    "Sao-Tome-and-Principe": "ST", "East-Timor": "TL", "Macau": "MO", "Goa": "IN",
    "Azores": "PT-Azores", "Madeira": "PT-Madeira", "Alentejo": "PT-Alentejo", "Minho": "PT-Minho",
    "Northern-Portugal": "PT-North", "Southern-Portugal": "PT-South", "Lisbon": "PT-Lisbon",
    "Porto": "PT-Porto", "Galicia": "Galicia",
}
REGISTER_TAGS = {
    "colloquial", "informal", "slang", "vulgar", "offensive", "derogatory", "pejorative", "formal",
    "literary", "poetic", "archaic", "obsolete", "dated", "rare", "historical", "nonstandard",
    "proscribed", "dialectal", "regional", "humorous", "euphemistic", "figuratively", "idiomatic",
    "familiar", "childish", "jargon", "Internet", "neologism", "uncommon",
    "transitive", "intransitive", "pronominal", "reflexive", "uncountable", "countable", "ironic",
}
# Variety bits stored on entry.variety / sense.variety
VAR_PT, VAR_BR, VAR_AF = 1, 2, 4


def variety_bits(labels) -> int:
    bits = 0
    for t in labels:
        r = REGION_TAGS.get(t, "")
        if r.startswith("PT"):
            bits |= VAR_PT
        elif r == "BR":
            bits |= VAR_BR
        elif r in {"AO", "MZ", "CV", "GW", "ST"}:
            bits |= VAR_AF
    return bits
