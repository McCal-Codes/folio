/**
 * Keeps the forum posts in #bug-reports and #feature-ideas in step with their GitHub issues.
 *
 * A post is tracked when its title starts with an issue number, like "[#433] Dock handle rarely works". McCal sets that
 * with /track; posts without one are never touched. Each pass works out the tag every tracked post should carry from
 * its issue's labels and state, and plans only the changes that differ. The tag is the record of what was already done,
 * so nothing is stored and a missed pass is repaired by the next.
 *
 * planChanges() is pure: it decides, and sends nothing. runIssueSync() reads GitHub and Discord and acts on the plan
 * according to ISSUE_SYNC: "off" does nothing, "dry" only logs it, "tags" applies only the plain tag changes on open
 * issues, and "on" does everything (notes, archive and lock, reopen). "tags" skips closed issues and needs-info on
 * purpose: the tag is the record of what was done, so putting it on without the note would lose the note for good.
 */

export const REPO = 'McCal-Codes/folio'
/** Most posts changed in one pass, in case a bad read makes everything look different. */
export const MAX_CHANGES = 5
/** Discord lets a forum post carry five tags. */
const MAX_TAGS = 5

/** The issue number at the start of a post title, or null. */
export function issueRef(title) {
  const match = /^\s*\[#(\d+)\]/.exec(String(title ?? ''))
  return match ? Number(match[1]) : null
}

/** The title with its issue number set to `number`, cut to Discord's 100 characters. */
export function withIssueRef(title, number) {
  const bare = String(title ?? '').replace(/^\s*\[#\d+\]\s*/, '').trim()
  return `[#${number}] ${bare}`.slice(0, 100)
}

/**
 * The status tag each forum uses for each state, by name. Names are looked up in the forum's own tags every pass, so a
 * tag renamed or missing is skipped and logged, never guessed. null means that state has no tag in that forum.
 */
export const STATUS_TAGS = {
  bug: { needsInfo: 'Needs info', planned: 'Planned', inMain: 'Fixed in main', fixed: 'Fixed', notPlanned: 'Not planned', duplicate: 'Duplicate' },
  idea: { needsInfo: null, planned: 'Planned', inMain: 'In main', fixed: 'Shipped', notPlanned: 'Not planned', duplicate: null },
}

const hasLabel = (issue, name) => (issue.labels ?? []).some((label) => (label.name ?? label) === name)

/** Where an issue stands: one of the STATUS_TAGS keys, or null for plain open. */
export function statusFor(issue) {
  if (issue.state === 'closed') {
    if (hasLabel(issue, 'duplicate')) return 'duplicate'
    if (hasLabel(issue, 'wontfix') || issue.state_reason === 'not_planned') return 'notPlanned'
    return 'fixed'
  }
  if (hasLabel(issue, 'needs-info')) return 'needsInfo'
  if (hasLabel(issue, 'fixed-in-main')) return 'inMain'
  if (hasLabel(issue, 'planned')) return 'planned'
  return null
}

const PRERELEASE = /alpha|beta|rc|dev/i
const releaseName = (milestone) => (milestone ? `Folio ${milestone}${PRERELEASE.test(milestone) ? ' (a beta)' : ''}` : null)

/**
 * What Mr Folio says in the post when its status changes. DRAFT WORDING: McCal rewrites these before the sync goes live.
 * Returns null when a state needs no note. `ping` is whether the person who posted is mentioned.
 */
export function noteFor(status, { issue, ownerId }) {
  const link = `https://github.com/${REPO}/issues/${issue.number}`
  const release = releaseName(issue.milestone?.title)
  const mention = ownerId ? `<@${ownerId}> ` : ''
  switch (status) {
    case 'needsInfo':
      return { ping: true, content: `${mention}McCal needs a bit more to look into this. Please add what he asked for here or on ${link}.` }
    case 'fixed':
      if (release) {
        return { ping: true, content: `${mention}This is fixed in ${release}. Update Folio to get it. I'm locking this post now; if it still happens after updating, please open a new one. Tracked as ${link}.` }
      }
      return { ping: false, content: `This was closed on GitHub as done (${link}). I'm locking this post; if it still happens, please open a new one.` }
    case 'notPlanned':
      return { ping: false, content: `McCal closed this as not planned (${link}). I'm locking this post.` }
    case 'duplicate':
      return { ping: false, content: `This was closed as a duplicate of another report (${link}). I'm locking this post.` }
    default:
      return null
  }
}

/**
 * What to change, as a list of planned changes (at most MAX_CHANGES), plus the issue numbers that were referenced but not in
 * `issues` so the caller can fetch them and try again.
 *
 * threads: Discord thread objects (applied_tags, thread_metadata.archived/locked, owner_id, parent_id, name).
 * issues:  Map of number -> GitHub issue (state, state_reason, labels, milestone, number).
 * forums:  { forumId: { kind: 'bug' | 'idea', tags: [{ id, name }] } }.
 *
 * Each change is { thread, issue, status, tags, steps, why }. A step is { patch } (a body for PATCH /channels/{thread}) or
 * { say, ping }. For a closed issue the order is: tag, note, then archive and lock, because posting in a locked post fails.
 */
export function planChanges({ threads, issues, forums }) {
  const changes = []
  const missing = new Set()
  const problems = []
  for (const thread of threads) {
    const forum = forums[thread.parent_id]
    const number = issueRef(thread.name)
    if (!forum || number === null) continue
    const issue = issues.get(number)
    if (!issue) {
      missing.add(number)
      continue
    }
    const names = STATUS_TAGS[forum.kind]
    const statusIds = new Set(forum.tags.filter((tag) => Object.values(names).includes(tag.name)).map((tag) => tag.id))
    const status = statusFor(issue)
    const wantName = status ? names[status] : null
    const want = wantName ? forum.tags.find((tag) => tag.name === wantName) : null
    if (wantName && !want) {
      problems.push(`#${number}: the forum has no tag called "${wantName}"`)
      continue
    }
    const applied = thread.applied_tags ?? []
    const tags = [...applied.filter((id) => !statusIds.has(id)), ...(want ? [want.id] : [])]
    if (tags.length > MAX_TAGS) {
      problems.push(`#${number}: no room for the "${wantName}" tag (${MAX_TAGS} tags already)`)
      continue
    }
    const tagChanged = tags.length !== applied.length || tags.some((id) => !applied.includes(id))
    const archived = Boolean(thread.thread_metadata?.archived)
    const locked = Boolean(thread.thread_metadata?.locked)
    const steps = []
    let why = ''
    if (issue.state === 'closed') {
      if (tagChanged) {
        steps.push({ patch: { applied_tags: tags, archived, locked } })
        const note = noteFor(status, { issue, ownerId: thread.owner_id })
        if (note) steps.push({ say: note.content, ping: note.ping })
      }
      if (!archived || !locked || tagChanged) steps.push({ patch: { applied_tags: tags, archived: true, locked: true } })
      why = tagChanged ? `closed, now tagged ${wantName ?? 'none'}` : 'closed, finishing the archive and lock'
    } else {
      if (archived || locked) {
        steps.push({ patch: { applied_tags: tags, archived: false, locked: false } })
        why = 'reopened on GitHub'
      } else if (tagChanged) {
        steps.push({ patch: { applied_tags: tags } })
        why = `now tagged ${wantName ?? 'none'}`
      }
      const note = tagChanged && !archived && !locked ? noteFor(status, { issue, ownerId: thread.owner_id }) : null
      if (note && status === 'needsInfo') steps.push({ say: note.content, ping: note.ping })
    }
    if (steps.length) changes.push({ thread, issue, status, tags, steps, why })
  }
  return { changes: changes.slice(0, MAX_CHANGES), skipped: Math.max(0, changes.length - MAX_CHANGES), missing: [...missing], problems }
}

/** One line per planned change, for the log. */
export function describe(change) {
  const said = change.steps.filter((step) => step.say).length
  const patches = change.steps.filter((step) => step.patch).length
  return `#${change.issue.number} "${change.thread.name}": ${change.why} (${patches} edit${patches === 1 ? '' : 's'}${said ? `, ${said} note` : ''})`
}

/** Tags that can be applied on their own, without a note or an archive following. */
const TAG_ONLY = new Set([null, 'planned', 'inMain'])

/** The changes a mode is allowed to carry out. "dry" and "off" carry out none. */
export function forMode(mode, changes) {
  if (mode === 'on') return changes
  if (mode !== 'tags') return []
  return changes.filter(
    (change) => change.issue.state === 'open' && TAG_ONLY.has(change.status) && change.steps.every((step) => step.patch && !('archived' in step.patch)),
  )
}

const SUPPRESS_EMBEDS = 4

/** Carries out one change in order and stops at the first step Discord refuses. Returns whether all of it went through. */
export async function applyChange(change, discordCall) {
  for (const step of change.steps) {
    const response = step.patch
      ? await discordCall('PATCH', `/channels/${change.thread.id}`, step.patch)
      : await discordCall('POST', `/channels/${change.thread.id}/messages`, {
          content: step.say,
          flags: SUPPRESS_EMBEDS,
          allowed_mentions: step.ping && change.thread.owner_id ? { users: [change.thread.owner_id] } : { parse: [] },
        })
    if (!response.ok) {
      console.error(`issue sync: #${change.issue.number} stopped, Discord said ${response.status}`)
      return false
    }
  }
  return true
}

const asJson = (response) => (response.ok ? response.json() : Promise.reject(new Error(`${response.url ?? 'request'} said ${response.status}`)))

/**
 * One pass. "dry" reads everything and logs what it would change, "tags" and "on" also carry it out (see the top of the
 * file), anything else does nothing at all. Returns the plan so the tests, and the log, can see it. Every failure is caught and logged, never thrown.
 */
export async function runIssueSync(env, send = fetch) {
  const mode = env.ISSUE_SYNC
  if (!['dry', 'tags', 'on'].includes(mode) || !env.DISCORD_BOT_TOKEN) return null
  const github = (path) =>
    send(`https://api.github.com/repos/${REPO}${path}`, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'folio-bot', ...(env.GITHUB_TOKEN ? { authorization: `Bearer ${env.GITHUB_TOKEN}` } : {}) },
    }).then(asJson)
  const discord = (path) => send(`https://discord.com/api/v10${path}`, { headers: { authorization: `Bot ${env.DISCORD_BOT_TOKEN}` } }).then(asJson)
  const discordCall = (method, path, body) =>
    send(`https://discord.com/api/v10${path}`, {
      method,
      headers: { authorization: `Bot ${env.DISCORD_BOT_TOKEN}`, 'content-type': 'application/json', 'x-audit-log-reason': 'Folio issue sync' },
      body: JSON.stringify(body),
    })
  try {
    const kinds = { [env.BUG_FORUM]: 'bug', [env.IDEAS_FORUM]: 'idea' }
    delete kinds[undefined]
    if (!Object.keys(kinds).length) return null
    const forums = {}
    const threads = (await discord(`/guilds/${env.GUILD_ID}/threads/active`)).threads ?? []
    for (const id of Object.keys(kinds)) {
      const channel = await discord(`/channels/${id}`)
      forums[id] = { kind: kinds[id], tags: (channel.available_tags ?? []).map(({ id: tag, name }) => ({ id: tag, name })) }
      threads.push(...((await discord(`/channels/${id}/threads/archived/public?limit=25`)).threads ?? []))
    }
    const tracked = threads.filter((thread) => forums[thread.parent_id] && issueRef(thread.name) !== null)
    if (!tracked.length) return { changes: [], skipped: 0, missing: [], problems: [] }
    const issues = new Map()
    const keep = (list) => list.filter((issue) => !issue.pull_request).forEach((issue) => issues.set(issue.number, issue))
    keep(await github('/issues?state=open&per_page=100'))
    keep(await github('/issues?state=closed&sort=updated&per_page=50'))
    let plan = planChanges({ threads: tracked, issues, forums })
    for (const number of plan.missing.slice(0, MAX_CHANGES)) keep([await github(`/issues/${number}`).catch(() => ({ pull_request: true }))])
    if (plan.missing.length) plan = planChanges({ threads: tracked, issues, forums })
    for (const change of plan.changes) console.log(`issue sync (${mode}): ${describe(change)}`)
    for (const problem of plan.problems) console.error(`issue sync: ${problem}`)
    if (plan.skipped) console.log(`issue sync (${mode}): ${plan.skipped} more change(s) wait for the next pass`)
    for (const change of forMode(mode, plan.changes)) await applyChange(change, discordCall)
    return plan
  } catch (error) {
    console.error(`Issue sync pass failed: ${error.message}`)
    return null
  }
}
