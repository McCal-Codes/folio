/**
 * Tells Discord which commands exist. Run it once, and again whenever COMMANDS changes.
 *
 *   FOLIO_APP_ID=1553079678988849294 node tools/folio-bot/register.mjs            # everywhere, up to an hour to appear
 *   FOLIO_APP_ID=… FOLIO_GUILD_ID=… node tools/folio-bot/register.mjs             # one server, instantly
 *
 * The bot token comes from ~/.folio-discord-bot-token and goes nowhere near the repository.
 */
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'
import { COMMANDS } from './commands.mjs'

const appId = process.env.FOLIO_APP_ID
const guildId = process.env.FOLIO_GUILD_ID
if (!appId) throw new Error('Set FOLIO_APP_ID to the application id.')

const token = readFileSync(join(homedir(), '.folio-discord-bot-token'), 'utf8').trim()
const where = guildId ? `/applications/${appId}/guilds/${guildId}/commands` : `/applications/${appId}/commands`

const response = await fetch(`https://discord.com/api/v10${where}`, {
  method: 'PUT',
  headers: { authorization: `Bot ${token}`, 'content-type': 'application/json' },
  body: JSON.stringify(COMMANDS),
})
if (!response.ok) throw new Error(`Discord said ${response.status}: ${(await response.text()).slice(0, 300)}`)
const registered = await response.json()
console.log(`Registered ${registered.length} commands${guildId ? ` in ${guildId}` : ' globally'}:`)
for (const command of registered) console.log(`  /${command.name} — ${command.description}`)
