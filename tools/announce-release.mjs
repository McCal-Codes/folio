/**
 * Posts a release to a Discord webhook, written for Mod My Android's #app-updates channel.
 *
 *   node tools/announce-release.mjs --dry-run    # print the message, send nothing
 *   node tools/announce-release.mjs             # read $GITHUB_EVENT_PATH and post it
 *
 * The shape follows what that channel already looks like rather than what a webhook can do. Niagara, Nova and Smart
 * Launcher all post a plain message: the app and version on the first line, a short account of each change, then a
 * download link. No embed cards, so this does not either.
 *
 * Environment:
 *   DISCORD_WEBHOOK_URL   required to actually send. One webhook, or several separated by commas: Folio's own
 *                         server should not hear about a release after everyone else. Without it this prints and
 *                         exits 0, so a fork never fails.
 *   DISCORD_ROLE_ID       optional. The role to ping on the first line, one per webhook in the same order, since a
 *                         role only exists in its own server. Leave a position empty for no ping there:
 *                         ",1553093586705449062" pings nobody in the first server and that role in the second.
 *   ANNOUNCE_PRERELEASES  "true" to post betas as well. Off by default: a beta a week is how a channel gets muted.
 *
 * No APK is attached. Discord's upload limit is below a release build, and the file should come from GitHub, where
 * its checksum and signing certificate sit beside it.
 */
import { readFileSync } from 'node:fs'

const SITE = 'https://foliolauncher.com'
/** Discord's hard limit on message content. Everything below budgets against it. */
const CONTENT_LIMIT = 2000
/** SUPPRESS_EMBEDS. Three links would otherwise each try to unfurl a preview card under the message. */
const SUPPRESS_EMBEDS = 4
/** One bullet, cut to its point. The channel's other posts are a sentence a change, not a paragraph. */
const BULLET_LIMIT = 190
/**
 * How much of a release goes in the message. Discord allows 2000 characters, but the posts already in that channel
 * are a few lines: filling the limit is how a release reads as noise. The rest is one tap away in the full notes.
 */
const MAX_SECTIONS = 2
const MAX_BULLETS = 5
const TARGET = 1200

/**
 * What kind of release this is, from what is in it rather than from the numbers. Folio is 0.x, where a patch bump
 * carried the whole Market: semver would have called that a minor update in a channel full of launcher updates.
 */
export function kindOf(body = '') {
  const named = sections(body)
  const has = (word) => named.some((section) => new RegExp(word, 'i').test(section.name))
  const added = named.find((section) => /added|new/i.test(section.name))
  if (added && added.bullets.length >= 3) return 'Feature update'
  if (added) return 'Update'
  if (has('fixed') || has('fix')) return 'Fix release'
  return 'Update'
}

/** The most the line under the heading may run to, so a paragraph with no full stop cannot push the links off the post. */
const TAGLINE_LIMIT = 220

/**
 * Links into the private supporters' repo (folio-beta) cannot be opened by anyone else, and a copied sentence or
 * bullet can carry one. A Markdown link keeps its words and loses its address; a bare address or `<address>` goes.
 */
export function stripPrivateLinks(text = '') {
  return text
    .replace(/\[([^\]]*)\]\(\s*<?https?:\/\/github\.com\/[^\s)>]*folio-beta[^\s)>]*>?\s*\)/gi, '$1')
    .replace(/<https?:\/\/github\.com\/[^\s>]*folio-beta[^\s>]*>/gi, '')
    .replace(/https?:\/\/github\.com\/[^\s)>\]]*folio-beta[^\s)>\]]*/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

const ABBREVIATIONS = /(?:\be\.g|\bi\.e|\betc|\bvs|\bapprox|\bno|\bca|\bcf)\.$/i

/**
 * The first sentence of a paragraph, however short: "Small fix." is a sentence. A full stop that ends an abbreviation
 * ("e.g.") or sits inside a number ("0.6.9") is not the end. With no end in sight the paragraph is cut at a word,
 * so the result always fits.
 */
