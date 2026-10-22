/**
 * /track, run by McCal inside a forum post. It links the post to a GitHub issue by putting the issue number at the start
 * of the post's title, which is all issuesync.mjs needs to keep the tags in step.
 *
 *   /track 433   links the post to issue #433
 *   /track       opens a new issue from the post, then links it
 *
 * The bot cannot read what anyone wrote in a post, so a new issue holds the post's title and a link back, and the
 * version, phone and steps stay in the post. The post's link is also how it avoids opening a second issue for the same post.
 */
import { REPO, applyChange, forMode, issueRef, planChanges, withIssueRef } from './issuesync.mjs'

/** Manage Threads, so the command only shows for people who can manage posts. The staff check below is the real lock. */
const MANAGE_THREADS = '17179869184'

export const TRACK_COMMAND = {
  name: 'track',
  description: 'Link this post to a GitHub issue, or open one from it',
  type: 1,
  integration_types: [0],
  contexts: [0],
  default_member_permissions: MANAGE_THREADS,
  options: [{ name: 'number', description: 'An existing issue number. Leave empty to open a new issue from this post.', type: 4, min_value: 1 }],
}

const linkTo = (guild, thread) => `https://discord.com/channels/${guild}/${thread}`

/** The text of the answer, which only the person who ran the command sees. */
export async function track(env, interaction, send = fetch) {
  const staff = String(env.STAFF_IDS || '').split(',').filter(Boolean)
  if (!staff.includes(interaction.member?.user?.id ?? interaction.user?.id)) return 'Only McCal can link posts to issues.'

  const kinds = { [env.BUG_FORUM]: 'bug', [env.IDEAS_FORUM]: 'idea' }
  delete kinds[undefined]
  const thread = interaction.channel
  const kind = kinds[thread?.parent_id]
  if (!kind) return 'Run this inside a post in the bug reports or feature ideas forum.'

  const github = (path, init = {}) =>
    send(`https://api.github.com/repos/${REPO}${path}`, {
      ...init,
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'folio-bot', ...(env.GITHUB_TOKEN ? { authorization: `Bearer ${env.GITHUB_TOKEN}` } : {}), ...init.headers },
    })
  const discord = (method, path, body) =>
    send(`https://discord.com/api/v10${path}`, {
      method,
      headers: { authorization: `Bot ${env.DISCORD_BOT_TOKEN}`, 'content-type': 'application/json', 'x-audit-log-reason': 'Folio /track' },
      body: body ? JSON.stringify(body) : undefined,
    })

  const asked = (interaction.data?.options ?? []).find((option) => option.name === 'number')?.value
  let issue
  let opened = false
  if (asked) {
    const response = await github(`/issues/${asked}`)
    if (!response.ok) return `I can't find issue #${asked} (GitHub said ${response.status}).`
    issue = await response.json()
    if (issue.pull_request) return `#${asked} is a pull request, not an issue.`
  } else {
    if (issueRef(thread.name) !== null) return `This post is already linked to #${issueRef(thread.name)}. Run /track with a number to change it.`
    if (!env.GITHUB_TOKEN) return 'Opening issues needs a GitHub token on the bot, and none is set yet. Run /track with an issue number instead.'
    const link = linkTo(env.GUILD_ID, thread.id)
    // Look for an issue that already points at this post, so a failed rename can never open a second one.
    const existing = await github('/issues?state=all&per_page=100')
    if (existing.ok) issue = (await existing.json()).find((candidate) => !candidate.pull_request && String(candidate.body ?? '').includes(link))
    if (!issue) {
      const created = await github('/issues', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          title: String(thread.name).slice(0, 200),
          body: `Reported on Discord: ${link}\n\nThe version, phone and steps are in that post.`,
          labels: [kind === 'bug' ? 'bug' : 'enhancement', 'from-discord'],
        }),
      })
      if (!created.ok) return `GitHub would not open the issue (it said ${created.status}). The post is unchanged.`
      issue = await created.json()
      opened = true
    }
  }

  const renamed = await discord('PATCH', `/channels/${thread.id}`, { name: withIssueRef(thread.name, issue.number) })
  if (!renamed.ok) return `${opened ? `Opened #${issue.number}, but ` : ''}I couldn't rename the post (Discord said ${renamed.status}). Add [#${issue.number}] to its title by hand.`

  // Put the tag on straight away when the sync is allowed to, instead of waiting for the next pass.
  let tagged = ''
  const mode = env.ISSUE_SYNC
  if (mode === 'tags' || mode === 'on') {
    try {
      const [channel, parent] = await Promise.all([discord('GET', `/channels/${thread.id}`).then((r) => r.json()), discord('GET', `/channels/${thread.parent_id}`).then((r) => r.json())])
      const forums = { [thread.parent_id]: { kind, tags: (parent.available_tags ?? []).map(({ id, name }) => ({ id, name })) } }
      const { changes } = planChanges({ threads: [channel], issues: new Map([[issue.number, issue]]), forums })
      for (const change of forMode(mode, changes)) await applyChange(change, discord)
      if (changes.length) tagged = ' Tags are up to date.'
    } catch (error) {
      console.error(`track: tagging failed: ${error.message}`)
    }
  }
  return `${opened ? 'Opened' : 'Linked this post to'} #${issue.number}: https://github.com/${REPO}/issues/${issue.number}.${tagged}`
}
