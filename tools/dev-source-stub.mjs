#!/usr/bin/env node
/**
 * A loopback stand-in for the Folio Dev routes of the supporter worker, for trying the debug source without a
 * deployment, a private repository or a network.
 *
 *   node tools/dev-source-stub.mjs <folder> [--port 8787] [--dev-keys <public key>[,<key>]]
 *   adb reverse tcp:8787 tcp:8787
 *   (Folio Dev > Market > Sources > Folio Dev builds > "Use the local stub")
 *
 * <folder> is what `tools/dev-publish.py --to-folder` and `tools/dev-tweaks.py --to-folder` write:
 *
 *   builds.json, builds.json.sig       the signed list of builds
 *   tweaks/                            the signed work-in-progress tweaks source
 *   releases/<tag>/<name>.apk          one build
 *   releases/<tag>/SHA256SUMS.txt      its checksum file
 *
 * It serves the worker's three routes with the worker's own rules: the code is checked by the real `checkDevCode`
 * from tools/kofi-worker/dev.js, and a path that climbs is refused. /dev/apk/<tag> and /dev/sums/<tag> answer 302,
 * like the worker, to an address on this stub that wants no code (standing in for GitHub's short-lived link).
 *
 * The dev keys default to the public half in DevSourceKeys.kt, so the dev code in ~/.folio works as it will against
 * the real worker. Tests pass throwaway keys. The stub listens on 127.0.0.1 only and holds no secret.
 */
import { createHash } from 'node:crypto'
import { createServer } from 'node:http'
import { readFileSync, statSync, createReadStream, readdirSync } from 'node:fs'
import { dirname, join, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { codeFrom } from './kofi-worker/beta.js'
import { checkDevCode, safeFilePath } from './kofi-worker/dev.js'

const here = dirname(fileURLToPath(import.meta.url))
const SUMS_NAME = 'SHA256SUMS.txt'
const safeTag = tag => /^[A-Za-z0-9._-]{1,120}$/.test(tag) && tag !== '.' && tag !== '..'

/** The public dev code key the app compiles in (DevSourceKeys.CODE), so there is one place to change it. */
export function appCodeKey() {
  const text = readFileSync(join(here, '..', 'app/src/devsource/java/com/mccal/folio/DevSourceKeys.kt'), 'utf8')
  const found = text.match(/const val CODE = "([^"]+)"/)
  if (!found) throw new Error('DevSourceKeys.kt has no CODE key')
  return found[1]
}

const inside = (root, file) => file === root || file.startsWith(root + sep)

/**
 * Starts the stub. Resolves with { server, port, log } where `log` records every request (path and whether it carried
 * an Authorization header), so a test can show the code never went to the blob address.
 */
export function startStub({ folder, devKeys, port = 0, now = () => Date.now() }) {
  const root = resolve(folder)
  const log = []
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1')
    const entry = { method: req.method, path: url.pathname, authorization: req.headers.authorization ?? null }
    log.push(entry)
    const end = (status, body = '', headers = {}) => { res.writeHead(status, headers); res.end(body) }
    try {
      if (req.method !== 'GET') return end(405, 'method not allowed', { Allow: 'GET' })
      const origin = `http://${req.headers.host}`

      // The stand-in for GitHub's short-lived link: no code wanted, and none expected.
      if (url.pathname.startsWith('/blob/')) {
        const [tag, name] = url.pathname.slice('/blob/'.length).split('/')
        if (!safeTag(tag) || !name || !/^[A-Za-z0-9._-]+$/.test(name)) return end(404, 'no such file')
        return sendFile(res, join(root, 'releases', tag, name), end)
      }
      if (!url.pathname.startsWith('/dev/')) return end(404, 'no such route')

      const allowed = await checkDevCode({ DEV_KEYS: devKeys.join(',') }, codeFrom(new Request(origin, { headers: req.headers.authorization ? { Authorization: req.headers.authorization } : {} })), now())
      if (!allowed.ok) return end(allowed.status, allowed.why)

      if (url.pathname.startsWith('/dev/file/')) {
        const path = url.pathname.slice('/dev/file/'.length)
        if (!safeFilePath(path)) return end(404, 'no such file')
        const file = resolve(root, path)
        if (!inside(root, file)) return end(404, 'no such file')
        let body
        try { body = readFileSync(file) } catch { return end(404, 'no such file') }
        const etag = `"${createHash('sha1').update(body).digest('hex')}"`
        const headers = { ETag: etag, 'Content-Type': 'application/octet-stream', 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff' }
        if (req.headers['if-none-match'] === etag) return end(304, '', headers)
        return end(200, body, headers)
      }
      const asset = (prefix, pick) => {
        const tag = url.pathname.slice(prefix.length)
        if (!safeTag(tag)) return end(404, 'no such release')
        let names
        try { names = readdirSync(join(root, 'releases', tag)) } catch { return end(404, 'no such release') }
        const name = names.find(pick)
        if (!name) return end(404, 'no such file')
        return end(302, '', { Location: `${origin}/blob/${tag}/${name}`, 'Cache-Control': 'no-store' })
      }
      if (url.pathname.startsWith('/dev/apk/')) return asset('/dev/apk/', n => n.toLowerCase().endsWith('.apk'))
      if (url.pathname.startsWith('/dev/sums/')) return asset('/dev/sums/', n => n === SUMS_NAME)
      return end(404, 'no such route')
    } catch (error) {
      return end(500, String(error?.message ?? error))
    }
  })
  return new Promise(resolveStart => {
    server.listen(port, '127.0.0.1', () => resolveStart({ server, port: server.address().port, log }))
  })
}

function sendFile(res, file, end) {
  let size
  try { size = statSync(file).size } catch { return end(404, 'no such file') }
  res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': size })
  createReadStream(file).pipe(res)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2)
  const option = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback }
  const folder = args.find((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--')))
  if (!folder) { console.error('usage: node tools/dev-source-stub.mjs <folder> [--port 8787] [--dev-keys <public key>]'); process.exit(2) }
  const devKeys = (option('--dev-keys', null) ?? appCodeKey()).split(',').filter(Boolean)
  const { port } = await startStub({ folder, devKeys, port: Number(option('--port', 8787)) })
  console.log(`Folio Dev stub on http://127.0.0.1:${port}, serving ${resolve(folder)}`)
  console.log('Run: adb reverse tcp:' + port + ' tcp:' + port)
}