export function firstSentence(text, limit = TAGLINE_LIMIT) {
  const ends = /[.!?](?=\s|$)/g
  for (let hit = ends.exec(text); hit; hit = ends.exec(text)) {
    const candidate = text.slice(0, hit.index + 1)
    if (ABBREVIATIONS.test(candidate)) continue
    if (candidate.length <= limit) return candidate
    break
  }
  if (text.length <= limit) return text
  const cut = text.slice(0, limit - 1)
  return `${cut.slice(0, Math.max(cut.lastIndexOf(' '), limit / 2)).trimEnd()}…`
}

/**
 * The line under the heading: the first sentence of the release's opening paragraph, if it wrote one. A paragraph
 * wraps across lines in the file, so it is joined first; cutting at the first physical line left sentences hanging.
 */
export function tagline(body = '') {
  // A release that heads its list with a bold "Folio 0.6.8: Your language, your Home, and Smooth" has already said it in
  // one line; that beats a personal opening paragraph, which is a greeting rather than a summary.
  const headline = /^\*\*Folio [^:*\n]+:\s*([^*\n]+?)\*\*[ \t]*$/m.exec(body.replace(/\r/g, ''))
  if (headline) return firstSentence(stripPrivateLinks(headline[1].trim()))
  const afterTitle = body.replace(/\r/g, '').replace(/^\s*#\s+[^\n]*\n+/, '')
  const paragraph = afterTitle.split(/\n\s*\n/).find((block) => block.trim())
  if (!paragraph || paragraph.trim().startsWith('#') || paragraph.trim().startsWith('-')) return ''
  const text = stripPrivateLinks(paragraph.replace(/\s+/g, ' ').trim())
  return firstSentence(text)
}

/**
 * The headed lists of changes, with their bullets cut to the first sentence. A release page writes `## What's new`
 * and the changelog writes `### Added`, so both count; a heading with no bullets under it is skipped, which drops
 * the "taken from CHANGELOG.md" preamble along with it.
 */
export function sections(body = '') {
  const found = []
  // Some notes head their list with a bold line instead of a "##": treat that line as a heading too.
  const headed = body.replace(/\r/g, '').replace(/^\*\*([^*\n]+)\*\*[ \t]*$/gm, '## $1')
  for (const block of headed.split(/^#{2,3}\s+/m).slice(1)) {
    const name = block.slice(0, block.indexOf('\n')).trim()
    // How to check the download is not a change, even though it is a bulleted list.
    if (/^download/i.test(name)) continue
    // A bullet wraps onto the lines under it until a blank line or the next bullet, so those lines belong to it.
    const joined = []
    for (const line of block.split('\n').slice(1)) {
      if (line.startsWith('- ')) joined.push(line.slice(2))
      else if (joined.length && joined[joined.length - 1] !== null && line.trim() && !line.startsWith('#')) joined[joined.length - 1] += ` ${line.trim()}`
      else if (!line.trim()) joined.push(null)
    }
    const bullets = joined.filter(Boolean).map((line) => shorten(stripPrivateLinks(line.trim()))).filter(Boolean)
    if (bullets.length) found.push({ name, bullets })
  }
  return found
}

/** A bullet's first sentence, keeping its bold lead. Links and code survive; the rest is on the release page. */
export function shorten(text, limit = BULLET_LIMIT) {
  const oneLine = text.replace(/\s+/g, ' ').trim()
  if (oneLine.length <= limit) return oneLine
  const sentence = /^(.{40,}?[.!?])\s/.exec(oneLine)
  const cut = sentence && sentence[1].length <= limit ? sentence[1] : `${oneLine.slice(0, limit - 1).trimEnd()}…`
  return cut
}

/**
 * Words that must never reach the channel: tooling, drafting and authorship notes belong in the repo, not in a post
 * to the community. The list is data so the tests can walk it without spelling any of it out.
 */
export const BLOCKED_TERMS = [
  'claude',
  'anthropic',
  'chatgpt',
  'openai',
  'copilot',
  'gemini',
  'llm',
  'a\\.?i',
  'artificial intelligence',
  'generated',
  'co-authored',
  'draft',
  'rel-\\d+',
]

/** HTML a release's notes may use, which is markup and not a placeholder. */
const HTML_TAGS = 'br|hr|b|i|u|s|em|strong|code|kbd|p|a|img|sup|sub|small|details|summary|ul|ol|li|blockquote|pre|h[1-6]'

/** What is wrong with a finished message, if anything. A post with a problem is never sent, not even in a dry run. */
export function problemsWith(content = '') {
  const found = []
  for (const term of BLOCKED_TERMS) {
    const hit = new RegExp(`(?<![\\w-])${term}(?![\\w-])`, 'i').exec(content)
    if (hit) found.push(`mentions "${hit[0]}"`)
  }
  // A <name> left in from a template. Not a placeholder: a Discord mention (<@...>, <#...>), an emoji (<:x:>, <a:x:>),
  // a link in angle brackets (any <scheme:...>, such as <https://x.co> or <mailto:a@b.c>), or an ordinary HTML tag.
  const placeholder = new RegExp(`<(?![@#:!]|[A-Za-z][A-Za-z0-9+.-]*:|(?:${HTML_TAGS})(?:[\\s/>]))[A-Za-z][^>\\n]{0,40}>`).exec(content)
  if (placeholder) found.push(`has a placeholder, ${placeholder[0]}`)
  const privateLink = /https?:\/\/github\.com\/[^\s)>\]]*folio-beta[^\s)>\]]*/i.exec(content)
  if (privateLink) found.push(`links to the private beta repo, ${privateLink[0]}`)
  return found
}

