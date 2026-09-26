import { test } from 'node:test'
import assert from 'node:assert/strict'
import worker, { createWorker } from './worker.js'

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

test('a quick answer goes back in place, with no follow-up', async () => {
  const sent = []
  const fast = createWorker({
    sources: { release: async () => ({ version: '0.6.6', published: '2026-09-21', apk: 'x', size: '5.1 MB' }) },
    send: async (...args) => { sent.push(args); return new Response(null, { status: 200 }) },
    wait: 500,
  })
  const body = JSON.stringify({ type: 2, application_id: 'app', token: 'tok', data: { name: 'version' } })
  const answer = await (await fast.fetch(await signed(body), env, { waitUntil() {} })).json()
  assert.equal(answer.type, 4)
  assert.match(answer.data.content, /Folio 0\.6\.6/)
  assert.equal(sent.length, 0)
})

test('a slow answer is deferred inside the deadline, then edited in with the interaction token', async () => {
  const sent = []
  let finished
  const slow = createWorker({
    sources: {
      release: () => new Promise((resolve) =>
        setTimeout(() => resolve({ version: '0.6.6', published: '2026-09-21', apk: 'x', size: '5.1 MB' }), 150)),
    },
    send: async (url, init) => { sent.push({ url, init }); return new Response(null, { status: 200 }) },
    wait: 20,
  })
  const body = JSON.stringify({ type: 2, application_id: 'app123', token: 'tok456', data: { name: 'version' } })
  const started = Date.now()
  const response = await slow.fetch(await signed(body), env, { waitUntil(p) { finished = p } })
  assert.ok(Date.now() - started < 120, 'the deferral has to come back before the slow answer does')
  assert.deepEqual(await response.json(), { type: 5 })

  await finished
  assert.equal(sent.length, 1)
  assert.equal(sent[0].url, 'https://discord.com/api/v10/webhooks/app123/tok456/messages/@original')
  assert.equal(sent[0].init.method, 'PATCH')
  const edited = JSON.parse(sent[0].init.body)
  assert.match(edited.content, /Folio 0\.6\.6/)
  assert.deepEqual(edited.allowed_mentions, { parse: [] })
})

test('every command says where it can be used, and /redeem only works in a server', async () => {
  const { COMMANDS } = await import('./commands.mjs')
  for (const command of COMMANDS) {
    if (command.name === 'redeem') {
      assert.deepEqual(command.integration_types, [0])
      assert.deepEqual(command.contexts, [0])
      continue
    }
    assert.deepEqual(command.integration_types, [0, 1], command.name)
    assert.deepEqual(command.contexts, [0, 1, 2], command.name)
  }
})

test("/redeem's answer is private, whether it comes back in place or after a deferral", async () => {
  const redeemBody = JSON.stringify({
    type: 2, application_id: 'app', token: 'tok', guild_id: 'somewhere-else',
    member: { user: { id: 'u1' } }, data: { name: 'redeem', options: [{ name: 'code', value: 'ABCDEFGHJKMNPQRSTVWX' }] },
  })
  const quick = createWorker({ discord: () => ({}) })
  const inPlace = await (await quick.fetch(await signed(redeemBody), { ...env, GUILD_ID: 'folio' }, {})).json()
  assert.equal(inPlace.type, 4)
  assert.equal(inPlace.data.flags & 64, 64, 'the reply must be ephemeral')
  assert.match(inPlace.data.content, /Folio Community server/)

  // A zero wait forces the deferral path, which is the one that decides privacy for the edit that follows.
  let finished
  const slow = createWorker({ discord: () => ({}), wait: 0, send: async () => new Response(null, { status: 200 }) })
  const deferred = await (await slow.fetch(await signed(redeemBody), { ...env, GUILD_ID: 'folio' }, { waitUntil(p) { finished = p } })).json()
  await finished
  if (deferred.type === 5) assert.equal(deferred.data?.flags, 64, 'a public deferral would make the answer public')
})

test('the read-only answers stay public', async () => {
  const body = JSON.stringify({ type: 2, data: { name: 'sandwich', options: [] } })
  const answer = await (await worker.fetch(await signed(body), env)).json()
  assert.equal(answer.data.flags & 64, 0)
})
