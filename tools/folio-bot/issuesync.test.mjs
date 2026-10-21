import { test } from 'node:test'
import assert from 'node:assert/strict'
import { MAX_CHANGES, issueRef, noteFor, planChanges, runIssueSync, statusFor, withIssueRef } from './issuesync.mjs'

const tags = (names) => names.map((name, i) => ({ id: `t${i}-${name}`, name }))
const BUG_TAGS = tags(['Crash', 'Needs info', 'Fixed', 'Planned', 'Fixed in main', 'Not planned', 'Duplicate'])
const id = (name) => BUG_TAGS.find((tag) => tag.name === name).id
const forums = { bugs: { kind: 'bug', tags: BUG_TAGS }, ideas: { kind: 'idea', tags: tags(['Home screen', 'Planned', 'In main', 'Shipped', 'Not planned']) } }

const issue = (number, extra = {}) => ({ number, state: 'open', labels: [], ...extra })
const label = (...names) => names.map((name) => ({ name }))
const thread = (name, extra = {}) => ({ id: `th-${name}`, name, parent_id: 'bugs', owner_id: 'poster', applied_tags: [], thread_metadata: { archived: false, locked: false }, ...extra })
const plan = (threads, issues) => planChanges({ threads, issues: new Map(issues.map((i) => [i.number, i])), forums })

test('the issue number comes from the start of the title, and only there', () => {
  assert.equal(issueRef('[#433] Dock handle'), 433)
  assert.equal(issueRef('  [#7]x'), 7)
  assert.equal(issueRef('Dock handle [#433]'), null)
  assert.equal(issueRef('Dock handle #433'), null)
  assert.equal(issueRef(undefined), null)
})

test('adding a number replaces an old one and stays within 100 characters', () => {
  assert.equal(withIssueRef('Dock handle', 433), '[#433] Dock handle')
  assert.equal(withIssueRef('[#12] Dock handle', 433), '[#433] Dock handle')
  assert.equal(withIssueRef('x'.repeat(200), 5).length, 100)
})

test('an issue\'s status comes from its state and labels, with needs-info ahead of the rest', () => {
  assert.equal(statusFor(issue(1)), null)
  assert.equal(statusFor(issue(1, { labels: label('bug', 'planned') })), 'planned')
  assert.equal(statusFor(issue(1, { labels: label('planned', 'fixed-in-main') })), 'inMain')
  assert.equal(statusFor(issue(1, { labels: label('fixed-in-main', 'needs-info') })), 'needsInfo')
  assert.equal(statusFor(issue(1, { state: 'closed', state_reason: 'completed' })), 'fixed')
  assert.equal(statusFor(issue(1, { state: 'closed', state_reason: 'not_planned' })), 'notPlanned')
  assert.equal(statusFor(issue(1, { state: 'closed', labels: label('wontfix') })), 'notPlanned')
  assert.equal(statusFor(issue(1, { state: 'closed', labels: label('duplicate') })), 'duplicate')
})

test('a post with no number, or in another channel, is never touched', () => {
  const result = plan([thread('No number here'), thread('[#1] elsewhere', { parent_id: 'general' })], [issue(1, { labels: label('planned') })])
  assert.deepEqual(result.changes, [])
  assert.deepEqual(result.missing, [])
})

test('fixed-in-main is a tag and nothing more: no note, no ping, still open', () => {
  const { changes } = plan([thread('[#2] Badge')], [issue(2, { labels: label('fixed-in-main') })])
  assert.equal(changes.length, 1)
  assert.deepEqual(changes[0].steps, [{ patch: { applied_tags: [id('Fixed in main')] } }])
})

test('needs-info tags the post and pings the poster once', () => {
  const { changes } = plan([thread('[#3] Search')], [issue(3, { labels: label('needs-info') })])
  assert.equal(changes[0].steps.length, 2)
  assert.match(changes[0].steps[1].say, /<@poster>/)
  assert.equal(changes[0].steps[1].ping, true)
  // The tag is the record: once it is on, nothing more is planned.
  const done = plan([thread('[#3] Search', { applied_tags: [id('Needs info')] })], [issue(3, { labels: label('needs-info') })])
  assert.deepEqual(done.changes, [])
})

test('other tags on a post are kept, and the old status tag is swapped out', () => {
  const t = thread('[#4] Crash on open', { applied_tags: [id('Crash'), id('Planned')] })
  const { changes } = plan([t], [issue(4, { labels: label('fixed-in-main') })])
  assert.deepEqual(changes[0].tags.sort(), [id('Crash'), id('Fixed in main')].sort())
})

test('a closed issue with a release names it, pings once, then archives and locks, in that order', () => {
  const closed = issue(5, { state: 'closed', state_reason: 'completed', milestone: { title: '0.6.9-beta.2' } })
  const { changes } = plan([thread('[#5] Clock')], [closed])
  const [tag, note, lock] = changes[0].steps
  assert.deepEqual(tag.patch.applied_tags, [id('Fixed')])
  assert.match(note.say, /Folio 0\.6\.9-beta\.2 \(a beta\)/)
  assert.equal(note.ping, true)
  assert.deepEqual(lock.patch, { applied_tags: [id('Fixed')], archived: true, locked: true })
})

