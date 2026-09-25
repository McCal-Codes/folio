import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildMessage, kindOf, sections, shorten, splitWebhooks, tagline, wallOf } from './announce-release.mjs'

const release = {
  tag_name: 'v0.6.6',
  html_url: 'https://github.com/McCal-Codes/folio/releases/tag/v0.6.6',
  published_at: '2026-09-21T10:00:00Z',
  body: [
    '# Folio 0.6.6',
    '',
    'The Market arrives early for supporters.',
    '',
    "## What's new",
    '',
    'Taken from `CHANGELOG.md`.',
    '',
    '- **The Folio Market:** the app icon opens a store. Everyone gets it in 0.7.0.',
    '- **Sources you can trust:** add any HTTPS source.',
    '',
    '## Tested',
    '',
    '- 476 app tests, no failures.',
  ].join('\n'),
  assets: [{ name: 'Folio-0.6.6.apk', size: 5347737, browser_download_url: 'https://example.invalid/Folio-0.6.6.apk' }],
}

test('it opens the way the channel does: app, version linked, kind, then the summary line', () => {
  const { content } = buildMessage({ release })
  const [first, second] = content.split('\n')
  assert.equal(first, '**[Folio Launcher 0.6.6](https://github.com/McCal-Codes/folio/releases/tag/v0.6.6)** · Update')
  assert.equal(second, 'The Market arrives early for supporters.')
})

test('the kind comes from what is in the release, not from the version numbers', () => {
  const added = (n) => ['## Added', ''].concat(Array.from({ length: n }, (_, i) => `- thing ${i}`)).join('\n')
  assert.equal(kindOf(added(3)), 'Feature update')
  assert.equal(kindOf(added(1)), 'Update')
  assert.equal(kindOf('## Fixed\n\n- a crash on start'), 'Fix release')
  assert.equal(kindOf(''), 'Update')
})

