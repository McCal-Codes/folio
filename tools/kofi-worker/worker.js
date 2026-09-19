/**
 * Ko-fi → Folio supporter code.
 *
 * Ko-fi posts here when a payment completes. The worker checks the payment really came from Ko-fi, takes the next
 * unused code out of a pool that was minted on McCal's Mac, and emails it. The signing key never comes near the
 * internet: this worker only hands out codes someone else made.
 *
 * It answers 200 for anything it has already handled or deliberately ignores, so Ko-fi stops retrying, and 500 only
 * when something really failed and a retry might work.
 */
export default {
  async fetch(request, env) {
    if (request.method !== 'POST') return new Response('Folio supporter codes', { status: 200 })

    const payment = await readPayment(request, env)
    if (!payment.ok) return new Response(payment.why, { status: payment.status })
    const { data } = payment

    // Ko-fi retries the same message_id until it gets a 200, so every payment is handled exactly once.
    const already = await env.DB.prepare('SELECT code FROM handled WHERE message_id = ?').bind(data.message_id).first()
    if (already) return new Response('already handled', { status: 200 })

    const pool = poolFor(data, env)
    if (!pool) return new Response('nothing to send for this payment', { status: 200 })

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
  if (!sameSecret(data.verification_token ?? '', env.KOFI_TOKEN ?? '')) {
    return { ok: false, why: 'wrong token', status: 401 }
  }
  return { ok: true, data }
}

/** Compare without giving away, by how fast it answers, how much of the token was right. */
function sameSecret(a, b) {
  if (typeof a !== 'string' || a.length !== b.length) return false
  let same = 0
  for (let i = 0; i < a.length; i++) same |= a.charCodeAt(i) ^ b.charCodeAt(i)
  return same === 0
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
    const paid = Number(data.amount ?? 0)
    const bands = [...(rules.tipBands ?? [])].sort((a, b) => Number(b.from) - Number(a.from))
    for (const band of bands) if (paid >= Number(band.from)) return band.pool ?? null
    const from = Number(rules.tipFrom ?? Infinity)
    return paid >= from ? rules.tipPool ?? null : null
  }
  return null
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
  await env.DB.prepare('INSERT INTO handled (message_id, code, pool, at, emailed) VALUES (?, ?, ?, ?, 0)')
    .bind(data.message_id, taken.code, pool, new Date().toISOString()).run()
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
