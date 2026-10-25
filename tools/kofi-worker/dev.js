/**
 * The Folio Dev debug source: development builds and work-in-progress tweaks out of a private repository.
 *
 * Only debug builds of Folio (package `com.mccal.folio.dev`) ask here. The credential is a signed code with the `dev`
 * scope, checked against its own public key list (DEV_KEYS), so a supporter code can never open these routes and a
 * dev code can never open /beta. The GitHub token is its own secret (DEV_GITHUB_TOKEN), never the beta token.
 *
 * Routes, all GET with the code in `Authorization: Bearer`:
 *   /dev/file/<path>   a small file from the repository's `source` branch (builds.json, tweaks/...)
 *   /dev/apk/<tag>     302 to GitHub's own short-lived address for that release's .apk; the worker never carries it
 *   /dev/sums/<tag>    the same for the release's SHA256SUMS.txt
 *
 * Everything fails closed: missing configuration, a repository that is not private, or a GitHub error all refuse.
 */
import { codeFrom, decodeCode, endsOn, readCode, signedByFolio } from './beta.js'

const SCOPE_DEV = 4  // bit 4 of the scope byte (BetaCodes.SCOPE_DEV in the app)
const DAY = 86_400_000
/**
 * A dev code is minted for at most 180 days; one that claims more is refused. A day of slack covers minting late.
 * Note what the rule really bounds: a code minted for longer is refused only until its last 181 days, then it works, so
 * it limits how long a leaked code is useful once it is in range, not how far ahead a code may have been dated.
 */
const MAX_CODE_DAYS = 181
const BRANCH = 'source'
const PRIVATE_CHECK_MS = 10 * 60_000
const FILE_CACHE_SECONDS = 60
/** Packages are at most 20 MiB (SourceFiles.kt); a file over this is not something /dev/file serves. */
const MAX_FILE_BYTES = 25 * 1024 * 1024
const SUMS_NAME = 'SHA256SUMS.txt'

/** GitHub's own download hosts. A redirect anywhere else is not handed to the app. */
export const isGitHubHost = host => host === 'github.com' || host.endsWith('.github.com') ||
  host === 'githubusercontent.com' || host.endsWith('.githubusercontent.com')

/** The body in memory, or null once it passes [max] bytes, whether or not the server said how long it was. */
async function readCapped(response, max) {
  if (!response.body) return new Blob([])
  const reader = response.body.getReader()
  const chunks = []
  let total = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    total += value.length
    if (total > max) { await reader.cancel().catch(() => {}); return null }
    chunks.push(value)
  }
  return new Blob(chunks)
}

/** repo -> when it was last seen private. Only a success is remembered, so a recovered GitHub is noticed at once. */
const privateSeen = new Map()

/** Forgets what has been cached in this isolate. For tests. */
export function resetDevCaches() {
  privateSeen.clear()
}

/**
 * Checks a dev code: signed by a DEV_KEYS key, carrying the dev scope, not withdrawn, with an end date within
 * 180 days. A code that buys months is refused: dev codes count no months, so there is no D1 row to keep.
 */
export async function checkDevCode(env, text, now = Date.now()) {
  const keys = String(env.DEV_KEYS ?? '').split(/[,\s]+/).filter(Boolean)
  if (!keys.length) return { ok: false, why: 'no dev key configured', status: 503 }
  const code = readCode(decodeCode(text))
  if (!code) return { ok: false, why: 'not a Folio code', status: 400 }
  if (!(await signedByFolio(code, keys))) return { ok: false, why: 'not signed by Folio', status: 403 }
  if (((code.scopeBits >> SCOPE_DEV) & 1) !== 1) return { ok: false, why: 'this code has no dev access', status: 403 }
  const withdrawn = String(env.DEV_WITHDRAWN ?? '').split(/[,\s]+/).filter(Boolean).map(Number)
  if (withdrawn.includes(code.serial)) return { ok: false, why: 'this code has been withdrawn', status: 403 }
  if (code.months > 0) return { ok: false, why: 'dev codes do not count months', status: 403 }
  const ends = endsOn(code, null)
  if (ends === null) return { ok: false, why: 'dev codes must have an end date', status: 403 }
  if (now > ends + DAY) return { ok: false, why: 'this code has run out', status: 403 }
  if (ends - now > MAX_CODE_DAYS * DAY) return { ok: false, why: 'this code lasts longer than a dev code may', status: 403 }
  return { ok: true, code, ends }
}

/** A path under the `source` branch: plain segments only, nothing that climbs or hides. */
export function safeFilePath(path) {
  if (!path || path.length > 200 || !/^[A-Za-z0-9._/-]+$/.test(path)) return false
  return path.split('/').every(part => part !== '' && part !== '.' && part !== '..')
}

const safeTag = tag => typeof tag === 'string' && tag.length > 0 && tag.length <= 120 && /^[A-Za-z0-9._-]+$/.test(tag) &&
  tag !== '.' && tag !== '..'

function github(env, path, headers = {}) {
  return fetch(`https://api.github.com/repos/${env.DEV_REPO}${path}`, {
    headers: {
      Authorization: `Bearer ${env.DEV_GITHUB_TOKEN}`,
      'User-Agent': 'folio-supporter-codes',
      Accept: 'application/vnd.github+json',
      ...headers,
    },
    redirect: 'manual',
  })
}

