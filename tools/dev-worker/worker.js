/**
 * Folio Dev source worker: serves ONLY /dev/* (see ../kofi-worker/dev.js for the routes and their rules).
 *
 * It is separate from the supporter worker on purpose. That one takes Ko-fi payments, holds the codes database and sends
 * email; this one holds a read-only token for one private repository and nothing else. Anything that is not /dev/ gets a
 * plain 404, including /beta, /admin and the Ko-fi webhook, so a mistake in routing cannot expose them here.
 */
import { devRoutes } from '../kofi-worker/dev.js'

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    if (url.pathname.startsWith('/dev/')) return devRoutes(request, env, url)
    return new Response('not found', { status: 404 })
  },
}
