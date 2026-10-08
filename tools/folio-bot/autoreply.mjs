/**
 * The first reply in #help and the two forums, so a person who asks is told where answers are and what to include
 * while they wait for McCal.
 *
 * Mr Folio answers over HTTP and holds no live connection, so it cannot react to a message the moment it is posted.
 * Instead a timer wakes it every couple of minutes, it looks at the latest messages and new posts, and replies once to
 * anything new. It never reads what anyone wrote: Discord hides other people's text from a bot without the privileged
 * message-content permission, and none of this needs it. It only needs to know who posted, when, and whether it has
 * already answered.
 *
 * Nothing is stored. "Already answered" is read from Discord itself: the bot's reply points at the message it answers.
 */

/** Only things posted this recently are answered, so turning this on never replies to the whole backlog. */
export const MAX_AGE_MS = 30 * 60 * 1000
/** One automatic reply per person in this long, so a back-and-forth in #help is not answered line by line. */
export const PER_PERSON_MS = 2 * 60 * 60 * 1000
/** A ceiling per run, in case something goes wrong and the list looks all new. */
export const MAX_PER_RUN = 3
const DEFAULT_MESSAGE = 0
const SUPPRESS_EMBEDS = 4

const DISCORD_EPOCH = 1420070400000n
/** When a Discord id (a snowflake) was made, in milliseconds since 1970. */
export const madeAt = (id) => Number((BigInt(id) >> 22n) + DISCORD_EPOCH)

/** What each place says. Short, and no promise about when McCal will answer. */
export function replies(env) {
  const faq = env.FAQ_CHANNEL ? `<#${env.FAQ_CHANNEL}>` : 'the FAQ'
  const bugs = env.BUG_FORUM ? `<#${env.BUG_FORUM}>` : 'the bug reports forum'
  return {
    help:
      `Thanks for asking! Most answers are written down: ${faq}, https://foliolauncher.com/help, or \`/help\` right here ` +
      `with a topic. If something is broken, please post it in ${bugs} with your Folio version, your phone and whether ` +
      `it's the cover or inner screen. McCal reads everything here and replies when he can.`,
    bug:
      `Thanks for the report! If they aren't in your post yet, please add your Folio version (Settings, What's New), ` +
      `your phone and Android version, whether you were on the cover or inner screen, folded or unfolded, and what you ` +
      `did just before it happened. Settings, Help, Report a Bug fills most of that in for you. McCal triages these and ` +
      `tags them as he goes.`,
    idea:
      `Thanks for the idea! \`/roadmap\` here shows what's already planned. McCal reads every idea, and a 👍 on the ` +
      `first message helps him see what matters most.`,
  }
}

/**
 * Which of a channel's latest messages (newest first, as Discord returns them) should get a first reply. Pure: it
 * decides nothing about sending.
 */
export function pickMessages(messages, { botId, staffIds = [], now = Date.now() }) {
  const staff = new Set(staffIds)
  const answered = new Set() // ids of messages the bot has already replied to
  const answeredAuthors = new Map() // author id -> when the bot last replied to them
  const byId = new Map(messages.map((m) => [m.id, m]))
  for (const m of messages) {
    if (m.author?.id !== botId || !m.message_reference?.message_id) continue
    answered.add(m.message_reference.message_id)
    const target = byId.get(m.message_reference.message_id)
    if (target) {
      const at = madeAt(m.id)
      if (at > (answeredAuthors.get(target.author?.id) ?? 0)) answeredAuthors.set(target.author?.id, at)
    }
  }
  const picked = []
  const claimed = new Set()
  for (const m of messages) {
    if (m.type !== DEFAULT_MESSAGE || m.author?.bot || m.webhook_id) continue
    if (staff.has(m.author?.id) || m.message_reference) continue // McCal's own posts, and replies to someone else
    if (now - madeAt(m.id) > MAX_AGE_MS || answered.has(m.id)) continue
    if (now - (answeredAuthors.get(m.author.id) ?? 0) < PER_PERSON_MS || claimed.has(m.author.id)) continue
    claimed.add(m.author.id)
    picked.push(m)
  }
  return picked.slice(0, MAX_PER_RUN)
}

/** Which new forum posts (threads) have not been answered yet. A thread is answered if the bot has posted in it. */
export function pickThreads(threads, { forums, now = Date.now(), staffIds = [] }) {
  const staff = new Set(staffIds)
  return threads
    .filter((t) => forums.includes(t.parent_id) && !staff.has(t.owner_id) && now - madeAt(t.id) <= MAX_AGE_MS)
    .slice(0, MAX_PER_RUN)
}

const asJson = (response) => (response.ok ? response.json() : Promise.reject(new Error(`Discord said ${response.status}`)))

/** One pass. Returns how many replies it sent, for the log. Every failure is caught and logged, never thrown. */
export async function runAutoReply(env, send = fetch, now = Date.now()) {
  if (env.AUTO_REPLY !== 'on' || !env.DISCORD_BOT_TOKEN) return 0
  const api = (path, init = {}) =>
    send(`https://discord.com/api/v10${path}`, {
      ...init,
      headers: { authorization: `Bot ${env.DISCORD_BOT_TOKEN}`, 'content-type': 'application/json', ...init.headers },
    })
  const staffIds = String(env.STAFF_IDS || '').split(',').filter(Boolean)
  const say = replies(env)
  const post = (channel, content, replyTo) =>
    api(`/channels/${channel}/messages`, {
      method: 'POST',
      body: JSON.stringify({
        content,
        flags: SUPPRESS_EMBEDS,
        allowed_mentions: { parse: [], replied_user: Boolean(replyTo) },
        ...(replyTo ? { message_reference: { message_id: replyTo, fail_if_not_exists: false } } : {}),
      }),
    })
  let sent = 0
  try {
    if (env.HELP_CHANNEL) {
      const messages = await api(`/channels/${env.HELP_CHANNEL}/messages?limit=25`).then(asJson)
      for (const m of pickMessages(messages, { botId: env.BOT_ID, staffIds, now })) {
        const response = await post(env.HELP_CHANNEL, say.help, m.id)
        if (response.ok) sent += 1
        else console.error(`Could not reply in #help: Discord said ${response.status}`)
      }
    }
    const forums = { [env.BUG_FORUM]: 'bug', [env.IDEAS_FORUM]: 'idea' }
    delete forums[undefined]
    if (Object.keys(forums).length) {
      const { threads = [] } = await api(`/guilds/${env.GUILD_ID}/threads/active`).then(asJson)
      for (const thread of pickThreads(threads, { forums: Object.keys(forums), now, staffIds })) {
        const inside = await api(`/channels/${thread.id}/messages?limit=10`).then(asJson).catch(() => null)
        if (!inside || inside.some((m) => m.author?.id === env.BOT_ID)) continue
        const response = await post(thread.id, say[forums[thread.parent_id]])
        if (response.ok) sent += 1
        else console.error(`Could not reply in a forum post: Discord said ${response.status}`)
      }
    }
  } catch (error) {
    console.error(`Auto-reply pass failed: ${error.message}`)
  }
  return sent
}
