# 0010: The site and docs get a standard

- **Status:** proposed, 2026-10-02

## Context

Every standard in `docs/standards/` is about the app. The public site, the README and the help pages had rules too, but
they lived in `folio-site`'s check script, a CI file and the owner's habits, not where a contributor would look. An
audit on 1 Oct 2026 found the live site a release behind, a roadmap that would have listed unreleased work as shipped,
and two claims (a crash-report upload and a VirusTotal link on every release) that the app and its releases did not
back up. None of it was caught by a check, because no rule said it had to be true.

## Decision

- **Add `docs/standards/site.md` (`WEB`)** with numbered MUST / SHOULD / MAY rules, in the same shape as the others: rules,
  where things stand today, ranked gaps.
- **The site reads the app at the release's tag**, so it never describes something nobody can download.
- **Public claims about privacy, permissions and signing must match the documents that make them**, and the checks that
  can enforce a rule live in `folio-site` and are listed as gaps until they exist.
- **Wording that speaks for the owner comes from the owner.**

## Consequences

- **Good:** a reviewer can cite `WEB-3` instead of a preference, and the rules for the site are in the repo.
- **Good:** the gap list ranks the checks still worth writing.
- **Bad:** one more standard to keep current. Several rules (WEB-3, WEB-7) are held by review until a check exists.
- **Bad:** WEB-8 means a tool or contributor cannot publish intro or pitch copy on its own, which slows some work on
  purpose.