test('a stable release is not called a beta, and a close with no release pings nobody', () => {
  const stable = noteFor('fixed', { issue: issue(6, { milestone: { title: '0.6.8' } }), ownerId: 'u' })
  assert.match(stable.content, /Folio 0\.6\.8\./)
  assert.doesNotMatch(stable.content, /beta/)
  const none = noteFor('fixed', { issue: issue(6), ownerId: 'u' })
  assert.equal(none.ping, false)
  assert.doesNotMatch(none.content, /<@u>/)
})

test('a closed issue whose post is already tagged, archived and locked plans nothing', () => {
  const closed = issue(7, { state: 'closed', state_reason: 'completed' })
  const done = thread('[#7] Done', { applied_tags: [id('Fixed')], thread_metadata: { archived: true, locked: true } })
  assert.deepEqual(plan([done], [closed]).changes, [])
})

test('a closed issue with the tag already on only finishes the archive, with no second note', () => {
  const closed = issue(8, { state: 'closed', state_reason: 'completed' })
  const half = thread('[#8] Half', { applied_tags: [id('Fixed')] })
  const { changes } = plan([half], [closed])
  assert.equal(changes[0].steps.length, 1)
  assert.deepEqual(changes[0].steps[0].patch, { applied_tags: [id('Fixed')], archived: true, locked: true })
})

test('reopening an issue unlocks and unarchives its post and clears the closed tag', () => {
  const reopened = thread('[#9] Back', { applied_tags: [id('Fixed')], thread_metadata: { archived: true, locked: true } })
  const { changes } = plan([reopened], [issue(9)])
  assert.deepEqual(changes[0].steps, [{ patch: { applied_tags: [], archived: false, locked: false } }])
})

test('the ideas forum uses its own tag names, and a state it has no tag for adds none', () => {
  const idea = thread('[#10] Six rows', { parent_id: 'ideas' })
  const shipped = plan([idea], [issue(10, { state: 'closed', state_reason: 'completed' })]).changes[0]
  assert.equal(forums.ideas.tags.find((tag) => tag.id === shipped.tags[0]).name, 'Shipped')
  const ask = plan([thread('[#11] x', { parent_id: 'ideas' })], [issue(11, { labels: label('needs-info') })])
  assert.deepEqual(ask.changes, [])
})

test('a missing tag is reported and skipped, never invented', () => {
  const odd = { bugs: { kind: 'bug', tags: tags(['Crash']) } }
  const result = planChanges({ threads: [thread('[#12] x')], issues: new Map([[12, issue(12, { labels: label('planned') })]]), forums: odd })
  assert.deepEqual(result.changes, [])
  assert.match(result.problems[0], /no tag called "Planned"/)
})

test('an issue that was not in the list is asked for, and a pass changes at most five posts', () => {
  assert.deepEqual(plan([thread('[#99] unknown')], []).missing, [99])
  const many = Array.from({ length: 8 }, (_, i) => i + 100)
  const result = plan(many.map((n) => thread(`[#${n}] x`)), many.map((n) => issue(n, { labels: label('planned') })))
  assert.equal(result.changes.length, MAX_CHANGES)
  assert.equal(result.skipped, 3)
})

const env = { ISSUE_SYNC: 'dry', DISCORD_BOT_TOKEN: 'secret', GUILD_ID: 'g', BUG_FORUM: 'bugs', IDEAS_FORUM: 'ideas' }
function fakeFetch(calls) {
  return async (url, init = {}) => {
    calls.push({ url: String(url), method: init.method ?? 'GET' })
    const body = (data) => ({ ok: true, status: 200, json: async () => data })
    const u = String(url)
    if (u.endsWith('/threads/active')) return body({ threads: [thread('[#2] Badge'), thread('Untracked')] })
    if (u.includes('/threads/archived/public')) return body({ threads: [] })
    if (u.endsWith('/channels/bugs')) return body({ available_tags: BUG_TAGS })
    if (u.endsWith('/channels/ideas')) return body({ available_tags: [] })
    if (u.includes('/issues?state=open')) return body([issue(2, { labels: label('fixed-in-main') })])
    if (u.includes('/issues?state=closed')) return body([])
    return { ok: false, status: 404 }
  }
}

test('the pass does nothing unless ISSUE_SYNC is "dry", and "dry" only ever reads', async () => {
  const calls = []
  assert.equal(await runIssueSync({ ...env, ISSUE_SYNC: 'off' }, fakeFetch(calls)), null)
  assert.equal(await runIssueSync({ ...env, ISSUE_SYNC: undefined }, fakeFetch(calls)), null)
  assert.equal(calls.length, 0)
  const result = await runIssueSync(env, fakeFetch(calls))
  assert.equal(result.changes.length, 1)
  assert.ok(calls.length > 0 && calls.every((call) => call.method === 'GET'))
})

test('a failure in the pass is caught, not thrown', async () => {
  const result = await runIssueSync(env, async () => ({ ok: false, status: 500 }))
  assert.equal(result, null)
})
