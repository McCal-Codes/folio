/** Talks to Discord as the bot, for the one thing it still does there: giving and taking back the release-ping role. */
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
