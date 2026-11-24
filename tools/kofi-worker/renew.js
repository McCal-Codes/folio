/**
 * Renewal for Ko-fi members: a phone whose code is about to end asks here for the code its newest payment earned.
 *
 * The worker already emails a fresh code with every monthly payment. What was missing was a way for the phone to pick
 * it up without the member opening the email, so this is that and nothing more. It never makes a code and never
 * extends one; it only hands back a code that a real Ko-fi payment already claimed for the same payer.
 *
 * "The same payer" is a keyed hash of the email address Ko-fi sends with each subscription payment (see [payerKey]).
 * The address itself is still used to send the email and dropped, as it always was; only the hash is kept, and without
 * the PAYER_SECRET it can't be turned back into an address or matched against a list of them.
 *
 *   GET /renew     Authorization: Bearer <the phone's current code>
 *     200 {"code": "..."}   a newer code for the same member
 *     204                   nothing newer: the code is already the latest, or no payment has come since
 *     404                   this code was not earned by a membership payment, so there is nothing to renew
 *     400/403               not a Folio code / not signed by Folio / withdrawn
 *
 * An ended code is welcome here: the member whose phone was off for a month is exactly who this is for.
 */
import { codeFrom, decodeCode, readCode, signedByFolio } from './beta.js'

/** A code as the database holds it, however the phone wrote it: no dashes or spaces, Crockford's look-alikes folded. */
export function canonical(text) {
  return [...String(text ?? '').toUpperCase()]
    .filter((c) => c !== '-' && !/\s/.test(c))
    .map((c) => (c === 'I' || c === 'L' ? '1' : c === 'O' ? '0' : c))
    .join('')
}

/**
 * A stable, private name for whoever paid: HMAC-SHA256 of their lower-cased email with the worker's own secret,
 * shortened. Null when there is no secret or no address, which simply means that payment can't be renewed from the
 * phone; the emailed code works as it always did.
 */
export async function payerKey(env, email) {
  const address = String(email ?? '').trim().toLowerCase()
  if (!env.PAYER_SECRET || !address) return null
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(String(env.PAYER_SECRET)),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(address)))
  return [...mac.slice(0, 16)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

export async function renew(request, env) {
  const keys = String(env.SUPPORTER_KEYS ?? '').split(/[,\s]+/).filter(Boolean)
  if (!keys.length) return text('no supporter key configured', 503)
  if (!env.DB) return text('renewal cannot be checked right now', 503)

  const asked = codeFrom(request)
  const code = readCode(decodeCode(asked))
  if (!code) return text('not a Folio code', 400)
  if (!(await signedByFolio(code, keys))) return text('not signed by Folio', 403)
  const withdrawn = String(env.WITHDRAWN ?? '').split(/[,\s]+/).filter(Boolean).map(Number)
  if (withdrawn.includes(code.serial)) return text('this code has been withdrawn', 403)

  const mine = canonical(asked)
  const row = await env.DB.prepare(`SELECT payer FROM handled WHERE REPLACE(code, '-', '') = ?`).bind(mine).first()
  if (!row?.payer) return text('nothing to renew', 404)

  const newest = await env.DB.prepare(
    `SELECT code FROM handled WHERE payer = ? AND type = 'Subscription' ORDER BY at DESC, rowid DESC LIMIT 1`)
    .bind(row.payer).first()
  if (!newest?.code || canonical(newest.code) === mine) return new Response(null, { status: 204, headers: noStore })
  return Response.json({ code: newest.code }, { headers: noStore })
}

const noStore = { 'Cache-Control': 'no-store' }
const text = (why, status) => new Response(why, { status, headers: noStore })
