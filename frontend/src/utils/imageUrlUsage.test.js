import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

const albumSource = read('../views/diary/Album.vue')
const albumDetailSource = read('../views/diary/AlbumDetail.vue')
const diaryListSource = read('../views/diary/DiaryList.vue')
const diaryDetailSource = read('../views/diary/DiaryDetail.vue')
const diaryGallerySource = read('../components/diary/DiaryMediaGallery.vue')
const mediaImageSource = read('../components/diary/MediaImage.vue')
const homeSource = read('../views/home/Home.vue')
const timelineSource = read('../views/diary/Timeline.vue')
const anniversariesSource = read('../views/diary/Anniversaries.vue')
const buildConfigSource = read('../../vite.config.js')

test('album and diary grids use signed media urls for rendered tiles and previews', () => {
  for (const source of [albumDetailSource, diaryListSource, diaryGallerySource]) {
    assert.match(source, /MediaImage/)
    assert.doesNotMatch(source, /\.thumbnailUrl|\.contentUrl/)
    assert.doesNotMatch(source, /imagePathList/)
  }
  assert.match(mediaImageSource, /mediaThumbnailUrl/)
  assert.match(mediaImageSource, /mediaPreviewUrl/)
  assert.match(mediaImageSource, /preview-src-list/)
  assert.match(mediaImageSource, /查看原图/)
  assert.match(diaryDetailSource, /DiaryMediaGallery/)
})

test('album covers and lightweight image strips use media asset urls', () => {
  for (const source of [albumSource, homeSource, timelineSource, anniversariesSource]) {
    assert.match(source, /mediaThumbnailUrl/)
    assert.doesNotMatch(source, /\.thumbnailUrl|\.contentUrl/)
    assert.doesNotMatch(source, /`\/images\/\$\{/)
    assert.doesNotMatch(source, /coverImagePath|imagePathList/)
  }
  assert.match(albumSource, /mediaThumbnailUrl\(album\.coverMedia\)/)
  assert.match(homeSource, /loading="lazy"/)
  assert.match(timelineSource, /loading="lazy"/)
})

test('build configuration removes the old image proxy and isolates the editor dependency chunk', () => {
  assert.doesNotMatch(buildConfigSource, /['"]\/images['"]\s*:/)
  assert.match(buildConfigSource, /@tiptap/)
  assert.match(buildConfigSource, /prosemirror-/)
  assert.match(buildConfigSource, /return 'editor-vendor'/)
})
