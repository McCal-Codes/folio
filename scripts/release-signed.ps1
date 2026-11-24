<#
.SYNOPSIS
  Builds a signed Folio release on Windows with one command, asking for the keystore passwords in the terminal.

.DESCRIPTION
  A wrapper around scripts/release-signed.sh for PowerShell. Run it from a git worktree checked out at the release tag:

    git worktree add --detach C:/Users/<you>/dev/folio-<version> v<version>
    cd C:/Users/<you>/dev/folio-<version>
    ./scripts/release-signed.ps1

  It does what went wrong by hand on 14 Nov 2026 (docs/standards/releases.md, "Building a signed beta on Windows"):
    - runs Git Bash, not the `bash` PowerShell finds first, which is WSL's and sees neither the repo nor these variables;
    - checks the folder is a real worktree at the exact tag with nothing uncommitted, before anything is typed;
    - uses the original release keystore (~/folio-release.jks, alias folio) and refuses the 2026 one, which signed
      0.6.5 with the wrong key;
    - reads the passwords as hidden input, passes them to the build only, and clears them afterwards;
    - compares the finished APK's signing certificate with the one every release so far carries.

.PARAMETER Keystore
  The release keystore, outside the repository. Default: ~/folio-release.jks.

.PARAMETER Alias
  The key alias in the keystore. Default: folio.
#>
param(
    [string]$Keystore = (Join-Path $HOME 'folio-release.jks'),
    [string]$Alias = 'folio'
)

$ErrorActionPreference = 'Stop'

# The certificate every release since 0.6.6 is signed with (docs/releases/0.7.0-beta.3.md). A build that differs
# will not install over what is on the phone.
$ExpectedCertificate = 'bad8e099557b70c690e71a3fffe13561c8662c20612047ce1e2559a1c14d8441'

function Stop-Release([string]$Message) {
    Write-Host "STOPPED: $Message" -ForegroundColor Red
    exit 1
}

$repository = (Split-Path -Parent $PSScriptRoot)
Set-Location $repository

# 1. Right place: git must treat this folder as its own worktree, at the tag the version names, with a clean tree.
$top = (git rev-parse --show-toplevel 2>$null)
if (-not $top -or ((Resolve-Path $top).Path -ne (Resolve-Path $repository).Path)) {
    Stop-Release "git does not treat $repository as a checkout of its own (top is '$top'). A worktree folder that was half-removed falls back to the repository above it. Make a fresh one with git worktree add --detach."
}
$versionLine = Select-String -Path (Join-Path $repository 'app/build.gradle.kts') -Pattern '^val folioVersion = "(.*)"$'
if (-not $versionLine) { Stop-Release 'Could not read folioVersion from app/build.gradle.kts.' }
$version = $versionLine.Matches[0].Groups[1].Value
$tag = (git describe --tags --exact-match 2>$null)
if ($tag -ne "v$version") { Stop-Release "HEAD is at '$tag', not v$version. Check out the tag first." }
if (git status --short) { Stop-Release 'There are uncommitted changes, so nobody could rebuild this APK from a commit.' }
Write-Host "Folio $version at $tag" -ForegroundColor Green

# 2. Right keystore.
$Keystore = (Resolve-Path $Keystore -ErrorAction SilentlyContinue).Path
if (-not $Keystore) { Stop-Release 'The keystore was not found. Pass -Keystore with its path (outside the repository).' }
if ($Keystore -like '*folio-release-2026*') { Stop-Release 'That is the 2026 keystore, 0.6.5''s wrong signer. Use folio-release.jks.' }
if ($Keystore.StartsWith($repository, [StringComparison]::OrdinalIgnoreCase)) { Stop-Release 'The keystore must live outside the repository.' }

# 3. Git Bash, found from git itself. Plain `bash` in PowerShell can be WSL's.
$gitExe = (Get-Command git).Source
$bash = Join-Path (Split-Path -Parent (Split-Path -Parent $gitExe)) 'bin\bash.exe'
if (-not (Test-Path $bash)) { Stop-Release "Git Bash was not found at $bash." }

# 4. Environment: Android SDK and JDK when not already set, the keystore, hidden passwords.
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $env:JAVA_HOME) {
    $studio = 'C:/Program Files/Android/Android Studio/jbr'
    if (Test-Path $studio) { $env:JAVA_HOME = $studio }
}
$env:FOLIO_RELEASE_STORE_FILE = $Keystore -replace '\\', '/'
$env:FOLIO_RELEASE_KEY_ALIAS = $Alias

function Read-Secret([string]$Prompt) {
    $secure = Read-Host -AsSecureString $Prompt
    return [System.Net.NetworkCredential]::new('', $secure).Password
}

$code = 1
try {
    $storePassword = Read-Secret 'Keystore password (typing is hidden)'
    $keyPassword = Read-Secret 'Key password (hidden; press Enter if it is the same)'
    if (-not $keyPassword) { $keyPassword = $storePassword }
    $env:FOLIO_RELEASE_STORE_PASSWORD = $storePassword
    $env:FOLIO_RELEASE_KEY_PASSWORD = $keyPassword
    & $bash 'scripts/release-signed.sh'
    $code = $LASTEXITCODE
} finally {
    Remove-Item Env:FOLIO_RELEASE_STORE_PASSWORD, Env:FOLIO_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $storePassword = $null; $keyPassword = $null
}
if ($code -ne 0) { Stop-Release "the build failed (exit $code). The passwords are not printed above." }

# 5. The finished APK must carry the usual certificate.
$output = Join-Path $repository "dist/Folio-$version"
$certificate = Select-String -Path (Join-Path $output 'signing-certificate.txt') -Pattern 'certificate SHA-256 digest: (\w+)' |
    Select-Object -First 1
$found = if ($certificate) { $certificate.Matches[0].Groups[1].Value } else { $null }
if ($found -ne $ExpectedCertificate) {
    Stop-Release "the APK is signed with $found, not $ExpectedCertificate. Do not publish it: it will not install over the current Folio."
}
Write-Host "Signing certificate matches ($ExpectedCertificate)." -ForegroundColor Green
Write-Host "DONE: $output" -ForegroundColor Green
Get-Content (Join-Path $output 'SHA256SUMS.txt')
