"""Build data/build/dicio.sqlite from the raw datasets.

    python3 -I tools/build_dictionary.py [--limit N] [--out path]
"""
from __future__ import annotations

import argparse
import datetime as dt
import math
import re
import sqlite3
from collections import defaultdict

from . import frequency, kaikki_en, kaikki_pt, natura
from .common import BUILD, REGION_TAGS, VAR_BR, Timer, norm, source_meta, variety_bits

SCHEMA_VERSION = 2

SCHEMA = """
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE source(id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, license TEXT NOT NULL, attribution TEXT NOT NULL, url TEXT NOT NULL);
CREATE TABLE entry(
  id INTEGER PRIMARY KEY,
  word TEXT NOT NULL,          -- display headword (European spelling when one exists)
  alt_word TEXT,               -- source headword when it differs (e.g. Brazilian spelling)
  norm TEXT NOT NULL,          -- lowercase, accent-stripped lookup key of word
  pos TEXT NOT NULL,
  gender TEXT,
  head TEXT,                   -- inflection summary line
  ipa TEXT,                    -- European Portuguese IPA
  ipa_br TEXT,
  audio TEXT,                  -- Wikimedia Commons file name of a recording (Portugal preferred)
  hyph TEXT,                   -- syllabification, middle-dot separated
  etym TEXT,
  variety INTEGER NOT NULL DEFAULT 0,  -- bit 1 = Portugal only, 2 = Brazil only, 4 = Africa only
  flags INTEGER NOT NULL DEFAULT 0,    -- bit 1 = all senses archaic/obsolete, 2 = Brazilian spelling, 4 = AO90-valid
  freq REAL NOT NULL DEFAULT 0,        -- PT-PT occurrences per million (headword + forms)
  rank INTEGER,                        -- 1 = most frequent entry
  score INTEGER NOT NULL DEFAULT 0,    -- search ordering score
  source_id INTEGER NOT NULL
);
CREATE TABLE sense(
  id INTEGER PRIMARY KEY,
  entry_id INTEGER NOT NULL,
  ord INTEGER NOT NULL,
  gloss TEXT NOT NULL,
  labels TEXT,                 -- comma separated: Portugal,colloquial
  topic TEXT,
  variety INTEGER NOT NULL DEFAULT 0,
  source_id INTEGER NOT NULL
);
CREATE TABLE example(id INTEGER PRIMARY KEY, entry_id INTEGER NOT NULL, sense_id INTEGER, pt TEXT NOT NULL, en TEXT, source_id INTEGER NOT NULL);
-- Interned grammar/variant tag strings ("first-person plural imperfect indicative", "pre-AO90").
CREATE TABLE tagset(id INTEGER PRIMARY KEY, tags TEXT NOT NULL);
CREATE TABLE form(entry_id INTEGER NOT NULL, ord INTEGER NOT NULL, form TEXT NOT NULL, tagset INTEGER NOT NULL,
  PRIMARY KEY(entry_id, ord)) WITHOUT ROWID;
CREATE TABLE rel(entry_id INTEGER NOT NULL, sense_id INTEGER, kind TEXT NOT NULL, word TEXT NOT NULL, labels TEXT);
-- Portuguese lookup: every string that should resolve to an entry.
-- kind 0 = headword, 1 = spelling variant of the headword (pre-AO90, Brazilian, alternative...),
--      2 = inflected form, 3 = spelling variant of an inflected form
-- text is NULL when it equals key; tagset 0 = none.
CREATE TABLE lookup(key TEXT NOT NULL, kind INTEGER NOT NULL, entry_id INTEGER NOT NULL, tagset INTEGER NOT NULL, text TEXT,
  PRIMARY KEY(key, kind, entry_id, tagset)) WITHOUT ROWID;
-- English -> Portuguese index. weight 0-120; higher is better.
CREATE TABLE en_index(term TEXT NOT NULL, entry_id INTEGER NOT NULL, sense_id INTEGER, weight INTEGER NOT NULL, note TEXT);
CREATE TABLE en_form(form TEXT NOT NULL, lemma TEXT NOT NULL);
"""

INDEXES = """
CREATE INDEX entry_norm ON entry(norm, score DESC);
CREATE INDEX entry_score ON entry(score DESC);
CREATE INDEX sense_entry ON sense(entry_id, ord);
CREATE INDEX example_entry ON example(entry_id);
CREATE INDEX rel_entry ON rel(entry_id);
CREATE INDEX en_term ON en_index(term, weight DESC);
CREATE INDEX en_form_form ON en_form(form);
"""

