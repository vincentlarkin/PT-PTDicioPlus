# Dictionary Sources

Every external dataset is listed in [`data/sources/datasets.json`](../data/sources/datasets.json): URL, licence, attribution, and a `use` policy.

- `ship`: text may appear in the app database, with attribution on the About/Sources screen.
- `reference`: used only for statistics, frequency, PT-PT vs PT-BR scoring, or QA. Its text never ships (OpenSubtitles, TED2020, MUSE, verbecc).

Fetch everything (Linux/macOS, needs `curl` and Python 3.10+):

```sh
python3 tools/fetch_datasets.py              # core tier (~8 GB)
python3 tools/fetch_datasets.py --tier all   # + JRC-Acquis, EMEA, PT-BR contrast, langlinks, CLDR
python3 tools/fetch_datasets.py tatoeba-por  # one dataset
```

Each dataset lands in `data/raw/<id>/` with a `source.json` recording URL, bytes, sha256 and fetch time. `data/raw/` is git-ignored.

## What each source contributes

| Need | Sources |
|---|---|
| PT headwords, EN glosses, POS, inflections, etymology | Kaikki English Wiktionary (Portuguese) - ~80k lemmas, 359k form entries |
| PT-PT pronunciation | Kaikki: IPA tagged `Portugal` on ~75k lemmas |
| EN -> PT | Kaikki English Wiktionary translation tables (53.5k EN headwords, 101k pairs); Wikcionario translation sections; Wikidata Lexemes; FreeDict |
| Portuguese-language definitions | Dicionario Aberto (128k entries); Wikcionario |
| PT-PT spelling authority, AO90 / pre-AO aliases | Projecto Natura Hunspell + word lists (AO90 and pre-AO) |
| Form -> lemma morphology | Kaikki forms, Natura jspell, UniMorph, UD Bosque |
| Synonyms | LibreOffice pt_PT thesaurus (PAPEL + Onto.PT), OpenWordnet-PT |
| Example sentences (PT with EN) | Tatoeba (445k PT sentences); Europarl, DGT, JRC-Acquis, EMEA (EU texts in European Portuguese) |
| Frequency / commonness | OpenSubtitles `pt` frequency list, HF EU-PT web list; OpenSubtitles corpus (reference) |
| PT-PT vs PT-BR scoring | OpenSubtitles `pt` vs `pt_br` frequency contrast; Wiktionary Portugal/Brazil tags |
| Proper nouns, technical terms | Wikipedia langlinks, CLDR pt-PT, IATE (manual) |

## Manual sources

- **IATE** (EU terminology, EN-PT): export needs a free EU Login account at iate.europa.eu. Put the TBX/CSV export in `data/raw/iate/`.
- **Lingua Libre** (Portugal-speaker word recordings, CC BY-SA 4.0): fetch through Wikimedia Commons later.
- **CETEMPublico** (Linguateca): research licence only, frequency statistics at most.

## Gotchas

- Kaikki (current format) nests most translation tables under `senses[].translations`, not top-level `translations` (top-level alone finds 4k EN headwords instead of 53.5k).
- Natura word lists are Latin-1, not UTF-8.
- Natura `jspell.pt.big` is a plain tar despite the `.tar.gz` name.
- The LibreOffice thesaurus uses pre-AO spelling (`excepto`); map it through the AO90 alias table.
- OpenSubtitles `pt` is European Portuguese and `pt_br` is Brazilian. OPUS keeps them apart.

## Rules

- Every entry, sense, form and example keeps its source ID, and the build emits a source manifest.
- Official PT-PT sources outrank community sources for spelling and lemma validation.
- Never scrape publisher sites (Priberam, Infopedia, etc.) or bypass logins, paywalls or rate limits. Copyrighted material is used only from files lawfully provided under the authorisation recorded in `source-registry.json`, kept in the ignored `data/raw/authorized/` folders.
