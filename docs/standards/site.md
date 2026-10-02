# Site and docs standard (`WEB`)

How foliolauncher.com and Folio's public pages are written and kept true: the claims they make, the words they use,
the pictures on them, and what stops them rotting. The site lives in [McCal-Codes/folio-site](https://github.com/McCal-Codes/folio-site);
these rules apply to it, to the README and to anything else the project says to the public.

The short version:

> **The site says what the app says, at the release people can download, in words a person wrote, and a check fails
> when it stops being true.**

This standard exists because the site drifted in the places nobody was looking. On 1 Oct 2026 an audit found the live
site a release behind (0.6.7 while 0.6.7.3 was out) because the deploy skipped quietly without its token; a roadmap
that would have listed unreleased work as shipped; a privacy claim ("two things leave the phone, one of them a crash
report") that `PRIVACY.md` contradicts, since Folio sends no crash reports; a promise of a VirusTotal link on every
release that 0.6.6, 0.6.7 and 0.6.7.3 did not carry; and help pages that could never be flagged as stale. Every rule below is aimed at
that shape of mistake.

## Rules

### Truth

- **WEB-1 MUST** build product facts from the app repository at the latest release's tag, never from `main` and never
  retyped: version, date, size, checksum, signing certificate, changelog, roadmap, tweak pages and the screen matrix.
  The site never runs ahead of the APK a reader can download.
- **WEB-2 MUST NOT** present planned, exploring or unreleased work as shipped. A roadmap section counts as shipped
  only when its version is a dated changelog entry.
- **WEB-3 MUST** keep every claim about privacy, permissions, signing, networking and what a release contains in step
  with [PRIVACY.md](../../PRIVACY.md), [PERMISSIONS.md](../../PERMISSIONS.md) and the release's own assets. When one of
  those changes, the page that repeats it changes in the same week. A claim that cannot be traced to one of them does
  not go on the site.
- **WEB-4 MUST** say so when something is missing rather than fall back silently. A release without
  `signing-certificate.txt` is covered by an explicit fallback that logs it, and a blank or non-hex checksum or signer
  fails the build.
- **WEB-5 MUST NOT** invent content: no testimonials, ratings, download counts, compatibility claims, comparison
  tables about other products, or "verified" and "official" badges Folio did not earn ([MKT-13](market.md)).
  Compatibility claims come from what Folio is tested on ([ADP](adaptive-layout.md), the screen matrix).
- **WEB-6 MUST** credit what a thing is inspired by wherever it is described, as the app does. A tweak's page names
  the person who made the idea, taken from the package's own depiction.

### Words

- **WEB-7 MUST** be first person and plain: concrete claims over adjectives, no "revolutionary", "seamless" or
  "next-generation". Punctuate without em dashes ([REL-9](releases.md)) and spell American.
- **WEB-8 MUST** get wording for bios, intros, pitches and anything else that speaks for the project's owner from the
  owner. A draft by anyone else, including a tool, is labeled a draft until the owner has changed it.
- **WEB-9 SHOULD** name the thing the way someone would say the problem: "An update will not install", not
  "Signature mismatch". The answer comes in the first paragraph.
- **WEB-10 MUST NOT** use the word "safe" as a claim. Say what is true and checkable: "signed with the same key as
  every release", "asks for no permissions".

### Pictures

- **WEB-11 MUST** use real screenshots, taken in Screenshot Mode (9:41, no personal notifications, no private device
  names or calendar data), or label the image "Design preview, not final". A mockup never looks like a shipped
  feature, and AI-generated phone screens never stand in for Folio's.
- **WEB-12 MUST** give every image useful alt text and declared dimensions. An image wider than three times its slot is
  wasted bytes.
- **WEB-13 MUST** attach a screenshot to a release or a feature only when it really shows it. A stop with no matching
  shot has none.
- **WEB-14 SHOULD** give each page its own share image, drawn from its own title and description. A page without one
  must not link a missing file.

### Privacy and security

- **WEB-15 MUST NOT** add analytics, trackers, session replay, fingerprinting, ads or third-party marketing scripts.
  Performance is measured in the lab, never from visitors.
- **WEB-16 MUST** keep a restrictive Content Security Policy, HSTS, `X-Content-Type-Options`, `Referrer-Policy` and
  `Permissions-Policy` on every HTML response, and **MUST** know whether each response comes from the static assets or
  from Worker code, since headers rules do not apply to Worker responses.
- **WEB-17 MUST NOT** put a secret, token, supporter code or private address in the repository or in client-side code.
  Deploy tokens live in repository secrets and are set by their owner.
- **WEB-18 MUST** treat anything rendered from the app repo as trusted only because the tag it comes from is. Markdown
  from a changelog is rendered, not executed, and a new source of content gets the same thought before it is added.

### Accessibility and performance

- **WEB-19 MUST** meet WCAG 2.2 AA, with [A11Y](accessibility.md) applying to the web: one meaningful `h1`, no skipped
  heading levels, a skip link, visible focus, targets large enough to touch, contrast in both light and dark, no
  information carried by color alone, and a usable page at 200% zoom.
- **WEB-20 MUST** respect `prefers-reduced-motion` and add no animation merely because it is available. Navigation is
  ordinary browser navigation.
- **WEB-21 MUST** ship no JavaScript a page does not need. Interactions CSS and HTML can do (filters, disclosure,
  scrolling rows) are done with CSS and HTML.
- **WEB-22 MUST** hold Largest Contentful Paint at or under 2.5 s and Cumulative Layout Shift at or under 0.1 on the
  pages that matter, and accessibility at 95 or above, as measured by Lighthouse in CI. These limits fail the run.
- **WEB-23 MUST** keep the layout correct from a 320px phone to a very wide display, with no horizontal scrolling of
  the page itself.

### Checks and upkeep

- **WEB-24 MUST NOT** weaken `scripts/check.mjs` to make a build pass. A legitimate new class of mistake gets a new
  check, not a bigger exception list.
- **WEB-25 MUST** give each help page the release it was written for, set by hand, so the freshness check can fail. A
  page is flagged when what it describes changed, not because time passed.
- **WEB-26 MUST** make a green deploy mean the site was published. A missing deploy token fails the run.
- **WEB-27 SHOULD** show the work before it is committed: screenshots or a preview of any visible change, and what was
  and was not checked in the pull request. Say what you could not check.

## Where Folio is today

Measured on 1 and 2 Oct 2026, against folio-site `main`.

- **Truth (WEB-1, 2, 4).** `scripts/fetch-release.mjs` reads the latest release, then the changelog, roadmap, tweak
  packages (manifest, depiction, icon) and screen matrix at that release's tag. It fails the build on a failed fetch, an
  empty list or a blank checksum. `roadmap.astro` counts a section as shipped only if it is a dated changelog entry.
  `check.mjs` fails if the download version has no dated changelog entry and page.
- **Claims (WEB-3).** Partly enforced. `src/data/claims.json` in folio-site ties eight claims about privacy, permissions
  and signing to the phrase in `PRIVACY.md`, `PERMISSIONS.md` or Keyd's README that supports each, and `check.mjs`
  fails the build when a page makes a claim whose phrase is gone, or repeats a phrase the documents contradict. It is a
  tripwire: it only recognizes the phrasings listed, and it proves the documents say something, not that the app does.
- **Words (WEB-7, 8).** Held by habit and by review. No check looks for em dashes or banned words.
- **Pictures (WEB-11 to 14).** `check.mjs` checks alt text, dimensions, aspect ratio, images wider than three times
  their slot, and unused files. It also fails if a page's share image does not exist. 41 pages have share images
  drawn per page by `npm run og`; the rest are hand-made. The roadmap timeline has screenshots only for 0.6.0 and
  0.6.6.
- **Privacy and security (WEB-15 to 17).** No analytics and no client script on any page. `public/_headers` sets the CSP,
  HSTS, nosniff, Referrer-Policy and Permissions-Policy, and they appear on HTML responses served through the Worker.
  `workers_dev` is off.
- **Accessibility and performance (WEB-19 to 23).** 258 page and width combinations (43 pages, six widths from 320 to
  2560) have no horizontal overflow. A contrast sweep of nine pages found no failures in light or dark. Lighthouse in CI
  (`.github/workflows/lighthouse.yml`) measures six pages: LCP 1.1 to 1.9 s, layout shift 0.000, every category 100.
- **Upkeep (WEB-24 to 26).** Help pages are pinned to 0.6.7 by hand and the check fails when one is two releases
  behind. `deploy.yml` fails without `CLOUDFLARE_API_TOKEN`. The dispatch from this repo (`site.yml`) has not fired
  for a real release yet.

## Gaps

| | What it takes | Size |
|---|---|---|
| 1 | ~~A check that the site's privacy, permissions and signing sentences match `PRIVACY.md` and `PERMISSIONS.md`~~ (done as a tripwire, see Claims above) | M |
| 1a | Check the claims against the code instead of prose: compare the permissions in the built APK's manifest with the list in `PERMISSIONS.md`, and Keyd's manifest with "asks for no permissions". This belongs in the app repo's CI, where the manifest is, and is what would make WEB-3 a guarantee | M |
| 1b | Flag any sentence on a page that makes a privacy or safety claim ("never", "no tracking", "safe") and is not in the registry, so a person reviews new phrasings | S |
| 2 | A lint for em dashes and banned words in `src/` (WEB-7) | S |
| 3 | Lighthouse over every page type, not six (WEB-22), and a test that a failing page really turns the run red | S |
| 4 | Screenshots for the timeline stops and the tweak pages, from Screenshot Mode (WEB-11, 13) | M |
| 5 | The release workflow attaches `signing-certificate.txt` every time, so the fallback in WEB-4 is never used | S |
| 6 | Confirm the `site.yml` dispatch fires on the next real release (WEB-26) | S |
| 7 | Tab order through the interactive pages with a real keyboard and a screen reader (WEB-19) | S |

## Recorded exceptions

None.
