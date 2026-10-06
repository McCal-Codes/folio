# Folio Market threat model

**Scope:** the Market, sources, packages and the installer (Folio 0.7.0). There is no script sandbox: the `script` kind is reserved and refused (T10).

**Method:** STRIDE (spoofing, tampering, repudiation, information disclosure, denial of service, elevation of privilege). Each threat lists its mitigation and the test that proves it.

## Assets

- **The user's Home setup:** layout, theme, tweaks and Focus settings.
- **Data Folio can read:** notifications (if allowed), app list, contacts (if allowed), calendar (if allowed).
- **Folio's own stability:** Home must always open.
- **Trust in sources:** the official Folio source, the Community source, and each source's pinned key.

## Trust boundaries

1. The network, between Folio and a source's HTTPS host.
2. A source's publisher, who controls the list and the files it serves.
3. A package's author, who is not always its publisher: a mirror can carry someone else's package, so the author
   signs the package and the source signs the list.
4. The package's contents, which are parsed and applied on the phone.
5. A script's code, which Folio refuses to install (T10).
6. Files the user shares with Folio: `.foliopkg` files and launcher backups.

## Threats and mitigations

| # | Threat | STRIDE | Mitigation | Test |
|---|---|---|---|---|
| T1 | Attacker on the network swaps the index or a package | T | HTTPS only. The signed `entry.json` pins the index's sha256; the index pins every package's sha256 and size. | `RepoClientTest` T1 |
| T2 | An old, vulnerable index is replayed | T | Refuse any `timestamp` older than the last accepted one for that source | `RepoClientTest` T2 |
| T3 | A stale index is served forever to hide an update or revocation | D | Refuse the index after `timestamp + maxAge` (at most 30 days) and show "Couldn't refresh" | `RepoClientTest` T3 |
| T4 | Someone impersonates a source | S | The key fingerprint is shown and pinned the first time the user adds a source. A changed key blocks the source until the user confirms. The official key ships in the app. | `RepoClientTest` T4 |
| T5 | A publisher's key is stolen | S/T | Revocation list; key rotation needs the user's confirmation; Community source signing happens only in CI; build provenance shows the repo and commit | `RepoClientTest` T5 |
| T6 | A malicious package ships code | E | Folio never loads DEX, JAR or native code; unknown file types are rejected; kinds are a closed list | `PackageInstallerTest` T6 |
| T7 | Zip-slip, zip bomb or symlink in a package | T/D | The package is read in memory and never extracted, so no name in it reaches the file system; names must be relative with no `..`; caps on packed size, unpacked size and file count; only a closed set of file types, which also refuses an entry claiming to be a link | `PackageInstallerTest` T7, and the `archive` fuzz target |
| T8 | A malformed manifest or depiction crashes Folio | D | Strict parsers with size caps, where unknown values fall back to safe defaults; Jazzer fuzzing | `ParserFuzzTest`, 5 minutes per parser |
| T9 | A package or script misbehaves and makes Home unusable | D | Safe Mode: two crashes within 60 s of a package change start Folio with that package's changes taken off Home and its record and settings kept, so Try Again puts it back and Remove takes it away; everything else keeps working | `PackageInstallerTest` T9, `InstallStateMachineTest` |
| T10 | A package runs code | E | No package runs code: the `script` kind is reserved and refused when a package is read and when its listing is parsed (`PackageKind.reserved`), and nothing loads DEX, JAR or native code | `PackageInstallerTest` refuses script and settings-page packages; `AuthorSignatureTest` marks a script listing as needing a newer Folio |
| T11 | A package hides what it does | I | The privacy label is generated from `permissions`, never from the author's text; permissions are checked when the package runs | A permission missing from the manifest is denied |
| T12 | A depiction leaks data or phishes | I/S | Closed set of block types; Markdown subset with no HTML or remote images; links must be https and show their domain | HTML and http depictions are rejected by the schema |
| T13 | A source tracks users | I | Only static files; no accounts; no cookies; Folio sends only a plain `User-Agent: Folio`; refresh is opt-in and can be Wi-Fi only | `RepoClientTest` counts every request |
| T14 | Market traffic uses up GitHub rate limits | D | Static files only, conditional requests (ETag), refresh at most every 6 h | `RepoClientTest` T14 |
| T15 | A launcher backup import is malicious | T/D | Only files the user picks; strict parsing and size caps; preview before anything is applied; full undo | Oversized and malformed backup fixtures fail |
| T16 | A reported package stays live | R | Report opens the source's issue form with the package id, version and sha256; Community takedowns go into `revoked.json` | Process documented in the Community repo |
| T19 | A source hands over an app instead of a package | E/T | Off by default (Settings › Market › Installing apps). The listing comes from a signed index whose key was pinned when the source was added; the download is checked against the checksum that index promised **before** the file is written anywhere Android can reach; a listing with no checksum is never installed whatever the setting says; and Android shows its own install screen every time, naming the app, because Folio is not the installer of record for other people's apps. What is *not* checked, and cannot be, is the app itself: Software Update can insist an update carries the same signature as the copy already running, and there is no such anchor for somebody else's app. The source vouches for it. That is Cydia's and Sileo's bargain, and the setting says so in those words (McCal, 2026-09-20). | `MarketApkInstallTest` |
| T17 | A mirror alters a package it carries, or publishes under an author's name | T/S | A source's signature only says the list came from that source. An author signs the package itself: `folio-pkg:<id>:<version>:<sha256>` in the index, and `folio-pkg-files:<id>:<version>:<digest of the files>` in `signature.json` inside the package, so a file shared by hand carries its own proof. The first signed copy pins that key to the package id, the way a phone pins an app's signing key; a later copy signed by another key, one that doesn't match its bytes, or one that has dropped a signature the id has always had, is refused. An unsigned package is allowed and the page says nobody signed it. | `AuthorSignatureTest`, `OpensslInteropTest`, and the `authorSignature` and `authorPayload` fuzz targets |
| T18 | Two sources use one package id | S | An id that belongs to a package inside Folio is refused outright; an id two added sources both offer is shown on both and marked as such. Where the package is signed, the author key pinned to that id settles it: the copy signed by another key is refused. Where neither copy is signed there is nothing to tell them apart, so the store says so and the user chooses | `MarketSourcesTest` |
| T20 | A package or intent steers a privileged call | E | A package never names a provider or a command; it names a capability id Folio validated, and the broker runs a typed operation. There is no `exec(command)` anywhere | `CapabilityBrokerTest` |
| T21 | A rogue app binds the bridge or Shizuku service Folio uses | S/E | The bridge is a signature-protected bound service that also checks the caller's package and certificate; in-process Shizuku UserService code is Folio's own | Phase D tests (bridge does not exist yet) |
| T22 | An old or newer bridge is downgraded to or mismatched | T | The handshake carries a protocol version and the oldest Folio protocol the bridge speaks; too old or too new is "unavailable", never partial trust | `BridgeHandshakeTest` |
| T23 | Privileged integration crashes or loops Folio or SystemUI | D | One switch and Safe Mode turn off every provider above A2 and keep the configuration; a provider that throws counts as lost and the call falls back; no integration runs at boot | `SafeModeBridgeDisableTest`, `BridgeUnavailableFallbackTest` |
| T24 | Access is revoked or Shizuku stops while a rule depends on it | D | Loss is an ordinary state shown in words; the capability falls back and the call is logged | `PrivilegeRevokedTest` |
| T25 | The audit trail leaks what a privileged call touched | I | The ledger holds capability, tier and outcome only, stays on the phone, and is shared only by the person | `AuditEvent` has no content field |
| T26 | A package's privilege wording understates what it asks | I | The label for privileged ids is generated by Folio from the declared ids, like T11 | Phase G |

## Out of scope

- **A compromised phone:** a malicious accessibility service, or root the user installed for something else. Root or Shizuku that the person grants *to Folio* is in scope from T20 on (ADR 0009); a phone already controlled by an attacker is not.
- **Apps installed from Play Store, F-Droid or Obtainium** after the Market hands off to them (later feature). Those stores' own protections apply.
- **Sources the user explicitly trusts after the warning.** Folio still enforces everything above except the review.
