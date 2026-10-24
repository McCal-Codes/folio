/**
 * The Folio Dev debug source: who may read the private dev repository, and what the routes hand back.
 * Run with `node dev.test.mjs`: no account and no network; GitHub is stood in for here, and every key is a throwaway
 * made inside this file.
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import { betaReleases, checkBetaCode } from './beta.js'
import { checkDevCode, devRoutes, resetDevCaches, safeFilePath } from './dev.js'

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const DAY = 86_400_000
const EPOCH = Date.UTC(2026, 0, 1)
const NOW = Date.UTC(2026, 8, 20)
const TODAY = Math.floor((NOW - EPOCH) / DAY)
const SCOPE_BETA = 0b00001
const SCOPE_DEV = 0b10000

async function throwawayKey() {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
  let binary = ''
  for (const b of spki) binary += String.fromCharCode(b)
  return { pair, publicKey: btoa(binary) }
}

/** Mints a code the way scripts/beta-code.py does: nine bytes signed, then base32. */
async function mint(key, { scopes = SCOPE_DEV, tier = 1, months = 0, expiryDay = TODAY + 90, serial = 7 } = {}) {
  const version = months > 0 ? 2 : 1
  const payload = new Uint8Array([version, scopes, months > 0 ? (months << 4) | tier : tier,
    (expiryDay >> 8) & 0xff, expiryDay & 0xff,
    (serial >>> 24) & 0xff, (serial >> 16) & 0xff, (serial >> 8) & 0xff, serial & 0xff])
  const signature = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key.pair.privateKey, payload))
  let text = ''
  let buffer = 0
  let bits = 0
  for (const b of [...payload, ...signature]) {
    buffer = (buffer << 8) | b
    bits += 8
    while (bits >= 5) { bits -= 5; text += ALPHABET[(buffer >> bits) & 31] }
  }
  if (bits > 0) text += ALPHABET[(buffer << (5 - bits)) & 31]
  return text
}

/** Two unrelated keys: the one for supporter codes, and the dev one. They are never the same key. */
async function setup(extra = {}) {
  resetDevCaches()
  const supporter = await throwawayKey()
  const dev = await throwawayKey()
  const env = {
    SUPPORTER_KEYS: supporter.publicKey,
    DEV_KEYS: dev.publicKey,
    DEV_REPO: 'McCal-Codes/folio-dev-builds',
    DEV_GITHUB_TOKEN: 'dev-token',
    GITHUB_TOKEN: 'beta-token',
    BETA_REPO: 'McCal-Codes/folio-beta',
    TICKET_SECRET: 'a-secret-only-the-worker-knows',
    ...extra,
  }
  return { supporter, dev, env }
}

const asking = (code, path) => ({
  request: new Request(`https://codes.example${path}`, { headers: code ? { Authorization: `Bearer ${code}` } : {} }),
  url: new URL(`https://codes.example${path}`),
})

/**
 * A pretend GitHub. `answers` maps a path (after /repos/<repo>) to a function giving the Response; everything
 * the worker asks is recorded with its headers so the tests can see which token went where.
 */
function pretendGitHub(answers) {
  const calls = []
  const real = globalThis.fetch
  globalThis.fetch = async (input, init = {}) => {
    const address = String(input)
    const headers = new Headers(init.headers)
    calls.push({ address, authorization: headers.get('Authorization'), accept: headers.get('Accept') })
    const path = address.replace('https://api.github.com/repos/McCal-Codes/folio-dev-builds', '')
    const handler = answers[path]
    if (!handler) return new Response('unexpected', { status: 500 })
    return handler()
  }
  return { calls, restore: () => { globalThis.fetch = real } }
}

const privateRepo = () => Response.json({ private: true })
const baseAnswers = extra => ({ '': privateRepo, ...extra })

