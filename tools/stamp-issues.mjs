/**
 * When a release is published, marks the issues it carries as shipped: every open issue labelled `fixed-in-main` gets the
 * release as its milestone and is closed as completed, with a one-line comment.
 *
 *   node tools/stamp-issues.mjs --dry-run     # print what it would do, change nothing
 *   node tools/stamp-issues.mjs               # do it
 *
 * Mr Folio (tools/folio-bot/issuesync.mjs) reads the milestone to tell the person who reported it, in the Discord post,
 * which release or beta fixed it. This is the one moment that knows: a `fixed-in-main` label means "fixed on main",
 * and the release is what carries main out.
 *
 * Environment: GH_TOKEN (the workflow's own token, with issues: write), GITHUB_REPOSITORY, and RELEASE_FILE, the
 * release JSON the workflow fetched. A pre-release is a beta and stamps the same way.
 */
import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export const LABEL = 'fixed-in-main'

/** The milestone title for a release: its tag without a leading v, so it reads "0.6.9-beta.2". */
export const milestoneTitle = (release) => String(release.tag_name ?? '').replace(/^v/, '')

/**
 * What to do, given the release, the open issues carrying the label and the milestones that exist. Pure.
 * Pull requests are skipped (GitHub lists them with issues), and so is anything already in a milestone, which means
 * a maintainer put it somewhere on purpose.
 */
export function planStamp({ release, issues, milestones }) {
  const title = milestoneTitle(release)
  if (!title || release.draft) return { title, create: false, issues: [] }
  const existing = milestones.find((milestone) => milestone.title === title)
  const todo = issues.filter((issue) => !issue.pull_request && issue.state === 'open' && !issue.milestone)
  return { title, create: !existing && todo.length > 0, milestone: existing?.number ?? null, issues: todo.map((issue) => issue.number) }
}

export function commentFor(release) {
  return `Shipped in [${release.name || release.tag_name}](${release.html_url}).`
}

async function main() {
  const dryRun = process.argv.includes('--dry-run')
  const repo = process.env.GITHUB_REPOSITORY
  const token = process.env.GH_TOKEN
  if (!repo || !process.env.RELEASE_FILE) throw new Error('Run this from the workflow: GITHUB_REPOSITORY and RELEASE_FILE are needed.')
  if (!token && !dryRun) throw new Error('GH_TOKEN is needed to change issues.')
  const release = JSON.parse(readFileSync(process.env.RELEASE_FILE, 'utf8'))
  const api = async (method, path, body) => {
    const response = await fetch(`https://api.github.com/repos/${repo}${path}`, {
      method,
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'folio-stamp-issues', ...(token ? { authorization: `Bearer ${token}` } : {}), 'content-type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined,
    })
    if (!response.ok) throw new Error(`${method} ${path} said ${response.status}`)
    return response.status === 204 ? null : response.json()
  }
  const issues = await api('GET', `/issues?labels=${LABEL}&state=open&per_page=100`)
  const milestones = await api('GET', '/milestones?state=all&per_page=100')
  const plan = planStamp({ release, issues, milestones })
  if (!plan.issues.length) return console.log(`No open "${LABEL}" issues to stamp for ${plan.title || 'this release'}.`)
  console.log(`${dryRun ? 'Would stamp' : 'Stamping'} ${plan.issues.map((n) => `#${n}`).join(', ')} as shipped in ${plan.title}${plan.create ? ' (new milestone)' : ''}.`)
  if (dryRun) return
  const milestone = plan.create ? (await api('POST', '/milestones', { title: plan.title })).number : plan.milestone
  for (const number of plan.issues) {
    await api('POST', `/issues/${number}/comments`, { body: commentFor(release) })
    await api('PATCH', `/issues/${number}`, { milestone, state: 'closed', state_reason: 'completed' })
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message)
    process.exit(1)
  })
}
