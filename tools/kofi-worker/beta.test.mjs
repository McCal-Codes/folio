/**
 * The beta broker: who gets to read the private repository's releases, and for how long a download link lasts.
 * Run with `node beta.test.mjs` — no account and no network; GitHub is stood in for here.
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import { betaAsset, betaReleases, checkBetaCode, decodeCode, endsOn, makeTicket, readCode, ticketIsGood } from './beta.js'

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const DAY = 86_400_000
const NOW = Date.UTC(2026, 8, 20)

/** The key McCal would keep on his Mac, made fresh for each run. */
async function folioKey() {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
  let binary = ''
  for (const b of spki) binary += String.fromCharCode(b)
  return { pair, publicKey: btoa(binary) }
}

/** Mints a code the way scripts/beta-code.py does: nine bytes signed, then base32. */
async function mint(key, { scopes = 0b0001, tier = 1, months = 0, expiryDay = 0, serial = 7 } = {}) {
  const version = months > 0 ? 2 : 1
  const payload = new Uint8Array([version, scopes, months > 0 ? (months << 4) | tier : tier,
    (expiryDay >> 8) & 0xff, expiryDay & 0xff,
    (serial >>> 24) & 0xff, (serial >> 16) & 0xff, (serial >> 8) & 0xff, serial & 0xff])
  const signature = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key.pair.privateKey, payload))
  const bytes = [...payload, ...signature]
  let text = ''
  let buffer = 0
  let bits = 0
  for (const b of bytes) {
    buffer = (buffer << 8) | b
    bits += 8
    while (bits >= 5) { bits -= 5; text += ALPHABET[(buffer >> bits) & 31] }
  }
  if (bits > 0) text += ALPHABET[(buffer << (5 - bits)) & 31]
  return text
}

/** Just enough of D1 for the one table the broker writes. */
function database(rows = new Map()) {
  return {
    rows,
    prepare(sql) {
      let args = []
      const self = {
        bind(...values) { args = values; return self },
        async run() {
          if (sql.startsWith('INSERT OR IGNORE INTO beta_seen') && !rows.has(args[0])) rows.set(args[0], args[1])
          return { success: true }
        },
        async first() {
          if (sql.startsWith('SELECT first_seen')) return rows.has(args[0]) ? { first_seen: rows.get(args[0]) } : null
          return null
        },
      }
      return self
    },
  }
}

function environment(key, extra = {}) {
  return {
    SUPPORTER_KEYS: key.publicKey,
    TICKET_SECRET: 'a-secret-only-the-worker-knows',
    BETA_REPO: 'McCal-Codes/folio-beta',
    GITHUB_TOKEN: 'token',
    DB: database(),
    ...extra,
  }
}

/** What Folio sends: the code in an Authorization header, nothing else. */
const asking = (code, path = '/beta/releases') => ({
  request: new Request(`https://codes.example${path}`, { headers: code ? { Authorization: `Bearer ${code}` } : {} }),
  url: new URL(`https://codes.example${path}`),
})

test('a code that carries beta access reads the private releases', async () => {
  const key = await folioKey()
  const env = environment(key)
  const github = globalThis.fetch
  globalThis.fetch = async () => Response.json([
    { tag_name: 'v0.7.0-beta.1', body: 'notes', draft: false, prerelease: true, html_url: 'https://example/r',
      assets: [{ id: 42, name: 'Folio-0.7.0-beta.1.apk', size: 1234 }] },
    { tag_name: 'v0.7.0-beta.0', draft: true, assets: [] },
  ])
  try {
    const ask = asking(await mint(key))
    const answer = await betaReleases(ask.request, env, ask.url, NOW)
    assert.equal(answer.status, 200)
    const releases = await answer.json()
    assert.equal(releases.length, 1, 'a draft is not a release anyone should see')
    const [apk] = releases[0].assets
    assert.match(apk.browser_download_url, /^https:\/\/codes\.example\/beta\/asset\/42\?t=/)
    assert.equal(releases[0].prerelease, true)
  } finally { globalThis.fetch = github }
})

