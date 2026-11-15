<#
.SYNOPSIS
  Publishes a built Folio beta to McCal-Codes/folio-beta from the release notes, then checks what was uploaded.

.DESCRIPTION
  Run it from the release checkout (a worktree at the tag) after scripts/release-signed.ps1 has built the APK:

    ./scripts/publish-beta.ps1            # dry run: checks everything, writes the release body, uploads nothing
    ./scripts/publish-beta.ps1 -Publish   # asks you to type "publish", uploads, downloads the APK back and compares it

  The release page is written from docs/releases/<version>.md, so the notes are written and reviewed once, before the
  version bump (docs/standards/releases.md, "Building a signed beta on Windows"). The notes carry no hash: this script
  adds the APK and certificate hashes, the version and the versionCode to the top of the page, from the build itself.

  It stops if the APK's hash does not match SHA256SUMS.txt, if the signing certificate is not the usual one, if the
  tag or the tree is wrong, or (when publishing) if the release already exists on folio-beta.

.PARAMETER Root
  The release checkout. Default: the repository this script is in.

.PARAMETER NotesFile
  The release notes to build the page from. Default: docs/releases/<version>.md in the checkout.

.PARAMETER Publish
  Actually create the release. Without it nothing is uploaded.
#>
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$NotesFile,
    [switch]$Publish
)

$ErrorActionPreference = 'Stop'
$BetaRepository = 'McCal-Codes/folio-beta'
$ExpectedCertificate = 'bad8e099557b70c690e71a3fffe13561c8662c20612047ce1e2559a1c14d8441'

function Stop-Publish([string]$Message) {
    Write-Host "STOPPED: $Message" -ForegroundColor Red
    exit 1
}

Set-Location $Root
$repository = (Get-Location).Path

# 1. Right place: a checkout of its own, at the tag the version names, nothing uncommitted.
$top = (git rev-parse --show-toplevel 2>$null)
if (-not $top -or ((Resolve-Path $top).Path -ne (Resolve-Path $repository).Path)) {
    Stop-Publish "git does not treat $repository as a checkout of its own (top is '$top')."
}
$versionLine = Select-String -Path 'app/build.gradle.kts' -Pattern '^val folioVersion = "(.*)"$'
if (-not $versionLine) { Stop-Publish 'Could not read folioVersion from app/build.gradle.kts.' }
$version = $versionLine.Matches[0].Groups[1].Value
if ($version -notmatch '^(\d+\.\d+\.\d+)-beta\.(\d+)$') { Stop-Publish "$version is not a beta; this script publishes betas only." }
$base = $Matches[1]; $number = $Matches[2]
$tag = (git describe --tags --exact-match 2>$null)
if ($tag -ne "v$version") { Stop-Publish "HEAD is at '$tag', not v$version." }
if (git status --short) { Stop-Publish 'There are uncommitted changes.' }

# 2. The build: APK, hash list and certificate, all agreeing.
$output = Join-Path $repository "dist/Folio-$version"
$apkName = "Folio-$version.apk"
$apk = Join-Path $output $apkName
$sums = Join-Path $output 'SHA256SUMS.txt'
foreach ($file in @($apk, $sums, (Join-Path $output 'signing-certificate.txt'))) {
    if (-not (Test-Path $file)) { Stop-Publish "Missing $file. Run scripts/release-signed.ps1 first." }
}
$apkHash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$listed = (Get-Content $sums | Where-Object { $_ -match [regex]::Escape($apkName) } | Select-Object -First 1)
if (-not $listed -or $listed.Split(' ')[0].ToLower() -ne $apkHash) { Stop-Publish "SHA256SUMS.txt does not match the APK ($apkHash)." }
$certificateLine = Select-String -Path (Join-Path $output 'signing-certificate.txt') -Pattern 'certificate SHA-256 digest: (\w+)' | Select-Object -First 1
$certificate = if ($certificateLine) { $certificateLine.Matches[0].Groups[1].Value } else { $null }
if ($certificate -ne $ExpectedCertificate) { Stop-Publish "The APK is signed with $certificate, not $ExpectedCertificate." }

