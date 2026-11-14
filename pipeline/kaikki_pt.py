"""English Wiktionary (Kaikki) Portuguese entries -> entries, senses, forms, aliases, relations."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field

from .common import REGION_TAGS, REGISTER_TAGS, raw

POS_MAP = {
    "name": "proper noun", "prep_phrase": "phrase", "adv_phrase": "phrase", "intj": "interjection",
    "adj": "adjective", "adv": "adverb", "prep": "preposition", "conj": "conjunction",
    "pron": "pronoun", "det": "determiner", "num": "numeral", "contraction": "contraction",
}
SKIP_POS = {"character", "symbol", "punct", "interfix"}
FORM_SKIP_TAGS = {"table-tags", "inflection-template", "class"}
SPELLING_RE = re.compile(r"\bspelling\b.*?\bof\b|\bform of\b|\bsuperseded\b", re.I)
SPELLING_KIND = [
    ("pre-reform", "pre-AO90"), ("superseded", "pre-AO90"), ("Brazilian", "Brazil spelling"),
    ("European Portuguese", "Portugal spelling"), ("obsolete", "obsolete spelling"),
    ("archaic", "archaic spelling"), ("misspelling", "misspelling"), ("nonstandard", "nonstandard spelling"),
    ("eye dialect", "pronunciation spelling"), ("pronunciation spelling", "pronunciation spelling"),
]
GLOSS_TARGET = re.compile(r"\bof ([^\s;,()]+)\s*$")
GRAMMAR_WORDS = {
    "first-person", "second-person", "third-person", "singular", "plural", "masculine", "feminine", "present",
    "preterite", "pluperfect", "imperfect", "future", "conditional", "indicative", "subjunctive", "imperative",
    "affirmative", "negative", "infinitive", "personal", "impersonal", "gerund", "past", "participle", "and",
    "preterite/pluperfect", "of", "the", "form", "inflection", "diminutive", "augmentative", "superlative",
    "comparative", "short", "long", "simple", "compound", "person", "first", "second", "third", "/",
}
ETY_NOISE = re.compile(r"\s*\(This etymology is missing or incomplete\..*?\)\s*", re.S)


@dataclass
class Sense:
    gloss: str
    labels: list[str]
    topics: list[str]
    examples: list[tuple[str, str]]  # (pt, en)
    synonyms: list[tuple[str, list[str]]]
    antonyms: list[tuple[str, list[str]]]


@dataclass
class Entry:
    word: str
    pos: str
    head: str = ""
    gender: str = ""
    ipa: str = ""
    ipa_br: str = ""
    audio: str = ""
    hyph: str = ""
    etym: str = ""
    senses: list[Sense] = field(default_factory=list)
    forms: list[tuple[str, str]] = field(default_factory=list)  # (form, tags)
    rels: list[tuple[str, str, str]] = field(default_factory=list)  # (kind, word, labels)
    display: str = ""  # preferred European display spelling (set later)


@dataclass
class FormOf:
    form: str
    lemma: str
    pos: str
    tags: str


@dataclass
class Alias:
    alias: str
    target: str
    pos: str
    kind: str  # pre-AO90, Brazil spelling, alternative, obsolete spelling, ...


def _labels(tags) -> list[str]:
    return [t for t in tags if t in REGION_TAGS or t in REGISTER_TAGS]


def _clean_etym(text: str) -> str:
    if not text:
        return ""
    if text.startswith("Etymology tree"):
        lines = text.split("\n")
        # Tree lines end at the "Portuguese <word>" line; prose follows.
        idx = next((i for i, l in enumerate(lines) if l.startswith("Portuguese ")), None)
        text = "\n".join(lines[idx + 1:] if idx is not None else lines[1:])
    return ETY_NOISE.sub(" ", text).strip()


def _pick_ipa(sounds, region: str) -> str:
    best = ""
    for s in sounds:
        ipa = s.get("ipa")
        tags = s.get("tags", [])
        if not ipa or region not in tags:
            continue
        if any(t in tags for t in ("Northern", "Southern", "Central", "Rio-de-Janeiro", "São-Paulo", "Caipira")):
            best = best or ipa  # regional variant only as a fallback
            continue
        if ipa.startswith("/"):
            return ipa
        best = best or ipa
    return best


def _pick_audio(sounds) -> str:
    for want in ("Portugal", None):
        for s in sounds:
            if not s.get("audio"):
                continue
            if want is None or want in s.get("tags", []) or "Portugal" in s.get("note", "") or "Lisbon" in s.get("note", ""):
                if want is None and ("Brazil" in s.get("tags", []) or "Pt-br" in s.get("audio", "") or "Brazil" in s.get("note", "")):
                    continue
                return s["audio"]
    return ""


def _gender(e) -> str:
    for h in e.get("head_templates", []):
        if h.get("name") in ("pt-noun", "pt-proper noun"):
            g = h.get("args", {}).get("1", "")
            return {"m": "m", "f": "f", "mf": "m/f", "m-f": "m/f", "mfbysense": "m/f", "m-p": "m pl", "f-p": "f pl"}.get(g, g)
    return ""


def _rel_list(items) -> list[tuple[str, list[str]]]:
    out = []
    for x in items or []:
        w = x.get("word", "").strip()
        if w and not w.startswith("Thesaurus:"):
            out.append((w, _labels(x.get("tags", []) + x.get("raw_tags", []))))
    return out


def parse(limit: int | None = None):
    entries: list[Entry] = []
    formofs: list[FormOf] = []
    aliases: list[Alias] = []
    path = raw("kaikki-en-portuguese", "*.jsonl")
    with path.open(encoding="utf-8") as f:
        for n, line in enumerate(f):
            if limit and n >= limit:
                break
            e = json.loads(line)
            if e.get("lang_code") != "pt" or e.get("pos") in SKIP_POS:
                continue
            word = e["word"]
            pos = POS_MAP.get(e["pos"], e["pos"])
            ent = Entry(word=word, pos=pos)
            for s in e.get("senses", []):
                tags = s.get("tags", [])
                glosses = s.get("glosses") or []
                gloss = glosses[-1] if glosses else ""
                if s.get("form_of"):
                    t = " ".join(x for x in tags if x not in ("form-of", "masculine", "feminine") or pos != "verb")
                    for fo in s["form_of"]:
                        if fo.get("word"):
                            formofs.append(FormOf(word, fo["word"], pos, t))
                    continue
                if s.get("alt_of") or ("alt-of" in tags):
                    kind = "Brazil spelling" if "Brazil" in tags else "Portugal spelling" if "Portugal" in tags else \
                        "obsolete spelling" if "obsolete" in tags else "abbreviation" if "abbreviation" in tags else \
                        "misspelling" if "misspelling" in tags else "alternative"
                    for ao in s.get("alt_of", []):
                        if ao.get("word"):
                            aliases.append(Alias(word, ao["word"], pos, kind))
                    if "abbreviation" not in tags:
                        continue  # abbreviations keep their own sense text too
                m = GLOSS_TARGET.search(gloss) if gloss else None
                if m and all(w in GRAMMAR_WORDS for w in gloss[:m.start()].replace("/", " / ").split()):
                    # "third-person plural preterite/pluperfect indicative of fazer" without a form_of field
                    formofs.append(FormOf(word, m.group(1), pos, " ".join(x for x in tags if x != "form-of")))
                    continue
                if gloss and SPELLING_RE.search(gloss) and len(gloss) < 260 and not s.get("examples") and (s.get("links") or m):
                    target = m.group(1) if m else s["links"][0][0]
                    kind = next((k for key, k in SPELLING_KIND if key.lower() in gloss.lower()), "alternative")
                    if target and target != word:
                        aliases.append(Alias(word, target, pos, kind))
                        continue
                if not gloss or "no-gloss" in tags:
                    continue
                exs = []
                for x in s.get("examples", []):
                    pt, en = x.get("text", "").strip(), (x.get("english") or x.get("translation") or "").strip()
                    if pt and (en or x.get("type") == "example") and len(pt) <= 220:
                        exs.append((pt, en))
                ent.senses.append(Sense(
                    gloss=gloss,
                    labels=_labels(tags + s.get("raw_tags", [])),
                    topics=s.get("topics", [])[:3],
                    examples=exs[:4],
                    synonyms=_rel_list(s.get("synonyms")),
                    antonyms=_rel_list(s.get("antonyms")),
                ))
            # Forms (paradigm) are kept even when the entry itself has no senses.
            for fm in e.get("forms", []):
                ftags = fm.get("tags", [])
                form = fm.get("form", "").strip()
                if not form or FORM_SKIP_TAGS & set(ftags) or form == "-":
                    continue
                if "alternative" in ftags:
                    kind = "Portugal spelling" if "Portugal" in ftags else "Brazil spelling" if "Brazil" in ftags else \
                        "obsolete spelling" if "obsolete" in ftags else "alternative"
                    aliases.append(Alias(form, word, pos, kind))
                    continue
                ent.forms.append((form, " ".join(ftags)))
            if not ent.senses:
                # Paradigm-only page (rare): expose its forms as form-of rows.
                for form, t in ent.forms:
                    formofs.append(FormOf(form, word, pos, t))
                continue
            sounds = e.get("sounds", [])
            ent.head = (e.get("head_templates") or [{}])[0].get("expansion", "")
            ent.gender = _gender(e)
            ent.ipa = _pick_ipa(sounds, "Portugal")
            ent.ipa_br = _pick_ipa(sounds, "Brazil")
            ent.audio = _pick_audio(sounds)
            hy = e.get("hyphenations") or []
            if hy and hy[0].get("parts") and len(hy[0]["parts"]) > 1:
                ent.hyph = "·".join(hy[0]["parts"])
            ent.etym = _clean_etym(e.get("etymology_text", ""))
            for kind in ("synonyms", "antonyms", "derived", "related", "hypernyms", "hyponyms"):
                for w, lab in _rel_list(e.get(kind)):
                    ent.rels.append((kind.rstrip("s"), w, ",".join(lab)))
            entries.append(ent)
    return entries, formofs, aliases
