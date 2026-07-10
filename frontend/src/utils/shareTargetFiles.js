export const SHARE_TARGET_CACHE = 'baby-diary-share-target-v1'
export const SHARE_TARGET_META_URL = '/__baby-diary-share-target/latest.json'

const firstQueryValue = (value) => Array.isArray(value) ? value[0] : value

export const isShareTargetEntryRoute = (route) => firstQueryValue(route?.query?.shared) === '1'

export const toSharedUploadItem = (file, index, timestamp = Date.now()) => ({
  name: file.name || `shared-image-${index + 1}`,
  uid: `shared-${timestamp}-${index}`,
  url: URL.createObjectURL(file),
  raw: file
})

const fileFromBlob = (blob, entry, index) => {
  const name = entry.name || `shared-image-${index + 1}`
  const type = entry.type || blob.type || 'application/octet-stream'
  return new File([blob], name, {
    type,
    lastModified: entry.lastModified || Date.now()
  })
}

export const consumeSharedImageFiles = async ({ cachesApi = globalThis.caches } = {}) => {
  if (!cachesApi?.open) {
    return []
  }

  const cache = await cachesApi.open(SHARE_TARGET_CACHE)
  const metadataResponse = await cache.match(SHARE_TARGET_META_URL)
  if (!metadataResponse) {
    return []
  }

  let metadata = null
  try {
    metadata = await metadataResponse.json()
  } catch (error) {
    await cache.delete(SHARE_TARGET_META_URL)
    return []
  }

  const entries = Array.isArray(metadata?.files) ? metadata.files : []
  const files = []
  for (const [index, entry] of entries.entries()) {
    if (!entry?.cacheKey) continue
    const response = await cache.match(entry.cacheKey)
    if (!response) continue
    const blob = await response.blob()
    files.push(fileFromBlob(blob, entry, index))
    await cache.delete(entry.cacheKey)
  }
  await cache.delete(SHARE_TARGET_META_URL)
  return files
}
