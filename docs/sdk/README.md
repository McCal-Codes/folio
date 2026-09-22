# Folio SDK

Make themes, tweaks and layouts for the Folio Market.

**Status:** draft, first shipped in Folio 0.6.6 for supporters. The format can still change before the Market opens to everyone in 0.7.0.

| Page | What it covers |
|---|---|
| [Package format v1](format-v1.md) | `.foliopkg` files, sources, permissions, links |
| [JSON Schemas](schema/v1/) | Manifest, page, index, entry, revocation list, tweak bundle, settings page |
| [Folio's own source](source/) | The app's real themes and tweaks as packages: the worked example |
| [ADR 0002: signing](../adr/0002-signing.md) | Why ECDSA P-256, and how keys are pinned |
| [Threat model](threat-model.md) | What the Market defends against, and how |
| [Cabinet sample](examples/cabinet/) | A complete `tweakBundle` package |
| [Decision records](../adr/) | Why the format works this way |

## Quick start

1. Copy `examples/cabinet/` and change `id`, `name` and `author` in `manifest.json`.
2. Add `"$schema"` to each JSON file (the sample already does). VS Code then autocompletes fields and flags mistakes as you type.
3. Check it with the validator, which needs python3 and nothing else:

   ```bash
   tools/folio-pkg.py validate path/to/your/source
   ```

   It takes a source, a single package folder or a `.foliopkg`, and checks three things: every file against the
   schemas, the things the schemas can't see (that the index and the packages agree, that every picture named is
   actually there, that a signed source's entry matches its index), and the limits a phone applies before it will
   open a package at all.
4. Publish it in one of three ways:
   - **Your own source:** use the template repo; a GitHub Action signs it and publishes it to Pages.
   - **The Community source:** open a pull request, CI checks it, and the Folio project reviews it.
   - **A single file:** share the `.foliopkg`. Folio labels it "Unknown developer" because it isn't signed.

## Rules for every package

- **Data only:** JSON and images, plus an optional sandboxed script. No DEX, JAR or native code.
- **Declare everything:** list every permission your package uses. The privacy label is built from that list.
- **Credit and licensing:** credit anything that inspired you, and don't include GPL code.
- **Made with AI:** set `aiAssisted` in the manifest, also start `description` with "AI-assisted (tool name).", and keep
  both on updates and forks ([AI-6](../standards/ai-contributions.md)).
- **Look, read and fit like Folio:** contrast, screens, real screenshots and testing are in the template's
  [STANDARDS.md](https://github.com/McCal-Codes/folio-source-template/blob/main/STANDARDS.md), drawn from Folio's
  [standards](../standards/README.md).

## Working on the parsers

The schemas are the contract, so the tests check Folio's parsers against them (ADR 0005):

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :market:testDebugUnitTest
```

`SchemaConformanceTest` validates the samples, checks that the Kotlin enums match the schema's values, and mutates the
samples thousands of times, failing if the schema and the parser ever disagree. Change a limit in one place and it fails.

Fuzzing (five minutes per parser, one target per run):

```bash
JAZZER_FUZZ=1 JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :market:testDebugUnitTest --tests '*ParserFuzzTest.manifest'
```

Targets: `manifest`, `depiction`, `entry`, `index`, `revoked`, `archive`, `tweaks`, `jsonGuard`, `versions`, `localizedText`. Without `JAZZER_FUZZ` the same test replays the
saved inputs in `market/src/test/resources/com/mccal/folio/market/ParserFuzzTestInputs/`, which is what CI does. When
fuzzing finds something, Jazzer writes the input next to those seeds; commit it so the case stays covered.