test('a dev code reads a file from the source branch, with the dev token and nothing else', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({
    '/contents/builds.json?ref=source': () => new Response('{"builds":[]}', { headers: { ETag: '"abc"' } }),
  }))
  try {
    const ask = asking(await mint(dev), '/dev/file/builds.json')
    const answer = await devRoutes(ask.request, env, ask.url, NOW)
    assert.equal(answer.status, 200)
    assert.equal(await answer.text(), '{"builds":[]}')
    assert.equal(answer.headers.get('ETag'), '"abc"')
    assert.equal(answer.headers.get('Cache-Control'), 'private, no-store')
    assert.ok(github.calls.length > 0)
    for (const call of github.calls) {
      assert.equal(call.authorization, 'Bearer dev-token', 'only DEV_GITHUB_TOKEN ever goes to GitHub')
    }
    assert.equal(github.calls.at(-1).accept, 'application/vnd.github.raw+json')
  } finally { github.restore() }
})

test('a conditional request that matches GitHub ETag gets a 304', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({
    '/contents/builds.json?ref=source': () => new Response('x', { headers: { ETag: '"abc"' } }),
  }))
  try {
    const code = await mint(dev)
    const request = new Request('https://codes.example/dev/file/builds.json',
      { headers: { Authorization: `Bearer ${code}`, 'If-None-Match': '"abc"' } })
    const answer = await devRoutes(request, env, new URL(request.url), NOW)
    assert.equal(answer.status, 304)
  } finally { github.restore() }
})

test('a supporter code never opens the dev routes, even one that also says dev', async () => {
  const { supporter, env } = await setup()
  const github = pretendGitHub(baseAnswers({}))
  try {
    for (const scopes of [SCOPE_BETA, SCOPE_BETA | SCOPE_DEV, SCOPE_DEV]) {
      const ask = asking(await mint(supporter, { scopes }), '/dev/file/builds.json')
      const answer = await devRoutes(ask.request, env, ask.url, NOW)
      assert.equal(answer.status, 403, `scopes ${scopes}: signed by the supporter key, not the dev key`)
    }
    assert.equal(github.calls.length, 0, 'a refused code costs GitHub nothing')
  } finally { github.restore() }
})

test('a dev-key code without the dev scope is refused', async () => {
  const { dev, env } = await setup()
  const verdict = await checkDevCode(env, await mint(dev, { scopes: SCOPE_BETA }), NOW)
  assert.equal(verdict.ok, false)
  assert.equal(verdict.status, 403)
})

test('a dev code does not open /beta', async () => {
  const { dev, env } = await setup()
  const code = await mint(dev, { scopes: SCOPE_BETA | SCOPE_DEV })
  assert.equal((await checkBetaCode(env, code, NOW)).ok, false)
  const github = pretendGitHub({})
  try {
    const ask = asking(code, '/beta/releases')
    const answer = await betaReleases(ask.request, env, ask.url, NOW)
    assert.equal(answer.status, 403)
    assert.equal(github.calls.length, 0)
  } finally { github.restore() }
})

test('no code, a garbled code, an unsigned code and a withdrawn code are all refused', async () => {
  const { dev, env } = await setup({ DEV_WITHDRAWN: '99, 100' })
  assert.equal((await checkDevCode(env, '', NOW)).status, 400)
  assert.equal((await checkDevCode(env, 'not a code!', NOW)).status, 400)
  const stranger = await throwawayKey()
  assert.equal((await checkDevCode(env, await mint(stranger), NOW)).status, 403)
  assert.equal((await checkDevCode(env, await mint(dev, { serial: 99 }), NOW)).status, 403)
  assert.equal((await checkDevCode(env, await mint(dev, { serial: 7 }), NOW)).ok, true)
})

test('a dev code must end, must not outlast 180 days, and must not count months', async () => {
  const { dev, env } = await setup()
  assert.equal((await checkDevCode(env, await mint(dev, { expiryDay: 0 }), NOW)).status, 403, 'no end date')
  assert.equal((await checkDevCode(env, await mint(dev, { expiryDay: TODAY + 200 }), NOW)).status, 403, 'too long')
  assert.equal((await checkDevCode(env, await mint(dev, { expiryDay: TODAY + 180 }), NOW)).ok, true)
  assert.equal((await checkDevCode(env, await mint(dev, { months: 2 }), NOW)).status, 403, 'months need a database')
  assert.equal((await checkDevCode(env, await mint(dev, { expiryDay: TODAY - 3 }), NOW)).status, 403, 'run out')
  assert.equal((await checkDevCode(env, await mint(dev, { expiryDay: TODAY }), NOW)).ok, true, 'works through its last day')
})

