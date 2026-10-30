# Folio 0.7.0: Apple Services tweak (proposal)

Status: **planned / experimental**, not implemented. Created 2026-10-25.
Target: Folio 0.7.0 Market release, without blocking the Market's stable launch.

## Decision

Add one **Apple Services** entry to the 0.7.0 roadmap, exposing four independently enabled modules: **iMessage**, **iCloud**, **Find My**, and **FaceTime**. The tweak owns discovery, on-device UI, configuration, shortcuts and capability/status displays. Any native third-party Apple-protocol implementation must live in a separate optional Android companion, not inside a downloaded `.foliopkg`.

The existing `app/src/main/assets/roadmap.json` has Apple services in **Exploring**. The separately proposed `0.7.0-apple-services-roadmap_COPY.json` is an exact copy of the current roadmap with that entry moved into 0.7.0 as **planned**, not done. The live roadmap is intentionally unchanged pending review.

### Why this architecture

- `docs/sdk/README.md`: Market packages are **data only** (JSON/images, no DEX/JAR/native libraries/scripts). No Rust messaging binary may be silently installed as a package.
- `docs/architecture.md`: Folio is an Android Home app; its process must not host an always-on Apple push client.
- `docs/standards/releases.md`: `main` stays releasable; changes enter via small topic branches and pull requests. Incomplete behavior must be gated; beta features are not stable promises.
- Treat all reverse-engineered functionality as unsupported, subject to Apple changes, service bans and licensing review.

## Modules, starting scope and boundaries

### 1. iMessage
- 0.7.0 minimum: opt-in settings + status ("Not installed", "Not activated", "Ready", "Unavailable"), Android app launch/hand-off to a separately installed compatible client, troubleshooting and transparent limitations. No fake chat UI.
- Experimental follow-on: fork or original companion **Folio Messages**, based on OpenBubbles protocol *research*, with independent process, encrypted local secrets, permissions, background-delivery tests and Mac-hardware activation. Prototype email-address registration first.
- Self-host means owning optional provisioning/management infrastructure, **not** self-hosting Apple's delivery servers. Genuine Mac activation may not require an always-on relay. Apple hardware data must not be pooled, published or copied into public Market packages.
- Phone-number registration remains a separate research item and may require an always-on compatible iPhone.
- OpenBubbles/rustpush license is SSPL with an OpenBubbles-specific exception: do not assume the exception applies to Folio or import native code without reviewing the actual license and distribution obligations.
- **Never require disabling Advanced Data Protection or Contact Key Verification on a user's primary Apple Account.** Clearly display upstream incompatibilities.

### 2. iCloud
- 0.7.0 minimum: browser-backed authenticated access to `https://www.icloud.com/` with launch points for web Mail, Photos, Drive, Notes, Calendar, Contacts, etc., but no unsupported silent web scraping. No storage of full Apple Account passwords.
- After authorization review, consider native iCloud Mail/Calendar/Contacts through Apple's supported third-party authorization, with app-specific password only when required. Let Android's existing specialist apps and sync providers do the work where possible.
- Shared Albums: OpenBubbles claims invited album viewing/contributing; **not creating albums**. Prototype only after hands-on verification.
- Full native iCloud Photos, Drive, Keychain and device-backup sync are **out of 0.7.0 scope**; no general public Android API is established.

### 3. Find My
- 0.7.0 minimum: open Apple's official `https://www.icloud.com/find` for **Find Devices**. Prefer the system browser and Apple's login, not credential capture.
- Experimental follow-on: a clearly read-only **Find My** companion viewer based on OpenBubbles' published functionality. Its FAQ claims tracking devices and receiving shared locations, but **not sharing live location**. Independently test what types of items/people/devices work.
- Apple Find My network accessory MFi integration is **not** the same thing as a public Android API for locating consumer Apple devices, people or AirTags.
- No hidden location polling, no location-sharing impersonation, and no geolocation transfer to a Folio server. Destructive operations such as erase, or account-affecting operations such as Lost Mode, stay in Apple's own signed-in UI unless separately designed and authorized.

### 4. FaceTime
- 0.7.0 minimum: recognize valid `facetime.apple.com` invitation links shared by the user and open in supported Chrome/Edge external browser. Android can **join** a call via Apple's supported web experience but cannot create calls that way.
- Experimental follow-on: investigate native incoming/outgoing calls using OpenBubbles, which claims both. Treat this as an **upstream claim, not yet a Folio-verified guarantee**. Do not imply web FaceTime includes SharePlay or screen sharing.
- Camera/microphone access belongs to browser or companion. The launcher must not request either permission merely to display the tweak.

