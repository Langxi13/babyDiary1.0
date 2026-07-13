import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
const nativeImages = read('./nativeImages.js')
const serverSetup = read('../views/auth/ServerSetup.vue')
const diaryForm = read('../views/diary/DiaryForm.vue')
const profile = read('../views/auth/Profile.vue')
const anniversaries = read('../views/diary/Anniversaries.vue')
const spaceEditor = read('../components/workspace/SpaceDiaryEditor.vue')
const manifest = read('../../android/app/src/main/AndroidManifest.xml')

test('native image adapter uses bounded JPEG photo and gallery operations', () => {
  assert.match(nativeImages, /Camera\.chooseFromGallery/)
  assert.match(nativeImages, /Camera\.takePhoto/)
  assert.match(nativeImages, /NATIVE_IMAGE_MAX_DIMENSION = 1920/)
  assert.match(nativeImages, /NATIVE_IMAGE_QUALITY = 85/)
  assert.match(nativeImages, /NATIVE_IMAGE_MAX_BYTES = 10 \* 1024 \* 1024/)
})

test('every image upload surface exposes native image actions', () => {
  for (const source of [diaryForm, profile, anniversaries, spaceEditor]) {
    assert.match(source, /NativeImageActions/)
  }
})

test('Android app uses direct image actions without registering as a system share target', () => {
  assert.doesNotMatch(manifest, /android\.intent\.action\.SEND/)
  assert.doesNotMatch(manifest, /android\.intent\.action\.SEND_MULTIPLE/)
  assert.match(manifest, /android:allowBackup="false"/)
})

test('native onboarding stores only a validated server origin', () => {
  assert.match(serverSetup, /连接服务器/)
  assert.match(serverSetup, /testServerConnection/)
  assert.match(serverSetup, /saveServerOrigin/)
})
