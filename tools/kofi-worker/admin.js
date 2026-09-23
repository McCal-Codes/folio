/**
 * The worker's admin side, for McCal's Mac (scripts/code-admin.py) and, later, Folio Dev.
 *
 * Every route needs `Authorization: Bearer <ADMIN_TOKEN>`. With no ADMIN_TOKEN set the whole of /admin/ answers 503,
 * for the same reason the webhook refuses to run without KOFI_TOKEN: two empty strings compare equal.
 *
 *   GET  /admin/health        what's configured, stock per pool, problems, the last Ko-fi test, sample codes
 *   GET  /admin/recent        the codes handed out, newest first, with who and which Ko-fi transaction
 *   POST /admin/pool          {pool, codes: [...]}: add codes minted on the Mac (never minted here)
 *   POST /admin/test          run a pretend payment through the real pipeline and put everything back
 *   POST /admin/claim         {pool, name}: hand one code out by hand (Folio Dev's "Hand out a code")
 *
 * Nothing here can make a code. The signing key stays on the Mac; the worker only ever holds codes made there.
 */
import { sameSecret } from './beta.js'

// Crockford base32 in groups of five, 73 bytes: what beta-code.py prints. Anything else is refused at the door.
const CODE = /^[0-9A-HJKMNP-TV-Z]{5}(-[0-9A-HJKMNP-TV-Z]{1,5}){23}$/
const POOL = /^[a-z0-9][a-z0-9+_-]{0,31}$/

export async function admin(request, env, url, pipeline) {
  if (!env.ADMIN_TOKEN) return json({ error: 'no admin token configured' }, 503)
  const presented = (request.headers.get('authorization') ?? '').replace(/^Bearer\s+/i, '')
  if (!sameSecret(presented, env.ADMIN_TOKEN)) return json({ error: 'wrong admin token' }, 401)

  const route = `${request.method} ${url.pathname}`
  try {
    if (route === 'GET /admin/health') return json(await health(env))
    if (route === 'GET /admin/recent') return json(await recent(env, Number(url.searchParams.get('limit') ?? 200)))
    if (route === 'POST /admin/pool') return json(await addToPool(env, await request.json()))
    if (route === 'POST /admin/test') return json(await testPayment(env, await body(request), pipeline))
    if (route === 'POST /admin/claim') return json(await claimByHand(env, await request.json(), pipeline))
  } catch (problem) {
    return json({ error: String(problem?.message ?? problem) }, 400)
  }
  return json({ error: 'no such admin route' }, 404)
}

async function body(request) {
  const text = await request.text()
  return text ? JSON.parse(text) : {}
}

/** Everything the Mac's health check needs in one answer. */
async function health(env) {
  let rules = null, rulesError = null
  try { rules = JSON.parse(env.POOLS ?? '{}') } catch (e) { rulesError = String(e.message) }
  const stock = await env.DB.prepare(
    `SELECT pool, SUM(CASE WHEN used_at IS NULL THEN 1 ELSE 0 END) AS free, COUNT(*) AS total
     FROM codes GROUP BY pool ORDER BY pool`).all()
  const problems = await env.DB.prepare('SELECT message_id, pool, at FROM problems ORDER BY at DESC LIMIT 50').all()
  const lastTest = await env.DB.prepare(`SELECT value, at FROM checks WHERE name = 'kofi-test'`).first()
  // A few unused codes per pool, so the Mac can check they carry its signature. They're sent only to the admin.
  const samples = await env.DB.prepare(
    `SELECT pool, code FROM (SELECT pool, code, ROW_NUMBER() OVER (PARTITION BY pool ORDER BY code) AS n
       FROM codes WHERE used_at IS NULL) WHERE n <= 5`).all()
  // A database made before the Mac's columns existed takes a code and then fails to write down who got it.
  let columns = true
  try {
    await env.DB.prepare('SELECT transaction_id, from_name, type, amount, currency, by_hand FROM handled LIMIT 1').all()
  } catch {
    columns = false
  }
  return {
    ok: true,
    columns,
    configured: {
      kofiToken: Boolean(env.KOFI_TOKEN),
      resendKey: Boolean(env.RESEND_KEY),
      mailFrom: Boolean(env.MAIL_FROM) && !/example\.com/.test(env.MAIL_FROM),
      pools: rules,
      poolsError: rulesError,
    },
    // Pools the rules send payments to, whether or not any codes were ever loaded into them.
    wanted: poolsNamed(rules),
    stock: stock.results ?? [],
    problems: problems.results ?? [],
    lastKofiTest: lastTest ? { ...JSON.parse(lastTest.value), at: lastTest.at } : null,
    samples: samples.results ?? [],
  }
}

