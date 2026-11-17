/**
 * Renewal for Ko-fi members: when the phone is handed a newer code, and when it is told there is nothing to take.
 * Run with `node renew.test.mjs`: no account and no network, the database is stood in for here.
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import { canonical, payerKey, renew } from './renew.js'
import worker from './worker.js'

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

async function folioKey() {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
  let binary = ''
  for (const b of spki) binary += String.fromCharCode(b)
  return { pair, publicKey: btoa(binary) }
}

/** A code the way scripts/beta-code.py writes one: nine bytes signed, base32, dashes every five characters. */
async function mint(key, serial, { months = 2 } = {}) {
  const payload = new Uint8Array([2, 0b0001, (months << 4) | 1, 0, 0,
    (serial >>> 24) & 0xff, (serial >> 16) & 0xff, (serial >> 8) & 0xff, serial & 0xff])
  const signature = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key.pair.privateKey, payload))
  let text = ''
  let buffer = 0
  let bits = 0
  for (const b of [...payload, ...signature]) {
    buffer = (buffer << 8) | b
    bits += 8
    while (bits >= 5) { bits -= 5; text += ALPHABET[(buffer >> bits) & 31] }
  }
  if (bits > 0) text += ALPHABET[(buffer << (5 - bits)) & 31]
  return text.match(/.{1,5}/g).join('-')
}

/** The two reads renewal makes of `handled`. */
function database(rows) {
  return {
    prepare(sql) {
      let args = []
      return {
        bind(...values) { args = values; return this },
        async first() {
          if (sql.includes('SELECT payer')) return rows.find((r) => r.code.replaceAll('-', '') === args[0]) ?? null
          if (sql.includes("type = 'Subscription'")) {
            const mine = rows.filter((r) => r.payer === args[0] && r.type === 'Subscription')
            return mine.sort((a, b) => (a.at < b.at ? 1 : -1))[0] ?? null
          }
          return null
        },
      }
    },
  }
}

const asking = (code) => new Request('https://codes.example/renew', { headers: code ? { Authorization: `Bearer ${code}` } : {} })

async function setup() {
  const key = await folioKey()
  const [a, b, c, tip] = await Promise.all([mint(key, 1), mint(key, 2), mint(key, 3), mint(key, 4, { months: 1 })])
  const rows = [
    { code: a, payer: 'p1', type: 'Subscription', at: '2026-09-01T10:00:00Z' },
    { code: b, payer: 'p1', type: 'Subscription', at: '2026-10-01T10:00:00Z' },
    { code: c, payer: 'p2', type: 'Subscription', at: '2026-10-05T10:00:00Z' },
    { code: tip, payer: '', type: 'Tip', at: '2026-10-06T10:00:00Z' },
  ]
  return { key, a, b, c, tip, env: { SUPPORTER_KEYS: key.publicKey, DB: database(rows) } }
}

test('a member whose newest payment earned a newer code is handed it', async () => {
  const { a, b, env } = await setup()
  const answer = await renew(asking(a), env)
  assert.equal(answer.status, 200)
  assert.deepEqual(await answer.json(), { code: b })
})

test('the newest code already held gets nothing more', async () => {
  const { b, env } = await setup()
  assert.equal((await renew(asking(b), env)).status, 204)
})

test('the code is found however the phone wrote it', async () => {
  const { a, b, env } = await setup()
  const messy = ` ${a.toLowerCase().replaceAll('-', ' ')} `
  const answer = await renew(asking(messy), env)
  assert.equal(answer.status, 200)
  assert.equal((await answer.json()).code, b)
})

test('a code that was not earned by a membership payment has nothing to renew', async () => {
  const { tip, env } = await setup()
  assert.equal((await renew(asking(tip), env)).status, 404)
})

test('a member never receives somebody else’s code', async () => {
  const { c, a, b, env } = await setup()
  const answer = await renew(asking(c), env)
  assert.equal(answer.status, 204)
  assert.notEqual(await answer.text(), a)
  assert.notEqual(await answer.text(), b)
})

test('only a genuine, unwithdrawn Folio code gets an answer', async () => {
  const { a, env } = await setup()
  assert.equal((await renew(asking(''), env)).status, 400)
  assert.equal((await renew(asking('not-a-code'), env)).status, 400)
  const stranger = await folioKey()
  assert.equal((await renew(asking(await mint(stranger, 9)), env)).status, 403)
  assert.equal((await renew(asking(a), { ...env, WITHDRAWN: '1' })).status, 403)
})

test('it refuses to guess when the worker is not set up', async () => {
  const { a, env } = await setup()
  assert.equal((await renew(asking(a), { ...env, SUPPORTER_KEYS: '' })).status, 503)
  assert.equal((await renew(asking(a), { ...env, DB: undefined })).status, 503)
})

test('the worker answers GET /renew', async () => {
  const { a, env } = await setup()
  const routed = await worker.fetch(asking(a), env)
  assert.equal(routed.status, 200)
})

test('the payer key is private, steady, and different for different people', async () => {
  const env = { PAYER_SECRET: 'secret' }
  const one = await payerKey(env, 'Jo@Example.com ')
  assert.equal(one, await payerKey(env, 'jo@example.com'))
  assert.notEqual(one, await payerKey(env, 'al@example.com'))
  assert.notEqual(one, await payerKey({ PAYER_SECRET: 'other' }, 'jo@example.com'))
  assert.equal(await payerKey({}, 'jo@example.com'), null)
  assert.equal(await payerKey(env, ''), null)
})

test('canonical folds what the app and the database might disagree on', () => {
  assert.equal(canonical(' ab-c0 Il\n'), 'ABC011')
})
