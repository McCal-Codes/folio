import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DatabaseSync } from 'node:sqlite'
import { expire, redeem, roleFor } from './redeem.mjs'

// A throwaway signing key, so these codes are signed exactly as McCal's Mac signs real ones: ECDSA P-256, SHA-256,
// the 9-byte payload followed by the 64-byte signature.
const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
const spki = Buffer.from(await crypto.subtle.exportKey('spki', pair.publicKey)).toString('base64')
const stranger = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const EPOCH = Date.UTC(2026, 0, 1)
const DAY = 86_400_000
const SCOPES = ['beta', 'look', 'power', 'keys', 'dev', 'thanks']

function crockford(bytes) {
  let out = ''
  let buffer = 0
  let bits = 0
  for (const byte of bytes) {
    buffer = ((buffer << 8) | byte) & 0xffff
    bits += 8
    while (bits >= 5) {
      bits -= 5
      out += ALPHABET[(buffer >> bits) & 31]
    }
  }
  if (bits > 0) out += ALPHABET[(buffer << (5 - bits)) & 31]
  return out
}

/** Mints a code the way scripts/beta-code.py does, including the version-2 months-and-tier byte. */
async function mint({ scopes = ['beta', 'look', 'power', 'keys'], tier = 1, months = 1, expires = null, serial = 1, key = pair.privateKey } = {}) {
  const bits = scopes.reduce((all, scope) => all | (1 << SCOPES.indexOf(scope)), 0)
  const version = months > 0 ? 2 : 1
  const tierByte = months > 0 ? (months << 4) | tier : tier
  const day = expires ? Math.round((Date.parse(expires) - EPOCH) / DAY) : 0
  const payload = new Uint8Array([version, bits, tierByte, day >> 8, day & 0xff, serial >>> 24, (serial >> 16) & 0xff, (serial >> 8) & 0xff, serial & 0xff])
  const signature = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key, payload))
  return crockford(new Uint8Array([...payload, ...signature]))
}

/** Real SQLite, loaded with the Ko-fi worker's real schema, behind the few calls D1 is asked for. */
function database() {
  const db = new DatabaseSync(':memory:')
  db.exec(readFileSync(new URL('../kofi-worker/schema.sql', import.meta.url), 'utf8'))
  return {
    raw: db,
    prepare(sql) {
      const statement = db.prepare(sql)
      let values = []
      const api = {
        bind: (...args) => { values = args; return api },
        run: async () => { statement.run(...values); return { success: true } },
        first: async () => statement.get(...values) ?? null,
        all: async () => ({ results: statement.all(...values) }),
      }
      return api
    },
  }
}

const ROLES = { ROLE_COFFEE: 'coffee-role', ROLE_BACKER: 'backer-role', ROLE_BUILDER: 'builder-role' }
const GUILD = 'guild-1'
const NOW = Date.UTC(2026, 8, 25, 12)

function setup(overrides = {}) {
  const env = { DB: database(), DISCORD_BOT_TOKEN: 'token', GUILD_ID: GUILD, SUPPORTER_KEYS: spki, ...ROLES, ...overrides }
  const calls = []
  let status = 204
  const discord = {
    addRole: async (...args) => { calls.push(['add', ...args]); return status },
    removeRole: async (...args) => { calls.push(['remove', ...args]); return status },
    answer: (next) => { status = next },
  }
  const rows = () => env.DB.raw.prepare('SELECT * FROM discord_roles ORDER BY serial').all()
  return { env, calls, discord, rows }
}

test('a plain code earns Coffee, until the day its month runs out', async () => {
  const { env, calls, discord, rows } = setup()
  const reply = await redeem(env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 7 }) }, discord, NOW)
  assert.match(reply, /\*\*Coffee\*\* role until 2026-10-25/)
  assert.deepEqual(calls, [['add', GUILD, 'u1', 'coffee-role', 'Redeemed supporter code 7']])
  const [row] = rows()
  assert.equal(row.serial, 7)
  assert.equal(row.user_id, 'u1')
  assert.equal(row.ends_on, '2026-10-25')
})

test('the thanks scope earns Builder, and tier 2 earns Backer', async () => {
  const builder = setup()
  const code = await mint({ scopes: ['beta', 'look', 'power', 'keys', 'thanks'], months: 2, serial: 8 })
  assert.match(await redeem(builder.env, { userId: 'u1', guildId: GUILD, text: code }, builder.discord, NOW), /\*\*Builder\*\*.*2026-11-25/)
  assert.equal(builder.calls[0][3], 'builder-role')

  const backer = setup()
  await redeem(backer.env, { userId: 'u1', guildId: GUILD, text: await mint({ tier: 2, serial: 9 }) }, backer.discord, NOW)
  assert.equal(backer.calls[0][3], 'backer-role')
})

test('roleFor reads the scope before the tier, since every code so far is tier 1', () => {
  assert.equal(roleFor({ scopeBits: 0b100001, tier: 1 }, ROLES).name, 'Builder')
  assert.equal(roleFor({ scopeBits: 0b100001, tier: 2 }, ROLES).name, 'Builder')
  assert.equal(roleFor({ scopeBits: 0b000001, tier: 2 }, ROLES).name, 'Backer')
  assert.equal(roleFor({ scopeBits: 0b001111, tier: 1 }, ROLES).name, 'Coffee')
})

