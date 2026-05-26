# Dictionary Sources

EU-PTDicio+ keeps source handling explicit. Raw source data is not committed. Generated databases under `data/build/` are also not committed until we decide what artifact policy makes sense for app packaging.

Current imported source:

- Kaikki / Wiktextract Portuguese JSONL
  - URL: https://kaikki.org/dictionary/Portuguese/kaikki.org-dictionary-Portuguese.jsonl
  - Local raw file: `data/raw/kaikki.org-dictionary-Portuguese.jsonl`
  - Local SQLite build: `data/build/euptdicio-kaikki.sqlite`
  - Imported counts: 427,242 entries; 557,145 senses; 503,273 forms; 10,091 example sentences
  - License: Wiktionary content under CC BY-SA 3.0 and GFDL

- Hugging Face `diplomaticvegetation/portuguese` word frequency list
  - URL: https://huggingface.co/datasets/diplomaticvegetation/portuguese/blob/main/words-top.txt
  - Local raw file: `data/raw/hf-eu-pt-words-top.txt`
  - License: CC0-1.0
  - Use: entry commonality scoring from European Portuguese web frequency

- FreeDict Portuguese-English TEI
  - URL: https://download.freedict.org/dictionaries/por-eng/0.1.1/freedict-por-eng-0.1.1.src.tar.bz2
  - Local raw file: `data/raw/freedict-por-eng.tei`
  - Imported counts: 10,661 entries
  - License: GNU GPL 2.0 or later, per TEI header
  - Use: supplemental PT->EN bilingual coverage

- FreeDict English-Portuguese TEI
  - URL: https://download.freedict.org/dictionaries/eng-por/0.3/freedict-eng-por-0.3.src.tar.xz
  - Local raw file: `data/raw/freedict-eng-por.tei`
  - Imported counts: 30,068 inverted entries
  - License: GNU GPL 2.0 or later, per TEI header
  - Use: supplemental EN->PT lookup by inverting Portuguese translation quotes into Portuguese entries with English glosses

Authorized source slots:

- Official European Portuguese sources: `data/raw/authorized/official-pt/`
- Publisher/commercial sources with explicit source-specific authorization: `data/raw/authorized/publisher/`

The authorization document provided by the project owner is tracked in `data/sources/source-registry.json` as metadata only. Do not commit scans, raw exports, proprietary dumps, API keys, cookies, or session-derived files.

Import rules:

- Every adapter must create or update a source manifest row.
- Every entry/sense/form should keep a source ID.
- Source priority must be explicit. Official PT-PT sources should outrank community sources for spelling and lemma validation; bilingual meaning quality can still come from multiple ranked sources.
- Available copyrighted sources are allowed only within the project authorization scope and with attribution; do not bypass paywalls, authentication, API keys, cookies, session gates, or other access controls.
- Protected raw data must stay in ignored folders. The app database can include adapted/excerpted data only within the authorization scope.
