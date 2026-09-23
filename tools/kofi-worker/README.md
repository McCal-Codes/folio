# Ko-fi supporter codes

A small Cloudflare Worker that turns a Ko-fi payment into a Folio supporter code.

It hands out codes from a pool that was minted on your Mac, so **the signing key never goes online**. If this worker
is ever broken into, the worst anyone gets is the codes already sitting in the pool — not the ability to make more.

```
Ko-fi payment ──webhook──▶ worker ──▶ takes one unused code from D1 ──▶ emails it ──▶ Settings › Supporter
```

## The short version (22 Sep 2026)

Folio's own settings are already in `wrangler.toml`: a tip or donation of any size earns one month (`m1`), Backer and
Builder payments earn a month each, and Coffee earns nothing. After deploying, the Mac's admin page
(`scripts/code-admin.py`) does the rest: **Send to the Ko-fi worker** fills the pool, **Check** says whether
everything is set, **Test payment** runs a pretend payment through and puts the code back, and **Sync** brings each
hand-out into the ledger. The one-time steps, in order:

```bash
npx wrangler login
npx wrangler d1 create folio-codes                      # paste the database_id into wrangler.toml
npx wrangler d1 execute folio-codes --remote --file=schema.sql
npx wrangler secret put KOFI_TOKEN                      # Ko-fi › Settings › API › Webhooks › Verification token
npx wrangler secret put ADMIN_TOKEN                     # any long random string: openssl rand -base64 32
npx wrangler secret put RESEND_KEY                      # optional, to email the codes; set MAIL_FROM too
npx wrangler deploy                                     # prints the worker's address
```

Then paste that address into Ko-fi › Settings › API › Webhooks and press Ko-fi's Send test: the worker writes the
test down and uses no code, and the admin page's Check shows it arrived. In the admin page's Ko-fi worker section,
save the address and the same `ADMIN_TOKEN`, then Send to the Ko-fi worker to fill the pool and Check again.

To try it all without an account: put `KOFI_TOKEN=...` and `ADMIN_TOKEN=...` in `.dev.vars` (gitignored), run
`npx wrangler d1 execute folio-codes --local --file=schema.sql` and `npx wrangler dev --local`, and point the admin
page at `http://127.0.0.1:8787`.

## Selling a code as a shop item

The least work for the most cover: a shop item anyone can buy, with no membership and no thinking about amounts.

1. **Ko-fi › Shop › Add item.** Name it what it is, for example "Folio early access, one month". Price it at $3, the
   same as a month of Coffee, so nobody can buy access cheaper than a member gets it. No shipping, no stock limit.
2. **Copy its link.** The last part is its `direct_link_code`, for example `ko-fi.com/s/1a2b3c4d5e` → `1a2b3c4d5e`.
3. **Put it in `POOLS`** in `wrangler.toml`, then deploy again:

   ```json
   {"shop": {"1a2b3c4d5e": "m1"}}
   ```

4. **Buy it once yourself** to see the whole path work, then check the admin page: the hand-out shows in Sync, with
   the Ko-fi transaction next to it.

Codes are one month from the day they're **redeemed**, not from the day they're minted, so an item can sit in the shop
for months and the code a buyer gets is still a full month. Nothing has to be re-uploaded.

Say so in the item's description: what it opens (the Market, Keyd, Beta Features), that it lasts a month, that Folio
is free and open source either way, and that the code arrives by email straight after paying.

## Setting it up

You need a Cloudflare account and `npx wrangler`. Everything below happens in this folder.

**1. Make the database and its tables**

```bash
npx wrangler d1 create folio-codes
```

Paste the printed `database_id` into `wrangler.toml`, then:

```bash
npx wrangler d1 execute folio-codes --remote --file=schema.sql
```

**2. Mint a pool of codes and load them**

```bash
python3 ../../scripts/beta-code.py pool --scopes beta --count 200 --pool beta > pool.sql
npx wrangler d1 execute folio-codes --remote --file=pool.sql
```