/** A pre-release, or anything published in the private beta repo. */
export function isBeta(release) {
  return release.prerelease === true || /\/folio-beta\//.test(release.html_url ?? '')
}

export function buildMessage({ release, roleId, site = SITE }) {
  const version = String(release.tag_name ?? '').replace(/^v/, '')
  // A beta is published in the private supporters' repo, so its release page and APK are links nobody else can open,
  // and the site builds no changelog page for it. It links only to pages the public can reach.
  const beta = isBeta(release)
  const apk = beta ? undefined : (release.assets ?? []).find((asset) => asset.name?.endsWith('.apk'))
  const kind = kindOf(release.body)

  const head = [
    roleId ? `<@&${roleId}>` : '',
    // The version links to the release, the way the channel's other posts link their version line.
    `**[Folio Launcher ${version}](${beta ? `${site}/download/` : release.html_url})**${kind ? ` · ${kind}` : ''}`,
    tagline(release.body),
  ].filter(Boolean)

  const links = (beta
    ? [`[How to install it](${site}/download/)`, `[What is coming](${site}/roadmap/)`]
    : [
        apk ? `[Download the APK](${apk.browser_download_url})` : `[The release](${release.html_url})`,
        `[How to install it](${site}/download/)`,
        `[Everything that changed](${site}/changelog/${version}/)`,
      ]
  ).join(' · ')
  const size = apk ? `${(apk.size / 1048576).toFixed(1)} MB` : ''
  const tail = `${links}\n${[size, 'Android 12 and up', 'free and open source, no ads'].filter(Boolean).join(' · ')}`

  // The middle is what gets cut, never the heading or the links: someone skimming needs the version and the file.
  const all = sections(release.body)
  let budget = Math.min(TARGET, CONTENT_LIMIT - head.join('\n').length - tail.length - 8)
  const middle = []
  let used = 0
  let trimmed = all.length > MAX_SECTIONS
  const lone = all.length === 1
  for (const section of all.slice(0, MAX_SECTIONS)) {
    const heading = lone ? '' : `**${section.name}**`
    const lines = []
    for (const bullet of section.bullets) {
      const line = `- ${bullet}`
      if (used >= MAX_BULLETS || heading.length + lines.join('\n').length + line.length + 4 > budget) {
        trimmed = true
        break
      }
      lines.push(line)
      used += 1
    }
    if (!lines.length) { trimmed = true; break }
    const block = [heading, lines.join('\n')].filter(Boolean).join('\n')
    budget -= block.length + 2
    middle.push(block)
  }
  if (trimmed && !beta) middle.push(`The rest is in [the full notes](${release.html_url}).`)

  const content = [head.join('\n'), middle.join('\n\n'), tail].filter(Boolean).join('\n\n').trim()
  const message = {
    content: content.slice(0, CONTENT_LIMIT),
    flags: SUPPRESS_EMBEDS,
    allowed_mentions: roleId ? { parse: [], roles: [roleId] } : { parse: [] },
  }
  return message
}

