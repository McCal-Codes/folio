import { test } from 'node:test'
import assert from 'node:assert/strict'
import worker from './worker.js'

// A throwaway Ed25519 pair, so the tests sign the way Discord does rather than mocking the check away.
const pair = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify'])
const raw = new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey))
const toHex = (bytes) => [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
const env = { DISCORD_PUBLIC_KEY: toHex(raw) }

async function signed(body, { timestamp = '1700000000', key = pair.privateKey } = {}) {
  const signature = new Uint8Array(
    await crypto.subtle.sign({ name: 'Ed25519' }, key, new TextEncoder().encode(timestamp + body)),
  )
  return new Request('https://bot.invalid/', {
    method: 'POST',
    headers: { 'x-signature-ed25519': toHex(signature), 'x-signature-timestamp': timestamp },
    body,
  })
}

test('a signed ping is ponged', async () => {
  const body = JSON.stringify({ type: 1 })
  const response = await worker.fetch(await signed(body), env)
  assert.equal(response.status, 200)
  assert.deepEqual(await response.json(), { type: 1 })
})

test('a body that was not signed with that key is refused', async () => {
  const body = JSON.stringify({ type: 1 })
  const request = await signed(body)
  // The signature is valid, for a different body: this is the tampering the check exists to catch.
  const tampered = new Request(request, { body: JSON.stringify({ type: 2, data: { name: 'version' } }) })
  const response = await worker.fetch(tampered, env)
  assert.equal(response.status, 401)
})

test('a signature from the wrong key is refused', async () => {
  const other = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify'])
  const body = JSON.stringify({ type: 1 })
  const response = await worker.fetch(await signed(body, { key: other.privateKey }), env)
  assert.equal(response.status, 401)
})

test('a request with no signature headers is refused', async () => {
  const response = await worker.fetch(
    new Request('https://bot.invalid/', { method: 'POST', body: JSON.stringify({ type: 1 }) }),
    env,
  )
  assert.equal(response.status, 401)
})

test('nonsense in the signature headers is refused rather than thrown', async () => {
  const response = await worker.fetch(
    new Request('https://bot.invalid/', {
      method: 'POST',
      headers: { 'x-signature-ed25519': 'not-hex', 'x-signature-timestamp': 'nor-this' },
      body: JSON.stringify({ type: 1 }),
    }),
    env,
  )
  assert.equal(response.status, 401)
})

test('without the public key it fails loudly instead of answering unverified', async () => {
  const response = await worker.fetch(await signed(JSON.stringify({ type: 1 })), {})
  assert.equal(response.status, 500)
})

test('GET is something to look at, and other methods are refused', async () => {
  const get = await worker.fetch(new Request('https://bot.invalid/'), env)
  assert.equal(get.status, 200)
  assert.match(await get.text(), /foliolauncher\.com/)

  const put = await worker.fetch(new Request('https://bot.invalid/', { method: 'PUT' }), env)
  assert.equal(put.status, 405)
})

test('a signed command answers with a message, with mentions off', async () => {
  const body = JSON.stringify({ type: 2, data: { name: 'sandwich', options: [] } })
  const response = await worker.fetch(await signed(body), env)
  const answer = await response.json()
  assert.equal(answer.type, 4)
  assert.match(answer.data.content, /do not know sandwich/)
  assert.deepEqual(answer.data.allowed_mentions, { parse: [] })
})
