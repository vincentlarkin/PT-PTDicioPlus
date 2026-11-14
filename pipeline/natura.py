"""Projecto Natura PT-PT word lists: AO90 validity and pre-AO90 -> AO90 spelling map."""
from __future__ import annotations

import itertools
import lzma
import re

from .common import raw


def _load(dataset_id: str) -> set[str]:
    with lzma.open(raw(dataset_id, "*.xz"), "rt", encoding="latin-1") as f:
        return {line.strip() for line in f if line.strip()}


def load_ao90() -> set[str]:
    return _load("natura-wordlist-big")


def load_preao() -> set[str]:
    return _load("natura-wordlist-preao")


_SILENT = re.compile(r"[cp](?=[cçt])")  # acção, óptimo, director, excepção, adopção, baptismo


def _candidates(w: str):
    """AO90 spellings a pre-AO word might have become."""
    spots = [m.start() for m in _SILENT.finditer(w)]
    for r in range(1, len(spots) + 1):
        for combo in itertools.combinations(spots, r):
            yield "".join(ch for i, ch in enumerate(w) if i not in combo)
    yield w.replace("êem", "eem").replace("ôo", "oo")          # lêem -> leem, vôo -> voo
    yield w.replace("pára", "para").replace("pêlo", "pelo").replace("pólo", "polo").replace("pêra", "pera")
    if "-" in w:                                                  # auto-estrada -> autoestrada
        a, _, b = w.partition("-")
        if b[:1] in "rs" and a[-1:] in "aeiou":
            yield a + b[0] + b                                    # anti-semita -> antissemita
        yield a + b
    if w[:1].isupper():
        yield w.lower()                                           # Janeiro -> janeiro


def preao_to_ao90(ao90: set[str], preao: set[str]) -> dict[str, str]:
    """Map words that are only valid pre-AO90 to their AO90 spelling."""
    out = {}
    for w in preao - ao90:
        for c in _candidates(w):
            if c != w and c in ao90:
                out[w] = c
                break
    return out
