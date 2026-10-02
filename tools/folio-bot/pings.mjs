/**
 * The "Get release pings" button. Pressing it gives you the `Folio updates` role, which @-mentions you when a release
 * is posted, and pressing it again takes the role back. The press is a component interaction (type 3), signed like a
 * command, and the answer is private to the person who pressed.
 */
export const PINGS_BUTTON = 'folio-release-pings'

/** The message component to attach to a post: one row, one button. */
export const pingsComponents = () => [
  { type: 1, components: [{ type: 2, style: 2, label: 'Get release pings', emoji: { name: '🔔' }, custom_id: PINGS_BUTTON }] },
]

export async function togglePings(env, interaction, discord) {
  if (!env.GUILD_ID || interaction.guild_id !== env.GUILD_ID || !env.ROLE_UPDATES) {
    return 'Release pings are set up in the Folio Community server.'
  }
  const userId = interaction.member?.user?.id
  if (!userId || !env.DISCORD_BOT_TOKEN) return 'That did not work. Try again in a little while.'
  // The interaction carries the person's roles, so there is no second lookup to race.
  const has = (interaction.member.roles ?? []).includes(env.ROLE_UPDATES)
  const status = has
    ? await discord.removeRole(env.GUILD_ID, userId, env.ROLE_UPDATES, 'Turned release pings off')
    : await discord.addRole(env.GUILD_ID, userId, env.ROLE_UPDATES, 'Turned release pings on')
  if (status === 403) {
    // Discord only lets a bot hand out roles below its own, and this is the one that reads like nothing without a hint.
    return "I could not change that: Mr Folio's role has to sit above Folio updates in Server Settings › Roles. McCal can fix it."
  }
  if (status >= 300) return 'That did not work. Try again in a little while.'
  return has ? 'Release pings are off.' : 'Done. You will be pinged when a new release is posted. Press the button again to stop.'
}