/**
 * The release's own feature wall, if it was attached to the release. Uploading it beats linking it: a link would
 * need the preview cards turned back on, and then all three links unfurl.
 *
 * It has to come from the release itself rather than from anywhere else, which is REL-30: a published picture comes
 * from the build being released. Attach the wall as a release asset and it goes out with the post; do not, and the
 * post is text, which is what the channel mostly is anyway.
 */
export function wallOf(release) {
  return (release.assets ?? []).find(
    (asset) => /\.(png|jpe?g|webp)$/i.test(asset.name ?? '') && /wall|feature|banner|hero/i.test(asset.name ?? ''),
  )
}

/**
 * One webhook or a comma-separated list of them, so a release can reach Folio's own server and MMD's channel in the
 * same job. Blanks and stray whitespace are dropped, because a list edited in a settings box usually has both.
 */
export function splitWebhooks(value = '') {
  return String(value ?? '')
    .split(',')
    .map((one) => one.trim())
    .filter(Boolean)
}

/**
 * The role for each webhook, by position. Unlike the webhooks, blanks are kept: an empty position is a deliberate
 * "no ping in that server", and dropping it would shift every later role onto the wrong server.
 */
export function rolesFor(webhooks, value = '') {
  const roles = String(value ?? '').split(',').map((one) => one.trim())
  return webhooks.map((_, index) => roles[index] ?? '')
}

/** Discord takes 10 MB on a server with no boosts. Eight is the line where a slow connection still gets the post. */
const UPLOAD_LIMIT = 8 * 1024 * 1024

/** The wall's bytes, read once however many servers the post goes to. Null means the post goes out as text. */
async function loadWall(wall) {
  if (!wall) return null
  if (wall.size > UPLOAD_LIMIT) {
    console.log(`${wall.name} is ${(wall.size / 1048576).toFixed(1)} MB, past the upload limit. Posting without it.`)
    return null
  }
  const response = await fetch(wall.browser_download_url)
  if (!response.ok) {
    console.log(`Could not read ${wall.name} (${response.status}). Posting without it.`)
    return null
  }
  return { bytes: await response.arrayBuffer(), type: wall.content_type, name: wall.name }
}

function bodyFor(message, loaded) {
  if (!loaded) return { body: JSON.stringify(message), headers: { 'content-type': 'application/json' } }
  const form = new FormData()
  form.append('payload_json', JSON.stringify(message))
  form.append('files[0]', new Blob([loaded.bytes], { type: loaded.type }), loaded.name)
  // No content-type header: fetch sets it with the multipart boundary, and setting it by hand breaks the upload.
  return { body: form, headers: {} }
}