test('a release with one list does not get a heading saying so', () => {
  const one = '# Folio 0.6.7\n\nA summary.\n\n## What\'s new\n\n- **A thing:** it happens.'
  const { content } = buildMessage({ release: { ...release, body: one } })
  assert.doesNotMatch(content, /\*\*What's new\*\*/)
  assert.match(content, /- \*\*A thing:\*\*/)
})

test('a feature wall attached to the release is found; other assets are not', () => {
  assert.equal(wallOf(release), undefined)
  const withWall = { ...release, assets: [...release.assets, { name: 'folio-066-wall.jpg', size: 400000 }] }
  assert.equal(wallOf(withWall).name, 'folio-066-wall.jpg')
  const notAWall = { ...release, assets: [{ name: 'SHA256SUMS.txt', size: 82 }] }
  assert.equal(wallOf(notAWall), undefined)
})

test('the file, the install page and the changelog page all get a link', () => {
  const { content } = buildMessage({ release })
  assert.match(content, /\[Download the APK\]\(https:\/\/example\.invalid\/Folio-0\.6\.6\.apk\)/)
  assert.match(content, /foliolauncher\.com\/download\//)
  assert.match(content, /foliolauncher\.com\/changelog\/0\.6\.6\//)
  assert.match(content, /5\.1 MB · Android 12 and up/)
})

test('a heading with no bullets under it is not a section', () => {
  const found = sections(release.body)
  assert.deepEqual(found.map((s) => s.name), ["What's new", 'Tested'])
  assert.equal(found[0].bullets.length, 2)
})

test('a bullet is cut to its first sentence, keeping the bold lead', () => {
  const long = '**The Folio Market:** the app icon opens a store with everything in it. Everyone gets it in 0.7.0.'
  assert.equal(shorten(long, 90), '**The Folio Market:** the app icon opens a store with everything in it.')
  // Under 40 characters there is no sentence worth trusting, so it is a hard cut: a "." that early is usually an
  // abbreviation or a version number.
  assert.equal(shorten('**A:** one. two.', 12), '**A:** one.…')
  assert.match(shorten('x'.repeat(300)), /…$/)
  assert.ok(shorten('x'.repeat(300)).length <= 190)
})

test("the notes' own title is never the summary line", () => {
  assert.equal(tagline('# Folio 0.6.6\n\nThe Market arrives.'), 'The Market arrives.')
  assert.equal(tagline('# Folio 0.6.6\n\n- a bullet'), '')
})

test('a long release is cut to a few bullets, with a link to the rest', () => {
  const many = ['# Folio 0.9.0', '', 'Lots.', '', '## Added', '']
    .concat(Array.from({ length: 30 }, (_, i) => `- **Thing ${i}:** it does something useful for you.`))
  const { content } = buildMessage({ release: { ...release, body: many.join('\n') } })
  assert.ok(content.length <= 2000, `was ${content.length}`)
  assert.equal((content.match(/^- /gm) ?? []).length, 5)
  assert.match(content, /The rest is in \[the full notes\]/)
})

test('nothing is pinged unless a role was given, and then only that role', () => {
  const quiet = buildMessage({ release })
  assert.doesNotMatch(quiet.content, /<@&/)
  assert.deepEqual(quiet.allowed_mentions, { parse: [] })

  const pinged = buildMessage({ release, roleId: '123' })
  assert.match(pinged.content, /^<@&123>\n/)
  assert.deepEqual(pinged.allowed_mentions, { parse: [], roles: ['123'] })
})

test('link previews are suppressed, so three links do not unfurl three cards', () => {
  assert.equal(buildMessage({ release }).flags, 4)
})

test('a release with no APK points at the release page instead', () => {
  const { content } = buildMessage({ release: { ...release, assets: [] } })
  assert.match(content, /\[The release\]\(https:\/\/github\.com/)
  assert.doesNotMatch(content, /Download the APK/)
})

test('one webhook or several, and a list edited by hand still parses', () => {
  assert.deepEqual(splitWebhooks('https://a'), ['https://a'])
  assert.deepEqual(splitWebhooks(' https://a , https://b '), ['https://a', 'https://b'])
  assert.deepEqual(splitWebhooks('https://a,,https://b,'), ['https://a', 'https://b'])
  assert.deepEqual(splitWebhooks(''), [])
  assert.deepEqual(splitWebhooks(undefined), [])
})

test('each webhook gets its own role, by position, and a blank means no ping there', async () => {
  const { rolesFor } = await import('./announce-release.mjs')
  assert.deepEqual(rolesFor(['a', 'b'], '111,222'), ['111', '222'])
  assert.deepEqual(rolesFor(['a', 'b'], ',222'), ['', '222'])
  assert.deepEqual(rolesFor(['a', 'b'], '111'), ['111', ''])
  assert.deepEqual(rolesFor(['a', 'b'], ''), ['', ''])
  assert.deepEqual(rolesFor(['a', 'b'], undefined), ['', ''])
})

test('two servers each receive their own ping, and the wall is downloaded once', async () => {
  const { createServer } = await import('node:http')
  const { spawn } = await import('node:child_process')
  const { mkdtempSync, writeFileSync } = await import('node:fs')
  const { tmpdir } = await import('node:os')
  const { join } = await import('node:path')

  const received = {}
  let wallFetches = 0
  const server = createServer((request, response) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () => {
      const path = request.url.split('?')[0]
      if (path === '/wall.png') {
        wallFetches += 1
        response.writeHead(200, { 'content-type': 'image/png' })
        return response.end(Buffer.from([0x89, 0x50, 0x4e, 0x47]))
      }
      const body = Buffer.concat(chunks).toString('latin1')
      const json = body.match(/name="payload_json"\r\n\r\n([\s\S]*?)\r\n--/)?.[1] ?? body
      received[path] = { message: JSON.parse(json), hasFile: body.includes('filename="Folio-0.6.6-wall.png"') }
      response.writeHead(200, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ id: path }))
    })
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const base = `http://127.0.0.1:${server.address().port}`

  const dir = mkdtempSync(join(tmpdir(), 'announce-'))
  const event = join(dir, 'event.json')
  writeFileSync(event, JSON.stringify({
    release: {
      ...release,
      assets: [...release.assets, {
        name: 'Folio-0.6.6-wall.png', size: 4, content_type: 'image/png', browser_download_url: `${base}/wall.png`,
      }],
    },
  }))

  const code = await new Promise((resolve) => {
    const child = spawn(process.execPath, [new URL('./announce-release.mjs', import.meta.url).pathname], {
      env: {
        ...process.env,
        GITHUB_EVENT_PATH: event,
        DISCORD_WEBHOOK_URL: `${base}/folio,${base}/mmd`,
        DISCORD_ROLE_ID: '1553093586705449062,999',
      },
      stdio: 'ignore',
    })
    child.on('exit', resolve)
  })
  server.close()

  assert.equal(code, 0)
  assert.equal(wallFetches, 1, 'the wall should be read once, not once per server')

  const folio = received['/folio']
  assert.match(folio.message.content, /^<@&1553093586705449062>\n/)
  assert.deepEqual(folio.message.allowed_mentions.roles, ['1553093586705449062'])
  assert.ok(folio.hasFile)

  const mmd = received['/mmd']
  assert.match(mmd.message.content, /^<@&999>\n/)
  assert.deepEqual(mmd.message.allowed_mentions.roles, ['999'])
  assert.doesNotMatch(mmd.message.content, /1553093586705449062/)
  assert.ok(mmd.hasFile)
})
