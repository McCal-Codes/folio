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
 *   DISCORD_WEBHOOK_URL   required to actually send. Without it this prints and exits 0, so a fork never fails.
 *   DISCORD_ROLE_ID       optional. The role the channel made for Folio, pinged on the first line.
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

/** The line under the heading: the release's own one-sentence summary, if it wrote one. */
export function tagline(body = '') {
  const afterTitle = body.replace(/\r/g, '').replace(/^\s*#\s+[^\n]*\n+/, '')
  const first = afterTitle.split('\n').find((line) => line.trim() && !line.startsWith('#'))
  return first && !first.trim().startsWith('-') ? first.trim() : ''
}

/**
 * The headed lists of changes, with their bullets cut to the first sentence. A release page writes `## What's new`
 * and the changelog writes `### Added`, so both count; a heading with no bullets under it is skipped, which drops
 * the "taken from CHANGELOG.md" preamble along with it.
 */
export function sections(body = '') {
  const found = []
  for (const block of body.replace(/\r/g, '').split(/^#{2,3}\s+/m).slice(1)) {
    const name = block.slice(0, block.indexOf('\n')).trim()
    const bullets = block
      .split('\n')
      .filter((line) => line.startsWith('- '))
      .map((line) => shorten(line.slice(2).trim()))
      .filter(Boolean)
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

export function buildMessage({ release, roleId, site = SITE }) {
  const version = String(release.tag_name ?? '').replace(/^v/, '')
  const apk = (release.assets ?? []).find((asset) => asset.name?.endsWith('.apk'))
  const kind = kindOf(release.body)

  const head = [
    roleId ? `<@&${roleId}>` : '',
    // The version links to the release, the way the channel's other posts link their version line.
    `**[Folio Launcher ${version}](${release.html_url})**${kind ? ` · ${kind}` : ''}`,
    tagline(release.body),
  ].filter(Boolean)

  const links = [
    apk ? `[Download the APK](${apk.browser_download_url})` : `[The release](${release.html_url})`,
    `[How to install it](${site}/download/)`,
    `[Everything that changed](${site}/changelog/${version}/)`,
  ].join(' · ')
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
  if (trimmed) middle.push(`The rest is in [the full notes](${release.html_url}).`)

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

/** Discord takes 10 MB on a server with no boosts. Eight is the line where a slow connection still gets the post. */
const UPLOAD_LIMIT = 8 * 1024 * 1024

async function attach(message, wall) {
  if (!wall) return { body: JSON.stringify(message), headers: { 'content-type': 'application/json' } }
  if (wall.size > UPLOAD_LIMIT) {
    console.log(`${wall.name} is ${(wall.size / 1048576).toFixed(1)} MB, past the upload limit. Posting without it.`)
    return { body: JSON.stringify(message), headers: { 'content-type': 'application/json' } }
  }
  const response = await fetch(wall.browser_download_url)
  if (!response.ok) {
    console.log(`Could not read ${wall.name} (${response.status}). Posting without it.`)
    return { body: JSON.stringify(message), headers: { 'content-type': 'application/json' } }
  }
  const form = new FormData()
  form.append('payload_json', JSON.stringify(message))
  form.append('files[0]', new Blob([await response.arrayBuffer()], { type: wall.content_type }), wall.name)
  // No content-type header: fetch sets it with the multipart boundary, and setting it by hand breaks the upload.
  return { body: form, headers: {} }
}

/** One retry, and only on what Discord says is worth retrying. A release post is not worth a retry loop. */
async function post(webhook, message, wall) {
  const { body, headers } = await attach(message, wall)
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
  const eventPath = process.env.GITHUB_EVENT_PATH
  if (!eventPath) throw new Error('No GITHUB_EVENT_PATH; run this from the workflow, which writes one either way')
  const { release } = JSON.parse(readFileSync(eventPath, 'utf8'))
  if (!release) throw new Error('The event carries no release')

  if (release.draft) return console.log('Draft release, nothing posted.')
  if (release.prerelease && process.env.ANNOUNCE_PRERELEASES !== 'true') {
    return console.log(`${release.tag_name} is a pre-release and ANNOUNCE_PRERELEASES is not true. Nothing posted.`)
  }

  const message = buildMessage({ release, roleId: process.env.DISCORD_ROLE_ID?.trim() })
  const wall = wallOf(release)
  const webhook = process.env.DISCORD_WEBHOOK_URL?.trim()
  if (dryRun || !webhook) {
    console.log(dryRun ? 'Dry run. This is the message:' : 'No DISCORD_WEBHOOK_URL set, so nothing is sent:')
    console.log('-'.repeat(60))
    console.log(message.content)
    console.log('-'.repeat(60))
    console.log(`${message.content.length} of ${CONTENT_LIMIT} characters.`)
    return console.log(wall ? `With ${wall.name} attached.` : 'No feature wall on this release, so text only.')
  }

  const sent = await post(webhook, message, wall)
  console.log(`Posted ${release.tag_name}, message ${sent?.id ?? 'sent'}.`)
}

if (import.meta.url === `file://${process.argv[1]}`) await main()
