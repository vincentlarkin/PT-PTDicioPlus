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

## Native App Direction

Use Kotlin with Jetpack Compose for Android. Google now describes Compose as the recommended native Android UI toolkit, and it gives us a good path to share presentation patterns with Compose for Wear OS later.

Current modules:

- `app`: Android phone app.
- `dictionary-core`: Kotlin library for lookup, normalization, ranking, and dictionary result models.

Planned modules:

- `dictionary-data`: generated SQLite or FTS asset from curated source data.
- `wear`: Wear OS companion module for quick lookup, recent words, and saved favorites.

Storage/search direction:

- Ship a prebuilt SQLite database with Android-compatible FTS4-backed prefix/full-text search.
- Keep a separate normalized-form table mapping inflected forms to lemmas.
- Store source IDs per sense so attribution and debugging stay possible.
- Load compact result rows first, then lazy-load richer sense details.

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

## Dictionary Data Plan

Candidate sources, in priority order:

1. Wiktextract / Kaikki JSONL from Wiktionary
   - Use Portuguese entries with English glosses, part of speech, pronunciations, examples where useful, translations, and inflection metadata.
   - Good machine-readable structure and updated regularly.
   - Requires respecting Wiktionary content licensing and attribution.

2. Dicionário Aberto
   - Open Portuguese dictionary, useful as a PT-PT lexical authority and monolingual cross-check.
   - Licensed under Creative Commons Attribution-ShareAlike 2.5 Portugal.
   - Use for lemma validation, Portuguese definitions, spelling variants, and quality checks, not as the only English translation source.

3. FreeDict Portuguese-English
   - Useful bilingual baseline, but the current Portuguese-English package appears old and smaller than Wiktionary-derived data.
   - Check the TEI header license before bundling any data.
   - Treat as supplemental coverage and comparison data.

4. Hunspell / LibreOffice `pt_PT`
   - Useful for spelling and generated-form coverage.
   - Not enough for dictionary senses by itself.
   - License compatibility must be verified before bundling.

Data pipeline:

- Download source snapshots into `data/raw/`, which should not be committed.
- Convert sources into normalized intermediate JSON under `data/build/`, also not committed.
- Generate a deterministic app asset under `dictionary-data/src/main/assets/`.
- Emit a source manifest with versions, source URLs, licenses, build date, row counts, and checksums.
- Add automated spot checks for high-risk words and forms.

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

Requirements:

- JDK 17.
- Android SDK with API 36.
- `ANDROID_HOME` or `local.properties` pointing to the SDK.

Android Studio:

- Open this folder directly in Android Studio.
- Let Android Studio sync with the checked-in Gradle wrapper.
- Keep `local.properties` local; it points Gradle at the installed Android SDK and is intentionally ignored by git.

Useful commands:

```powershell
.\gradlew.bat :dictionary-core:test
.\gradlew.bat :app:assembleDebug
```

Data import:

```powershell
.\tools\fetch-open-sources.ps1
.\gradlew.bat :dictionary-importer:run --args="--input C:\path\to\EU-PTDicio+\data\raw\kaikki.org-dictionary-Portuguese.jsonl --frequency C:\path\to\EU-PTDicio+\data\raw\hf-eu-pt-words-top.txt --freedict-por-eng C:\path\to\EU-PTDicio+\data\raw\freedict-por-eng.tei --freedict-eng-por C:\path\to\EU-PTDicio+\data\raw\freedict-eng-por.tei --output C:\path\to\EU-PTDicio+\data\build\euptdicio-kaikki.sqlite"
```

See `docs/SOURCES.md` and `data/sources/source-registry.json` for source policy and authorized input slots.

Current local database:

- 467,971 entries: 427,242 Kaikki, 10,661 FreeDict PT->EN, and 30,068 inverted FreeDict EN->PT.
- 608,499 senses and 503,273 forms.
- 10,091 example sentences, 7,567 with English translations.
- 142,443 entries with European Portuguese frequency/commonality signals.
- Indexed lowercase glosses and column-scoped FTS keep common EN/PT lookups in the low single-digit millisecond range on the local build machine.

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

## Milestones

### 0. Planning

- Confirm data source licenses and attribution requirements.
- Choose final database schema.
- Create a small manually curated test set of PT-PT words/forms.

### 1. Android Skeleton

- Create Kotlin/Gradle Android project.
- Add Compose phone UI shell.
- Add `dictionary-core` with fake in-memory data.
- Add baseline tests for normalization and ranking.

### 2. Data Prototype

- Build importer for a small Wiktextract sample.
- Generate SQLite/FTS asset.
- Wire exact, prefix, and lemma lookup.
- Add source manifest output.

### 3. Morphology

- Add inflected-form lookup tables.
- Add clitic normalization for common EP forms.
- Add regression tests for irregular verbs, noun/adjective forms, accents, contractions, and clitics.

### 4. App Quality

- Polish UI for speed and one-handed lookup.
- Add favorites and recents.
- Add attribution/source screen.
- Profile database queries and startup.

### 5. Wear OS

- Add Wear OS module.
- Implement recents/favorites sync.
- Add quick lookup UI for Galaxy Watch.

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
