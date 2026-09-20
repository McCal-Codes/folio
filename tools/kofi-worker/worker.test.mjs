/**
 * What the Ko-fi worker does with the payments Ko-fi actually sends. Run with `node worker.test.mjs` — no account,
 * no network: the database and the mailer are stood in for here.
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import worker from './worker.js'

const TOKEN = 'token-from-kofi'

/** Just enough of D1 for the four statements the worker runs. */
function database(codes = [{ code: 'CODE-1', pool: 'beta' }]) {
  const free = [...codes]
  const handled = new Map()
  const problems = []
  return {
    handled, problems, free,
    prepare(sql) {
      let args = []
      const self = {
        bind: (...values) => { args = values; return self },
        first: async () => {
          if (sql.includes('SELECT code FROM handled')) {
            const row = handled.get(args[0])
            return row ? { code: row.code } : null
          }
          if (sql.startsWith('UPDATE codes')) {
            const pool = args[1]
            const at = free.findIndex((c) => c.pool === pool)
            return at < 0 ? null : { code: free.splice(at, 1)[0].code }
          }
          return null
        },
        run: async () => {
          if (sql.startsWith('INSERT INTO handled')) handled.set(args[0], { code: args[1], pool: args[2], emailed: 0 })
          if (sql.startsWith('INSERT INTO problems')) problems.push({ message_id: args[0], pool: args[1] })
          if (sql.startsWith('UPDATE handled')) handled.get(args[1]).emailed = args[0]
          return { success: true }
        },
      }
      return self
    },
  }
}

function payment(fields) {
  const data = { verification_token: TOKEN, message_id: 'm1', type: 'Tip', amount: '3.00', email: 'jo@example.com', ...fields }
  const body = new URLSearchParams({ data: JSON.stringify(data) })
  return new Request('https://codes.example/', { method: 'POST', body })
}

const POOLS = JSON.stringify({ tiers: { Bronze: 'beta', Gold: 'all' }, shop: { abc123: 'all' }, tipFrom: 5, tipPool: 'beta' })

test('a membership payment claims a code for its tier', async () => {
  const DB = database([{ code: 'CODE-1', pool: 'beta' }])
  const response = await worker.fetch(payment({ type: 'Subscription', tier_name: 'Bronze' }), { DB, KOFI_TOKEN: TOKEN, POOLS })
  assert.equal(response.status, 200)
  assert.equal(DB.handled.get('m1').code, 'CODE-1')
  assert.equal(DB.free.length, 0)
})

test('the same webhook arriving twice hands out one code', async () => {
  const DB = database([{ code: 'CODE-1', pool: 'beta' }, { code: 'CODE-2', pool: 'beta' }])
  const env = { DB, KOFI_TOKEN: TOKEN, POOLS }
  await worker.fetch(payment({ type: 'Subscription', tier_name: 'Bronze' }), env)
  const again = await worker.fetch(payment({ type: 'Subscription', tier_name: 'Bronze' }), env)
  assert.equal(again.status, 200)
  assert.equal(await again.text(), 'already handled')
  assert.equal(DB.free.length, 1)
})

test('a shop order matches on the item, a small tip earns nothing', async () => {
  const DB = database([{ code: 'CODE-1', pool: 'all' }])
  const env = { DB, KOFI_TOKEN: TOKEN, POOLS }
  await worker.fetch(payment({ type: 'Shop Order', shop_items: [{ direct_link_code: 'abc123', quantity: 1 }] }), env)
  assert.equal(DB.handled.get('m1').code, 'CODE-1')

  const small = database([{ code: 'CODE-2', pool: 'beta' }])
  const response = await worker.fetch(payment({ message_id: 'm2', type: 'Tip', amount: '3.00' }), { ...env, DB: small })
  assert.equal(response.status, 200)
  assert.equal(small.free.length, 1)
})

test('a big enough tip earns the tip pool', async () => {
  const DB = database([{ code: 'CODE-1', pool: 'beta' }])
  await worker.fetch(payment({ type: 'Tip', amount: '10.00' }), { DB, KOFI_TOKEN: TOKEN, POOLS })
  assert.equal(DB.handled.get('m1').code, 'CODE-1')
})

