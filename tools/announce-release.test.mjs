import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildMessage, kindOf, sections, shorten, tagline } from './announce-release.mjs'

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

test('it opens the way the channel does: app, version, kind, then the summary line', () => {
  const { content } = buildMessage({ release, previousTag: 'v0.6.5' })
  const [first, second] = content.split('\n')
  assert.equal(first, '**Folio Launcher 0.6.6** · Minor update')
  assert.equal(second, 'The Market arrives early for supporters.')
})

test('a feature release says so, and an unknown previous version says nothing', () => {
  assert.equal(kindOf('0.7.0', '0.6.9'), 'Feature update')
  assert.equal(kindOf('1.0.0', '0.9.0'), 'Major update')
  assert.equal(kindOf('0.6.6', ''), '')
})

test('the file, the install page and the changelog page all get a link', () => {
  const { content } = buildMessage({ release })
  assert.match(content, /\[Download the APK\]\(https:\/\/example\.invalid\/Folio-0\.6\.6\.apk\)/)
  assert.match(content, /foliolauncher\.com\/download\//)
  assert.match(content, /foliolauncher\.com\/changelog\/0\.6\.6\//)
  assert.match(content, /5\.1 MB, Android 12 and up/)
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
  assert.equal((content.match(/^- /gm) ?? []).length, 6)
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
