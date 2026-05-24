# Dictionary Sources

EU-PTDicio+ keeps source handling explicit. Raw source data is not committed. Generated databases under `data/build/` are also not committed until we decide what artifact policy makes sense for app packaging.

Current imported source:

- Kaikki / Wiktextract Portuguese JSONL
  - URL: https://kaikki.org/dictionary/Portuguese/kaikki.org-dictionary-Portuguese.jsonl
  - Local raw file: `data/raw/kaikki.org-dictionary-Portuguese.jsonl`
  - Local SQLite build: `data/build/euptdicio-kaikki.sqlite`
  - Imported counts: 427,242 entries; 557,145 senses; 503,273 forms
  - License: Wiktionary content under CC BY-SA 3.0 and GFDL

Authorized source slots:

- Official European Portuguese sources: `data/raw/authorized/official-pt/`
- Publisher/commercial sources with explicit source-specific authorization: `data/raw/authorized/publisher/`

The authorization document provided by the project owner is tracked in `data/sources/source-registry.json` as metadata only. Do not commit scans, raw exports, proprietary dumps, API keys, cookies, or session-derived files.

Import rules:

- Every adapter must create or update a source manifest row.
- Every entry/sense/form should keep a source ID.
- Source priority must be explicit. Official PT-PT sources should outrank community sources for spelling and lemma validation; bilingual meaning quality can still come from multiple ranked sources.
- Protected raw data must stay in ignored folders. The app database can include adapted/excerpted data only within the authorization scope.