test('a one-off payment lands in the band it paid for', async () => {
  const BANDS = JSON.stringify({
    tipBands: [{ from: 5, pool: 'months1' }, { from: 10, pool: 'months2' }, { from: 20, pool: 'months4' }],
  })
  const paid = async (amount) => {
    const DB = database([{ code: 'M1', pool: 'months1' }, { code: 'M2', pool: 'months2' }, { code: 'M4', pool: 'months4' }])
    await worker.fetch(payment({ amount }), { DB, KOFI_TOKEN: TOKEN, POOLS: BANDS })
    return DB.handled.get('m1')?.pool ?? null
  }
  assert.equal(await paid('5.00'), 'months1')
  assert.equal(await paid('10.00'), 'months2')
  assert.equal(await paid('12.00'), 'months2')   // between bands: the one it cleared, not the next one up
  assert.equal(await paid('20.00'), 'months4')
  assert.equal(await paid('3.00'), null)         // under the first band, so nothing
})

test('a tip is read in the currency it was paid in, not as dollars', async () => {
  const BANDS = JSON.stringify({
    tipCurrency: 'USD',
    tipRates: { EUR: 1.08 },
    tipBands: [{ from: 5, pool: 'months1' }, { from: 20, pool: 'months4' }],
  })
  const paid = async (fields) => {
    const DB = database([{ code: 'M1', pool: 'months1' }, { code: 'M4', pool: 'months4' }])
    await worker.fetch(payment(fields), { DB, KOFI_TOKEN: TOKEN, POOLS: BANDS })
    return { pool: DB.handled.get('m1')?.pool ?? null, problems: DB.problems }
  }
  // 500 yen is about three dollars: the four-month band is for people who paid four months' worth.
  const yen = await paid({ amount: '500.00', currency: 'JPY' })
  assert.equal(yen.pool, null)
  assert.deepEqual(yen.problems, [{ message_id: 'm1', pool: 'unpriced JPY' }])   // paid, so not dropped in silence

  // A currency with a rate is converted: 5 euros clears the five-dollar band, 4 doesn't.
  assert.equal((await paid({ amount: '5.00', currency: 'EUR' })).pool, 'months1')
  assert.equal((await paid({ amount: '4.00', currency: 'EUR' })).pool, null)

  const dollars = await paid({ amount: '20.00', currency: 'USD' })
  assert.equal(dollars.pool, 'months4')
  assert.deepEqual(dollars.problems, [])
})

test('a half-written band leaves the flat tip pool reachable', async () => {
  // The from:5 band has no pool yet. A $10 tip should still earn what tipPool says, not nothing at all.
  const POOLS_HALF = JSON.stringify({ tipBands: [{ from: 20, pool: 'months4' }, { from: 5 }], tipFrom: 5, tipPool: 'beta' })
  const DB = database([{ code: 'CODE-1', pool: 'beta' }])
  await worker.fetch(payment({ amount: '10.00' }), { DB, KOFI_TOKEN: TOKEN, POOLS: POOLS_HALF })
  assert.equal(DB.handled.get('m1').code, 'CODE-1')
})

test('a wrong token is turned away and claims nothing', async () => {
  const DB = database()
  const response = await worker.fetch(payment({ verification_token: 'someone-else', type: 'Subscription', tier_name: 'Gold' }),
    { DB, KOFI_TOKEN: TOKEN, POOLS })
  assert.equal(response.status, 401)
  assert.equal(DB.free.length, 1)
})

test('an empty pool is written down, and Ko-fi is not asked to retry', async () => {
  const DB = database([])
  const response = await worker.fetch(payment({ type: 'Subscription', tier_name: 'Gold' }), { DB, KOFI_TOKEN: TOKEN, POOLS })
  assert.equal(response.status, 200)
  assert.equal(DB.problems[0].pool, 'all')
})

test('without a mail key the code is still claimed, to send by hand', async () => {
  const DB = database([{ code: 'CODE-1', pool: 'beta' }])
  await worker.fetch(payment({ type: 'Subscription', tier_name: 'Bronze' }), { DB, KOFI_TOKEN: TOKEN, POOLS })
  assert.equal(DB.handled.get('m1').emailed, 0)
  assert.equal(DB.handled.get('m1').code, 'CODE-1')
})
