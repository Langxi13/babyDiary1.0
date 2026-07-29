import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

const albumSource = read('../views/diary/Album.vue')
const albumDetailSource = read('../views/diary/AlbumDetail.vue')
const diaryListSource = read('../views/diary/DiaryList.vue')
const diaryDetailSource = read('../views/diary/DiaryDetail.vue')
const homeSource = read('../views/home/Home.vue')
const timelineSource = read('../views/diary/Timeline.vue')
const anniversariesSource = read('../views/diary/Anniversaries.vue')

test('album and diary grids use signed media urls for rendered tiles and previews', () => {
  for (const source of [albumDetailSource, diaryListSource, diaryDetailSource]) {
    assert.match(source, /thumbnailUrl/)
    assert.match(source, /contentUrl/)
    assert.match(source, /preview-src-list/)
    assert.match(source, /\slazy(\s|>)/)
    assert.doesNotMatch(source, /imagePathList/)
  }
})

test('album covers and lightweight image strips use media asset urls', () => {
  for (const source of [albumSource, homeSource, timelineSource, anniversariesSource]) {
    assert.match(source, /thumbnailUrl/)
    assert.doesNotMatch(source, /`\/images\/\$\{/)
    assert.doesNotMatch(source, /coverImagePath|imagePathList/)
  }
  assert.match(albumSource, /album\.coverMedia\.thumbnailUrl\s*\|\|\s*album\.coverMedia\.contentUrl/)
  assert.match(homeSource, /loading="lazy"/)
  assert.match(timelineSource, /loading="lazy"/)
})
