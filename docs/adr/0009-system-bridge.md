# 0009: The System Bridge is a broker, typed providers and a handshake, never a root shell

- **Status:** proposed, 2026-10-05
- **Extends:** [0008](0008-platform-architecture.md) (tiers A0 to A5, the Capability Broker). Does not replace it.

## Context

ADR 0008 says a system bridge is the third layer and that packages never inherit privilege. It does not say how Folio
code reaches Shizuku, root or SystemUI, how a bridge and Folio of different versions talk, or what fails safe. PRV-17
already requires that every feature works without root and that anything privileged sits in a separate, labelled,
off-by-default tier.

## Decision

1. **Features ask for capabilities, not technologies.** `FolioCapability` ids (`system.shade.open`,
   `device.hinge.angle`, `system.status.modules`) are semantic. Only a `CapabilityProvider` touches Shizuku, `su`,
   a bridge or a vendor API. No UI file imports any of them.
2. **One `CapabilityBroker`** picks the lowest tier that can do a capability (least privilege), reports why one cannot
   (`UnavailableReason`: no provider, not granted, lost, unsupported, turned off, Safe Mode), and runs every
   privileged call with a standard fallback. A provider that throws is "lost", never a crash.
3. **Tier names stay ADR 0008's.** STANDARD is A0 to A2, SHIZUKU is A3, ROOT is A4, SYSTEM is A5. OEM is not a tier:
   it is an adapter under a tier (a Samsung hinge reader is an A3 or A4 provider that was measured on a device).
4. **Shizuku first, in Folio's own APK.** A3 needs only the Shizuku API and a UserService running Folio-owned code.
   A separate Folio Bridge APK (same signing key, signature-protected bound service, package check) is built only
   when root or a module host needs one (Phase D). Until then there is no new trust boundary to sign or version.
5. **Typed operations only.** No `exec(command)` exists at any layer, nothing a package or the network supplies is
   ever a command, and a package reaches a provider only through a capability id Folio validated.
6. **Handshake.** A bridge says `{protocolVersion, minFolioProtocol, bridgeVersion, capabilityIds, androidApi,
   systemUiAdapter?, oemAdapter?}`. Folio negotiates: too old, needs a newer Folio, nothing usable, or compatible with
   the capabilities both sides know. Unknown ids are ignored. Folio never waits on a bridge to open Home.
7. **Fail safe.** The kill switch (Settings > Advanced > System Bridge) and Safe Mode both switch off every provider
   above A2 at once and keep the configuration. A SystemUI adapter that does not match the Android version reports
   "unsupported": the feature is unavailable, nothing else changes. Nothing in Folio runs at boot.
8. **Presentation is not destruction.** Notification Scenes and any SystemUI presentation never cancel the Android
   notification. Lock-screen work changes presentation only and never touches credential or biometric decisions.
9. **The Market stays declarative.** A package declares `requires.features` ids. Privileged ids need `format: 2`
   (a new permission needs a new format, format-v1) and Folio generates the privacy label from them. That is Phase G
   and waits until the model above is proven.

## Consequences

- Phase A (this record plus `CapabilityBroker.kt` and its tests) changes no behavior. The broker is not yet wired into
  any feature; the first user is the Capability Viewer, then a read-only Shizuku probe behind a Dev Mode flag.
- The threat model gains T20 to T26 and its "compromised phone: root" exclusion is narrowed.
- Folio Studio, the Control Center composer and the shared media surface are configuration models rendered by Folio
  first. They do not need privilege and are not blocked by this record.
- Claims about Fold8 hinge data, Samsung private interfaces and SystemUI hooks stay "unverified" in the capability
  matrix until measured on a phone. The Fold8 is locked and not rooted, so A4 and A5 cannot be verified there.
- **Cost:** a broker is more code than calling Shizuku directly. It is what lets every privileged feature have a
  tested fallback and one place to switch it off.