test('missing configuration fails closed', async () => {
  const code = async (dev) => mint(dev)
  for (const missing of ['DEV_KEYS', 'DEV_REPO', 'DEV_GITHUB_TOKEN']) {
    const { dev, env } = await setup()
    const minted = await code(dev)
    env[missing] = ''
    const github = pretendGitHub(baseAnswers({}))
    try {
      const ask = asking(minted, '/dev/file/builds.json')
      const answer = await devRoutes(ask.request, env, ask.url, NOW)
      assert.equal(answer.status, 503, missing)
      assert.equal(github.calls.length, 0, `${missing}: nothing was asked of GitHub`)
    } finally { github.restore() }
  }
})

test('the beta GITHUB_TOKEN is never a fallback for the dev token', async () => {
  const { dev, env } = await setup()
  delete env.DEV_GITHUB_TOKEN
  const github = pretendGitHub(baseAnswers({}))
  try {
    const ask = asking(await mint(dev), '/dev/file/builds.json')
    assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 503)
    assert.equal(github.calls.length, 0)
  } finally { github.restore() }
})

test('a repository that is not private, or whose flag cannot be read, is never served', async () => {
  for (const answer of [() => Response.json({ private: false }), () => Response.json({}), () => new Response('nope', { status: 500 }),
    () => new Response('not json', { status: 200 })]) {
    const { dev, env } = await setup()
    const github = pretendGitHub({ '': answer, '/contents/builds.json?ref=source': () => new Response('secret') })
    try {
      const ask = asking(await mint(dev), '/dev/file/builds.json')
      const result = await devRoutes(ask.request, env, ask.url, NOW)
      assert.equal(result.status, 503)
      assert.ok(!(await result.text()).includes('secret'))
      assert.equal(github.calls.length, 1, 'only the privacy check was made')
    } finally { github.restore() }
  }
})

test('a GitHub that cannot be reached for the privacy check fails closed', async () => {
  const { dev, env } = await setup()
  const real = globalThis.fetch
  globalThis.fetch = async () => { throw new Error('offline') }
  try {
    const ask = asking(await mint(dev), '/dev/file/builds.json')
    assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 503)
  } finally { globalThis.fetch = real }
})

test('the privacy answer is kept ten minutes, then asked again', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({ '/contents/builds.json?ref=source': () => new Response('x') }))
  try {
    const code = await mint(dev)
    const ask = asking(code, '/dev/file/builds.json')
    await devRoutes(ask.request, env, ask.url, NOW)
    await devRoutes(ask.request, env, ask.url, NOW + 9 * 60_000)
    const checks = () => github.calls.filter(call => call.address.endsWith('/folio-dev-builds')).length
    assert.equal(checks(), 1)
    await devRoutes(ask.request, env, ask.url, NOW + 11 * 60_000)
    assert.equal(checks(), 2)
  } finally { github.restore() }
})

test('a file is kept for a minute in the cache, so a refresh costs GitHub nothing', async () => {
  const { dev, env } = await setup()
  const stored = new Map()
  globalThis.caches = {
    default: {
      async match(request) { return stored.get(request.url)?.clone() },
      async put(request, response) { stored.set(request.url, response) },
    },
  }
  const github = pretendGitHub(baseAnswers({ '/contents/builds.json?ref=source': () => new Response('first', { headers: { ETag: '"e"' } }) }))
  try {
    const ask = asking(await mint(dev), '/dev/file/builds.json')
    const one = await devRoutes(ask.request, env, ask.url, NOW)
    const two = await devRoutes(ask.request, env, ask.url, NOW + 1000)
    assert.equal(await one.text(), 'first')
    assert.equal(await two.text(), 'first')
    assert.equal(two.headers.get('ETag'), '"e"')
    assert.equal(github.calls.filter(call => call.address.includes('/contents/')).length, 1)
  } finally { github.restore(); delete globalThis.caches }
})