function poolsNamed(rules) {
  if (!rules) return []
  const names = new Set()
  for (const pool of Object.values(rules.tiers ?? {})) names.add(pool)
  for (const pool of Object.values(rules.shop ?? {})) names.add(pool)
  if (rules.tipPool) names.add(rules.tipPool)
  for (const band of rules.tipBands ?? []) if (band.pool) names.add(band.pool)
  return [...names].filter(Boolean).sort()
}

async function recent(env, limit) {
  const rows = await env.DB.prepare(
    `SELECT message_id, transaction_id, from_name, type, amount, currency, code, pool, at, emailed, by_hand
     FROM handled ORDER BY at DESC LIMIT ?`).bind(Math.min(Math.max(limit, 1), 1000)).all()
  return { handled: rows.results ?? [] }
}

async function addToPool(env, { pool, codes }) {
  if (!POOL.test(String(pool ?? ''))) throw new Error('pool names are lowercase letters, digits, + _ -')
  if (!Array.isArray(codes) || codes.length === 0 || codes.length > 1000) throw new Error('send 1 to 1000 codes')
  const bad = codes.filter((code) => !CODE.test(String(code)))
  if (bad.length) throw new Error(`${bad.length} of those aren't Folio codes`)
  let added = 0
  for (const code of codes) {
    const done = await env.DB.prepare('INSERT OR IGNORE INTO codes (code, pool) VALUES (?, ?)').bind(code, pool).run()
    added += done.meta?.changes ?? 0
  }
  return { added, skipped: codes.length - added }
}

/**
 * A pretend Ko-fi payment through the same rules and the same claim a real one uses, then everything put back: the
 * code goes back in its pool and the payment is forgotten. It proves the rules parse, the payment earns a pool, the
 * pool has a code, and the database takes the claim. With `email` it also sends that code to the address given, to
 * prove Resend works; the code still goes back.
 */
async function testPayment(env, { type = 'Tip', amount = '5.00', currency = 'USD', tier_name, email }, pipeline) {
  const data = {
    message_id: `admin-test-${crypto.randomUUID()}`, type, amount, currency, tier_name,
    from_name: 'Folio test', email: email ?? '', kofi_transaction_id: 'admin-test',
  }
  const steps = []
  const pool = pipeline.poolFor(data, env)
  steps.push({ step: 'rules', ok: Boolean(pool), detail: pool ? `earns the ${pool} pool` : 'this payment earns nothing' })
  if (!pool) return { ok: false, steps }
  const code = await pipeline.claimCode(env, pool, data)
  steps.push({ step: 'claim', ok: Boolean(code), detail: code ? `took …${code.slice(-7)}` : `the ${pool} pool is empty` })
  if (!code) return { ok: false, steps }
  let sent = null
  try {
    if (email) {
      sent = await pipeline.email(env, data, code, pool)
      steps.push({ step: 'email', ok: sent, detail: sent ? `sent to ${email}` : 'Resend refused it, or no key is set' })
    }
  } finally {
    // Put it all back, whatever happened above: a test never costs a supporter a code.
    await env.DB.prepare('UPDATE codes SET used_at = NULL WHERE code = ?').bind(code).run()
    await env.DB.prepare('DELETE FROM handled WHERE message_id = ?').bind(data.message_id).run()
  }
  const back = await env.DB.prepare('SELECT used_at FROM codes WHERE code = ?').bind(code).first()
  steps.push({ step: 'put back', ok: back && back.used_at === null, detail: 'the code is unused again' })
  return { ok: steps.every((s) => s.ok), steps }
}

/** One code handed out by hand, recorded like a payment so the Mac's ledger picks it up on its next sync. */
async function claimByHand(env, { pool, name }, pipeline) {
  if (!POOL.test(String(pool ?? ''))) throw new Error('which pool?')
  const data = {
    message_id: `by-hand-${crypto.randomUUID()}`, type: 'By hand', from_name: String(name ?? '').slice(0, 80),
    kofi_transaction_id: '', by_hand: 1,
  }
  const code = await pipeline.claimCode(env, pool, data)
  if (!code) return { ok: false, error: `the ${pool} pool is empty; refill it from the Mac` }
  return { ok: true, code, pool, message_id: data.message_id }
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
  })
}