/** True only when GitHub says the repository is private. Anything else, including an error, is a no. */
async function repoIsPrivate(env, now) {
  const seen = privateSeen.get(env.DEV_REPO)
  if (seen !== undefined && now - seen < PRIVATE_CHECK_MS) return true
  const answer = await github(env, '').catch(() => null)
  if (!answer || !answer.ok) return false
  const info = await answer.json().catch(() => null)
  if (info?.private !== true) {
    privateSeen.delete(env.DEV_REPO)
    return false
  }
  privateSeen.set(env.DEV_REPO, now)
  return true
}

const fileCache = () => (typeof caches !== 'undefined' ? caches.default : null)

async function devFile(request, env, path) {
  if (!safeFilePath(path)) return new Response('no such file', { status: 404 })
  const cache = fileCache()
  const key = new Request(`https://dev-cache.invalid/${env.DEV_REPO}/${path}`)
  let stored = cache ? await cache.match(key) : null
  if (!stored) {
    const answer = await github(env, `/contents/${path.split('/').map(encodeURIComponent).join('/')}?ref=${BRANCH}`,
      { Accept: 'application/vnd.github.raw+json' })
    if (answer.status === 404) return new Response('no such file', { status: 404 })
    if (!answer.ok) return new Response('GitHub would not answer', { status: 502 })
    const size = Number(answer.headers.get('content-length') ?? 0)
    if (size > MAX_FILE_BYTES) return new Response('that file is too large for this route', { status: 502 })
    const headers = new Headers({ 'Cache-Control': `max-age=${FILE_CACHE_SECONDS}` })
    const etag = answer.headers.get('etag')
    if (etag) headers.set('ETag', etag)
    const body = await readCapped(answer, MAX_FILE_BYTES)
    if (!body) return new Response('that file is too large for this route', { status: 502 })
    stored = new Response(body, { status: 200, headers })
    // Kept for a minute so a page refresh costs no GitHub quota. The copy that is cached is never the one returned.
    if (cache) await cache.put(key, stored.clone())
  }
  const etag = stored.headers.get('ETag')
  const out = new Headers({
    'Content-Type': 'application/octet-stream',
    'Cache-Control': 'private, no-store',
    'X-Content-Type-Options': 'nosniff',
  })
  if (etag) out.set('ETag', etag)
  if (etag && request.headers.get('If-None-Match') === etag) return new Response(null, { status: 304, headers: out })
  return new Response(stored.body, { status: 200, headers: out })
}

/** Hands back GitHub's own short-lived address for one asset of one release; the token stays here. */
async function devAsset(env, tag, pick) {
  if (!safeTag(tag)) return new Response('no such release', { status: 404 })
  const release = await github(env, `/releases/tags/${encodeURIComponent(tag)}`)
  if (release.status === 404) return new Response('no such release', { status: 404 })
  if (!release.ok) return new Response('GitHub would not answer', { status: 502 })
  const info = await release.json().catch(() => null)
  if (info?.draft) return new Response('no such release', { status: 404 })
  const asset = (info?.assets ?? []).find(pick)
  if (!asset || !/^\d+$/.test(String(asset.id))) return new Response('no such file', { status: 404 })
  const answer = await github(env, `/releases/assets/${asset.id}`, { Accept: 'application/octet-stream' })
  const location = answer.headers.get('location')
  let target = null
  try { target = new URL(location ?? '') } catch { target = null }
  if (!target || target.protocol !== 'https:' || !isGitHubHost(target.hostname)) {
    return new Response('GitHub would not hand over the file', { status: 502 })
  }
  // A 302 with no body and no store: the address is short-lived, and the worker never carries the file itself.
  return new Response(null, { status: 302, headers: { Location: location, 'Cache-Control': 'no-store' } })
}

/** The /dev/ routes. Mounted from worker.js for every request whose path starts with /dev/. */
export async function devRoutes(request, env, url, now = Date.now()) {
  if (request.method !== 'GET') return new Response('method not allowed', { status: 405, headers: { Allow: 'GET' } })
  const allowed = await checkDevCode(env, codeFrom(request), now)
  if (!allowed.ok) return new Response(allowed.why, { status: allowed.status })
  if (!env.DEV_REPO || !env.DEV_GITHUB_TOKEN) return new Response('no dev repository configured', { status: 503 })
  if (!(await repoIsPrivate(env, now))) return new Response('the dev repository is not available', { status: 503 })

  const path = url.pathname
  try {
    if (path.startsWith('/dev/file/')) return await devFile(request, env, path.slice('/dev/file/'.length))
    if (path.startsWith('/dev/apk/')) {
      return await devAsset(env, path.slice('/dev/apk/'.length), asset => String(asset.name ?? '').toLowerCase().endsWith('.apk'))
    }
    if (path.startsWith('/dev/sums/')) {
      return await devAsset(env, path.slice('/dev/sums/'.length), asset => asset.name === SUMS_NAME)
    }
  } catch {
    // A GitHub that drops the connection or sends nonsense is a bad gateway, not a crash of the worker.
    return new Response('GitHub would not answer', { status: 502 })
  }
  return new Response('no such route', { status: 404 })
}
