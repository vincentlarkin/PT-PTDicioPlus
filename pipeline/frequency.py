"""Word frequency signals: European Portuguese (OpenSubtitles pt + EU-PT web) and Brazilian contrast."""
from __future__ import annotations

from .common import raw


def _read(path, sep=None, limit=None) -> dict[str, int]:
    out: dict[str, int] = {}
    with open(path, encoding="utf-8", errors="replace") as f:
        for i, line in enumerate(f):
            if limit and i >= limit:
                break
            parts = line.rstrip("\n").split(sep)
            if len(parts) < 2:
                continue
            w = parts[0].strip().lower()
            try:
                c = int(parts[1])
            except ValueError:
                continue
            if w and w not in out:
                out[w] = c
    return out


def load():
    """Return (pt_per_million, br_per_million) word -> occurrences per million tokens.

    The PT signal blends spoken-ish subtitles and web text so neither register dominates.
    """
    subs = _read(raw("freq-opensubtitles-pt", "*.txt"))
    web = _read(raw("freq-hf-eu-pt-web", "*.txt"), sep="\t")
    br = _read(raw("freq-opensubtitles-ptbr", "*.txt"))

    def per_million(d):
        total = sum(d.values()) or 1
        return {w: c * 1e6 / total for w, c in d.items()}

    subs, web, br = per_million(subs), per_million(web), per_million(br)
    pt = {}
    for w in subs.keys() | web.keys():
        pt[w] = (subs.get(w, 0.0) + web.get(w, 0.0)) / 2
    return pt, br
