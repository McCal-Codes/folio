/**
 * Posts a release to a Discord webhook. Written for Mod My Android's #app-updates channel, which takes an automated
 * post per release rather than a person pasting one.
 *
 *   node tools/announce-release.mjs --dry-run            # print the payload, send nothing
 *   node tools/announce-release.mjs                      # read the release from $GITHUB_EVENT_PATH and post it
 *
 * Environment:
 *   DISCORD_WEBHOOK_URL   required to actually send. Without it this prints and exits 0, so a fork never fails.
 *   DISCORD_ROLE_ID       optional. The role to ping, if the server made one for Folio.
 *   ANNOUNCE_PRERELEASES  "true" to post betas as well. Off by default: a beta a week is how a channel gets muted.
 *
 * No APK is attached. Discord caps uploads well below a release build, and the file should come from GitHub anyway,
 * where its checksum and signing certificate sit beside it.
 */
import { readFileSync } from 'node:fs'

const SITE = 'https://foliolauncher.com'
const TEAL = 0x2e5e66
/** Discord allows 4096 characters of description. Far less is readable in a busy channel. */
const BODY_LIMIT = 1400

/** The release notes, trimmed to something a channel will actually read, cut at a line rather than mid-word. */
export function summarise(body = '', limit = BODY_LIMIT) {
  const text = body
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\r/g, '')
    // The release notes open with their own "# Folio x.y.z", which the embed's title already says.
    .replace(/^\s*#\s+Folio\s+[0-9][^\n]*\n+/i, '')
    .split('\n')
    // Checksums and signing lines belong on the release page, not in a chat message.
    .filter((line) => !/^\s*(sha256|signing certificate|signed with)/i.test(line))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  if (text.length <= limit) return text
  const cut = text.slice(0, limit)
  return `${cut.slice(0, cut.lastIndexOf('\n')).trim()}\n…`
}

export function buildPayload({ release, roleId, site = SITE }) {
  const version = String(release.tag_name ?? '').replace(/^v/, '')
  const apk = (release.assets ?? []).find((asset) => asset.name?.endsWith('.apk'))
  const size = apk ? `${(apk.size / 1048576).toFixed(1)} MB` : 'see the release'
  const notes = summarise(release.body)

  const embed = {
    title: `Folio ${version}`,
    url: release.html_url,
    description: notes || 'A new release of Folio.',
    color: TEAL,
    thumbnail: { url: `${site}/icon.png` },
    fields: [
      {
        name: 'Get it',
        value: [
          apk ? `[Download the APK](${apk.browser_download_url})` : `[The release](${release.html_url})`,
          `[How to install it](${site}/download/)`,
          `[Everything that changed](${site}/changelog/${version}/)`,
        ].join(' · '),
      },
      { name: 'The file', value: `${size} · Android 12 and up · free and open source`, inline: true },
    ],
    footer: { text: 'Folio Launcher · no ads, no analytics, no account' },
    timestamp: release.published_at ?? new Date().toISOString(),
  }

  const payload = { embeds: [embed], allowed_mentions: { parse: [] } }
  if (roleId) {
    payload.content = `<@&${roleId}>`
    // Only the role Folio was given. Without this a stray @everyone in release notes would carry.
    payload.allowed_mentions = { parse: [], roles: [roleId] }
  }
  return payload
}

async function main() {
  const dryRun = process.argv.includes('--dry-run')
  const eventPath = process.env.GITHUB_EVENT_PATH
  if (!eventPath) throw new Error('No GITHUB_EVENT_PATH; run this from the release workflow, or pass --dry-run in one')
  const { release } = JSON.parse(readFileSync(eventPath, 'utf8'))
  if (!release) throw new Error('The event carries no release')

  if (release.draft) return console.log('Draft release, nothing posted.')
  if (release.prerelease && process.env.ANNOUNCE_PRERELEASES !== 'true') {
    return console.log(`${release.tag_name} is a pre-release and ANNOUNCE_PRERELEASES is not true. Nothing posted.`)
  }

  const payload = buildPayload({ release, roleId: process.env.DISCORD_ROLE_ID?.trim() })
  const webhook = process.env.DISCORD_WEBHOOK_URL?.trim()
  if (dryRun || !webhook) {
    console.log(dryRun ? 'Dry run. This is what would be posted:' : 'No DISCORD_WEBHOOK_URL set, so nothing is sent:')
    return console.log(JSON.stringify(payload, null, 2))
  }

  const response = await fetch(webhook, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  })
  // A webhook that has been revoked or rate-limited answers with a body worth reading, and a silent failure here
  // means a release nobody hears about.
  if (!response.ok) throw new Error(`Discord said ${response.status}: ${(await response.text()).slice(0, 300)}`)
  console.log(`Posted ${release.tag_name} to the webhook.`)
}

if (import.meta.url === `file://${process.argv[1]}`) await main()