SOURCES = [
    ("kaikki-en-portuguese", "wiktionary-en", "https://en.wiktionary.org/"),
    ("kaikki-en-english", "wiktionary-en-translations", "https://en.wiktionary.org/"),
    ("natura-wordlist-big", "natura", "https://natura.di.uminho.pt/"),
    ("freq-opensubtitles-pt", "frequency-opensubtitles", "https://github.com/hermitdave/FrequencyWords"),
    ("freq-hf-eu-pt-web", "frequency-web", "https://huggingface.co/datasets/diplomaticvegetation/portuguese"),
]

ARCHAIC = {"archaic", "obsolete", "dated", "historical"}
PAREN = re.compile(r"\([^)]*\)")
LEADING = re.compile(r"^(to|a|an|the|one's|someone's|something's)\s+", re.I)


def en_terms(gloss: str, pos: str) -> list[str]:
    """Short English headword-like terms from a gloss: 'to speak, talk (with)' -> ['speak', 'talk']."""
    out = []
    for part in re.split(r"[;,]|\bor\b", PAREN.sub("", gloss)):
        t = part.strip().strip(".:!?\"'").lower()
        t = LEADING.sub("", t).strip()
        if pos == "verb":
            t = LEADING.sub("", t).strip()
        if 0 < len(t) <= 40 and len(t.split()) <= 4 and not t.startswith(("of ", "with ", "that ", "which ")):
            out.append(t)
    return out


