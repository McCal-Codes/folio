/**
 * Mr Folio: Folio's Discord bot, as a Worker rather than a process.
 *
 * Discord can talk to a bot over plain HTTP instead of a gateway socket, which means there is nothing to keep
 * alive: Discord posts an interaction here, this answers it, and that is the whole lifetime. Same account and the
 * same `wrangler deploy` as the supporter worker.
 *
 * Secrets: DISCORD_PUBLIC_KEY (from the application's General Information page) is the only one phase 1 needs.
 * Handing out roles comes later and needs a bot token; nothing here has one.
 */
import { optionsOf, run } from './commands.mjs'
import { sources as liveSources } from './sources.mjs'

const PING = 1
const APPLICATION_COMMAND = 2
const PONG = 1
const CHANNEL_MESSAGE = 4
const DEFERRED_CHANNEL_MESSAGE = 5

/**
 * Discord gives an interaction three seconds before telling the person the app did not respond. Anything that has
 * not answered by this point is deferred instead, and edited in when it arrives. Two seconds leaves room for the
 * round trip; cached answers are well inside it, so most people never see the "thinking" state at all.
 */
export const ANSWER_WITHIN_MS = 2000

const hex = (value) => Uint8Array.from(value.match(/.{1,2}/g)?.map((byte) => parseInt(byte, 16)) ?? [])

/**
 * Discord signs every interaction, and sends deliberately bad ones when you first set the endpoint: an endpoint
 * that answers those is rejected. Verification is not optional and cannot be skipped in development.
 */
async function verify(request, body, publicKey) {
  const signature = request.headers.get('x-signature-ed25519')
  const timestamp = request.headers.get('x-signature-timestamp')
  if (!signature || !timestamp) return false
  try {
    const key = await crypto.subtle.importKey('raw', hex(publicKey), { name: 'Ed25519' }, false, ['verify'])
    return await crypto.subtle.verify(
      { name: 'Ed25519' },
      key,
      hex(signature),
      new TextEncoder().encode(timestamp + body),
    )
  } catch {
    return false
  }
}

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json' } })

/** A message the way every answer is shaped: no preview cards under it, and nobody pinged by it. */
const message = (content) => ({ content, flags: 4, allowed_mentions: { parse: [] } })

/**
 * Built with its dependencies passed in, so the tests can hand it slow sources and a fake fetch rather than
 * reaching GitHub and Discord.
 */
export function createWorker({ sources = liveSources, send = fetch, wait = ANSWER_WITHIN_MS } = {}) {
  return {
    async fetch(request, env, ctx) {
      if (request.method === 'GET') {
        // Something to look at when checking the Worker is up. Discord never uses it.
        return new Response('Mr Folio is listening. Folio is at https://foliolauncher.com', {
          headers: { 'content-type': 'text/plain' },
        })
      }
      if (request.method !== 'POST') return new Response('Method not allowed', { status: 405 })
      if (!env.DISCORD_PUBLIC_KEY) return new Response('No DISCORD_PUBLIC_KEY set', { status: 500 })

      const body = await request.text()
      if (!(await verify(request, body, env.DISCORD_PUBLIC_KEY))) {
        return new Response('Bad signature', { status: 401 })
      }

      const interaction = JSON.parse(body)
      if (interaction.type === PING) return json({ type: PONG })
      if (interaction.type !== APPLICATION_COMMAND) return json({ type: PONG })

      const answer = run(interaction.data?.name, optionsOf(interaction), sources)
      const timer = new Promise((resolve) => setTimeout(() => resolve(null), wait))
      const quick = await Promise.race([answer, timer])
      if (quick !== null) return json({ type: CHANNEL_MESSAGE, data: message(quick) })

      // Too slow to answer in place. Say so now, and edit the real answer in when it lands. The interaction's own
      // token authorises the edit, so no bot token is involved.
      const followUp = answer.then((content) =>
        send(
          `https://discord.com/api/v10/webhooks/${interaction.application_id}/${interaction.token}/messages/@original`,
          { method: 'PATCH', headers: { 'content-type': 'application/json' }, body: JSON.stringify(message(content)) },
        ).then((response) => {
          if (!response.ok) console.error(`Could not edit the answer in: Discord said ${response.status}`)
        }),
      )
      ctx?.waitUntil?.(followUp)
      return json({ type: DEFERRED_CHANNEL_MESSAGE })
    },
  }
}

export default createWorker()
