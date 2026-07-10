const CACHE_NAME = 'baby-diary-shell-v10'
const SHELL_ASSETS = ['/', '/index.html', '/favicon.svg', '/app-icon.png', '/manifest.webmanifest']
const SHARE_TARGET_PATH = '/share-target'
const SHARE_TARGET_CACHE = 'baby-diary-share-target-v1'
const SHARE_TARGET_META_URL = '/__baby-diary-share-target/latest.json'
const SHARE_TARGET_PREFIX = '/__baby-diary-share-target/'
const MAX_SHARED_FILES = 20
const MAX_SHARED_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_SHARED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(SHELL_ASSETS))
  )
  self.skipWaiting()
})

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.filter(key => key !== CACHE_NAME && key !== SHARE_TARGET_CACHE).map(key => caches.delete(key))
    ))
  )
  self.clients.claim()
})

const clearPreviousSharedFiles = async (cache) => {
  const keys = await cache.keys()
  await Promise.all(
    keys
      .filter(request => new URL(request.url).pathname.startsWith(SHARE_TARGET_PREFIX))
      .map(request => cache.delete(request))
  )
}

const sharedFileCacheKey = (timestamp, index, filename) => {
  const safeName = encodeURIComponent(filename || `shared-image-${index + 1}`)
  return `${SHARE_TARGET_PREFIX}${timestamp}-${index}-${safeName}`
}

const isAcceptedSharedImage = (file) => {
  return file
    && typeof file === 'object'
    && file.type
    && ACCEPTED_SHARED_IMAGE_TYPES.has(file.type.toLowerCase())
    && file.size > 0
    && file.size <= MAX_SHARED_FILE_SIZE
}

const handleShareTarget = async (request) => {
  try {
    const formData = await request.formData()
    const files = formData.getAll('photos')
      .filter(isAcceptedSharedImage)
      .slice(0, MAX_SHARED_FILES)

    const cache = await caches.open(SHARE_TARGET_CACHE)
    await clearPreviousSharedFiles(cache)

    const timestamp = Date.now()
    const metadata = []
    for (const [index, file] of files.entries()) {
      const cacheKey = sharedFileCacheKey(timestamp, index, file.name)
      await cache.put(cacheKey, new Response(file, {
        headers: {
          'Content-Type': file.type || 'application/octet-stream',
          'X-Shared-Filename': encodeURIComponent(file.name || '')
        }
      }))
      metadata.push({
        cacheKey,
        name: file.name || `shared-image-${index + 1}`,
        type: file.type,
        size: file.size,
        lastModified: file.lastModified || timestamp
      })
    }

    await cache.put(SHARE_TARGET_META_URL, new Response(JSON.stringify({
      createdAt: timestamp,
      files: metadata
    }), {
      headers: { 'Content-Type': 'application/json' }
    }))
    return Response.redirect('/diaries/create?shared=1', 303)
  } catch (error) {
    return Response.redirect('/diaries/create?shared=error', 303)
  }
}

self.addEventListener('fetch', event => {
  const requestUrl = new URL(event.request.url)
  if (event.request.method === 'POST' && requestUrl.pathname === SHARE_TARGET_PATH) {
    event.respondWith(handleShareTarget(event.request))
    return
  }

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
