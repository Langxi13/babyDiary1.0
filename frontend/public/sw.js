const CACHE_NAME = 'baby-diary-shell-v12'
const SHELL_ASSETS = ['/', '/index.html', '/favicon.svg', '/app-icon.png', '/manifest.webmanifest']

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(SHELL_ASSETS))
  )
  self.skipWaiting()
})

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
    ))
  )
  self.clients.claim()
})

self.addEventListener('fetch', event => {
  const requestUrl = new URL(event.request.url)
  if (event.request.method !== 'GET'
    || requestUrl.origin !== self.location.origin
    || requestUrl.pathname.startsWith('/api')
    || requestUrl.pathname.startsWith('/images')) {
    return
  }

  event.respondWith(
    fetch(event.request)
      .then(response => {
        if (response.ok) {
          const cacheUpdate = caches.open(CACHE_NAME)
            .then(cache => cache.put(event.request, response.clone()))
            .catch(() => {})
          event.waitUntil(cacheUpdate)
        }
        return response
      })
      .catch(async () => {
        const cached = await caches.match(event.request)
        if (cached) return cached
        if (event.request.mode === 'navigate') {
          return caches.match('/index.html')
        }
        return Response.error()
      })
  )
})

self.addEventListener('push', event => {
  let payload = { title: 'Baby Diary', body: '你有一条新通知', targetPath: '/notifications' }
  try {
    if (event.data) payload = { ...payload, ...event.data.json() }
  } catch {
    if (event.data) payload.body = event.data.text()
  }
  event.waitUntil(self.registration.showNotification(payload.title, {
    body: payload.body,
    icon: '/app-icon.png',
    badge: '/app-icon.png',
    tag: payload.targetPath || 'baby-diary-notification',
    data: { targetPath: payload.targetPath || '/notifications' }
  }))
})

self.addEventListener('notificationclick', event => {
  event.notification.close()
  const targetPath = event.notification.data?.targetPath || '/notifications'
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const existing = windows.find(client => new URL(client.url).origin === self.location.origin)
    if (existing) {
      await existing.focus()
      existing.navigate(targetPath)
      return
    }
    await self.clients.openWindow(targetPath)
  })())
})
