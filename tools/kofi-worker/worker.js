/**
 * Ko-fi → Folio supporter code.
 *
 * Ko-fi posts here when a payment completes. The worker checks the payment really came from Ko-fi, takes the next
 * unused code out of a pool that was minted on McCal's Mac, and emails it. The signing key never comes near the
 * internet: this worker only hands out codes someone else made.
 *
 * It answers 200 for anything it has already handled or deliberately ignores, so Ko-fi stops retrying, and 500 only
 * when something really failed and a retry might work.
 *
 * It also brokers the supporters' beta builds out of a private repository (see beta.js), and answers McCal's Mac
 * under /admin/ (see admin.js).
 */
import { admin } from './admin.js'
import { betaAsset, betaReleases, sameSecret } from './beta.js'

// Ko-fi's "Send test" buttons post a made-up payment with this transaction id. It proves the webhook reaches the
// worker, so it's written down for the health check, but it never takes a real code out of a pool.
const KOFI_TEST_TRANSACTION = '00000000-1111-2222-3333-444444444444'
// Ko-fi's test payload is also sent from its sample supporter, which covers a test whose id ever changes.
const KOFI_TEST_EMAIL = 'jo.example@example.com'
const isKofiTest = (data) => data.kofi_transaction_id === KOFI_TEST_TRANSACTION ||
  String(data.email ?? '').toLowerCase() === KOFI_TEST_EMAIL

export default {
  async fetch(request, env) {
    // Supporters' beta builds: Folio asks here rather than GitHub, because that repository is private.
    const url = new URL(request.url)
    if (request.method === 'GET' && url.pathname === '/beta/releases') return betaReleases(request, env, url)
    if (request.method === 'GET' && url.pathname.startsWith('/beta/asset/')) return betaAsset(request, env, url)
    if (url.pathname.startsWith('/admin/')) return admin(request, env, url, { poolFor, claimCode, email })
    if (request.method !== 'POST') return new Response('Folio supporter codes', { status: 200 })

    const payment = await readPayment(request, env)
    if (!payment.ok) return new Response(payment.why, { status: payment.status })
    const { data } = payment

    if (isKofiTest(data)) {
      const seen = { type: data.type ?? '', tier: data.tier_name ?? '', wouldEarn: poolFor(data, env) }
      await env.DB.prepare(`INSERT INTO checks (name, value, at) VALUES ('kofi-test', ?1, ?2)
        ON CONFLICT(name) DO UPDATE SET value = ?1, at = ?2`).bind(JSON.stringify(seen), new Date().toISOString()).run()
      return new Response('Ko-fi test received; no code used', { status: 200 })
    }

    // Ko-fi retries the same message_id until it gets a 200, so every payment is handled exactly once.
    const already = await env.DB.prepare('SELECT code FROM handled WHERE message_id = ?').bind(data.message_id).first()
    if (already) return new Response('already handled', { status: 200 })

    const pool = poolFor(data, env)
    if (!pool) {
      // Earning nothing is usually the right answer and needs no fuss. A tip in a currency with no rate is the
      // exception: somebody paid and no rule could price it, so write it down rather than drop it in silence.
      const unpriced = unpricedCurrency(data, env)
      if (unpriced) {
        await env.DB.prepare('INSERT INTO problems (message_id, pool, at) VALUES (?, ?, ?)')
          .bind(data.message_id, `unpriced ${unpriced}`, new Date().toISOString()).run()
      }
      return new Response('nothing to send for this payment', { status: 200 })
    }

    const code = await claimCode(env, pool, data)
    if (!code) {
      // Out of codes is McCal's problem to fix, not Ko-fi's to retry: record it and answer 200.
      await env.DB.prepare('INSERT INTO problems (message_id, pool, at) VALUES (?, ?, ?)')
        .bind(data.message_id, pool, new Date().toISOString()).run()
      return new Response('pool empty', { status: 200 })
    }

    const sent = await email(env, data, code, pool)
    await env.DB.prepare('UPDATE handled SET emailed = ? WHERE message_id = ?').bind(sent ? 1 : 0, data.message_id).run()
    return new Response('ok', { status: 200 })
  },
}

/** Ko-fi sends form data with one `data` field holding JSON, and a token that proves it was them. */
async function readPayment(request, env) {
  let data
  try {
    const form = await request.formData()
    data = JSON.parse(form.get('data') ?? '')
  } catch {
    return { ok: false, why: 'not a Ko-fi payment', status: 400 }
  }
  if (!data?.message_id) return { ok: false, why: 'no message_id', status: 400 }
  // No token configured means no way to tell Ko-fi from anybody else — and two empty strings compare equal, which
  // would let every caller through and empty the pool. A worker deployed before `wrangler secret put KOFI_TOKEN`
  // sits in exactly that state, so it refuses everything until the secret is there.
  if (!env.KOFI_TOKEN) return { ok: false, why: 'no Ko-fi token configured', status: 503 }
  if (!sameSecret(data.verification_token ?? '', env.KOFI_TOKEN)) {
    return { ok: false, why: 'wrong token', status: 401 }
  }
  return { ok: true, data }
}

