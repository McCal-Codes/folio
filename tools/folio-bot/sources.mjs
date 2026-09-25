/**
 * Where the bot's answers come from. Every one is a file the project already publishes, so the bot cannot tell
 * somebody something the app does not do.
 *
 * Nothing is written down twice: the changelog is the app's CHANGELOG.md, the roadmap is the file Settings › Help ›
 * Roadmap reads, the tweaks are the source index the Market reads, the screens are what ScreenMatrixTest runs.
 */
const REPO = 'McCal-Codes/folio'
const RAW = `https://raw.githubusercontent.com/${REPO}/main`
const SITE = 'https://foliolauncher.com'

/** Five minutes. Long enough that a busy channel does not hammer GitHub, short enough that a release shows up. */
const TTL = 300

async function get(url, { json = false } = {}) {
  const response = await fetch(url, {
    headers: { accept: json ? 'application/json' : 'text/plain', 'user-agent': 'folio-bot' },
    cf: { cacheTtl: TTL, cacheEverything: true },
  })
  if (!response.ok) throw new Error(`${url} said ${response.status}`)
  return json ? response.json() : response.text()
}

export const sources = {
  async release() {
    const release = await get(`https://api.github.com/repos/${REPO}/releases/latest`, { json: true })
    const apk = (release.assets ?? []).find((asset) => asset.name?.endsWith('.apk'))
    return {
      version: String(release.tag_name ?? '').replace(/^v/, ''),
      published: (release.published_at ?? '').slice(0, 10),
      url: release.html_url,
      apk: apk?.browser_download_url ?? release.html_url,
      size: apk ? `${(apk.size / 1048576).toFixed(1)} MB` : '',
    }
  },

  /** Every released section of CHANGELOG.md, newest first. */
  async changelog() {
    const markdown = await get(`${RAW}/CHANGELOG.md`)
    const entries = []
    for (const block of markdown.split(/^## /m).slice(1)) {
      const heading = block.slice(0, block.indexOf('\n'))
      const match = /^\[?([0-9]+\.[0-9]+\.[0-9]+[^\]\s]*)\]?\s*(?:-|–)?\s*([0-9]{4}-[0-9]{2}-[0-9]{2})?/.exec(heading)
      if (!match) continue
      entries.push({ version: match[1], date: match[2] ?? '', body: block.slice(heading.length).trim() })
    }
    return entries
  },

  async roadmap() {
    const file = await get(`${RAW}/app/src/main/assets/roadmap.json`, { json: true })
    return (file.sections ?? []).map((section) => ({
      name: section.release ? `Folio ${section.release}` : section.title,
      shipped: Boolean(section.release),
      items: (section.items ?? []).map(({ title, detail, status }) => ({ title, detail, status })),
    }))
  },

  async tweaks() {
    const index = await get(`${RAW}/docs/sdk/source/index.json`, { json: true })
    return (index.packages ?? [])
      .map(({ manifest }) => manifest)
      .filter((manifest) => manifest?.section === 'tweaks')
      .map((manifest) => ({
        id: manifest.id,
        name: manifest.name,
        description: manifest.description ?? '',
        version: manifest.version,
        screens: manifest.screens ?? [],
      }))
  },

  async screens() {
    const matrix = await get(`${RAW}/app/src/test/resources/screen-matrix.json`, { json: true })
    return (matrix.devices ?? []).map(({ name, width, height, group }) => ({ name, width, height, group }))
  },

  /** The help pages, from the site's own sitemap, so a new page needs no change here. */
  async help() {
    const xml = await get(`${SITE}/sitemap-0.xml`)
    return [...xml.matchAll(/<loc>([^<]+\/help\/[^<]*)<\/loc>/g)]
      .map(([, url]) => url)
      .filter((url) => !url.endsWith('/help/'))
      .map((url) => {
        const slug = url.replace(/\/$/, '').split('/').pop()
        return { slug, url, title: slug.replace(/-/g, ' ').replace(/^./, (c) => c.toUpperCase()) }
      })
  },
}

export { SITE, REPO }
