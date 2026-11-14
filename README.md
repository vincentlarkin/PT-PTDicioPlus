# EU-PTDicio+

Fast European Portuguese to English dictionary for Android, built as a native offline-first app.

## Product Goal

EU-PTDicio+ should feel instant: open, type a Portuguese word or form, and get the best English meaning without waiting on a network call. The first release is phone-first, with Galaxy Watch / Wear OS support planned as a companion lookup surface after the core dictionary is trustworthy.

Quality bar:

- European Portuguese first, not Brazilian Portuguese with a label changed.
- Lookup must handle common inflected forms, clitics, contractions, accents, and orthographic variants.
- Offline search must be fast enough for every-keystroke suggestions on mid-range Android phones.
- Definitions/translations must show source provenance and licensing.
- The app must stay small, clean, and maintainable.

Competitive bar:

- Priberam sets expectations around PT-PT/PT-BR and AO toggles, locutions, phraseology, synonyms/antonyms, conjugation, etymology, pronunciation, and technical vocabulary.
- Infopedia / Porto Editora sets expectations around large bilingual coverage, examples, expressions, verb support, and a polished cross-device dictionary surface.
- EU-PTDicio+ should not clone a publisher app; it should win on instant offline PT-PT lookup, transparent sources, and form-aware Portuguese-to-English results.

Learner UX rules:

- Search results stay compact. Tapping a result opens the full entry surface.
- Example sentences should be real source examples when available, paired with English translations when the source provides them.
- Verb forms should be grouped by learner task: present, past, future/conditional, subjunctive, commands, and non-finite forms.
- Form labels should translate grammar tags into English-speaker cues such as `I`, `we`, `he/she`, `preterite`, and `subjunctive`.

## Architecture

```
data/sources/datasets.json   every external dataset: URL, licence, ship vs reference-only
tools/fetch_datasets.py      downloads them into data/raw/<id>/ (git-ignored, ~9 GB)
pipeline/                    Python (stdlib only) build: raw data -> data/build/dicio.sqlite
dictionary-core/             pure Kotlin: search, clitics, grammar labels, conjugation tables
dictionary-android/          installs the bundled SQLite on first run; Android SqlDb adapter
app/                         phone app (Jetpack Compose), two-pane on foldables/tablets
wear/                        Wear OS app (voice/keyboard lookup)
buildSrc/                    BundleDictionary task: puts dicio.sqlite into app assets
```

Python only runs at build time on a PC. On the phone, every lookup is an indexed SQLite query
from Kotlin. Search takes about 1.5 to 4 ms in the emulator and on the desktop JVM.

The search code in `dictionary-core` talks to a two-method `SqlDb` interface, so the same code
runs on Android and in JVM unit tests against the real built database.

### Database (schema v2)

| Table | What |
|---|---|
| `entry` | headword (European spelling), POS, gender, PT-PT IPA, Commons audio file, syllables, etymology, PT/BR variety bits, frequency rank, search score |
| `sense` | English meaning, usage/region labels, topic |
| `example` | Portuguese example + English translation |
| `form` | full paradigm (conjugation / plural / feminine) with interned tag sets |
| `lookup` | every string that resolves to an entry: headword, spelling variant (pre-AO90, Brazilian), inflected form, old spelling of a form |
| `en_index` | English term -> entry, weighted by gloss position and Wiktionary translation tables, with region penalties |
| `rel` | synonyms, antonyms, derived terms, related words |
| `en_form` | English inflections (went -> go) for English-side lookup |

## European Portuguese Notes

European Portuguese is a morphologically rich language. A lookup app cannot only index lemma headwords.

Must support:

- Verb forms across person, number, tense, mood, and non-finite forms.
- Irregular verbs such as `ser`, `estar`, `ter`, `ir`, `fazer`, `dizer`, `ver`, `vir`, `poder`, `pôr`, and derived verbs.
- Noun/adjective gender and number: `bom`, `boa`, `bons`, `boas`; `cão`, `cães`; `alemão`, `alemães`.
- Accents and diacritics with accent-tolerant fallback, while preserving correct display forms.
- European clitic placement: enclisis (`dá-me`), proclisis after triggers such as negation (`não me dá`), and mesoclisis in formal future/conditional forms (`dar-me-á`).
- Verb + clitic spelling changes: `fazer` + `o` -> `fazê-lo`, `põe` + `o` -> `põe-no`, `dão` + `o` -> `dão-no`.
- Contractions and function words: `do`, `da`, `dos`, `das`, `no`, `na`, `pelo`, `à`, `às`.
- AO90 spelling as the default, while retaining useful pre-AO spellings as aliases where the source data allows.
- PT-PT vocabulary preference and region labels when an entry is Brazilian, African Portuguese, archaic, slang, or technical.

## Data

