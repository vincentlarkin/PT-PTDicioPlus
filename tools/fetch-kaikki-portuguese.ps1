$ErrorActionPreference = 'Stop'

$repo = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')
$rawDir = Join-Path $repo 'data\raw'
$url = 'https://kaikki.org/dictionary/Portuguese/kaikki.org-dictionary-Portuguese.jsonl'
$output = Join-Path $rawDir 'kaikki.org-dictionary-Portuguese.jsonl'
$manifest = Join-Path $rawDir 'kaikki-portuguese.source.json'

New-Item -ItemType Directory -Force -Path $rawDir | Out-Null

if (!(Test-Path -LiteralPath $output)) {
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $output
}

$file = Get-Item -LiteralPath $output
@{
    sourceId = 'kaikki-portuguese'
    sourceUrl = $url
    localFile = $file.FullName
    bytes = $file.Length
    downloadedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    source = 'English Wiktionary via Wiktextract / Kaikki'
    license = 'Wiktionary content: CC BY-SA 3.0 and GFDL'
    notes = 'Raw downloaded data is intentionally ignored by git.'
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifest -Encoding UTF8

Write-Host "Dataset ready: $output"
Write-Host "Manifest: $manifest"