Make one pool per thing you sell. `--pool all --scopes beta,look,power,keys` for the top tier, `--pool beta` for a
coffee. Add `--expires 2027-01-01` for codes that run out — worth doing for monthly members, so lapsing takes care of
itself. `pool.sql` holds real codes: don't commit it.

**3. Say which payment earns which pool**

Edit `POOLS` in `wrangler.toml`:

```json
{"tiers": {"Bronze": "beta", "Gold": "all"}, "shop": {"1a2b3c4d5e": "all"}, "tipFrom": 5, "tipPool": "beta"}
```

- `tiers` — by membership tier name, exactly as Ko-fi spells it. `"*"` covers every tier.
- `shop` — by the shop item's `direct_link_code` (it's in the webhook payload, and in the item's Ko-fi link).
- `tipFrom` / `tipPool` — one-off tips from this amount up earn this pool. Leave both out and tips earn nothing.
- `tipBands` — a pool per amount, for buying time rather than one flat thank-you. A payment earns the largest band
  it clears, and anything under the smallest earns nothing. Folio's own bands are $3, $6 and $12 for one, two and
  four months, which is what the Coffee tier costs a month, so nobody can buy access cheaper than a member gets it.
- `tipCurrency` / `tipRates` — what those amounts are written in, and what other currencies are worth in it.
  `tipCurrency` is US dollars unless you say otherwise. Ko-fi sends the amount in whatever the payer used, so
  without a rate a 500 JPY tip — about three dollars — would clear the `from: 12` band and buy four months. A
  currency with no rate earns nothing instead, and is written into `problems` as `unpriced JPY` for you to price
  and send by hand.

```json
{
  "tipCurrency": "USD",
  "tipRates": {"EUR": 1.08, "GBP": 1.27},
  "tipBands": [{"from": 3, "pool": "months1"}, {"from": 6, "pool": "months2"}, {"from": 12, "pool": "months4"}]
}
```

The rates are yours to keep roughly right; they don't need to be to the cent, and a currency you'd rather handle
yourself can simply be left out.

Mint those pools with the months on the code, so the clock starts when it's redeemed rather than when it was minted:

```bash
python3 ../../scripts/beta-code.py pool --scopes beta,keys --months 1 --count 50 --pool months1 > pool.sql
```

Anything not named here earns nothing, which is the safe default.

**4. Secrets, then deploy**

```bash
npx wrangler secret put KOFI_TOKEN     # the verification token from Ko-fi › Settings › Webhooks (tap Show)
npx wrangler secret put RESEND_KEY     # optional: a Resend API key to send the email
npx wrangler deploy
```

Without `RESEND_KEY` the worker still claims and records a code for each payment — you just send it by hand. A
missing mail provider must never lose someone's code. With one, set `MAIL_FROM` to an address on a domain you've
verified with Resend.

**5. Point Ko-fi at it and test**

Put the worker's URL in Ko-fi › Settings › Webhooks, then use Ko-fi's own test buttons. Ko-fi's tests carry the
made-up transaction id `00000000-1111-2222-3333-444444444444`, which the worker writes down in `checks` and answers
without using a code. For a test that does claim one, use the admin page's Test payment, which puts it back after.
Check what happened:

```bash
npx wrangler d1 execute folio-codes --remote --command "SELECT * FROM handled ORDER BY at DESC LIMIT 5"
```

Redeem one of those test codes in Folio (Settings › Supporter) to see the whole path work.

## Day to day

```bash
# How many codes are left, per pool
npx wrangler d1 execute folio-codes --remote --command \
  "SELECT pool, COUNT(*) FROM codes WHERE used_at IS NULL GROUP BY pool"

# Payments that arrived when a pool was empty — mint more, then send these by hand
npx wrangler d1 execute folio-codes --remote --command "SELECT * FROM problems"
```

Ko-fi retries a webhook until it gets a 200, so the worker records every `message_id` it has handled and answers
"already handled" on a repeat. One payment, one code.

## What this can't do

- **Ko-fi only tells you about payments**, never about a membership ending. So give members' codes an expiry a little
  longer than their billing period and mint a fresh one on each payment; lapsing then takes care of itself.
- **A code can't be recalled** once it's out, because Folio checks it offline. Refunds and sharing are handled by
  expiry, and if a code ever gets passed around, its serial can be blocked in an app update.