See [docs/SOURCES.md](docs/SOURCES.md) for every source, its licence, and what it contributes. Current build:

- 71k entries and 104k English meanings. There are 750k lookup keys: headwords, all inflected forms, and pre-AO90 and Brazilian spellings.
- 139k English→Portuguese index rows, 83k of them from Wiktionary translation tables.
- European IPA on about 75k entries, plus Lingua Libre / Commons recordings where they exist.
- 72 MB SQLite; the release APK is 29 MB.

Regression checks: `pipeline/checks.py` (SQL-level) and `DictionaryIntegrationTest` (Kotlin, real DB).
Example cases: bus→autocarro, train→comboio, breakfast→pequeno-almoço, acção→ação, dá-me→dar, falávamos→falar.

## Lookup Behavior

Ranking should prefer:

1. Exact surface form.
2. Exact lemma.
3. Inflected form mapped to lemma.
4. Accent-insensitive match.
5. Prefix match.
6. Fuzzy typo match, only when confidence is high.

Result display:

- Portuguese headword, pronunciation if available, part of speech, gender, and plural/conjugation summary.
- English meanings grouped by sense.
- Inflection note when lookup came from a form: `falávamos -> falar`.
- PT-PT usage labels and source tags.
- Favorite/save button.
- Recent lookups.

## Performance Targets

- Cold app launch to search-ready: under 700 ms on a mid-range phone after install-time database copy.
- Keystroke suggestions: under 30 ms p95 for common prefixes.
- Exact lookup: under 15 ms p95.
- App package data target: start under 75 MB compressed, revisit after source import.
- No network dependency for normal lookup.

## Development

Requirements: JDK 17+ (21 tested), Android SDK API 36, Python 3.10+, curl.

```sh
python3 tools/fetch_datasets.py                 # once: download sources (~9 GB)
python3 -I tools/build_dictionary.py            # ~40 s: build data/build/dicio.sqlite
python3 -I -c 'import sys; sys.path.insert(0,"."); from pipeline import checks; checks.main()'
./gradlew :dictionary-core:test                 # Kotlin unit + integration tests
./gradlew :app:assembleRelease :wear:assembleRelease
```

`local.properties` (git-ignored) needs `sdk.dir=/path/to/Android/Sdk`. The phone app's release
build signs with the upload key named in `keystore.properties` (git-ignored; keys:
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and falls back to the debug key
without it. `./gradlew :app:bundleRelease` builds the `.aab` for Google Play.

The launcher icon (an azulejo tile with a serif ç) is generated by
`tools/icons/make_icon.py`; store graphics are in `store/`.

## Android UX Plan

First screen is the dictionary, not a landing page.

Core phone screens:

- Search with instant suggestions.
- Entry detail.
- Recent words.
- Favorites.
- Source/about screen with licensing.
- Settings for accent-insensitive search, AO90/pre-AO aliases, and compact/detailed results.

Wear OS / Galaxy Watch backlog:

- Separate Wear OS module using Compose for Wear OS.
- Quick search by voice/text input.
- Recent and favorite words synced from phone.
- Use Wear OS Data Layer only for phone-watch sync where appropriate; keep watch data locally cached.

## Status

Done (v0.2):
- Data pipeline rebuilt in Python with 36 catalogued sources and a manifest with checksums.
- European spelling as the headword, with Brazilian and pre-AO90 spellings as aliases.
- Clitic lookup: enclisis, mesoclisis, and -lo/-no forms.
- Unified PT/EN search with automatic direction.
- Entry pages with conjugation tables (European forms first), synonyms and expressions.
- Favourites and history; "Look up" from selected text in any app.
- Two-pane layout on foldables and tablets.
- Wear OS lookup app.

Next:
- Tatoeba and Europarl/DGT example sentences, filtered for European Portuguese.
- Dicionário Aberto Portuguese definitions.
- Wikidata lexemes; the LibreOffice/Onto.PT thesaurus.
- Fuzzy "did you mean" for typos.
- Offline audio pack (optional download).
- IATE technical terms (needs a manual EU Login export).
- Store listing and Play internal testing.

## Reference Links

- Jetpack Compose Android docs: https://developer.android.com/develop/ui
- Android Compose-first announcement: https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- Wear OS Data Layer overview: https://developer.android.com/training/wearables/data/overview
- Wear OS data sync docs: https://developer.android.com/training/wearables/data/data-layer
- Kaikki / Wiktextract data downloads: https://kaikki.org/dictionary/
- Wiktextract project: https://github.com/tatuylonen/wiktextract
- Dicionário Aberto: https://dicionario-aberto.net/
- FreeDict: https://freedict.org/
- FreeDict licensing notes: https://freedict.org/documentation/
- Portuguese Orthographic Agreement text: https://www.portaldalinguaportuguesa.org/?action=acordo&version=1990
