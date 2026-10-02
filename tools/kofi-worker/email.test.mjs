/**
 * The supporter email: McCal's words kept as written, the HTML safe to build from a name anyone can type on Ko-fi, and
 * the "Open in Folio" link in the exact shape RedeemActivity (app/.../RedeemActivity.kt, since 0.6.5) accepts.
 * Run with `node --test email.test.mjs`; no network.
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import { codeLines, escapeHtml, redeemLink, supporterEmail } from './email.js'

// 24 groups of up to five characters, the shape admin.js's CODE accepts. Made up; it is not a real code.
const CODE = Array.from({ length: 24 }, (_, i) => (i === 23 ? 'JW' : `K9H${String(i).padStart(2, '0')}`.slice(0, 5))).join('-')

test('the code is set out four groups to a line and joins back to the same code', () => {
  const lines = codeLines(CODE)
  assert.equal(lines.length, 6)
  assert.ok(lines.slice(0, -1).every((line) => line.endsWith('-')), 'every line but the last ends on its hyphen')
  assert.equal(lines.join(''), CODE)
})

test('the plain text keeps the words as they were, without the old dash before the name', () => {
  const { text, subject } = supporterEmail({ name: 'Alex', code: CODE, pool: 'm1' })
  assert.equal(subject, 'Your Folio supporter code')
  assert.ok(text.startsWith('Thank you, Alex.\n\nHere is your Folio supporter code:\n\n    ' + CODE))
  assert.ok(text.includes('In Folio: Settings › Supporter › Redeem a Code, and paste it in. It is checked on your phone, so it works'))
  assert.ok(text.includes('Folio stays free and open source. Thank you for keeping it going.\nMcCal'))
  assert.ok(!text.includes('—'), 'no em dash')
})

test('without a name it just says thank you', () => {
  const { text, html } = supporterEmail({ name: '  ', code: CODE, pool: 'm1' })
  assert.ok(text.startsWith('Thank you.\n'))
  assert.ok(html.includes('>Thank you.</p>'))
})

test('the beta line is only for the pools that turn on Beta Features', () => {
  for (const pool of ['beta', 'all']) assert.ok(supporterEmail({ code: CODE, pool }).text.includes('It turns on Beta Features'))
  for (const pool of ['m1', 'thanks', '']) assert.ok(!supporterEmail({ code: CODE, pool }).text.includes('Beta Features'))
})

test('a name is escaped, so nothing a supporter types becomes markup', () => {
  const { html } = supporterEmail({ name: `<img src=x onerror=alert(1)> & "Co" 'x'`, code: CODE, pool: 'm1' })
  assert.ok(!html.includes('<img src=x'))
  assert.ok(html.includes('&lt;img src=x onerror=alert(1)&gt; &amp; &quot;Co&quot; &#39;x&#39;'))
  assert.equal(escapeHtml(undefined), '')
})

test('the HTML has no script, no javascript: link, and reaches out to nowhere but Folio and its repository', () => {
  const { html } = supporterEmail({ name: 'Alex', code: CODE, pool: 'beta' })
  assert.ok(!/<script/i.test(html))
  assert.ok(!/javascript:/i.test(html))
  const hosts = new Set([...html.matchAll(/https?:\/\/([a-z0-9.-]+)/gi)].map((m) => m[1].toLowerCase()))
  assert.deepEqual([...hosts].sort(), ['foliolauncher.com', 'github.com'])
  for (const line of codeLines(CODE)) assert.ok(html.includes(line))
})

test('the Open in Folio link is the one RedeemActivity reads', () => {
  const link = redeemLink(CODE)
  assert.equal(link, `folio://redeem?c=${CODE}`)
  assert.ok(supporterEmail({ code: CODE, pool: 'm1' }).html.includes(`href="${link}"`))
  assert.ok(supporterEmail({ code: CODE, pool: 'm1' }).text.includes(link))
  // The app's redeemCode(): the prefix, a c= parameter, and 20 to 200 letters, digits and hyphens.
  const query = link.slice('folio://redeem'.length).split('?')[1]
  const code = decodeURIComponent(query.split('&').find((p) => p.startsWith('c=')).slice(2))
  assert.ok(code.length >= 20 && code.length <= 200 && /^[A-Za-z0-9-]+$/.test(code))
  assert.equal(code, CODE)
})
