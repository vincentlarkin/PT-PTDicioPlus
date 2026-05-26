$ErrorActionPreference = 'Stop'

$repo = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')
$rawDir = Join-Path $repo 'data\raw'
New-Item -ItemType Directory -Force -Path $rawDir | Out-Null

$sources = @(
    @{
        id = 'kaikki-portuguese'
        url = 'https://kaikki.org/dictionary/Portuguese/kaikki.org-dictionary-Portuguese.jsonl'
        file = 'kaikki.org-dictionary-Portuguese.jsonl'
        license = 'Wiktionary content: CC BY-SA 3.0 and GFDL'
    },
    @{
        id = 'freedict-eng-por'
        url = 'https://download.freedict.org/dictionaries/eng-por/0.3/freedict-eng-por-0.3.stardict.tar.xz'
        file = 'freedict-eng-por-0.3.stardict.tar.xz'
        license = 'Verify package metadata before import'
    },
    @{
        id = 'freedict-eng-por-source'
        url = 'https://download.freedict.org/dictionaries/eng-por/0.3/freedict-eng-por-0.3.src.tar.xz'
        file = 'freedict-eng-por-0.3.src.tar.xz'
        extractEntry = 'eng-por/eng-por.tei'
        extractFile = 'freedict-eng-por.tei'
        license = 'GNU GPL 2.0 or later'
    },
    @{
        id = 'freedict-por-eng'
        url = 'https://download.freedict.org/dictionaries/por-eng/0.1.1/freedict-por-eng-0.1.1.dictd.tar.bz2'
        file = 'freedict-por-eng-0.1.1.dictd.tar.bz2'
        license = 'Verify package metadata before import'
    },
    @{
        id = 'freedict-por-eng-source'
        url = 'https://download.freedict.org/dictionaries/por-eng/0.1.1/freedict-por-eng-0.1.1.src.tar.bz2'
        file = 'freedict-por-eng-0.1.1.src.tar.bz2'
        extractEntry = 'por-eng/por-eng.tei'
        extractFile = 'freedict-por-eng.tei'
        license = 'GNU GPL 2.0 or later'
    },
    @{
        id = 'hf-eu-pt-web-frequency'
        url = 'https://huggingface.co/datasets/diplomaticvegetation/portuguese/resolve/main/words-top.txt'
        file = 'hf-eu-pt-words-top.txt'
        license = 'CC0-1.0'
    }
)

foreach ($source in $sources) {
    $output = Join-Path $rawDir $source.file
    if (!(Test-Path -LiteralPath $output)) {
        Invoke-WebRequest -UseBasicParsing -Uri $source.url -OutFile $output
    }
    $file = Get-Item -LiteralPath $output
    $extractedFile = $null
    if ($source.extractEntry) {
        $extractedFile = Join-Path $rawDir $source.extractFile
        if (!(Test-Path -LiteralPath $extractedFile)) {
            cmd /c "tar -xOf `"$output`" `"$($source.extractEntry)`" > `"$extractedFile`""
        }
    }
    $manifest = Join-Path $rawDir "$($source.id).source.json"
    @{
        sourceId = $source.id
        sourceUrl = $source.url
        localFile = $file.FullName
        extractedFile = $extractedFile
        bytes = $file.Length
        downloadedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        license = $source.license
        notes = 'Raw downloaded data is intentionally ignored by git.'
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifest -Encoding UTF8
    Write-Host "Ready: $($source.id) -> $output"
}