## User experience: one entry, four modules

Suggested Market and Settings terminology:

```
Apple Services  [Experimental]
  iMessage     [Open / Set up companion]   status
  iCloud       [Open iCloud]               web access
  Find My      [Find Devices]              web / optional viewer
  FaceTime     [Join a link]               paste/share link
  Advanced
    Connection status
    Experimental capabilities
    Privacy & data handling
    Disconnect / revoke
```

Keep each module independent, individually dismissible, and off until activated by the user. No Apple sign-in on initial launcher setup. Show clear provider attribution; do not imply affiliation with Apple.

An installed companion must authenticate interactions via explicit Android IPC permission/protocol contracts, limited exported components, and signature verification where appropriate. The launcher must gracefully degrade when companion is absent, crashed, outdated, or untrusted.

Do **not** bundle `OpenBubbles` binaries, private credentials, hardware codes or registration scripts inside declarative Market archives. The Market may offer documentation, toggles, presentation settings and install/deep links to a separately signed application **only after** the existing Market policy permits such links.

## Acceptance criteria for 0.7.0 MVP

- [ ] Apple Services appears as one optional tweak with four independently configurable entries.
- [ ] The browser entry points are real and working on a clean Android install: iCloud homepage and Find Devices.
- [ ] FaceTime invitation links can be safely opened in a supported browser without collecting them on a server; reject malicious/invalid domains and show actionable errors.
- [ ] iMessage entry transparently identifies its companion requirement; app-launch only when installed, otherwise describe setup instead of faking a connection.
- [ ] No Apple Account password, session token, message, hardware identifier or location is logged, exposed via Market diagnostics, exported by layout backup or placed in a package.
- [ ] The tweak does nothing in the background while disabled; launcher, Market and Safe Mode continue working without the companion.
- [ ] Verify behavior on a clean non-rooted Android 12+ device and the project's Fold8; cover closed/open fold and accessibility.
- [ ] Document Apple/third-party attribution, permission requirements, compatibility, account risk and external dependencies.
- [ ] Unit-test URL validation, link routing, settings/state migration and companion-unavailable behavior; run existing Market tests.
- [ ] Never mark an untested private protocol capability as `done`; postpone it if unsupported.

## Explicit non-goals and deferred work

- Replacing Google Messages as the default SMS/RCS provider within this tweak.
- Providing full iCloud Drive/Photos/Keychain sync.
- Hosting iMessage itself or making Apple's private servers optional.
- Native FaceTime, advanced Find My and native Shared Albums as 0.7.0 release blockers.
- Implementing any native client by loading arbitrary code from a `.foliopkg`.
- Requiring root, disabling SIP, or turning off security protections for every Folio user.

## Research references (checked 2026-10-25)

- OpenBubbles app: https://github.com/OpenBubbles/openbubbles-app
- OpenBubbles self-host: https://openbubbles.app/quickstart.html
- OpenBubbles FAQ including Find My, Shared Albums and FaceTime: https://openbubbles.app/docs/faq
- OpenBubbles Rust backend: https://github.com/OpenBubbles/rustpush
- BlueBubbles baseline: https://github.com/BlueBubblesApp/bluebubbles-app
- Apple supported FaceTime on Android: https://support.apple.com/en-ie/109364
- Apple iCloud on the web: https://support.apple.com/guide/icloud/sign-in-and-use-icloudcom-mm281e3e7d/icloud
- Apple Find Devices: https://support.apple.com/guide/icloud/find-devices-on-icloudcom-overview-mm6b1aa045/1.0/icloud/1.0
- Apple third-party Mail/Calendar/Contacts authorization: https://support.apple.com/en-us/121539
- Apple app-specific password: https://support.apple.com/en-us/102654
- Apple Find My MFi accessory scope: https://developer.apple.com/find-my/

## Follow-up before implementation

Audit the current branch for applicable package/tweak UI integration points, screen ownership, permissions, URL-routing helpers, app detection and tests. Implement UI and link routes in a small follow-up pull request. Keep native protocol support in its own repository/module and explicitly version a narrow companion integration contract. This document is planning, not a claim that the tweak has shipped.
