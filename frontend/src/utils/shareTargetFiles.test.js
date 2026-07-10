import test from 'node:test'
import assert from 'node:assert/strict'

import {
  SHARE_TARGET_CACHE,
  SHARE_TARGET_META_URL,
  consumeSharedImageFiles,
  isShareTargetEntryRoute,
  toSharedUploadItem
} from './shareTargetFiles.js'

const createFakeCaches = (metadata, fileResponses = {}) => {
  const deleted = []
  const cache = {
    async match(key) {
      if (key === SHARE_TARGET_META_URL && metadata) {
        return {
          async json() {
            return metadata
          }
        }
      }
      return fileResponses[key] || null
    },
    async delete(key) {
      deleted.push(key)
      return true
    }
  }

  return {
    deleted,
    async open(name) {
      assert.equal(name, SHARE_TARGET_CACHE)
      return cache
    }
  }
}

test('isShareTargetEntryRoute detects shared create routes', () => {
  assert.equal(isShareTargetEntryRoute({ query: { shared: '1' } }), true)
  assert.equal(isShareTargetEntryRoute({ query: { shared: ['1'] } }), true)
  assert.equal(isShareTargetEntryRoute({ query: { shared: 'error' } }), false)
  assert.equal(isShareTargetEntryRoute({ query: {} }), false)
})

test('toSharedUploadItem converts shared files into Element Plus upload items', () => {
  const previousUrl = globalThis.URL
  globalThis.URL = {
    createObjectURL(file) {
      return `blob:${file.name}`
    }
  }
  try {
    const file = new File(['data'], 'photo.jpg', { type: 'image/jpeg' })
    const item = toSharedUploadItem(file, 2, 1783090000000)

    assert.deepEqual(item, {
      name: 'photo.jpg',
      uid: 'shared-1783090000000-2',
      url: 'blob:photo.jpg',
      raw: file
    })
  } finally {
    globalThis.URL = previousUrl
  }
})

test('consumeSharedImageFiles reads cached share target files and clears them', async () => {
  const metadata = {
    files: [
      {
        cacheKey: '/__baby-diary-share-target/1-photo.jpg',
        name: 'photo.jpg',
        type: 'image/jpeg',
        lastModified: 1783090000000
      }
    ]
  }
  const fakeCaches = createFakeCaches(metadata, {
    '/__baby-diary-share-target/1-photo.jpg': {
      async blob() {
        return new Blob(['image-data'], { type: 'image/jpeg' })
      }
    }
  })

  const files = await consumeSharedImageFiles({ cachesApi: fakeCaches })

  assert.equal(files.length, 1)
  assert.equal(files[0].name, 'photo.jpg')
  assert.equal(files[0].type, 'image/jpeg')
  assert.equal(fakeCaches.deleted.includes('/__baby-diary-share-target/1-photo.jpg'), true)
  assert.equal(fakeCaches.deleted.includes(SHARE_TARGET_META_URL), true)
})

test('consumeSharedImageFiles returns an empty list when Cache API is unavailable', async () => {
  const files = await consumeSharedImageFiles({ cachesApi: null })
  assert.deepEqual(files, [])
})