- **Folio is open source**, so anyone can build it without the check at all. Codes are a thank-you and a convenience,
  not a lock.

## The admin routes

Everything under `/admin/` needs `Authorization: Bearer <ADMIN_TOKEN>` and is off (503) until that secret is set.
The Mac's admin page is the client; Folio Dev's "Hand out a code" will be the second.

| Route | What it does |
| --- | --- |
| `GET /admin/health` | Which secrets are set, POOLS, stock per pool against the pools POOLS names, problems, the last Ko-fi test, five unused codes per pool so the Mac can check they carry its signature |
| `GET /admin/recent` | Every hand-out, newest first: Ko-fi transaction id, name, type, amount, code, pool, emailed |
| `POST /admin/pool` | `{pool, codes}`: add codes minted on the Mac. Anything that isn't shaped like a Folio code is refused |
| `POST /admin/test` | A pretend payment through the real rules and claim, then the code goes back and the payment is forgotten |
| `POST /admin/claim` | `{pool, name}`: one code by hand, recorded as `by_hand` |

`handled` now keeps the Ko-fi transaction id, name, type, amount and currency next to each code, which is what lets
the Mac match a hand-out to its CSV. The email address is still used to send the code and never stored. A worker
created before these columns needs them added once:

```bash
for column in "transaction_id TEXT NOT NULL DEFAULT ''" "from_name TEXT NOT NULL DEFAULT ''" \
  "type TEXT NOT NULL DEFAULT ''" "amount TEXT NOT NULL DEFAULT ''" "currency TEXT NOT NULL DEFAULT ''" \
  "by_hand INTEGER NOT NULL DEFAULT 0"; do
  npx wrangler d1 execute folio-codes --remote --command "ALTER TABLE handled ADD COLUMN $column"
done
npx wrangler d1 execute folio-codes --remote --file=schema.sql   # adds the checks table
```

## Tests

```bash
node worker.test.mjs
node admin.test.mjs     # the admin routes and the webhook against real SQLite with schema.sql (Node 22+)
```

No account and no network: the database and the mailer are stood in for.

## Beta builds for supporters

Folio's updater reads GitHub's releases API directly, which only works on a public repository. The betas live in a
private one, so Folio asks this worker instead and sends the supporter code as its credential:

```
Folio  --GET /beta/releases, Authorization: Bearer <code>-->  worker
                                                              |- checks the code's signature (the check the app makes)
                                                              |- reads the private repo with its own GitHub token
                                                              '- rewrites each download link to /beta/asset/<id>?t=...
Folio  --GET /beta/asset/<id>?t=...-->  worker --302-->  GitHub's own short-lived file address
```

What that buys: the GitHub token never reaches a phone, the repository stays private, supporters need no GitHub
account, and access follows the code you issued rather than a list of invitations. A download link lasts 30 minutes,
works only for the one file it was made for, and only for the code it was made for — the code has to be sent with it,
so a link pasted somewhere public gets nobody in.

**What it refuses:** a code nobody signed, a code without the `beta` scope, a withdrawn serial, a code that has run
out, and a forged or expired download link. A months-code's window runs from the day it was first used here, recorded
in `beta_seen`, so it ends on the same day the phone says it does.

**Setting it up**

1. Run the schema again, for the `beta_seen` table:
   `npx wrangler d1 execute folio-codes --remote --file=schema.sql`
2. Put the beta repository and the public key in `wrangler.toml`: `BETA_REPO`, `SUPPORTER_KEYS`.
3. Add three secrets, each with `npx wrangler secret put <name>`: `GITHUB_TOKEN` (fine-grained, Contents: read, beta
   repository only), `TICKET_SECRET` (any long random string), and optionally `WITHDRAWN`.
4. Deploy, then put the worker's address in `SoftwareUpdate.BETA_BROKER` in the app. Until that constant is filled in,
   Beta Updates reads the public pre-releases exactly as it does today.

Run `node beta.test.mjs` for the checks: they mint real signed codes against a throwaway key, so the gate is exercised
rather than described.