test('file paths: plain names only, nothing that climbs, hides or escapes', async () => {
  for (const good of ['builds.json', 'tweaks/entry.json', 'tweaks/packages/duet-1.0.0-wip.1.foliopkg', 'a/b/c_d-e.f']) {
    assert.equal(safeFilePath(good), true, good)
  }
  for (const bad of ['', '../x', 'a/../b', 'a//b', '/a', 'a/', './a', 'a/./b', '%2e%2e/x', 'a b', 'a\\b', 'a?x=1', 'a#b', 'é', 'x'.repeat(201)]) {
    assert.equal(safeFilePath(bad), false, JSON.stringify(bad))
  }
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({}))
  try {
    // URL parsing already folds a literal ../ before the worker sees it, so only the encoded forms can reach it.
    for (const path of ['/dev/file/..%2fetc', '/dev/file/', '/dev/file/%2e%2e/x', '/dev/file/a//b']) {
      const ask = asking(await mint(dev), path)
      assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 404, path)
    }
    assert.equal(github.calls.filter(call => call.address.includes('/contents/')).length, 0)
  } finally { github.restore() }
})

test('a missing file is a 404 and a GitHub failure a 502', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({
    '/contents/gone.json?ref=source': () => new Response('', { status: 404 }),
    '/contents/broken.json?ref=source': () => new Response('', { status: 500 }),
  }))
  try {
    const code = await mint(dev)
    for (const [path, status] of [['gone.json', 404], ['broken.json', 502]]) {
      const ask = asking(code, `/dev/file/${path}`)
      assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, status)
    }
  } finally { github.restore() }
})

test('an oversized file is refused rather than carried', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({
    '/contents/big.bin?ref=source': () => new Response('x', { headers: { 'content-length': String(26 * 1024 * 1024) } }),
  }))
  try {
    const ask = asking(await mint(dev), '/dev/file/big.bin')
    assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 502)
  } finally { github.restore() }
})

test('the apk route answers a bodiless 302 to the address GitHub gave, and the token stays here', async () => {
  const { dev, env } = await setup()
  const tag = 'dev-202610241200-duet-abc1234'
  const github = pretendGitHub(baseAnswers({
    [`/releases/tags/${tag}`]: () => Response.json({ draft: false, assets: [
      { id: 11, name: 'SHA256SUMS.txt' }, { id: 12, name: 'FolioDev-duet.apk' }] }),
    '/releases/assets/12': () => new Response(null, { status: 302, headers: { Location: 'https://objects.githubusercontent.com/folio.apk?signed=1' } }),
  }))
  try {
    const ask = asking(await mint(dev), `/dev/apk/${tag}`)
    const answer = await devRoutes(ask.request, env, ask.url, NOW)
    assert.equal(answer.status, 302)
    assert.equal(answer.headers.get('Location'), 'https://objects.githubusercontent.com/folio.apk?signed=1')
    assert.equal(answer.headers.get('Cache-Control'), 'no-store')
    assert.equal(await answer.text(), '', 'the worker never carries the file')
    const asset = github.calls.find(call => call.address.endsWith('/releases/assets/12'))
    assert.equal(asset.accept, 'application/octet-stream')
    assert.equal(asset.authorization, 'Bearer dev-token')
    assert.ok(!JSON.stringify(Object.fromEntries(answer.headers)).includes('token'))
  } finally { github.restore() }
})

test('the sums route redirects to the checksum asset', async () => {
  const { dev, env } = await setup()
  const tag = 'dev-1'
  const github = pretendGitHub(baseAnswers({
    [`/releases/tags/${tag}`]: () => Response.json({ assets: [{ id: 11, name: 'SHA256SUMS.txt' }, { id: 12, name: 'a.apk' }] }),
    '/releases/assets/11': () => new Response(null, { status: 302, headers: { Location: 'https://objects.githubusercontent.com/sums?signed=1' } }),
  }))
  try {
    const ask = asking(await mint(dev), `/dev/sums/${tag}`)
    const answer = await devRoutes(ask.request, env, ask.url, NOW)
    assert.equal(answer.status, 302)
    assert.equal(answer.headers.get('Location'), 'https://objects.githubusercontent.com/sums?signed=1')
  } finally { github.restore() }
})

