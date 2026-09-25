import { test } from 'node:test'
import assert from 'node:assert/strict'
import { COMMANDS, LIMIT, firstSentence, optionsOf, run } from './commands.mjs'

// Stand-ins for the files the project publishes, shaped exactly as sources.mjs returns them.
const sources = {
  release: async () => ({
    version: '0.6.6',
    published: '2026-09-21',
    url: 'https://github.com/McCal-Codes/folio/releases/tag/v0.6.6',
    apk: 'https://example.invalid/Folio-0.6.6.apk',
    size: '5.1 MB',
  }),
  changelog: async () => [
    { version: '0.6.7', date: '', body: '### Added\n- **Unreleased thing:** not out yet.' },
    { version: '0.6.6', date: '2026-09-21', body: '### Added\n- **The Folio Market:** a store.\n- **Keyd:** a keyboard.' },
    { version: '0.6.5', date: '2026-09-20', body: '### Fixed\n- **A crash:** gone.' },
  ],
  roadmap: async () => [
    { name: 'Folio 0.6.6', shipped: true, items: [{ title: 'Market', detail: 'Shipped.', status: 'done' }] },
    { name: 'Next', shipped: false, items: [{ title: 'Language in Settings', detail: 'Pick it yourself.', status: 'planned' }] },
    { name: 'Later', shipped: false, items: [{ title: 'Dock Drawer', detail: 'Swipe in on the dock.', status: 'planned' }] },
  ],
  tweaks: async () => [
    { id: 'com.mccal.folio.cabinet', name: 'Cabinet', description: 'App panels.', version: '1.0.0', screens: ['cover'] },
    { id: 'com.mccal.folio.harborline', name: 'Harborline', description: 'A dock that swells.', version: '1.0.0', screens: [] },
  ],
  screens: async () => [
    { name: 'Fold8 cover', width: 360, height: 900, group: 'fold' },
    { name: 'Fold8 inner', width: 932, height: 1080, group: 'fold' },
    { name: 'Desktop window', width: 1920, height: 1080, group: 'desktop' },
  ],
  help: async () => [
    { slug: 'gestures', url: 'https://foliolauncher.com/help/gestures/', title: 'Gestures' },
    { slug: 'widgets', url: 'https://foliolauncher.com/help/widgets/', title: 'Widgets' },
    { slug: 'update-will-not-install', url: 'https://foliolauncher.com/help/update-will-not-install/', title: 'Update will not install' },
  ],
}

test('every registered command has a handler, and every handler is registered', async () => {
  const names = COMMANDS.map((command) => command.name).sort()
  assert.deepEqual(names, ['changelog', 'help', 'redeem', 'roadmap', 'screens', 'tweak', 'version'])
  // /redeem needs the database and the bot token, so the worker routes it to redeem.mjs rather than these handlers.
  for (const name of names.filter((one) => one !== 'redeem')) {
    const answer = await run(name, name === 'screens' ? { width: 932 } : {}, sources)
    assert.ok(answer.length > 0, `${name} said nothing`)
    assert.ok(answer.length <= LIMIT, `${name} was ${answer.length} characters`)
  }
})

