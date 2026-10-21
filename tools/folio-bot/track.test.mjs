import { test } from 'node:test'
import assert from 'node:assert/strict'
import { TRACK_COMMAND, track } from './track.mjs'

const env = { STAFF_IDS: 'mccal', BUG_FORUM: 'bugs', IDEAS_FORUM: 'ideas', GUILD_ID: 'g', DISCORD_BOT_TOKEN: 'd', GITHUB_TOKEN: 'gh' }
const interaction = (extra = {}) => ({
  member: { user: { id: 'mccal' } },
  channel: { id: 'thread1', parent_id: 'bugs', name: 'Dock handle rarely works' },
  data: { options: [] },
  ...extra,
})
const reply = (data, status = 200) => ({ ok: status < 400, status, json: async () => data })

function world({ issues = [], issue = null, createStatus = 201, renameStatus = 200 } = {}) {
  const calls = []
  const send = async (url, init = {}) => {
    const method = init.method ?? 'GET'
    const u = String(url)
    calls.push({ method, url: u, body: init.body ? JSON.parse(init.body) : undefined })
    if (u.includes('api.github.com') && u.endsWith('/issues') && method === 'POST') return reply({ number: 500 }, createStatus)
    if (u.includes('api.github.com') && u.includes('/issues?')) return reply(issues)
    if (u.includes('api.github.com') && /\/issues\/\d+$/.test(u)) return issue ? reply(issue) : reply({}, 404)
    if (method === 'PATCH') return reply({}, renameStatus)
    return reply({})
  }
  return { send, calls }
}

test('/track is staff only and is limited to people who can manage posts', async () => {
  assert.equal(TRACK_COMMAND.default_member_permissions, '17179869184')
  const { send, calls } = world()
  const said = await track(env, interaction({ member: { user: { id: 'someone' } } }), send)
  assert.match(said, /Only McCal/)
  assert.equal(calls.length, 0)
})

test('/track only works inside a post in the two forums', async () => {
  const { send } = world()
  assert.match(await track(env, interaction({ channel: { id: 'x', parent_id: 'general', name: 'n' } }), send), /inside a post/)
})

test('/track 433 renames the post and writes nothing to GitHub', async () => {
  const { send, calls } = world({ issue: { number: 433, state: 'open', labels: [] } })
  const said = await track(env, interaction({ data: { options: [{ name: 'number', value: 433 }] } }), send)
  assert.match(said, /Linked this post to #433/)
  const rename = calls.find((c) => c.method === 'PATCH')
  assert.deepEqual(rename.body, { name: '[#433] Dock handle rarely works' })
  assert.ok(calls.filter((c) => c.url.includes('github')).every((c) => c.method === 'GET'))
})

test('/track with a number GitHub does not have, or a pull request, changes nothing', async () => {
  const none = world()
  assert.match(await track(env, interaction({ data: { options: [{ name: 'number', value: 9 }] } }), none.send), /can't find issue #9/)
  const pr = world({ issue: { number: 9, pull_request: {} } })
  assert.match(await track(env, interaction({ data: { options: [{ name: 'number', value: 9 }] } }), pr.send), /pull request/)
  assert.equal(none.calls.concat(pr.calls).filter((c) => c.method === 'PATCH').length, 0)
})

test('/track alone opens one issue from the title, links back, then renames the post', async () => {
  const { send, calls } = world()
  const said = await track(env, interaction(), send)
  assert.match(said, /Opened #500/)
  const created = calls.find((c) => c.method === 'POST')
  assert.equal(created.body.title, 'Dock handle rarely works')
  assert.deepEqual(created.body.labels, ['bug', 'from-discord'])
  assert.match(created.body.body, /discord\.com\/channels\/g\/thread1/)
  assert.deepEqual(calls.find((c) => c.method === 'PATCH').body, { name: '[#500] Dock handle rarely works' })
})

test('an idea post gets the enhancement label', async () => {
  const { send, calls } = world()
  await track(env, interaction({ channel: { id: 't', parent_id: 'ideas', name: 'Six rows' } }), send)
  assert.deepEqual(calls.find((c) => c.method === 'POST').body.labels, ['enhancement', 'from-discord'])
})

test('a post that already has an issue pointing at it is reused, never opened twice', async () => {
  const { send, calls } = world({ issues: [{ number: 77, body: 'Reported on Discord: https://discord.com/channels/g/thread1' }] })
  const said = await track(env, interaction(), send)
  assert.match(said, /Linked this post to #77/)
  assert.equal(calls.filter((c) => c.method === 'POST').length, 0)
})

test('/track alone needs the GitHub token, and a post already linked is not linked again', async () => {
  const { send, calls } = world()
  assert.match(await track({ ...env, GITHUB_TOKEN: undefined }, interaction(), send), /needs a GitHub token/)
  assert.match(await track(env, interaction({ channel: { id: 't', parent_id: 'bugs', name: '[#5] x' } }), send), /already linked to #5/)
  assert.equal(calls.length, 0)
})

test('if GitHub refuses, the post is left alone; if the rename fails, the issue number is given', async () => {
  const refused = world({ createStatus: 403 })
  assert.match(await track(env, interaction(), refused.send), /would not open the issue/)
  assert.equal(refused.calls.filter((c) => c.method === 'PATCH').length, 0)
  const stuck = world({ renameStatus: 403 })
  assert.match(await track(env, interaction(), stuck.send), /Opened #500, but I couldn't rename.*\[#500\]/)
})