test('release lookups: bad tags, drafts, missing assets and non-https redirects are all refused', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({
    '/releases/tags/draft': () => Response.json({ draft: true, assets: [{ id: 1, name: 'a.apk' }] }),
    '/releases/tags/noapk': () => Response.json({ assets: [{ id: 1, name: 'SHA256SUMS.txt' }] }),
    '/releases/tags/gone': () => new Response('', { status: 404 }),
    '/releases/tags/down': () => new Response('', { status: 500 }),
    '/releases/tags/plain': () => Response.json({ assets: [{ id: 1, name: 'a.apk' }] }),
    '/releases/assets/1': () => new Response(null, { status: 302, headers: { Location: 'http://insecure.example/a.apk' } }),
  }))
  try {
    const code = await mint(dev)
    const status = async path => {
      const ask = asking(code, path)
      return (await devRoutes(ask.request, env, ask.url, NOW)).status
    }
    assert.equal(await status('/dev/apk/draft'), 404)
    assert.equal(await status('/dev/apk/noapk'), 404)
    assert.equal(await status('/dev/apk/gone'), 404)
    assert.equal(await status('/dev/apk/down'), 502)
    assert.equal(await status('/dev/apk/plain'), 502, 'a redirect that is not https is not followed on')
    assert.equal(await status('/dev/apk/'), 404)
    assert.equal(await status('/dev/apk/a..b%2Fc'), 404)
    assert.equal(await status('/dev/apk/a/b'), 404)
    assert.equal(await status('/dev/other'), 404)
  } finally { github.restore() }
})

test('only GET is answered', async () => {
  const { dev, env } = await setup()
  const request = new Request('https://codes.example/dev/file/builds.json',
    { method: 'POST', headers: { Authorization: `Bearer ${await mint(dev)}` } })
  assert.equal((await devRoutes(request, env, new URL(request.url), NOW)).status, 405)
})

test('a redirect to a host that is not GitHub is not handed to the app', async () => {
  const { dev, env } = await setup()
  const tag = 'dev-202610241200-duet-abc1234'
  for (const location of ['https://objects.example/a.apk', 'https://github.com.evil.example/a.apk', 'https://evil.example/githubusercontent.com']) {
    const github = pretendGitHub(baseAnswers({
      [`/releases/tags/${tag}`]: () => Response.json({ draft: false, assets: [{ id: 12, name: 'FolioDev-duet.apk' }] }),
      '/releases/assets/12': () => new Response(null, { status: 302, headers: { Location: location } }),
    }))
    try {
      const ask = asking(await mint(dev), `/dev/apk/${tag}`)
      assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 502, location)
    } finally { github.restore() }
  }
})

test('a file with no Content-Length is cut off at the cap while it streams', async () => {
  const { dev, env } = await setup()
  const chunk = new Uint8Array(1024 * 1024)
  const github = pretendGitHub(baseAnswers({
    '/contents/big.bin?ref=source': () => new Response(new ReadableStream({
      pull(controller) { controller.enqueue(chunk) },  // never ends
    })),
  }))
  try {
    const ask = asking(await mint(dev), '/dev/file/big.bin')
    assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 502)
  } finally { github.restore() }
})

test('a GitHub that throws is a 502, not a crash', async () => {
  const { dev, env } = await setup()
  const github = pretendGitHub(baseAnswers({
    '/contents/builds.json?ref=source': () => { throw new Error('connection reset') },
  }))
  try {
    const ask = asking(await mint(dev), '/dev/file/builds.json')
    assert.equal((await devRoutes(ask.request, env, ask.url, NOW)).status, 502)
  } finally { github.restore() }
})
