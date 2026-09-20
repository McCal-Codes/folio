/**
 * Beta updates for supporters, without making the beta repository public.
 *
 * Folio's updater reads GitHub's releases API directly, which only works on a public repository. The beta builds live
 * in a private one, so Folio asks this worker instead and sends the supporter code as its credential. The worker
 * checks the code's signature against McCal's public key — the same check the app makes offline — and only then uses
 * its GitHub token to read the private releases. The token never reaches the phone, and a download link is a
 * short-lived ticket tied to the code that asked for it, so passing it on gets nobody in.
 *
 * Nothing here can mint a code or grant access on its own: an unsigned code is refused exactly as the app refuses it.
 */

/** Crockford's base32, as the app writes codes: no I, L, O or U. */
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const PAYLOAD = 9
const SIGNATURE = 64
/** Day 0 of a code's expiry field. */
const EPOCH = Date.UTC(2026, 0, 1)
const DAY = 86_400_000
const SCOPE_BETA = 0  // bit 0 of the scope byte
/** How long a download link stays good: long enough to start an install, short enough to be worthless if shared. */
const TICKET_MINUTES = 30

/** The bytes inside a typed code, or null when the text isn't one. Mirrors BetaCodes.decode in the app. */
export function decodeCode(text) {
  const clean = [...String(text ?? '').toUpperCase()]
    .filter(c => c !== '-' && !/\s/.test(c))
    .map(c => (c === 'I' || c === 'L' ? '1' : c === 'O' ? '0' : c))
  if (!clean.length) return null
  const out = []
  let buffer = 0
  let bits = 0
  for (const character of clean) {
    const value = ALPHABET.indexOf(character)
    if (value < 0) return null
    buffer = (buffer << 5) | value
    bits += 5
    if (bits >= 8) {
      bits -= 8
      out.push((buffer >> bits) & 0xff)
    }
  }
  return new Uint8Array(out)
}

/** A code's contents, without judging it: version, scopes, tier, months, expiry day and serial. */
export function readCode(bytes) {
  if (!bytes || bytes.length !== PAYLOAD + SIGNATURE) return null
  const version = bytes[0]
  if (version !== 1 && version !== 2) return null
  const tierByte = bytes[2]
  return {
    version,
    scopeBits: bytes[1],
    months: version === 2 ? tierByte >> 4 : 0,
    tier: version === 2 ? tierByte & 0x0f : tierByte,
    expiryDay: (bytes[3] << 8) | bytes[4],
    serial: ((bytes[5] << 24) >>> 0) + (bytes[6] << 16) + (bytes[7] << 8) + bytes[8],
    payload: bytes.slice(0, PAYLOAD),
    signature: bytes.slice(PAYLOAD),
  }
}

function base64ToBytes(text) {
  const binary = atob(String(text).replace(/\s+/g, ''))
  return Uint8Array.from(binary, c => c.charCodeAt(0))
}

/** True when this code was signed by one of the given public keys (SPKI, base64 — the app's BetaKeys). */
export async function signedByFolio(code, keys) {
  for (const key of keys) {
    const ok = await crypto.subtle
      .importKey('spki', base64ToBytes(key), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
      .then(publicKey => crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, publicKey, code.signature, code.payload))
      .catch(() => false)
    if (ok) return true
  }
  return false
}

/** The last day a code works: its own last day, the end of its months, or whichever of the two comes first. */
export function endsOn(code, firstSeen) {
  const fixed = code.expiryDay > 0 ? EPOCH + code.expiryDay * DAY : null
  const window = code.months > 0 && firstSeen ? monthsAfter(firstSeen, code.months) : null
  const days = [fixed, window].filter(d => d !== null)
  return days.length ? Math.min(...days) : null
}

function monthsAfter(from, months) {
  const date = new Date(from)
  const day = date.getUTCDate()
  const moved = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + months, 1))
  // The same day next month, or the last day of a shorter one — February keeps a code honest.
  const lastOfMonth = new Date(Date.UTC(moved.getUTCFullYear(), moved.getUTCMonth() + 1, 0)).getUTCDate()
  moved.setUTCDate(Math.min(day, lastOfMonth))
  return moved.getTime()
}

/**
 * Checks a supporter code the way the app does, and remembers the day each months-code was first seen so its window
 * runs from the same day here as on the phone.
 */