test('version gives the number, the size and the three links', async () => {
  const answer = await run('version', {}, sources)
  assert.match(answer, /\*\*Folio 0\.6\.6\*\*, released 2026-09-21/)
  assert.match(answer, /5\.1 MB/)
  assert.match(answer, /foliolauncher\.com\/download\//)
})

test('changelog defaults to the newest released version, never an unreleased one', async () => {
  const answer = await run('changelog', {}, sources)
  assert.match(answer, /Folio 0\.6\.6/)
  assert.doesNotMatch(answer, /0\.6\.7|Unreleased thing/)
})

test('changelog takes a version, with or without the v', async () => {
  for (const version of ['0.6.5', 'v0.6.5']) {
    const answer = await run('changelog', { version }, sources)
    assert.match(answer, /Folio 0\.6\.5/)
    assert.match(answer, /A crash/)
  }
})

test('an unknown version is a helpful answer, not an error', async () => {
  const answer = await run('changelog', { version: '9.9.9' }, sources)
  assert.match(answer, /No release called 9\.9\.9/)
  assert.match(answer, /0\.6\.6/)
})

test('roadmap leaves out what has already shipped', async () => {
  const answer = await run('roadmap', {}, sources)
  assert.match(answer, /\*\*Next\*\*/)
  assert.match(answer, /\*\*Later\*\*/)
  assert.doesNotMatch(answer, /Folio 0\.6\.6/)
})

test('roadmap narrows to one section', async () => {
  const answer = await run('roadmap', { when: 'later' }, sources)
  assert.match(answer, /Dock Drawer/)
  assert.doesNotMatch(answer, /Language in Settings/)
})

test('help matches a page by part of its name, and lists them all when nothing matches', async () => {
  assert.match(await run('help', { topic: 'gestures' }, sources), /help\/gestures\//)
  assert.match(await run('help', { topic: 'update' }, sources), /update-will-not-install/)
  const miss = await run('help', { topic: 'sandwiches' }, sources)
  assert.match(miss, /Nothing about "sandwiches"/)
  assert.match(miss, /help\/widgets\//)
})

test('tweak finds one by name, and lists them without one', async () => {
  assert.match(await run('tweak', { name: 'cabinet' }, sources), /\*\*Cabinet\*\* 1\.0\.0/)
  const all = await run('tweak', {}, sources)
  assert.match(all, /Cabinet/)
  assert.match(all, /Harborline/)
  assert.match(await run('tweak', { name: 'nope' }, sources), /No tweak called nope/)
})

test('screens uses the same pane rule the app does', async () => {
  assert.match(await run('screens', { width: 932 }, sources), /\*\*two panes\*\*/)
  assert.match(await run('screens', { width: 1200 }, sources), /\*\*three panes\*\*/)
  assert.match(await run('screens', { width: 400 }, sources), /\*\*one pane\*\*/)
  assert.match(await run('screens', { width: 200 }, sources), /narrower than anything/)
  assert.match(await run('screens', { width: 932 }, sources), /Fold8 inner \(932×1080\)/)
})

test('a source that is down is an apology, not a stack trace', async () => {
  const broken = { ...sources, release: async () => { throw new Error('GitHub said 503') } }
  const answer = await run('version', {}, broken)
  assert.match(answer, /did not work just now/)
  assert.doesNotMatch(answer, /503|Error/)
})

test('an unknown command says so rather than throwing', async () => {
  assert.match(await run('sandwich', {}, sources), /do not know sandwich/)
})

test("Discord's option array becomes a plain object", () => {
  assert.deepEqual(optionsOf({ data: { options: [{ name: 'version', value: '0.6.5' }] } }), { version: '0.6.5' })
  assert.deepEqual(optionsOf({ data: {} }), {})
  assert.deepEqual(optionsOf(undefined), {})
})

test('a long changelog bullet is cut at its first sentence, not mid-word', () => {
  const long = '**The Folio Market:** the app icon opens a store with Featured, Sources and Packages. For now it is for supporters, and a code redeemed in Settings opens it for everyone who has one.'
  assert.equal(firstSentence(long, 120), '**The Folio Market:** the app icon opens a store with Featured, Sources and Packages.')
  assert.equal(firstSentence('short and whole', 120), 'short and whole')
})

test('the narrowest supported width comes from the matrix, not from a number in the code', async () => {
  const narrow = { ...sources, screens: async () => [{ name: 'Tiny', width: 320, height: 640, group: 'phone' }] }
  assert.match(await run('screens', { width: 330 }, narrow), /\*\*330dp\*\*: yes/)
  assert.match(await run('screens', { width: 300 }, narrow), /starts at 320dp/)
})

test('one sentence too long to keep whole ends on a clause, never inside a word', () => {
  const long = '**Packages:** each has a page with what it does, what you see, screenshots, what changed, and a privacy label built from what the package asks for when it is installed on your phone'
  const cut = firstSentence(long, 120)
  assert.ok(cut.endsWith('…'))
  assert.ok(cut.length <= 120, `was ${cut.length}`)
  assert.match(cut, /(screenshots|what changed)…$/)
  const noSpaces = firstSentence('x'.repeat(300), 50)
  assert.equal(noSpaces.length, 50)
})
