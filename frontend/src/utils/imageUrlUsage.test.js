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

test('album and diary grids use thumbnails for rendered tiles and originals for previews', () => {
  for (const source of [albumDetailSource, diaryListSource, diaryDetailSource]) {
    assert.match(source, /import\s*\{\s*originalImageUrl,\s*thumbnailImageUrl\s*\}/)
    assert.match(source, /:src="thumbnailImageUrl\(/)
    assert.match(source, /originalImageUrl/)
    assert.match(source, /preview-src-list/)
    assert.match(source, /\slazy(\s|>)/)
  }
})

test('album covers and lightweight image strips use thumbnail urls', () => {
  for (const source of [albumSource, homeSource, timelineSource, anniversariesSource]) {
    assert.match(source, /thumbnailImageUrl/)
    assert.doesNotMatch(source, /`\/images\/\$\{/)
  }
  assert.match(albumSource, /backgroundImage:\s*`url\(\$\{thumbnailImageUrl\(album\.coverImagePath\)\}\)`/)
  assert.match(homeSource, /loading="lazy"/)
  assert.match(timelineSource, /loading="lazy"/)
})
