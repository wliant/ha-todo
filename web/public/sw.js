/*
 * Deliberately inert.
 *
 * This worker exists so the app meets the install criteria - a registered
 * service worker with a fetch handler - and for nothing else. It caches
 * nothing: offline use is explicitly out of scope, and a precaching worker
 * would serve a stale index.html behind nginx's back, which is exactly what
 * `Cache-Control: no-store` on index.html exists to prevent.
 *
 * Do not add caching here without replacing
 * docs/adr/0007-installable-pwa-without-offline-caching.md.
 */
self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('fetch', () => {
  // Pass through: not calling respondWith leaves the request to the network.
})
