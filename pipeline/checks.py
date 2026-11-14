"""Golden lookups against the built DB. Exit 1 if any expectation fails.

    python3 -I tools/build_dictionary.py && python3 -I -c 'import sys; sys.path.insert(0,"."); from pipeline import checks; checks.main()'
"""
from __future__ import annotations

import sqlite3
import sys

from .common import BUILD, norm

# (query, direction, expected headword among top N, N)
PT = [
    ("falávamos", "falar", 1), ("cães", "cão", 1), ("acção", "ação", 1), ("óptimo", "ótimo", 1),
    ("ponho", "pôr", 1), ("autocarro", "autocarro", 1), ("ônibus", "ónibus", 2), ("económico", "económico", 1),
    ("fixe", "fixe", 1), ("telemóvel", "telemóvel", 1), ("pequeno-almoço", "pequeno-almoço", 1),
    ("fizeram", "fazer", 1), ("vimos", "ver", 2), ("boas", "bom", 3), ("director", "diretor", 1),
    ("comboio", "comboio", 1), ("sítio", "sítio", 1), ("casa", "casa", 1),
]
EN = [
    ("bus", "autocarro", 1), ("breakfast", "pequeno-almoço", 1), ("train", "comboio", 2),
    ("mobile phone", "telemóvel", 1), ("dog", "cão", 1), ("went", "ir", 3), ("to speak", "falar", 2),
    ("girl", "rapariga", 2), ("cool", "fixe", 5), ("house", "casa", 1), ("ice cream", "gelado", 2),
]


def pt_lookup(db, q, n=5):
    rows = db.execute("""
        SELECT e.word, max(coalesce(l.text, l.key) = ?) exact, min(l.kind) k, e.score
        FROM lookup l JOIN entry e ON e.id = l.entry_id
        WHERE l.key = ? GROUP BY e.id ORDER BY exact DESC, k, e.score DESC LIMIT ?""", (q, norm(q), n)).fetchall()
    return [r[0] for r in rows]


def en_lookup(db, q, n=5):
    t = q.lower().strip()
    if t.startswith("to "):
        t = t[3:]
    terms = [t] + [r[0] for r in db.execute("SELECT lemma FROM en_form WHERE form = ?", (t,))]
    rows = db.execute(f"""
        SELECT e.word, max(x.weight) w, e.score FROM en_index x JOIN entry e ON e.id = x.entry_id
        WHERE x.term IN ({",".join("?" * len(terms))}) GROUP BY e.id
        ORDER BY (max(x.weight) * 6 + e.score) DESC LIMIT ?""", (*terms, n)).fetchall()
    return [r[0] for r in rows]


def main(path=BUILD / "dicio.sqlite"):
    db = sqlite3.connect(path)
    bad = 0
    for label, cases, fn in (("PT", PT, pt_lookup), ("EN", EN, en_lookup)):
        for q, want, n in cases:
            got = fn(db, q)
            ok = want in got[:n]
            bad += not ok
            print(f"{'ok ' if ok else 'BAD'} {label} {q!r:18} -> {got}")
    print(f"{bad} failing")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
