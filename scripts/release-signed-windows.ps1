# Signs a Folio release on Windows with the passwords kept in a SecretManagement vault, the way
# release-signed-keychain.sh does with the macOS Keychain: never typed into a shell, written in a file or shown. It sets
# the four FOLIO_RELEASE_* variables for this process only and runs scripts/release-signed.sh in Git Bash, so every
# check in that script still applies, and passes its arguments on to it.
#
# One-time setup, in your own PowerShell (Set-Secret asks for the password, hidden):
#   Install-Module Microsoft.PowerShell.SecretManagement, Microsoft.PowerShell.SecretStore -Scope CurrentUser
#   Register-SecretVault -Name folio -ModuleName Microsoft.PowerShell.SecretStore -DefaultVault
#   Set-Secret -Name folio-release-store
#   Set-Secret -Name folio-release-key
#
# Defaults, each overridable from the environment:
#   FOLIO_RELEASE_STORE_FILE  ~\folio-release.jks   (outside the repository; release-signed.sh refuses one inside it)
#   FOLIO_RELEASE_KEY_ALIAS   folio
#   FOLIO_SECRET_STORE_ITEM   folio-release-store
#   FOLIO_SECRET_KEY_ITEM     folio-release-key
$ErrorActionPreference = 'Stop'

if (-not (Get-Command Get-Secret -ErrorAction SilentlyContinue)) {
    Write-Error 'This helper reads a SecretManagement vault, and the module is not installed. See the setup at the top of this file.'
}

function Get-ReleasePassword([string] $name) {
    try { $value = Get-Secret -Name $name -AsPlainText -ErrorAction Stop } catch { $value = $null }
    if ([string]::IsNullOrEmpty($value)) {
        Write-Error "No secret `"$name`" in the vault, or it is locked. Create it with: Set-Secret -Name $name"
    }
    $value
}

$store = if ($env:FOLIO_RELEASE_STORE_FILE) { $env:FOLIO_RELEASE_STORE_FILE } else { Join-Path $HOME 'folio-release.jks' }
if ($store.StartsWith('~')) { $store = Join-Path $HOME $store.Substring(1) }
if (-not (Test-Path -LiteralPath $store -PathType Leaf)) {
    Write-Error "The release keystore is not at $store. Set FOLIO_RELEASE_STORE_FILE to where it is."
}
# Git Bash reads C:/x but not C:\x as a path.
$env:FOLIO_RELEASE_STORE_FILE = (Resolve-Path -LiteralPath $store).Path -replace '\\', '/'
if (-not $env:FOLIO_RELEASE_KEY_ALIAS) { $env:FOLIO_RELEASE_KEY_ALIAS = 'folio' }
$storeItem = if ($env:FOLIO_SECRET_STORE_ITEM) { $env:FOLIO_SECRET_STORE_ITEM } else { 'folio-release-store' }
$keyItem = if ($env:FOLIO_SECRET_KEY_ITEM) { $env:FOLIO_SECRET_KEY_ITEM } else { 'folio-release-key' }
$env:FOLIO_RELEASE_STORE_PASSWORD = Get-ReleasePassword $storeItem
$env:FOLIO_RELEASE_KEY_PASSWORD = Get-ReleasePassword $keyItem

# Git's own bash, not the WSL one that System32 puts first on PATH.
$bash = Join-Path (Split-Path (Split-Path (Get-Command git).Source)) 'bin\bash.exe'
try {
    & $bash (Join-Path $PSScriptRoot 'release-signed.sh') @args
    exit $LASTEXITCODE
} finally {
    Remove-Item Env:FOLIO_RELEASE_STORE_PASSWORD, Env:FOLIO_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