export async function checkBetaCode(env, text, now = Date.now()) {
  const keys = String(env.SUPPORTER_KEYS ?? '').split(/[,\s]+/).filter(Boolean)
  if (!keys.length) return { ok: false, why: 'no supporter key configured', status: 503 }
  const code = readCode(decodeCode(text))
  if (!code) return { ok: false, why: 'not a Folio code', status: 400 }
  if (!(await signedByFolio(code, keys))) return { ok: false, why: 'not signed by Folio', status: 403 }
  if (((code.scopeBits >> SCOPE_BETA) & 1) !== 1) return { ok: false, why: 'this code has no beta access', status: 403 }
  const withdrawn = String(env.WITHDRAWN ?? '').split(/[,\s]+/).filter(Boolean).map(Number)
  if (withdrawn.includes(code.serial)) return { ok: false, why: 'this code has been withdrawn', status: 403 }

  let firstSeen = null
  // Without the database there is nowhere to count a month from, and counting from nothing would mean never
  // running out. A code that buys months is refused until the binding is there, rather than quietly lasting forever.
  if (code.months > 0 && !env.DB) return { ok: false, why: 'beta access cannot be checked right now', status: 503 }
  if (code.months > 0) {
    const today = new Date(now).toISOString().slice(0, 10)
    await env.DB.prepare('INSERT OR IGNORE INTO beta_seen (serial, first_seen) VALUES (?, ?)').bind(code.serial, today).run()
    const row = await env.DB.prepare('SELECT first_seen FROM beta_seen WHERE serial = ?').bind(code.serial).first()
    firstSeen = row?.first_seen ? Date.parse(`${row.first_seen}T00:00:00Z`) : null
  }
  const ends = endsOn(code, firstSeen)
  // A code works through its last day, not until the morning of it.
  if (ends !== null && now > ends + DAY) return { ok: false, why: 'this code has run out', status: 403 }
  return { ok: true, code, ends }
}

function bytesToBase64Url(bytes) {
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}

/** A download link nobody can forge, that stops working on its own, and that only its own code can use. */
export async function makeTicket(env, assetId, expiresAt, serial) {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(String(env.TICKET_SECRET ?? '')),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  const mac = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${assetId}.${expiresAt}.${serial}`))
  return `${expiresAt}.${bytesToBase64Url(new Uint8Array(mac))}`
}

export async function ticketIsGood(env, assetId, ticket, serial, now = Date.now()) {
  const [expiresAt, signature] = String(ticket ?? '').split('.')
  if (!expiresAt || !signature) return false
  // A timestamp that isn't a number is not an expiry that has passed — Number('abc') < now is false — so it is
  // refused here rather than carried into the signature as NaN.
  const at = Number(expiresAt)
  if (!Number.isFinite(at) || at < now) return false
  const expected = await makeTicket(env, assetId, at, serial)
  return sameSecret(expected, `${expiresAt}.${signature}`)
}

/** Compare without giving away, by how fast it answers, how much of the ticket was right. */
export function sameSecret(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false
  let same = 0
  for (let i = 0; i < a.length; i++) same |= a.charCodeAt(i) ^ b.charCodeAt(i)
  return same === 0
}

function github(env, path, headers = {}) {
  return fetch(`https://api.github.com/repos/${env.BETA_REPO}${path}`, {
    headers: {
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      'User-Agent': 'folio-supporter-codes',
      Accept: 'application/vnd.github+json',
      ...headers,
    },
    redirect: 'manual',
  })
}

/** The supporter code from the request, as Folio sends it. */
export function codeFrom(request) {
  const header = request.headers.get('Authorization') ?? ''
  return header.startsWith('Bearer ') ? header.slice(7).trim() : ''
}

/**
 * The private repository's releases, in exactly the shape GitHub returns them, so the app parses one the same way it
 * parses the other. Only the download links are rewritten, to point back here with a ticket.
 */
export async function betaReleases(request, env, url, now = Date.now()) {
  const allowed = await checkBetaCode(env, codeFrom(request), now)
  if (!allowed.ok) return new Response(allowed.why, { status: allowed.status })
  if (!env.BETA_REPO || !env.GITHUB_TOKEN) return new Response('no beta repository configured', { status: 503 })

  const answer = await github(env, '/releases?per_page=15')
  if (!answer.ok) return new Response('GitHub would not answer', { status: 502 })
  const releases = await answer.json()
  const expiresAt = now + TICKET_MINUTES * 60_000
  const rewritten = []
  for (const release of releases) {
    if (release.draft) continue
    const assets = []
    for (const asset of release.assets ?? []) {
      assets.push({
        name: asset.name,
        size: asset.size,
        browser_download_url: `${url.origin}/beta/asset/${asset.id}?t=${await makeTicket(env, asset.id, expiresAt, allowed.code.serial)}`,
      })
    }
    rewritten.push({
      tag_name: release.tag_name,
      body: release.body ?? '',
      html_url: release.html_url ?? '',
      draft: false,
      prerelease: true,  // everything here is a beta, however it is marked in the repository
      assets,
    })
  }
  return Response.json(rewritten, { headers: { 'Cache-Control': 'no-store' } })
}

/** Hands back GitHub's own short-lived download address; the token stays here. */
export async function betaAsset(request, env, url, now = Date.now()) {
  const assetId = url.pathname.split('/').pop()
  if (!/^\d+$/.test(assetId ?? '')) return new Response('no such asset', { status: 404 })
  // The code comes with the link, not only to make it: a ticket passed to somebody else has nothing to present.
  const allowed = await checkBetaCode(env, codeFrom(request), now)
  if (!allowed.ok) return new Response(allowed.why, { status: allowed.status })
  if (!(await ticketIsGood(env, assetId, url.searchParams.get('t'), allowed.code.serial, now))) {
    return new Response('that download link has expired', { status: 403 })
  }
  const answer = await github(env, `/releases/assets/${assetId}`, { Accept: 'application/octet-stream' })
  const location = answer.headers.get('location')
  if (!location) return new Response('GitHub would not hand over the file', { status: 502 })
  return Response.redirect(location, 302)
}
