/**
 * The admin routes and the webhook against a real SQLite database with the real schema.sql, standing in for D1 through
 * Node's built-in node:sqlite. Run with `node admin.test.mjs`: no account, no network.
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DatabaseSync } from 'node:sqlite'
import test from 'node:test'
import worker from './worker.js'

const KOFI = 'token-from-kofi'
const ADMIN = 'admin-token-for-the-mac'
const POOLS = JSON.stringify({ tiers: { Backer: 'm1', Builder: 'm1' }, tipFrom: 1, tipPool: 'm1', tipCurrency: 'USD' })
const code = (n) => `087J2-${String(n).padStart(5, '0')}` + '-ABCDE'.repeat(21) + '-0W'

/** D1's prepare/bind/first/all/run over node:sqlite, which is enough for everything the worker says. */
export function d1() {
  const db = new DatabaseSync(':memory:')
  db.exec(readFileSync(new URL('./schema.sql', import.meta.url), 'utf8'))
  return {
    db,
    prepare(sql) {
      let args = []
      const statement = db.prepare(sql)
      const self = {
        bind: (...values) => { args = values.map((v) => (v === undefined ? null : v)); return self },
        first: async () => statement.get(...args) ?? null,
        all: async () => ({ results: statement.all(...args) }),
        run: async () => { const r = statement.run(...args); return { success: true, meta: { changes: r.changes } } },
      }
      return self
    },
  }
}

const env = (DB, more = {}) => ({ DB, KOFI_TOKEN: KOFI, ADMIN_TOKEN: ADMIN, POOLS, ...more })
const call = (path, { method = 'GET', body, token = ADMIN } = {}) =>
  new Request(`https://codes.example${path}`, {
    method, body: body === undefined ? undefined : JSON.stringify(body),
    headers: token === null ? {} : { authorization: `Bearer ${token}` },
  })
function payment(fields) {
  const data = { verification_token: KOFI, message_id: 'm1', type: 'Tip', amount: '5.00', currency: 'USD',
    email: 'jo@example.com', from_name: 'Jo', kofi_transaction_id: 'tx-1', ...fields }
  return new Request('https://codes.example/', { method: 'POST', body: new URLSearchParams({ data: JSON.stringify(data) }) })
}
async function stock(DB, pool = 'm1') {
  return (await DB.prepare('SELECT COUNT(*) AS n FROM codes WHERE pool = ? AND used_at IS NULL').bind(pool).first()).n
}

test('admin routes are off without a token, and refuse a wrong one', async () => {
  const DB = d1()
  assert.equal((await worker.fetch(call('/admin/health'), env(DB, { ADMIN_TOKEN: '' }))).status, 503)
  assert.equal((await worker.fetch(call('/admin/health', { token: 'nope' }), env(DB))).status, 401)
  assert.equal((await worker.fetch(call('/admin/health', { token: null }), env(DB))).status, 401)
  assert.equal((await worker.fetch(call('/admin/health'), env(DB))).status, 200)
})

test('a refill adds codes once, and refuses anything that is not a Folio code', async () => {
  const DB = d1()
  const first = await (await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1), code(2)] } }), env(DB))).json()
  assert.deepEqual(first, { added: 2, skipped: 0 })
  const again = await (await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(2), code(3)] } }), env(DB))).json()
  assert.deepEqual(again, { added: 1, skipped: 1 })
  const bad = await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: ["x'); DROP TABLE codes;--"] } }), env(DB))
  assert.equal(bad.status, 400)
  assert.equal(await stock(DB), 3)
})

test('health reports configuration, stock against the pools the rules want, and samples', async () => {
  const DB = d1()
  await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1), code(2)] } }), env(DB))
  const h = await (await worker.fetch(call('/admin/health'), env(DB, { MAIL_FROM: 'Folio <folio@example.com>' }))).json()
  assert.equal(h.configured.kofiToken, true)
  assert.equal(h.configured.resendKey, false)
  assert.equal(h.configured.mailFrom, false, 'the placeholder address does not count')
  assert.deepEqual(h.wanted, ['m1'])
  assert.deepEqual(h.stock, [{ pool: 'm1', free: 2, total: 2 }])
  assert.equal(h.samples.length, 2)
  assert.equal(h.lastKofiTest, null)
})

