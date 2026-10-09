import { test } from 'node:test'
import assert from 'node:assert/strict'
import { MAX_AGE_MS, PER_PERSON_MS, madeAt, pickMessages, pickThreads, replies, runAutoReply } from './autoreply.mjs'

const BOT = '100'
const NOW = Date.UTC(2026, 9, 8, 20, 0, 0)
/** A snowflake id for something made `agoMs` before NOW, with a counter so ids differ. */
const idFor = (agoMs, n = 0) => String(((BigInt(NOW - agoMs) - 1420070400000n) << 22n) + BigInt(n))
const post = (author, agoMs, extra = {}) => ({ id: idFor(agoMs, extra.n ?? 0), type: 0, author: { id: author }, ...extra })

test('a snowflake id says when it was made', () => {
  assert.equal(madeAt(idFor(5 * 60 * 1000)), NOW - 5 * 60 * 1000)
})

test('a new post from a member gets a reply, and McCal, bots, webhooks and replies to others do not', () => {
  const messages = [
    post('mccal', 60_000, { n: 1 }),
    post('member', 120_000, { n: 2 }),
    post('somebot', 130_000, { n: 3, author: { id: 'x', bot: true } }),
    post('hook', 140_000, { n: 4, webhook_id: 'w' }),
    post('other', 150_000, { n: 5, message_reference: { message_id: 'z' }, type: 19 }),
  ]
  const picked = pickMessages(messages, { botId: BOT, staffIds: ['mccal'], now: NOW })
  assert.deepEqual(picked.map((m) => m.author.id), ['member'])
})

test('a post the bot already answered is left alone, and so is an old one', () => {
  const asked = post('a', 5 * 60_000, { n: 1 })
  const answer = { id: idFor(4 * 60_000, 2), type: 19, author: { id: BOT }, message_reference: { message_id: asked.id } }
  assert.equal(pickMessages([answer, asked], { botId: BOT, now: NOW }).length, 0)
  assert.equal(pickMessages([post('b', MAX_AGE_MS + 60_000)], { botId: BOT, now: NOW }).length, 0)
})

test('one automatic reply per person, not one per line of a back-and-forth', () => {
  const first = post('a', 20 * 60_000, { n: 1 })
  const answer = { id: idFor(19 * 60_000, 2), type: 19, author: { id: BOT }, message_reference: { message_id: first.id } }
  const again = post('a', 2 * 60_000, { n: 3 })
  const twoInARun = pickMessages([again, post('a', 3 * 60_000, { n: 4 })], { botId: BOT, now: NOW })
  assert.equal(twoInARun.length, 1)
  assert.equal(pickMessages([again, answer, first], { botId: BOT, now: NOW }).length, 0)
  // A reply made long ago no longer holds a person back.
  const stale = { ...answer, id: idFor(PER_PERSON_MS + 60_000, 2) }
  assert.equal(pickMessages([again, stale, first], { botId: BOT, now: NOW }).length, 1)
})

test('no more than three replies in one pass', () => {
  const crowd = ['a', 'b', 'c', 'd', 'e'].map((id, i) => post(id, 60_000 * (i + 1), { n: i }))
  assert.equal(pickMessages(crowd, { botId: BOT, now: NOW }).length, 3)
})

test('only new posts in the forums are picked, and McCal\'s own are skipped', () => {
  const thread = (id, parent, ago, owner = 'u') => ({ id: idFor(ago, id), parent_id: parent, owner_id: owner })
  const picked = pickThreads(
    [thread(1, 'bugs', 60_000), thread(2, 'bugs', MAX_AGE_MS + 1_000), thread(3, 'elsewhere', 60_000), thread(4, 'ideas', 60_000, 'mccal')],
    { forums: ['bugs', 'ideas'], now: NOW, staffIds: ['mccal'] },
  )
  assert.equal(picked.length, 1)
})

const env = { AUTO_REPLY: 'on', DISCORD_BOT_TOKEN: 'secret', BOT_ID: BOT, GUILD_ID: 'g', HELP_CHANNEL: 'help', BUG_FORUM: 'bugs', IDEAS_FORUM: 'ideas', FAQ_CHANNEL: 'faq', STAFF_IDS: 'mccal' }

function fakeDiscord(routes) {
  const calls = []
  const send = async (url, init = {}) => {
    const path = url.replace('https://discord.com/api/v10', '')
    calls.push({ method: init.method || 'GET', path, body: init.body ? JSON.parse(init.body) : null, auth: init.headers?.authorization })
    const hit = Object.entries(routes).find(([key]) => path.startsWith(key))
    return { ok: Boolean(hit), status: hit ? 200 : 404, json: async () => (hit ? hit[1] : {}) }
  }
  return { calls, send }
}

test('a new #help post is answered with a reply that pings nobody else and has no link preview', async () => {
  const asked = post('member', 60_000)
  const d = fakeDiscord({ '/channels/help/messages?limit': [asked], '/channels/help/messages': {}, '/guilds/g/threads/active': { threads: [] } })
  assert.equal(await runAutoReply(env, d.send, NOW), 1)
  const sent = d.calls.find((c) => c.method === 'POST')
  assert.equal(sent.path, '/channels/help/messages')
  assert.equal(sent.body.message_reference.message_id, asked.id)
  assert.deepEqual(sent.body.allowed_mentions.parse, [])
  assert.equal(sent.body.flags, 4)
  assert.equal(sent.auth, 'Bot secret')
  assert.match(sent.body.content, /<#faq>/)
})

test('a new bug-report post gets the checklist once, and not again when the bot is already in it', async () => {
  const thread = { id: idFor(60_000, 9), parent_id: 'bugs', owner_id: 'u' }
  const fresh = fakeDiscord({ '/channels/help/messages?limit': [], '/guilds/g/threads/active': { threads: [thread] }, [`/channels/${thread.id}/messages?limit`]: [post('u', 60_000)] , [`/channels/${thread.id}/messages`]: {} })
  assert.equal(await runAutoReply(env, fresh.send, NOW), 1)
  assert.match(fresh.calls.find((c) => c.method === 'POST').body.content, /Folio version/)
  const answered = fakeDiscord({ '/channels/help/messages?limit': [], '/guilds/g/threads/active': { threads: [thread] }, [`/channels/${thread.id}/messages?limit`]: [{ id: '1', author: { id: BOT } }] })
  assert.equal(await runAutoReply(env, answered.send, NOW), 0)
})

test('it does nothing when switched off, without a token, or when Discord is down', async () => {
  const d = fakeDiscord({})
  assert.equal(await runAutoReply({ ...env, AUTO_REPLY: 'off' }, d.send, NOW), 0)
  assert.equal(await runAutoReply({ ...env, DISCORD_BOT_TOKEN: '' }, d.send, NOW), 0)
  assert.equal(d.calls.length, 0)
  assert.equal(await runAutoReply(env, d.send, NOW), 0) // every route 404s: logged, not thrown
})

test('the replies are short, name the right places and promise no timing', () => {
  const say = replies(env)
  for (const text of Object.values(say)) assert.ok(text.length < 600)
  assert.match(say.help, /<#bugs>/)
  assert.doesNotMatch(Object.values(say).join(' '), /within|hours|24/)
})