test('a code without beta access is refused, and so is one nobody signed', async () => {
  const key = await folioKey()
  const env = environment(key)
  const keyboardOnly = await mint(key, { scopes: 0b1000 })
  assert.equal((await checkBetaCode(env, keyboardOnly, NOW)).status, 403)

  const other = await folioKey()
  const fromSomewhereElse = await mint(other)
  assert.equal((await checkBetaCode(env, fromSomewhereElse, NOW)).status, 403)

  assert.equal((await checkBetaCode(env, 'not-a-code', NOW)).status, 400)
  const empty = asking('')
  assert.equal((await betaReleases(empty.request, env, empty.url, NOW)).status, 400)
})

test('a withdrawn code stops working', async () => {
  const key = await folioKey()
  const env = environment(key, { WITHDRAWN: '7, 9' })
  assert.equal((await checkBetaCode(env, await mint(key, { serial: 7 }), NOW)).status, 403)
  assert.equal((await checkBetaCode(env, await mint(key, { serial: 8 }), NOW)).ok, true)
})

test('a code that has run out is refused, and one that still has time is not', async () => {
  const key = await folioKey()
  const env = environment(key)
  const yesterday = Math.round((NOW - DAY * 2 - Date.UTC(2026, 0, 1)) / DAY)
  const nextYear = Math.round((NOW + DAY * 300 - Date.UTC(2026, 0, 1)) / DAY)
  assert.equal((await checkBetaCode(env, await mint(key, { expiryDay: yesterday }), NOW)).ok, false)
  assert.equal((await checkBetaCode(env, await mint(key, { expiryDay: nextYear }), NOW)).ok, true)
})

test('a months code runs from the day it is first seen here', async () => {
  const key = await folioKey()
  const env = environment(key)
  const code = await mint(key, { months: 1, serial: 11 })
  assert.equal((await checkBetaCode(env, code, NOW)).ok, true)
  assert.equal(env.DB.rows.get(11), '2026-09-20')
  // Three weeks later it still works; five weeks later it doesn't, counted from that first day and not from today.
  assert.equal((await checkBetaCode(env, code, NOW + DAY * 21)).ok, true)
  assert.equal((await checkBetaCode(env, code, NOW + DAY * 35)).ok, false)
})

test('a download link expires, and a forged one never worked', async () => {
  const key = await folioKey()
  const env = environment(key)
  const ticket = await makeTicket(env, 42, NOW + 60_000)
  assert.equal(await ticketIsGood(env, 42, ticket, NOW), true)
  assert.equal(await ticketIsGood(env, 42, ticket, NOW + 120_000), false, 'a link has to stop working')
  assert.equal(await ticketIsGood(env, 43, ticket, NOW), false, 'a link is for one file')
  assert.equal(await ticketIsGood(env, 42, `${NOW + 60_000}.made-up`, NOW), false)

  const answer = await betaAsset(new Request('https://codes.example/beta/asset/42?t=nonsense'), env,
    new URL('https://codes.example/beta/asset/42?t=nonsense'), NOW)
  assert.equal(answer.status, 403)
})

test('a good link hands back GitHub own address, never the token', async () => {
  const key = await folioKey()
  const env = environment(key)
  const ticket = await makeTicket(env, 42, NOW + 60_000)
  const github = globalThis.fetch
  let sawToken = false
  globalThis.fetch = async (url, options) => {
    sawToken = String(options?.headers?.Authorization ?? '').includes('token')
    return new Response(null, { status: 302, headers: { location: 'https://objects.github.com/folio.apk?signed' } })
  }
  try {
    const url = new URL(`https://codes.example/beta/asset/42?t=${ticket}`)
    const answer = await betaAsset(new Request(url), env, url, NOW)
    assert.equal(answer.status, 302)
    assert.equal(answer.headers.get('location'), 'https://objects.github.com/folio.apk?signed')
    assert.equal(sawToken, true, 'the worker is the one holding the token')
  } finally { globalThis.fetch = github }
})

test('the pieces of a code read back the way the app reads them', async () => {
  const key = await folioKey()
  const code = readCode(decodeCode(await mint(key, { scopes: 0b0101, tier: 3, months: 6, serial: 123456 })))
  assert.equal(code.version, 2)
  assert.equal(code.months, 6)
  assert.equal(code.tier, 3)
  assert.equal(code.serial, 123456)
  assert.deepEqual([code.scopeBits & 1, (code.scopeBits >> 2) & 1], [1, 1])
  assert.equal(endsOn({ expiryDay: 0, months: 0 }, null), null, 'a code with no end never runs out')
})
