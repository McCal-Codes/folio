/**
 * /redeem: a supporter code becomes a supporter role, and the role goes when the code does.
 *
 * The code is checked by the Ko-fi worker's own checker, not a copy of it: the same signature, the same withdrawn
 * list, and the same first-seen day for a months-code, so the role ends on the day the code ends everywhere else.
 * The signing key never leaves McCal's Mac; this only ever reads codes.
 */
import { checkBetaCode, decodeCode, readCode, signedByFolio } from '../kofi-worker/beta.js'

/** Bit 5 of the scope byte, as scripts/beta-code.py and BetaCodes.SCOPE_BITS number them. */
const SCOPE_THANKS = 5

const DAY = 86_400_000
const isoDay = (time) => new Date(time).toISOString().slice(0, 10)

/**
 * Which of the server's roles a code earns. Every code Folio has minted so far is tier 1, so the tier cannot tell
 * Coffee from Backer: the `thanks` scope is what marks Builder (the Builder tier and tips of $15 and up), and a code
 * minted with `--tier 2` is Backer. Everything else is Coffee.
 */
export function roleFor(code, env) {
  if ((code.scopeBits >> SCOPE_THANKS) & 1) return { id: env.ROLE_BUILDER, name: 'Builder' }
  if (code.tier >= 2) return { id: env.ROLE_BACKER, name: 'Backer' }
  return { id: env.ROLE_COFFEE, name: 'Coffee' }
}

/** What the checker's reasons mean to the person who typed the code. Nothing here says more than it needs to. */
const REFUSALS = {
  'not a Folio code': 'That is not a Folio code. Check it was copied whole, including the last group after the final dash.',
  'not signed by Folio': 'That is not a Folio code. Check it was copied whole, including the last group after the final dash.',
  'this code has no beta access': 'That code does not include supporter access.',
  'this code has been withdrawn': 'That code has been withdrawn. If it was yours, message McCal and it will be sorted.',
  'this code has run out': 'That code has run out. Supporting again on Ko-fi gets a new one: https://ko-fi.com/mccal',
}
const UNAVAILABLE = 'Codes cannot be checked right now. Try again in a little while.'

/** Talks to Discord as the bot. Separate so the tests can stand in for it. */
export function discordFor(env, send = fetch) {
  const call = async (method, path, reason) => {
    const response = await send(`https://discord.com/api/v10${path}`, {
      method,
      headers: {
        authorization: `Bot ${env.DISCORD_BOT_TOKEN}`,
        // Shows in the server's audit log, so McCal can see why a role changed hands.
        'x-audit-log-reason': encodeURIComponent(reason),
      },
    })
    return response.status
  }
  return {
    addRole: (guild, user, role, reason) => call('PUT', `/guilds/${guild}/members/${user}/roles/${role}`, reason),
    removeRole: (guild, user, role, reason) => call('DELETE', `/guilds/${guild}/members/${user}/roles/${role}`, reason),
  }
}

export async function redeem(env, { userId, guildId, text }, discord, now = Date.now()) {
  if (!env.GUILD_ID || guildId !== env.GUILD_ID) {
    return 'Codes are redeemed in the Folio Community server, where the supporter roles live.'
  }
  if (!env.DB || !env.DISCORD_BOT_TOKEN) return UNAVAILABLE

  // Ownership comes before the full check, because the full check starts a months-code's clock. A stranger who
  // pastes someone else's code should be turned away without touching that code's window.
  const code = readCode(decodeCode(text))
  if (!code) return REFUSALS['not a Folio code']
  const keys = String(env.SUPPORTER_KEYS ?? '').split(/[,\s]+/).filter(Boolean)
  if (!keys.length) return UNAVAILABLE
  if (!(await signedByFolio(code, keys))) return REFUSALS['not signed by Folio']

  const existing = await env.DB.prepare('SELECT user_id, removed_at FROM discord_roles WHERE serial = ?')
    .bind(code.serial)
    .first()
  if (existing && existing.user_id !== userId) {
    return 'That code has already been redeemed by someone else. If it was yours, message McCal and it will be sorted.'
  }

  const check = await checkBetaCode(env, text, now)
  if (!check.ok) return REFUSALS[check.why] ?? UNAVAILABLE

  const role = roleFor(check.code, env)
  if (!role.id) return UNAVAILABLE
  const endsOn = check.ends === null ? null : isoDay(check.ends)

  const status = await discord.addRole(guildId, userId, role.id, `Redeemed supporter code ${code.serial}`)
  if (status === 403) {
    // Discord only lets a bot hand out roles below its own. This is the one that reads like nothing without a hint.
    return `The code is good, but I could not give you the ${role.name} role: Mr Folio's role has to sit above ${role.name} in Server Settings › Roles. McCal can fix that, then run /redeem again.`
  }
  if (status >= 300) return UNAVAILABLE

  const today = isoDay(now)
  await env.DB.prepare(
    `INSERT INTO discord_roles (serial, user_id, guild_id, role_id, granted_at, ends_on, removed_at)
     VALUES (?, ?, ?, ?, ?, ?, NULL)
     ON CONFLICT(serial) DO UPDATE SET role_id = excluded.role_id, ends_on = excluded.ends_on, removed_at = NULL`,
  )
    .bind(code.serial, userId, guildId, role.id, today, endsOn)
    .run()

  const until = endsOn ? `until ${endsOn}` : 'for as long as the code lasts'
  return `Thank you for supporting Folio. You have the **${role.name}** role ${until}.\nIf you typed the code anywhere public, delete that message: anyone who can see a code can use it.`
}

/**
 * The daily sweep: a role whose code has ended is taken back, unless another live code of the same person still
 * earns the same role. A code works through its last day, so the role goes the day after.
 */
export async function expire(env, discord, now = Date.now()) {
  const today = isoDay(now)
  const { results: due } = await env.DB.prepare(
    'SELECT serial, user_id, guild_id, role_id FROM discord_roles WHERE removed_at IS NULL AND ends_on IS NOT NULL AND ends_on < ?',
  )
    .bind(today)
    .all()

  const removed = []
  for (const row of due) {
    const stillEarned = await env.DB.prepare(
      `SELECT 1 FROM discord_roles WHERE user_id = ? AND role_id = ? AND serial != ? AND removed_at IS NULL
         AND (ends_on IS NULL OR ends_on >= ?)`,
    )
      .bind(row.user_id, row.role_id, row.serial, today)
      .first()
    if (!stillEarned) {
      const status = await discord.removeRole(row.guild_id, row.user_id, row.role_id, `Supporter code ${row.serial} ended`)
      // 404 is someone who has left the server: there is no role to take, so the row is closed all the same.
      if (status >= 300 && status !== 404) continue
      removed.push(row.serial)
    }
    await env.DB.prepare('UPDATE discord_roles SET removed_at = ? WHERE serial = ?').bind(today, row.serial).run()
  }
  return { checked: due.length, removed }
}

export { DAY }