# versionCode from the APK itself.
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$aapt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '[^\d.].*$', '') } | Select-Object -Last 1 |
    ForEach-Object { Join-Path $_.FullName 'aapt2.exe' }
$badging = & $aapt dump badging $apk
if ($badging -join ' ' -notmatch "package: name='com.mccal.folio' versionCode='(\d+)' versionName='$([regex]::Escape($version))'") {
    Stop-Publish "The APK's package, versionName or versionCode is not what $version should be."
}
$versionCode = $Matches[1]

# 3. The release page, from the notes: the intro paragraph, then "What's new" up to "Verify it".
$notesPath = if ($NotesFile) { $NotesFile } else { "docs/releases/$version.md" }
if (-not (Test-Path $notesPath)) { Stop-Publish "Missing $notesPath." }
$notes = Get-Content $notesPath -Raw
if ($notes -match 'DRAFT') { Stop-Publish "$notesPath still says DRAFT." }
$intro = ($notes -split "`r?`n" | Select-Object -Skip 1 | Where-Object { $_.Trim() -and -not $_.StartsWith('*') } | Select-Object -First 1)
$start = $notes.IndexOf("## What's new")
$end = $notes.IndexOf('## Verify it')
if ($start -lt 0 -or $end -lt $start) { Stop-Publish "$notesPath needs a ""## What's new"" section before ""## Verify it""." }
$sections = $notes.Substring($start, $end - $start).TrimEnd() -replace "## What's new", "## What's new since beta $($number - 1)"
$body = @"
**Folio $base beta $number, for supporters**

Version ``$version``, versionCode $versionCode, so it installs cleanly over every earlier Folio, and stable $base will install over it. Supporters get it through Market › Sources › Folio Beta.

$intro

APK SHA-256: ``$apkHash`` (also in ``SHA256SUMS.txt``). Signing certificate SHA-256: ``$certificate``, the same as every earlier release.

$sections
"@
$bodyPath = Join-Path $output 'release-body.md'
[System.IO.File]::WriteAllText($bodyPath, $body.Replace("`r`n", "`n") + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Checked: $apkName, versionCode $versionCode, hash and certificate agree." -ForegroundColor Green
Write-Host "Release page written to $bodyPath"

# 4. Publish.
$exists = $false
& gh release view "v$version" -R $BetaRepository *> $null
if ($LASTEXITCODE -eq 0) { $exists = $true }
if (-not $Publish) {
    if ($exists) { Write-Host "Note: v$version already exists on $BetaRepository; publishing would stop." -ForegroundColor Yellow }
    Write-Host 'Dry run: nothing uploaded. Review the page, then run again with -Publish.'
    exit 0
}
if ($exists) { Stop-Publish "v$version already exists on $BetaRepository. A version means one build." }
$answer = Read-Host "Publish v$version to $BetaRepository as a pre-release? Type publish"
if ($answer -ne 'publish') { Stop-Publish 'Not confirmed.' }
gh release create "v$version" $apk $sums -R $BetaRepository --prerelease --title "Folio $base beta $number" --notes-file $bodyPath
if ($LASTEXITCODE -ne 0) { Stop-Publish 'gh release create failed.' }

# 5. What was uploaded is the build: download the APK back and compare.
$check = Join-Path $output 'download-check'
New-Item -ItemType Directory -Force $check | Out-Null
gh release download "v$version" -R $BetaRepository -p $apkName -D $check --clobber
$downloaded = (Get-FileHash (Join-Path $check $apkName) -Algorithm SHA256).Hash.ToLower()
if ($downloaded -ne $apkHash) { Stop-Publish "The uploaded APK hashes to $downloaded, not $apkHash. Delete the release and publish again." }
Write-Host "Published and verified: https://github.com/$BetaRepository/releases/tag/v$version" -ForegroundColor Green
$date = Get-Date -Format 'yyyy-MM-dd'
Write-Host 'Catalog row for docs/releases/README.md:'
Write-Host "| $version | beta | $date | [$version.md]($version.md) | [folio-beta](https://github.com/$BetaRepository/releases/tag/v$version) |"
