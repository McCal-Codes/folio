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

Every answer comes from a file the project already publishes, cached for five minutes, so the bot cannot tell anyone
something the app does not do.

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
   it cannot be changed by editing a file.

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