/** One retry, and only on what Discord says is worth retrying. A release post is not worth a retry loop. */
async function post(webhook, message, loaded) {
  const { body, headers } = bodyFor(message, loaded)
  for (const attempt of [1, 2]) {
    const response = await fetch(`${webhook}?wait=true`, { method: 'POST', headers, body })
    if (response.ok) return response.json()
    const text = await response.text()
    const retryable = [429, 500, 502, 503, 504].includes(response.status)
    if (!retryable || attempt === 2) throw new Error(`Discord said ${response.status}: ${text.slice(0, 300)}`)
    // 429 carries retry_after in seconds; anything else waits a moment and tries once more.
    const wait = Number(JSON.parse(text || '{}').retry_after ?? 2)
    console.log(`Discord said ${response.status}. Waiting ${wait}s and trying once more.`)
    await new Promise((resolve) => setTimeout(resolve, Math.min(wait, 30) * 1000))
  }
}

async function main() {
  const dryRun = process.argv.includes('--dry-run')
  // A manual run has no release in GitHub's own event file, and a step cannot change GITHUB_EVENT_PATH, so the
  // workflow fetches the release itself and names the file in RELEASE_FILE. An automatic run reads the event.
  const releaseFile = process.env.RELEASE_FILE
  const eventPath = process.env.GITHUB_EVENT_PATH
  if (!releaseFile && !eventPath) throw new Error('No RELEASE_FILE or GITHUB_EVENT_PATH; run this from the workflow')
  const parsed = JSON.parse(readFileSync(releaseFile || eventPath, 'utf8'))
  const release = releaseFile ? parsed : parsed.release
  if (!release) throw new Error('The event carries no release')

  if (release.draft) return console.log('Draft release, nothing posted.')
  if (release.prerelease && process.env.ANNOUNCE_PRERELEASES !== 'true') {
    return console.log(`${release.tag_name} is a pre-release and ANNOUNCE_PRERELEASES is not true. Nothing posted.`)
  }

  const wall = wallOf(release)
  const webhooks = splitWebhooks(process.env.DISCORD_WEBHOOK_URL)
  const roles = rolesFor(webhooks, process.env.DISCORD_ROLE_ID)
  if (dryRun || !webhooks.length) {
    const message = buildMessage({ release, roleId: roles[0] || process.env.DISCORD_ROLE_ID?.split(',')[0]?.trim() })
    const problems = problemsWith(message.content)
    if (problems.length) throw new Error(`Not posting: the message ${problems.join(' and ')}.`)
    console.log(dryRun ? 'Dry run. This is the message:' : 'No DISCORD_WEBHOOK_URL set, so nothing is sent:')
    console.log('-'.repeat(60))
    console.log(message.content)
    console.log('-'.repeat(60))
    console.log(`${message.content.length} of ${CONTENT_LIMIT} characters.`)
    return console.log(wall ? `With ${wall.name} attached.` : 'No feature wall on this release, so text only.')
  }

  // Each webhook gets its own attempt. One channel refusing a post is not a reason for the others to miss it, so
  // a failure is reported at the end rather than thrown in the middle.
  // Checked before anything goes out: one bad message means no server gets a post.
  for (const [index] of webhooks.entries()) {
    const problems = problemsWith(buildMessage({ release, roleId: roles[index] }).content)
    if (problems.length) throw new Error(`Not posting: the message ${problems.join(' and ')}.`)
  }
  const loaded = await loadWall(wall)
  const failures = []
  for (const [index, one] of webhooks.entries()) {
    const label = webhooks.length > 1 ? `webhook ${index + 1} of ${webhooks.length}` : 'the webhook'
    // Each server gets its own message, because each server has its own role or none.
    const message = buildMessage({ release, roleId: roles[index] })
    try {
      const sent = await post(one, message, loaded)
      console.log(`Posted ${release.tag_name} to ${label}, message ${sent?.id ?? 'sent'}.`)
    } catch (error) {
      console.error(`Could not post to ${label}: ${error.message}`)
      failures.push(label)
    }
  }
  if (failures.length) throw new Error(`${failures.length} of ${webhooks.length} webhooks did not take the post.`)
}

if (import.meta.url === `file://${process.argv[1]}`) await main()