test('a code signed by anyone else is refused, and nothing is granted or recorded', async () => {
  const { env, calls, discord, rows } = setup()
  const forged = await mint({ serial: 10, key: stranger.privateKey })
  assert.match(await redeem(env, { userId: 'u1', guildId: GUILD, text: forged }, discord, NOW), /not a Folio code/)
  assert.equal(calls.length, 0)
  assert.equal(rows().length, 0)
})

test('text that is not a code at all gets the same answer as a forged one', async () => {
  const { env, discord } = setup()
  assert.match(await redeem(env, { userId: 'u1', guildId: GUILD, text: 'hello there' }, discord, NOW), /not a Folio code/)
})

test('a withdrawn code is refused', async () => {
  const { env, calls, discord } = setup({ WITHDRAWN: '11, 12' })
  assert.match(await redeem(env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 11 }) }, discord, NOW), /withdrawn/)
  assert.equal(calls.length, 0)
})

test('a code that has passed its last day is refused', async () => {
  const { env, discord } = setup()
  const old = await mint({ months: 0, expires: '2026-09-01', serial: 13 })
  assert.match(await redeem(env, { userId: 'u1', guildId: GUILD, text: old }, discord, NOW), /run out/)
})

test("someone else's code is refused without starting its month", async () => {
  const { env, calls, discord } = setup()
  const code = await mint({ serial: 14 })
  await redeem(env, { userId: 'owner', guildId: GUILD, text: code }, discord, NOW)
  env.DB.raw.prepare('DELETE FROM beta_seen').run()

  const reply = await redeem(env, { userId: 'stranger', guildId: GUILD, text: code }, discord, NOW)
  assert.match(reply, /already been redeemed by someone else/)
  assert.equal(calls.length, 1, 'only the owner was ever given a role')
  const seen = env.DB.raw.prepare('SELECT count(*) AS n FROM beta_seen').get()
  assert.equal(seen.n, 0, "the stranger's attempt must not touch the code's clock")
})

test('redeeming the same code twice keeps one record', async () => {
  const { env, discord, rows } = setup()
  const code = await mint({ serial: 15 })
  await redeem(env, { userId: 'u1', guildId: GUILD, text: code }, discord, NOW)
  await redeem(env, { userId: 'u1', guildId: GUILD, text: code }, discord, NOW + DAY)
  assert.equal(rows().length, 1)
  assert.equal(rows()[0].ends_on, '2026-10-25', 'the second go must not stretch the month')
})

test('a role Discord will not hand out says why, and is not recorded as given', async () => {
  const { env, discord, rows } = setup()
  discord.answer(403)
  const reply = await redeem(env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 16 }) }, discord, NOW)
  assert.match(reply, /Mr Folio's role has to sit above Coffee/)
  assert.equal(rows().length, 0, 'recording it would block the owner from trying again once the role order is fixed')
})

test('codes are only redeemed in the Folio server', async () => {
  const { env, calls, discord } = setup()
  assert.match(await redeem(env, { userId: 'u1', guildId: 'elsewhere', text: await mint() }, discord, NOW), /Folio Community server/)
  assert.equal(calls.length, 0)
})

test('the sweep takes a role back the day after its code ends', async () => {
  const { env, calls, discord, rows } = setup()
  await redeem(env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 20 }) }, discord, NOW)
  calls.length = 0

  assert.deepEqual((await expire(env, discord, Date.UTC(2026, 9, 25, 12))).removed, [], 'still its last day')
  const later = await expire(env, discord, Date.UTC(2026, 9, 26, 12))
  assert.deepEqual(later.removed, [20])
  assert.deepEqual(calls, [['remove', GUILD, 'u1', 'coffee-role', 'Supporter code 20 ended']])
  assert.equal(rows()[0].removed_at, '2026-10-26')
})

test('a newer code that earns the same role keeps it', async () => {
  const { env, calls, discord, rows } = setup()
  await redeem(env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 21 }) }, discord, NOW)
  await redeem(env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 22 }) }, discord, NOW + 20 * DAY)
  calls.length = 0

  const result = await expire(env, discord, Date.UTC(2026, 9, 26, 12))
  assert.deepEqual(result.removed, [])
  assert.equal(calls.length, 0, 'the role is still earned by code 22')
  assert.equal(rows().find((row) => row.serial === 21).removed_at, '2026-10-26', 'code 21 is closed all the same')
})

test('someone who left the server is closed off, and a Discord error is tried again tomorrow', async () => {
  const gone = setup()
  await redeem(gone.env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 30 }) }, gone.discord, NOW)
  gone.discord.answer(404)
  assert.deepEqual((await expire(gone.env, gone.discord, Date.UTC(2026, 9, 26))).removed, [30])

  const flaky = setup()
  await redeem(flaky.env, { userId: 'u1', guildId: GUILD, text: await mint({ serial: 31 }) }, flaky.discord, NOW)
  flaky.discord.answer(500)
  assert.deepEqual((await expire(flaky.env, flaky.discord, Date.UTC(2026, 9, 26))).removed, [])
  assert.equal(flaky.rows()[0].removed_at, null)
})