/**
 * Which pool of codes this payment earns. POOLS is JSON in the worker's settings, for example:
 *   {"tiers":{"Bronze":"beta","Gold":"all"},"shop":{"1a2b3c4d5e":"all"},"tipFrom":5,"tipPool":"beta"}
 * Anything not mentioned earns nothing, which is the safe default.
 *
 * A one-off payment can also buy time rather than one flat thank-you: `tipBands` names a pool per amount, so $5,
 * $10 and $20 can hand out one, two and four months of access. The bands are read largest first, and `tipFrom` /
 * `tipPool` still work on their own for a single threshold.
 *   {"tipBands":[{"from":5,"pool":"months1"},{"from":10,"pool":"months2"},{"from":20,"pool":"months4"}]}
 *
 * Those numbers are money, so they only mean anything alongside a currency: see [tipValue].
 */
function poolFor(data, env) {
  const rules = JSON.parse(env.POOLS ?? '{}')
  if (data.type === 'Shop Order') {
    for (const item of data.shop_items ?? []) {
      const pool = rules.shop?.[item.direct_link_code]
      if (pool) return pool
    }
    return null
  }
  if (data.type === 'Subscription') return rules.tiers?.[data.tier_name ?? ''] ?? rules.tiers?.['*'] ?? null
  if (data.type === 'Tip' || data.type === 'Donation') {
    const paid = tipValue(rules, data)
    if (paid === null) return null
    const bands = [...(rules.tipBands ?? [])].sort((a, b) => Number(b.from) - Number(a.from))
    // A band with no pool is half-written, not a rule to obey: skip it so the flat tipPool below is still reachable.
    for (const band of bands) if (band.pool && paid >= Number(band.from)) return band.pool
    const from = Number(rules.tipFrom ?? Infinity)
    return paid >= from ? rules.tipPool ?? null : null
  }
  return null
}

/**
 * What a one-off payment is worth in the currency the bands are written in, or null when there's no way to say.
 *
 * Ko-fi sends the amount in whatever the payer used, and 500 is 500 whether it's yen or dollars, so an amount read
 * without its currency hands a four-month code to a 500 JPY tip worth about three dollars. `tipCurrency` names what
 * `tipFrom` and `tipBands` are written in (US dollars unless it says otherwise) and `tipRates` gives a multiplier
 * into it for each other currency taken:
 *   {"tipCurrency":"USD","tipRates":{"EUR":1.08,"GBP":1.27}}
 * A currency with no rate earns nothing rather than being read as dollars; [unpricedCurrency] makes sure that a
 * payment lost that way is written down instead of quietly dropped. A payment that names no currency at all is
 * taken at face value: Ko-fi always sends one, and the webhook is signed.
 */
function tipValue(rules, data) {
  const paid = Number(data.amount ?? 0)
  if (!Number.isFinite(paid)) return null
  const paidIn = String(data.currency ?? '').toUpperCase()
  const bands = String(rules.tipCurrency ?? 'USD').toUpperCase()
  if (!paidIn || paidIn === bands) return paid
  const rate = Number(rules.tipRates?.[paidIn])
  return Number.isFinite(rate) && rate > 0 ? paid * rate : null
}

/** The currency of a one-off payment that earned nothing only because it couldn't be priced, for the record. */
function unpricedCurrency(data, env) {
  if (data.type !== 'Tip' && data.type !== 'Donation') return null
  const rules = JSON.parse(env.POOLS ?? '{}')
  return tipValue(rules, data) === null ? String(data.currency ?? '').toUpperCase() || 'unknown' : null
}

/**
 * Take one unused code and write down that this payment has it, in a single statement so two payments landing at
 * once can't be given the same code.
 */
async function claimCode(env, pool, data) {
  const taken = await env.DB.prepare(
    `UPDATE codes SET used_at = ?1 WHERE code = (SELECT code FROM codes WHERE pool = ?2 AND used_at IS NULL LIMIT 1)
     RETURNING code`).bind(new Date().toISOString(), pool).first()
  if (!taken?.code) return null
  // Who and which Ko-fi transaction, so the Mac's ledger can match this code to the CSV. The email address is used
  // to send the code and never stored.
  await env.DB.prepare(
    `INSERT INTO handled (message_id, code, pool, at, emailed, transaction_id, from_name, type, amount, currency, by_hand)
     VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)`)
    .bind(data.message_id, taken.code, pool, new Date().toISOString(), data.kofi_transaction_id ?? '',
      data.from_name ?? '', data.type ?? '', String(data.amount ?? ''), data.currency ?? '', data.by_hand ? 1 : 0).run()
  return taken.code
}

/**
 * Sends the code with Resend when a key is set. Without one the code is still claimed and recorded, so it can be
 * looked up and sent by hand — a missing email provider must never swallow someone's code.
 */
async function email(env, data, code, pool) {
  if (!env.RESEND_KEY || !data.email) return false
  const body = {
    from: env.MAIL_FROM,
    to: data.email,
    subject: 'Your Folio supporter code',
    text: [
      `Thank you${data.from_name ? `, ${data.from_name}` : ''}.`,
      '',
      'Here is your Folio supporter code:',
      '',
      `    ${code}`,
      '',
      'In Folio: Settings › Supporter › Redeem a Code, and paste it in. It is checked on your phone, so it works',
      'offline and tells nobody that you supported.',
      '',
      pool === 'beta' || pool === 'all'
        ? 'It turns on Beta Features, which you can switch off any time: early features come with more bugs.'
        : '',
      '',
      'Folio stays free and open source. Thank you for keeping it going.',
      '— McCal',
    ].filter((line) => line !== undefined).join('\n'),
  }
  const sent = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: { authorization: `Bearer ${env.RESEND_KEY}`, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
  return sent.ok
}
