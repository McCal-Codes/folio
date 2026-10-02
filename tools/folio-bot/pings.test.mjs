import { test } from 'node:test'
import assert from 'node:assert/strict'
import { PINGS_BUTTON, pingsComponents, togglePings } from './pings.mjs'

const env = { GUILD_ID: 'folio', ROLE_UPDATES: 'ping-role', DISCORD_BOT_TOKEN: 'token' }
const press = (roles, guild = 'folio') => ({ guild_id: guild, member: { user: { id: 'u1' }, roles } })
function fakeDiscord(status = 204) {
  const calls = []
  return { calls, addRole: async (...a) => (calls.push(['add', ...a]), status), removeRole: async (...a) => (calls.push(['remove', ...a]), status) }
}

test('the button is one row with one button that carries the id the worker listens for', () => {
  const [row] = pingsComponents()
  assert.equal(row.type, 1)
  assert.equal(row.components.length, 1)
  assert.equal(row.components[0].custom_id, PINGS_BUTTON)
  assert.equal(row.components[0].type, 2)
})

test('pressing it without the role gives the role, and pressing it with the role takes it back', async () => {
  const on = fakeDiscord()
  assert.match(await togglePings(env, press(['other']), on), /pinged/)
  assert.deepEqual(on.calls[0].slice(0, 4), ['add', 'folio', 'u1', 'ping-role'])
  const off = fakeDiscord()
  assert.match(await togglePings(env, press(['ping-role', 'other']), off), /are off/)
  assert.deepEqual(off.calls[0].slice(0, 4), ['remove', 'folio', 'u1', 'ping-role'])
})

test('a bot below the role says what to fix instead of failing quietly', async () => {
  assert.match(await togglePings(env, press([]), fakeDiscord(403)), /has to sit above Folio updates/)
})

test('another server, or a missing setting, changes nothing', async () => {
  const none = fakeDiscord()
  assert.match(await togglePings(env, press([], 'elsewhere'), none), /Folio Community server/)
  assert.match(await togglePings({ ...env, ROLE_UPDATES: undefined }, press([]), none), /Folio Community server/)
  assert.equal(none.calls.length, 0)
})

test('Discord failing is answered plainly', async () => {
  assert.match(await togglePings(env, press([]), fakeDiscord(500)), /did not work/)
})
