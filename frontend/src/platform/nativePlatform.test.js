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

test('native image adapter stages originals for streaming uploads', () => {
  assert.match(nativeImages, /Camera\.chooseFromGallery/)
  assert.match(nativeImages, /Camera\.takePhoto/)
  assert.match(nativeImages, /NATIVE_IMAGE_QUALITY = 100/)
  assert.match(nativeImages, /NATIVE_IMAGE_MAX_BYTES = 25 \* 1024 \* 1024/)
  assert.match(nativeImages, /Filesystem\.copy/)
  assert.match(nativeImages, /kind: 'native-uri'/)
  assert.doesNotMatch(nativeImages, /targetWidth|targetHeight/)
})

test('every image upload surface exposes native image actions', () => {
  for (const source of [diaryForm, profile, anniversaries, spaceEditor]) {
    assert.match(source, /NativeImageActions/)
  }
})

test('shared diary editor releases staged native images from every close action', () => {
  assert.match(spaceEditor, /@close="closeEditor"/)
  assert.match(spaceEditor, /<el-button @click="closeEditor">取消<\/el-button>/)
  assert.match(spaceEditor, /const closeEditor = \(\) => \{\s*discardSelectedFiles\(\)\s*emit\('update:modelValue', false\)\s*\}/)
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
