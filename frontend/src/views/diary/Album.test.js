import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./Album.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/Album.scss', import.meta.url), 'utf8')
].join('\n')
const detailSource = readFileSync(new URL('./AlbumDetail.vue', import.meta.url), 'utf8')
const detailStyle = readFileSync(new URL('./styles/AlbumDetail.scss', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../api/album.js', import.meta.url), 'utf8')
const routerSource = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8')

test('album page renders album groups and album cards without embedding the photo grid', () => {
  assert.match(source, /albumGroups/)
  assert.match(source, /selectedGroup/)
  assert.match(source, /class="album-group-list"/)
  assert.match(source, /class="album-list"/)
  assert.match(source, /openAlbumDetail/)
  assert.doesNotMatch(source, /class="photo-section"/)
  assert.doesNotMatch(source, /class="album-photo-grid"/)
})

test('album page exposes an inline retry state when group loading fails', () => {
  assert.match(source, /groupsError && !loadingGroups/)
  assert.match(source, /class="album-load-error"/)
  assert.match(source, /@click="loadGroups">重新加载/)
  assert.match(source, /catch\s*\{[\s\S]*?groupsError\.value\s*=/)
})

test('album cards use photo covers instead of text initials', () => {
  assert.match(source, /coverImagePath/)
  assert.match(source, /class="\{ empty: !album\.coverImagePath \}"/)
  assert.match(source, /<Picture/)
  assert.doesNotMatch(source, /album\.name\.slice/)
})

test('album detail route owns photo grid and supports system and editable albums', () => {
  assert.match(routerSource, /AlbumDetail:\s*\(\) => import\('@\/views\/diary\/AlbumDetail\.vue'\)/)
  assert.match(routerSource, /path:\s*'\/album\/system\/:systemKey'/)
  assert.match(routerSource, /path:\s*'\/album\/item\/:albumId'/)
  assert.match(detailSource, /album-photo-grid/)
  assert.match(detailSource, /loadAlbumPhotos/)
  assert.match(detailSource, /route\.params\.systemKey/)
  assert.match(detailSource, /route\.params\.albumId/)
})

test('album supports a default favorites system album', () => {
  assert.match(source, /album\.systemKey === 'favorites'/)
  assert.match(source, /\/album\/system\/favorites/)
  assert.match(detailSource, /systemKey === 'favorites'/)
  assert.match(detailSource, /'favorites'/)
  assert.match(detailSource, /route\.params\.systemKey === 'favorites'/)
  assert.match(detailSource, /await loadAlbumPhotos\(\{ force: true \}\)/)
  assert.match(apiSource, /systemKey === 'favorites'/)
  assert.match(apiSource, /\/api\/albums\/system\/favorites\/photos/)
})

test('system albums are shown as readonly while AI and custom albums can be edited', () => {
  assert.match(source, /album\.editable/)
  assert.match(source, /group\.editable/)
  assert.match(source, /openAlbumEditor/)
  assert.match(source, /deleteAlbum/)
  assert.match(source, /系统相册/)
})

test('AI album proposal flow supports date range prompt and review editing', () => {
  assert.match(source, /aiForm/)
  assert.match(source, /startDate/)
  assert.match(source, /endDate/)
  assert.match(source, /prompt/)
  assert.match(source, /generateProposal/)
  assert.match(source, /proposalAlbums/)
  assert.match(source, /confirmProposal/)
  assert.match(source, /removeProposalPhoto/)
  assert.match(source, /discardProposalAlbum/)
  assert.doesNotMatch(source, /type="daterange"/)
  assert.match(source, /class="ai-date-fields"/)
})

test('album API exposes group, photo, and AI proposal endpoints', () => {
  assert.match(apiSource, /getGroups\(options = \{\}\)/)
  assert.match(apiSource, /getSystemPhotos/)
  assert.match(apiSource, /getAlbumPhotos/)
  assert.match(apiSource, /createGroup/)
  assert.match(apiSource, /createAlbum/)
  assert.match(apiSource, /generateProposal/)
  assert.match(apiSource, /confirmProposal/)
})

test('album details load photos in bounded pages with progressive image states', () => {
  assert.match(detailSource, /const PHOTO_PAGE_SIZE = 24/)
  assert.match(detailSource, /getSystemPhotoPage/)
  assert.match(detailSource, /getAlbumPhotoPage/)
  assert.match(detailSource, /Promise\.all\(\[[\s\S]*?findAlbumMeta\(\),[\s\S]*?requestPhotoPage/)
  assert.match(detailSource, /loadMorePhotos/)
  assert.match(detailSource, /已显示 \{\{ photos\.length \}\} \/ \{\{ totalPhotos \}\} 张/)
  assert.match(detailSource, /#placeholder/)
  assert.match(detailSource, /#error/)
  assert.match(detailStyle, /\.image-loading/)
  assert.match(apiSource, /\/photos\/page/)
})

test('album pages display dates with Chinese formatting helpers', () => {
  assert.match(source, /import\s*\{\s*formatChineseDateRange,\s*formatChineseMonth/)
  assert.match(source, /formatChineseDateRange\(currentProposal\.startDate,\s*currentProposal\.endDate\)/)
  assert.match(source, /format="YYYY年MM月DD日"/)
  assert.match(detailSource, /import\s*\{\s*formatChineseDate\s*\}/)
  assert.match(detailSource, /formatChineseDate\(photo\.diaryDate\)/)
})
