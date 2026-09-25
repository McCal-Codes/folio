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
import { sources } from './sources.mjs'

const PING = 1
const APPLICATION_COMMAND = 2
const PONG = 1
const CHANNEL_MESSAGE = 4

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

export default {
  async fetch(request, env) {
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

    const content = await run(interaction.data?.name, optionsOf(interaction), sources)
    return json({
      type: CHANNEL_MESSAGE,
      data: {
        content,
        // Links carry their own titles here; a preview card under every answer would bury the next message.
        flags: 4,
        allowed_mentions: { parse: [] },
      },
    })
  },
}
