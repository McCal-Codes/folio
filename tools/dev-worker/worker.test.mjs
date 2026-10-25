/**
 * The Folio Dev source worker serves /dev/ and nothing else, and the payment worker no longer serves /dev/ at all.
 * No account and no network: GitHub is stood in for, and the keys are throwaways made here.
 */
import assert from 'node:assert/strict'
import test from 'node:test'
import kofiWorker from '../kofi-worker/worker.js'
import worker from './worker.js'

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const DAY = 86_400_000
const EPOCH = Date.UTC(2026, 0, 1)
const SCOPE_BETA = 0b00001
const SCOPE_DEV = 0b10000

async function throwawayKey() {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
  let binary = ''
  for (const b of spki) binary += String.fromCharCode(b)
  return { pair, publicKey: btoa(binary) }
}

async function mint(key, scopes = SCOPE_DEV) {
  const expiryDay = Math.floor((Date.now() - EPOCH) / DAY) + 90
  const payload = new Uint8Array([1, scopes, 1, (expiryDay >> 8) & 0xff, expiryDay & 0xff, 0, 0, 0, 5])
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

const asking = (path, code, method = 'GET') =>
  new Request(`https://dev.example${path}`, { method, headers: code ? { Authorization: `Bearer ${code}` } : {} })

async function withWorker(run) {
  const dev = await throwawayKey()
  const env = { DEV_KEYS: dev.publicKey, DEV_REPO: 'McCal-Codes/folio-dev-builds', DEV_GITHUB_TOKEN: 'dev-token' }
  const real = globalThis.fetch
  const asked = []
  globalThis.fetch = async input => {
    const address = String(input)
    asked.push(address)
    if (address.endsWith('/folio-dev-builds')) return Response.json({ private: true })
    if (address.includes('/contents/builds.json')) return new Response('{"format":1}', { headers: { ETag: '"e"' } })
    return new Response('unexpected', { status: 500 })
  }
  try { await run({ dev, env, asked }) } finally { globalThis.fetch = real }
}

test('a good dev code reads a file through the dev worker', async () => {
  await withWorker(async ({ dev, env }) => {
    const answer = await worker.fetch(asking('/dev/file/builds.json', await mint(dev)), env)
    assert.equal(answer.status, 200)
    assert.equal(await answer.text(), '{"format":1}')
  })
})

test('no code, or a code from another key, does not open /dev/', async () => {
  await withWorker(async ({ env }) => {
    assert.equal((await worker.fetch(asking('/dev/file/builds.json'), env)).status, 400)
    const other = await throwawayKey()
    assert.equal((await worker.fetch(asking('/dev/file/builds.json', await mint(other)), env)).status, 403)
    assert.equal((await worker.fetch(asking('/dev/file/builds.json', null, 'POST'), env)).status, 405)
  })
})

test('everything else is a plain 404 on the dev worker, whatever code is held', async () => {
  await withWorker(async ({ dev, env, asked }) => {
    const code = await mint(dev, SCOPE_BETA | SCOPE_DEV)
    for (const path of ['/', '/beta/releases', '/beta/asset/x', '/admin/codes', '/admin/', '/kofi', '/webhook', '/dev', '/devx/file/a']) {
      for (const method of ['GET', 'POST']) {
        const answer = await worker.fetch(asking(path, code, method), env)
        assert.equal(answer.status, 404, `${method} ${path}`)
      }
    }
    assert.deepEqual(asked, [], 'a 404 costs GitHub nothing')
  })
})

test('the payment worker no longer answers /dev/', async () => {
  const answer = await kofiWorker.fetch(new Request('https://codes.example/dev/file/builds.json'), {})
  assert.equal(await answer.text(), 'Folio supporter codes')
  assert.notEqual(answer.status, 400)
  assert.notEqual(answer.status, 403)
})