test('a tip of any size earns one month; Coffee earns nothing; Backer earns a month', async () => {
  const DB = d1()
  await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1), code(2), code(3)] } }), env(DB))
  await worker.fetch(payment({ message_id: 'a', amount: '1.00', kofi_transaction_id: 'tx-a' }), env(DB))
  await worker.fetch(payment({ message_id: 'b', type: 'Subscription', tier_name: 'Coffee', amount: '3.00', kofi_transaction_id: 'tx-b' }), env(DB))
  await worker.fetch(payment({ message_id: 'c', type: 'Subscription', tier_name: 'Backer', amount: '7.00', kofi_transaction_id: 'tx-c' }), env(DB))
  const r = await (await worker.fetch(call('/admin/recent'), env(DB))).json()
  assert.deepEqual(r.handled.map((h) => h.transaction_id).sort(), ['tx-a', 'tx-c'])
  const a = r.handled.find((h) => h.transaction_id === 'tx-a')
  assert.equal(a.from_name, 'Jo')
  assert.equal(a.type, 'Tip')
  assert.equal(a.pool, 'm1')
  assert.equal('email' in a, false, 'no email address is kept')
  assert.equal(await stock(DB), 1)
})

test("Ko-fi's Send test is written down and costs no code", async () => {
  const DB = d1()
  await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1)] } }), env(DB))
  const res = await worker.fetch(payment({ type: 'Subscription', tier_name: 'Backer', kofi_transaction_id: '00000000-1111-2222-3333-444444444444' }), env(DB))
  assert.equal(res.status, 200)
  assert.equal(await stock(DB), 1)
  const h = await (await worker.fetch(call('/admin/health'), env(DB))).json()
  assert.equal(h.lastKofiTest.wouldEarn, 'm1')
  assert.equal(h.lastKofiTest.tier, 'Backer')
  // Ko-fi's sample supporter counts as a test too, whatever the transaction id.
  await worker.fetch(payment({ message_id: 'k2', kofi_transaction_id: 'something-new', email: 'Jo.Example@example.com' }), env(DB))
  assert.equal(await stock(DB), 1)
})

test('the test payment runs the real claim and puts the code back', async () => {
  const DB = d1()
  const empty = await (await worker.fetch(call('/admin/test', { method: 'POST' }), env(DB))).json()
  assert.equal(empty.ok, false)
  assert.equal(empty.steps.at(-1).detail, 'the m1 pool is empty')
  await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1)] } }), env(DB))
  const t = await (await worker.fetch(call('/admin/test', { method: 'POST' }), env(DB))).json()
  assert.equal(t.ok, true, JSON.stringify(t.steps))
  assert.deepEqual(t.steps.map((s) => s.step), ['rules', 'claim', 'put back'])
  assert.equal(await stock(DB), 1)
  assert.equal((await DB.prepare('SELECT COUNT(*) AS n FROM handled').first()).n, 0)
  const coffee = await (await worker.fetch(call('/admin/test', { method: 'POST', body: { type: 'Subscription', tier_name: 'Coffee' } }), env(DB))).json()
  assert.equal(coffee.ok, false)
  assert.equal(coffee.steps[0].detail, 'this payment earns nothing')
})

test('a database missing the newer columns is reported, and strands no code', async () => {
  const DB = d1()
  // The shape a worker deployed before those columns has: this really happened on the live one.
  DB.db.exec('DROP TABLE handled')
  DB.db.exec(`CREATE TABLE handled (message_id TEXT PRIMARY KEY, code TEXT NOT NULL, pool TEXT NOT NULL,
    at TEXT NOT NULL, emailed INTEGER NOT NULL DEFAULT 0)`)
  await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1)] } }), env(DB))
  const h = await (await worker.fetch(call('/admin/health'), env(DB))).json()
  assert.equal(h.columns, false)
  const failed = await worker.fetch(call('/admin/test', { method: 'POST' }), env(DB))
  assert.equal(failed.status, 400)
  assert.equal(await stock(DB), 1, 'the code goes back when recording it fails')
})

test('a hand-out from Folio Dev is recorded as by hand', async () => {
  const DB = d1()
  await worker.fetch(call('/admin/pool', { method: 'POST', body: { pool: 'm1', codes: [code(1)] } }), env(DB))
  const got = await (await worker.fetch(call('/admin/claim', { method: 'POST', body: { pool: 'm1', name: 'Sam' } }), env(DB))).json()
  assert.equal(got.ok, true)
  assert.equal(got.code, code(1))
  const none = await (await worker.fetch(call('/admin/claim', { method: 'POST', body: { pool: 'm1', name: 'Al' } }), env(DB))).json()
  assert.equal(none.ok, false)
  const r = await (await worker.fetch(call('/admin/recent'), env(DB))).json()
  assert.equal(r.handled[0].by_hand, 1)
  assert.equal(r.handled[0].from_name, 'Sam')
})