def build(out_path, limit=None):
    with Timer("natura"):
        ao90 = natura.load_ao90()
        preao = natura.load_preao()
        pre_map = natura.preao_to_ao90(ao90, preao)
        print(f"  AO90 words {len(ao90):,}; pre-AO-only words mapped {len(pre_map):,}")
    with Timer("frequency"):
        pt_freq, br_freq = frequency.load()
    with Timer("kaikki pt"):
        entries, formofs, aliases = kaikki_pt.parse(limit)
        print(f"  entries {len(entries):,}; form-of {len(formofs):,}; aliases {len(aliases):,}")
    with Timer("kaikki en"):
        translations = kaikki_en.parse_translations() if not limit else []
        en_infl = kaikki_en.parse_english_inflections() if not limit else {}
        print(f"  EN->PT translations {len(translations):,}; English inflections {len(en_infl):,}")

    def valid_pt(word: str) -> bool:
        toks = re.findall(r"[^\s]+", word)
        return all(t in ao90 or t.lower() in ao90 for t in toks)

    # --- European display spelling ---------------------------------------------------------
    alias_by_target = defaultdict(list)
    for a in aliases:
        alias_by_target[a.target].append(a)
    for e in entries:
        e.display = e.word
        if valid_pt(e.word):
            continue
        for a in alias_by_target.get(e.word, []):
            if norm(a.alias) == norm(e.word) and a.alias != e.word and valid_pt(a.alias):
                e.display = a.alias  # ônibus -> ónibus, econômico -> económico
                break

    # --- frequency + score -----------------------------------------------------------------
    def entry_freq(e) -> float:
        seen = {e.word.lower(), e.display.lower()} | {f.lower() for f, _ in e.forms}
        return sum(pt_freq.get(w, 0.0) for w in seen)

    freqs = [entry_freq(e) for e in entries]
    order = sorted(range(len(entries)), key=lambda i: -freqs[i])
    rank = {i: r + 1 for r, i in enumerate(order) if freqs[i] > 0}

    with Timer("write sqlite"):
        out_path.parent.mkdir(parents=True, exist_ok=True)
        if out_path.exists():
            out_path.unlink()
        db = sqlite3.connect(out_path)
        db.executescript("PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA page_size=4096;")
        db.executescript(SCHEMA)
        src_id = {}
        for i, (ds, code, url) in enumerate(SOURCES, 1):
            m = source_meta(ds)
            db.execute("INSERT INTO source VALUES (?,?,?,?,?)", (i, code, m["license"], m["attribution"], url))
            src_id[code] = i
        WIKT = src_id["wiktionary-en"]

        lookup_rows = set()
        tagsets: dict[str, int] = {}

        def tagset_id(tags):
            if not tags:
                return 0
            if tags not in tagsets:
                tagsets[tags] = len(tagsets) + 1
            return tagsets[tags]

        by_word = defaultdict(list)  # exact word -> [(entry_id, pos)]
        sense_id = 0
        example_rows, form_rows, rel_rows, sense_rows, entry_rows = [], [], [], [], []
        entry_terms = []  # (entry_id, first_sense_id, sense list) for en_index

        for idx, e in enumerate(entries):
            eid = idx + 1
            svar = [variety_bits(s.labels) for s in e.senses]
            variety = svar[0]
            for v in svar[1:]:
                variety &= v
            flags = 0
            if all(ARCHAIC & set(s.labels) for s in e.senses):
                flags |= 1
            if e.display != e.word:
                flags |= 2
            if valid_pt(e.display):
                flags |= 4
            elif variety == 0 and not valid_pt(e.word) and br_freq.get(e.word.lower(), 0) > 5 * pt_freq.get(e.word.lower(), 0) + 1:
                variety = VAR_BR  # Brazilian spelling with no European equivalent recorded
            f = freqs[idx]
            score = int(100 * math.log10(1 + f * 100))  # 0..~800
            score += min(len(e.senses), 10) * 3
            if flags & 1:
                score -= 150
            if variety == VAR_BR:
                score -= 120
            if e.pos == "proper noun":
                score -= 60
            if " " in e.display:
                score -= 20
            entry_rows.append((eid, e.display, e.word if e.display != e.word else None, norm(e.display), e.pos,
                               e.gender or None, e.head or None, e.ipa or None, e.ipa_br or None, e.audio or None,
                               e.hyph or None, e.etym or None, variety, flags, round(f, 3), rank.get(idx), score, WIKT))
            by_word[e.word].append((eid, e.pos))
            if e.display != e.word:
                by_word[e.display].append((eid, e.pos))
            lookup_rows.add((norm(e.display), eid, 0, e.display, None))
            if e.display != e.word:
                lookup_rows.add((norm(e.word), eid, 1, e.word, "Brazil spelling"))
            sids = []
            for o, s in enumerate(e.senses):
                sense_id += 1
                sids.append(sense_id)
                sense_rows.append((sense_id, eid, o, s.gloss, ",".join(s.labels) or None,
                                   s.topics[0] if s.topics else None, svar[o], WIKT))
                for pt, en in s.examples:
                    example_rows.append((eid, sense_id, pt, en or None, WIKT))
                for w, lab in s.synonyms:
                    rel_rows.append((eid, sense_id, "synonym", w, ",".join(lab) or None))
                for w, lab in s.antonyms:
                    rel_rows.append((eid, sense_id, "antonym", w, ",".join(lab) or None))
            for kind, w, lab in e.rels:
                rel_rows.append((eid, None, kind, w, lab or None))
            for o, (form, tags) in enumerate(e.forms):
                form_rows.append((eid, o, form, tagset_id(tags)))
                key_form = form[4:] if form.startswith("não ") else form
                if key_form.lower() != e.display.lower():
                    lookup_rows.add((norm(key_form), eid, 2, key_form, tags))
            entry_terms.append((eid, e.pos, list(zip(sids, e.senses))))

        db.executemany("INSERT INTO entry VALUES (" + ",".join("?" * 18) + ")", entry_rows)
        db.executemany("INSERT INTO sense VALUES (?,?,?,?,?,?,?,?)", sense_rows)
        db.executemany("INSERT INTO example(entry_id,sense_id,pt,en,source_id) VALUES (?,?,?,?,?)", example_rows)
        db.executemany("INSERT INTO form VALUES (?,?,?,?)", form_rows)
        db.executemany("INSERT INTO rel VALUES (?,?,?,?,?)", rel_rows)

        def targets(word, pos):
            hits = by_word.get(word) or by_word.get(word.lower()) or []
            same = [eid for eid, p in hits if p == pos]
            return same or [eid for eid, _ in hits]

        # form-of pages (falávamos -> falar)
        for fo in formofs:
            for eid in targets(fo.lemma, fo.pos):
                lookup_rows.add((norm(fo.form), eid, 2, fo.form, fo.tags or None))
        # spelling aliases from Wiktionary
        for a in aliases:
            for eid in targets(a.target, a.pos):
                if norm(a.alias) != norm(entry_rows[eid - 1][1]) or a.alias != entry_rows[eid - 1][1]:
                    lookup_rows.add((norm(a.alias), eid, 1, a.alias, a.kind))
        # pre-AO90 spellings from Natura, resolved through whatever the AO90 word already resolves to
        by_key = defaultdict(set)
        for key, eid, kind, text, detail in lookup_rows:
            if kind in (0, 2):
                by_key[(text, kind)].add((eid, detail))
        added = 0
        for old, new in pre_map.items():
            for kind in (0, 2):
                for eid, detail in by_key.get((new, kind), ()):
                    lookup_rows.add((norm(old), eid, 1 if kind == 0 else 3, old, "pre-AO90" + (f" · {detail}" if kind == 2 and detail else "")))
                    added += 1
        print(f"  pre-AO90 lookup rows from Natura: {added:,}")
        final = {}
        for key, eid, kind, text, detail in lookup_rows:
            final[(key, kind, eid, tagset_id(detail))] = None if text == key else text
        db.executemany("INSERT INTO lookup VALUES (?,?,?,?,?)", sorted((*k, v) for k, v in final.items()))
        db.executemany("INSERT INTO tagset VALUES (?,?)", ((i, t) for t, i in tagsets.items()))
        print(f"  lookup rows {len(final):,}; tagsets {len(tagsets):,}")

        # --- English -> Portuguese index ------------------------------------------------------
        # Two independent signals per (English term, entry):
        #   g = the term appears in one of the entry's English glosses (earlier sense = better)
        #   t = an English Wiktionary translation table lists the entry (earlier English sense = better)
        # Final weight = max(g, t) + agreement bonus. Regional labels other than plain Portugal cost points.
        gloss_w: dict[tuple[str, int], tuple[int, int]] = {}
        trans_w: dict[tuple[str, int], tuple[int, str | None]] = {}

        def region_penalty(labels) -> tuple[int, str | None]:
            regions = [REGION_TAGS[x] for x in labels if x in REGION_TAGS]
            if not regions or regions == ["PT"]:
                return (8 if regions else 0), ("Portugal" if regions else None)
            if "BR" in regions and "PT" not in regions:
                return -35, "Brazil"
            sub = [r for r in regions if r != "PT"]
            return -40, sub[0].replace("PT-", "")  # Madeira, Azores, AO, MZ...

        for eid, pos, senses in entry_terms:
            for o, (sid, s) in enumerate(senses):
                base = 40 if ARCHAIC & set(s.labels) else 100 - min(o, 8) * 6
                base += region_penalty(s.labels)[0]
                for j, term in enumerate(en_terms(s.gloss, pos)):
                    w = base - j * 3
                    if gloss_w.get((term, eid), (-999,))[0] < w:
                        gloss_w[(term, eid)] = (w, sid)
        n_tr = 0
        for t in translations:
            hits = targets(t.pt, {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb"}.get(t.en_pos, t.en_pos))
            if not hits and t.pt in pre_map:
                hits = targets(pre_map[t.pt], t.en_pos)
            labs = t.labels.split(",") if t.labels else []
            pen, note = region_penalty(labs)
            w = 100 - min(t.sense_ord, 6) * 8 + pen - (40 if {"archaic", "obsolete"} & set(labs) else 0)
            for eid in hits:
                key = (t.en.lower(), eid)
                if trans_w.get(key, (-999,))[0] < w:
                    trans_w[key] = (w, note)
                n_tr += 1
        en_rows = {}
        for key in gloss_w.keys() | trans_w.keys():
            g, sid = gloss_w.get(key, (None, None))
            tw, note = trans_w.get(key, (None, None))
            w = max(x for x in (g, tw) if x is not None) + (15 if g is not None and tw is not None else 0)
            en_rows[key] = (w, sid, note)
        print(f"  EN index rows {len(en_rows):,} ({n_tr:,} translation-table links)")
        db.executemany("INSERT INTO en_index VALUES (?,?,?,?,?)",
                       ((term, eid, sid, w, note) for (term, eid), (w, sid, note) in en_rows.items()))
        terms = {term for term, _ in en_rows}
        db.executemany("INSERT INTO en_form VALUES (?,?)",
                       ((f, l) for f, lemmas in en_infl.items() if f not in terms for l in lemmas if l in terms))

        built = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
        for k, v in {"schema_version": SCHEMA_VERSION, "built_at_utc": built, "entries": len(entry_rows),
                     "senses": len(sense_rows), "lookup_rows": len(final), "en_index_rows": len(en_rows)}.items():
            db.execute("INSERT INTO meta VALUES (?,?)", (k, str(v)))
        db.commit()
    with Timer("index + vacuum"):
        db.executescript(INDEXES)
        db.execute("ANALYZE")
        db.commit()
        db.execute("VACUUM")
        db.close()
    print(f"wrote {out_path} ({out_path.stat().st_size / 1e6:.1f} MB)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, help="only read the first N Kaikki lines (fast dev builds)")
    ap.add_argument("--out", default=str(BUILD / "dicio.sqlite"))
    args = ap.parse_args()
    import pathlib
    build(pathlib.Path(args.out), args.limit)
