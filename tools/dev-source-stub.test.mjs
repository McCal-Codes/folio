/**
 * The loopback stub serves the worker's three routes with the worker's rules. No network beyond 127.0.0.1; the dev key
 * is a throwaway made here, and the code is minted the way scripts/beta-code.py does.
 */
import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { appCodeKey, startStub } from './dev-source-stub.mjs'

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const DAY = 86_400_000
const EPOCH = Date.UTC(2026, 0, 1)

async function throwawayKey() {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
  let binary = ''
  for (const b of spki) binary += String.fromCharCode(b)
  return { pair, publicKey: btoa(binary) }
}

async function mint(key, { scopes = 0b10000, days = 90 } = {}) {
  const expiryDay = Math.floor((Date.now() - EPOCH) / DAY) + days
  const payload = new Uint8Array([1, scopes, 1, (expiryDay >> 8) & 0xff, expiryDay & 0xff, 0, 0, 0, 9])
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

async function withStub(run) {
  const folder = mkdtempSync(join(tmpdir(), 'dev-stub-'))
  mkdirSync(join(folder, 'releases', 'dev-1'), { recursive: true })
  mkdirSync(join(folder, 'tweaks'), { recursive: true })
  writeFileSync(join(folder, 'builds.json'), '{"format":1}')
  writeFileSync(join(folder, 'tweaks', 'entry.json'), '{}')
  writeFileSync(join(folder, 'releases', 'dev-1', 'FolioDev-x.apk'), 'APKBYTES')
  writeFileSync(join(folder, 'releases', 'dev-1', 'SHA256SUMS.txt'), 'abc  FolioDev-x.apk\n')
  const key = await throwawayKey()
  const stub = await startStub({ folder, devKeys: [key.publicKey] })
  try { await run({ key, stub, base: `http://127.0.0.1:${stub.port}`, code: await mint(key) }) } finally { stub.server.close() }
}

const as = code => ({ headers: code ? { Authorization: `Bearer ${code}` } : {}, redirect: 'manual' })

test('no code, a wrong-key code and a no-dev-scope code are refused', async () => {
  await withStub(async ({ key, base }) => {
    assert.equal((await fetch(`${base}/dev/file/builds.json`, as(null))).status, 400)
    const other = await throwawayKey()
    assert.equal((await fetch(`${base}/dev/file/builds.json`, as(await mint(other)))).status, 403)
    assert.equal((await fetch(`${base}/dev/file/builds.json`, as(await mint(key, { scopes: 0b1 })))).status, 403)
  })
})

test('a file is served with an ETag, and a matching If-None-Match gets 304', async () => {
  await withStub(async ({ base, code }) => {
    const first = await fetch(`${base}/dev/file/builds.json`, as(code))
    assert.equal(first.status, 200)
    assert.equal(await first.text(), '{"format":1}')
    const etag = first.headers.get('etag')
    const again = await fetch(`${base}/dev/file/builds.json`, { headers: { Authorization: `Bearer ${code}`, 'If-None-Match': etag } })
    assert.equal(again.status, 304)
    assert.equal((await fetch(`${base}/dev/file/tweaks/entry.json`, as(code))).status, 200)
  })
})

test('a path that climbs or does not exist is a 404', async () => {
  await withStub(async ({ base, code }) => {
    assert.equal((await fetch(`${base}/dev/file/missing.json`, as(code))).status, 404)
    const raw = await fetch(`${base}/dev/file/%2e%2e/secret`, as(code))
    assert.equal(raw.status, 404)
  })
})

test('apk and sums answer a 302 to a blob address that wants no code', async () => {
  await withStub(async ({ base, code, stub }) => {
    const apk = await fetch(`${base}/dev/apk/dev-1`, as(code))
    assert.equal(apk.status, 302)
    const location = apk.headers.get('location')
    assert.ok(location.startsWith(`${base}/blob/dev-1/`))
    const blob = await fetch(location, { redirect: 'manual' })
    assert.equal(await blob.text(), 'APKBYTES')
    assert.equal(stub.log.at(-1).authorization, null, 'the blob request carried no code')
    const sums = await fetch(`${base}/dev/sums/dev-1`, as(code))
    assert.equal(sums.status, 302)
    assert.equal((await fetch(`${base}/dev/apk/nope`, as(code))).status, 404)
  })
})

test('the app compiles in a key the stub reads from DevSourceKeys.kt', () => {
  assert.match(appCodeKey(), /^MFkw/)
})
