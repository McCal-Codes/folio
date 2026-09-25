import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildPayload, summarise } from './announce-release.mjs'

const release = {
  tag_name: 'v0.6.6',
  html_url: 'https://github.com/McCal-Codes/folio/releases/tag/v0.6.6',
  published_at: '2026-09-21T10:00:00Z',
  body: '### Added\n- The Folio Market\n\nSHA256: abc123\nSigning certificate: def456',
  assets: [{ name: 'Folio-0.6.6.apk', size: 5347737, browser_download_url: 'https://example.invalid/Folio-0.6.6.apk' }],
}

test('the embed carries the version, the file and the three links', () => {
  const { embeds } = buildPayload({ release })
  const [embed] = embeds
  assert.equal(embed.title, 'Folio 0.6.6')
  assert.equal(embed.url, release.html_url)
  assert.match(embed.fields[1].value, /5\.1 MB/)
  assert.match(embed.fields[0].value, /foliolauncher\.com\/download\//)
  assert.match(embed.fields[0].value, /foliolauncher\.com\/changelog\/0\.6\.6\//)
  assert.match(embed.fields[0].value, /Folio-0\.6\.6\.apk/)
})

test('checksums and signing lines stay on the release page', () => {
  const { embeds } = buildPayload({ release })
  assert.doesNotMatch(embeds[0].description, /SHA256|Signing certificate/i)
  assert.match(embeds[0].description, /The Folio Market/)
})

test('nothing is pinged unless a role was given, and then only that role', () => {
  const quiet = buildPayload({ release })
  assert.equal(quiet.content, undefined)
  assert.deepEqual(quiet.allowed_mentions, { parse: [] })

  const pinged = buildPayload({ release, roleId: '123' })
  assert.equal(pinged.content, '<@&123>')
  assert.deepEqual(pinged.allowed_mentions, { parse: [], roles: ['123'] })
})

test('a long body is cut at a line, not mid-word', () => {
  const body = Array.from({ length: 200 }, (_, i) => `- line number ${i} of the notes`).join('\n')
  const short = summarise(body)
  assert.ok(short.length <= 1401, `was ${short.length}`)
  assert.ok(short.endsWith('…'))
  assert.doesNotMatch(short, /line number \d+ of the not$/)
})

test('a release with no APK still posts, pointing at the release page', () => {
  const { embeds } = buildPayload({ release: { ...release, assets: [] } })
  assert.match(embeds[0].fields[0].value, /releases\/tag\/v0\.6\.6/)
  assert.match(embeds[0].fields[1].value, /see the release/)
})

test("the notes' own title is dropped, since the embed already has one", () => {
  const { embeds } = buildPayload({ release: { ...release, body: '# Folio 0.6.6\n\nThe Market arrives early.' } })
  assert.equal(embeds[0].description, 'The Market arrives early.')
})
