import assert from 'node:assert/strict'
import test from 'node:test'

import { originalImageUrl, thumbnailImageUrl } from './imageUrl.js'

test('originalImageUrl encodes filenames under the images route', () => {
  assert.equal(originalImageUrl('宝宝 照片.jpg'), '/images/%E5%AE%9D%E5%AE%9D%20%E7%85%A7%E7%89%87.jpg')
})

test('thumbnailImageUrl points at the 480px thumbnail copy', () => {
  assert.equal(thumbnailImageUrl('photo.jpg'), '/images/thumbs/480/photo.jpg')
  assert.equal(thumbnailImageUrl('西南大学试点研学路线图.png'), '/images/thumbs/480/%E8%A5%BF%E5%8D%97%E5%A4%A7%E5%AD%A6%E8%AF%95%E7%82%B9%E7%A0%94%E5%AD%A6%E8%B7%AF%E7%BA%BF%E5%9B%BE.png')
})

test('image url helpers return an empty string for blank filenames', () => {
  assert.equal(originalImageUrl(''), '')
  assert.equal(thumbnailImageUrl(null), '')
})
