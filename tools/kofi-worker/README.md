# Ko-fi supporter codes

A small Cloudflare Worker that turns a Ko-fi payment into a Folio supporter code.

It hands out codes from a pool that was minted on your Mac, so **the signing key never goes online**. If this worker
is ever broken into, the worst anyone gets is the codes already sitting in the pool — not the ability to make more.

```
Ko-fi payment ──webhook──▶ worker ──▶ takes one unused code from D1 ──▶ emails it ──▶ Settings › Supporter
```

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

Put the worker's URL in Ko-fi › Settings › Webhooks, then use Ko-fi's own "Send membership tier test" and "Send shop
order test" buttons. Check what happened:

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

## Tests

```bash
node worker.test.mjs
```

No account and no network: the database and the mailer are stood in for.
