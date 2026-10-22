import { test } from 'node:test'
import assert from 'node:assert/strict'
import { commentFor, milestoneTitle, planStamp } from './stamp-issues.mjs'

const release = { tag_name: 'v0.6.9-beta.2', name: 'Folio 0.6.9 beta 2', html_url: 'https://example.invalid/r', prerelease: true }
const open = (number, extra = {}) => ({ number, state: 'open', ...extra })

test('the milestone is the tag without its v', () => {
  assert.equal(milestoneTitle(release), '0.6.9-beta.2')
  assert.equal(milestoneTitle({ tag_name: '0.6.8' }), '0.6.8')
})

test('open labelled issues are stamped, and a missing milestone is created', () => {
  const plan = planStamp({ release, issues: [open(1), open(2)], milestones: [] })
  assert.deepEqual(plan, { title: '0.6.9-beta.2', create: true, milestone: null, issues: [1, 2] })
})

test('an existing milestone is reused, and nothing is created when there is nothing to stamp', () => {
  const have = [{ title: '0.6.9-beta.2', number: 14 }]
  assert.equal(planStamp({ release, issues: [open(1)], milestones: have }).milestone, 14)
  assert.equal(planStamp({ release, issues: [open(1)], milestones: have }).create, false)
  assert.equal(planStamp({ release, issues: [], milestones: [] }).create, false)
})

test('pull requests, closed issues, issues already in a milestone, and drafts are left alone', () => {
  const issues = [open(1, { pull_request: {} }), open(2, { state: 'closed' }), open(3, { milestone: { title: '1.0' } }), open(4)]
  assert.deepEqual(planStamp({ release, issues, milestones: [] }).issues, [4])
  assert.deepEqual(planStamp({ release: { ...release, draft: true }, issues: [open(4)], milestones: [] }).issues, [])
})

test('the comment links to the release', () => {
  assert.equal(commentFor(release), 'Shipped in [Folio 0.6.9 beta 2](https://example.invalid/r).')
})
