# Mr Folio

Folio's Discord bot, application `1553079678988849294`. Phase 1 of the plan in
`~/dev/folio-marketing/discord/BOT.md`: six read-only commands, no permissions, no state.

| Command | Answers with | Read from |
|---|---|---|
| `/version` | The current release, its size and three links | GitHub's latest release |
| `/changelog [version]` | Five headline changes, newest release by default | `CHANGELOG.md` |
| `/roadmap [when]` | Next, Later and Exploring | `app/src/main/assets/roadmap.json` |
| `/help [topic]` | The matching help page, or the list | foliolauncher.com's sitemap |
| `/tweak [name]` | What a tweak does and which screens it runs on | `docs/sdk/source/index.json` |
| `/screens <width>` | Whether a window that wide fits, and how many panes | `screen-matrix.json` |
| `/redeem <code>` | Your supporter role, until your code ends. Only you see the reply | The code itself, checked by `kofi-worker/beta.js` |

Every answer comes from a file the project already publishes, cached for five minutes, so the bot cannot tell anyone
something the app does not do.

## /redeem

A supporter code becomes a supporter role, and the role goes when the code does. It reuses the Ko-fi worker's own
checker rather than a copy: the same signature, the same withdrawn list, and the same first-seen day for a
months-code, so the role ends on the day the code ends everywhere else. The signing key never leaves the Mac.

**Which role.** Every code minted so far is tier 1, so the tier cannot tell Coffee from Backer. The `thanks` scope
marks Builder (the Builder tier and tips of $15 and up); a code minted with `--tier 2` is Backer; anything else is
Coffee. That is `roleFor` in `redeem.mjs`, one function, if the mapping should change.

**One code, one person.** The serial is the key of `discord_roles` in the shared D1 database. A code someone else has
redeemed is refused, and it is refused before the full check runs, so a stranger pasting it cannot start its month.

**The role goes.** The cron in `wrangler.toml` runs `expire` daily at 06:17 UTC. A role is taken back the day after
its code's last day, unless another live code of the same person earns the same role. Someone who has left the
server is closed off; a Discord error is tried again the next day.

**Role order.** Discord only lets a bot hand out roles below its own, so **Mr Folio's role must sit above Builder**
in Server Settings › Roles. If it does not, `/redeem` says exactly that rather than failing with a bare 403, and
records nothing, so the person can try again once it is fixed.

## How it runs

A Cloudflare Worker on Discord's HTTP interactions, not a process on a gateway socket: Discord posts each command
here and the Worker answers it. Nothing stays running, and nothing costs anything at this size. Every request is
checked against the application's Ed25519 public key first; an unsigned or tampered one gets a 401, which is also
what Discord's own endpoint check expects.

## Setting it up

1. **Deploy**, from this directory:

   ```
   npx wrangler deploy
   npx wrangler secret put DISCORD_PUBLIC_KEY
   ```

   The public key is on the application's **General Information** page. It is not a secret, but it lives as one so
   it cannot be changed by editing a file. `/redeem` also needs the bot token:

   ```
   cat ~/.folio-discord-bot-token | npx wrangler secret put DISCORD_BOT_TOKEN
   ```

   and the `discord_roles` table, which is in `tools/kofi-worker/schema.sql` beside the tables it shares.

2. **Point Discord at it.** Same page, **Interactions Endpoint URL**, the `folio-bot` workers.dev address. Discord
   sends two deliberately bad requests when you save; the page only saves if the Worker refuses both.

3. **Register the commands.** Put the bot token in `~/.folio-discord-bot-token` (chmod 600, never in the
   repository), then:

   ```
   FOLIO_APP_ID=1553079678988849294 FOLIO_GUILD_ID=1551382087985266838 node tools/folio-bot/register.mjs
   ```

   With the guild id they appear in the Folio server straight away. Without it they register everywhere the app is
   installed and take up to an hour.

4. **Install it.** The application's **Installation** page already asks for `applications.commands`, which is all
   phase 1 needs. `bot` and Manage Roles come with phase 2.

## Tests

```
node --test tools/folio-bot/*.test.mjs
```

The command tests run every handler against stand-in data shaped like the real files, including a source that is
down. The worker tests sign requests with a real Ed25519 key rather than mocking the check away, so a tampered body,
the wrong key, missing headers and a missing public key are all proven to be refused.
