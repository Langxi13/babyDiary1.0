import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./DiaryForm.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/DiaryForm.scss', import.meta.url), 'utf8'),
  readFileSync(new URL('../../composables/useDiaryImages.js', import.meta.url), 'utf8')
].join('\n')

test('mobile diary form uses a native file input for photo selection', () => {
  assert.match(source, /class="mobile-native-upload"/)
  assert.match(source, /class="mobile-native-file-input"/)
  assert.match(source, /class="android-native-upload"/)
  assert.match(source, /class="android-native-file-input"/)
  assert.match(source, /type="file"/)
  assert.match(source, /aria-label="选择照片"/)
  assert.match(source, /@change="handleNativeImageChange"/)
  assert.match(source, /const handleNativeImageChange = \(event\) =>/)
  assert.match(source, /fileList\.value = \[\.\.\.fileList\.value,\s*\.\.\.acceptedFiles\]/)
})

test('mobile native upload control uses a full-card native input while Element Plus trigger is hidden on phone', () => {
  assert.match(source, /\.mobile-native-upload\s*\{[\s\S]*?display:\s*none;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.mobile-native-upload\s*\{[\s\S]*?display:\s*flex;/)
  assert.match(source, /\.mobile-native-file-input\s*\{[\s\S]*?display:\s*block;/)
  assert.match(source, /\.mobile-native-file-input\s*\{[\s\S]*?position:\s*absolute;/)
  assert.match(source, /\.mobile-native-file-input\s*\{[\s\S]*?inset:\s*0;/)
  assert.match(source, /\.mobile-native-file-input\s*\{[\s\S]*?z-index:\s*2;/)
  assert.match(source, /\.mobile-native-file-input\s*\{[\s\S]*?opacity:\s*0\.01;/)
  assert.doesNotMatch(source, /showPicker\(|\.click\(\)/)
  assert.match(source, /\.mobile-native-upload:active\s*\{/)
  assert.match(source, /\.mobile-native-upload:has\(\.mobile-native-file-input:active\)\s*\{/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.diary-upload-uploader\s*\{[\s\S]*?display:\s*none;/)
})

test('desktop diary image upload supports drag and click selection', () => {
  assert.match(source, /class="diary-upload-dropzone"/)
  assert.match(source, /:drag="true"/)
  assert.match(source, /:show-file-list="false"/)
  assert.match(source, /拖拽照片到这里/)
  assert.match(source, /点击添加/)
  assert.match(source, /\.diary-upload-dropzone\s*\{[\s\S]*?min-height:\s*72px;/)
  assert.doesNotMatch(source, /list-type="picture-card"/)
})

test('diary image list renders custom sortable thumbnails', () => {
  assert.match(source, /class="image-sort-grid"/)
  assert.match(source, /v-for="\(\s*file,\s*index\s*\) in fileList"/)
  assert.match(source, /class="image-sort-card"/)
  assert.match(source, /@click="handlePreview\(file\)"/)
  assert.match(source, /@click\.stop="moveImage\(index,\s*-1\)"/)
  assert.match(source, /@click\.stop="moveImage\(index,\s*1\)"/)
  assert.match(source, /@click\.stop="removeImageAt\(index\)"/)
  assert.match(source, /const moveImage = \(index,\s*direction\) =>/)
  assert.match(source, /const removeImageAt = \(index\) =>/)
})

test('diary update submits image order for existing and new images', () => {
  assert.match(source, /const imageOrderForFile = \(file,\s*newImageIndex\) =>/)
  assert.match(source, /`existing:\$\{file\.name\}`/)
  assert.match(source, /`new:\$\{newImageIndex\}`/)
  assert.match(source, /newImageIndex \+= 1/)
  assert.match(source, /formData\.append\('imageOrder', orderEntry\)/)
})

test('android mobile upload keeps the native file control visible and offers copy-link fallback dialog', () => {
  assert.match(source, /const isAndroidDevice = ref\(false\)/)
  assert.match(source, /const androidUploadHelpVisible = ref\(false\)/)
  assert.match(source, /\/Android\/i\.test\(navigator\.userAgent/)
  assert.match(source, /v-if="isAndroidDevice"/)
  assert.match(source, /v-else\s+class="mobile-native-upload"/)
  assert.match(source, /class="android-upload-browser-link"/)
  assert.match(source, /@click="androidUploadHelpVisible = true"/)
  assert.match(source, /v-model="androidUploadHelpVisible"/)
  assert.match(source, /class="android-upload-url"/)
  assert.match(source, /ref="androidUploadUrlInput"/)
  assert.match(source, /const androidUploadUrlInput = ref\(null\)/)
  assert.match(source, /selectAndroidUploadUrl\(\)/)
  assert.match(source, /@click="copyBrowserUploadUrl"/)
  assert.match(source, /const copyBrowserUploadUrl = async \(\) =>/)
  assert.match(source, /import\s*\{\s*copyText\s*\}\s*from '@\/utils\/copyText'/)
  assert.match(source, /copyText\(browserUploadUrl\.value,\s*selectAndroidUploadUrl\(\)\)/)
  assert.doesNotMatch(source, /target="_blank"/)
  assert.doesNotMatch(source, /document\.createElement\('textarea'\)/)
  assert.match(source, /\.android-native-file-input\s*\{[\s\S]*?opacity:\s*1;/)
  assert.doesNotMatch(source, /\.android-native-file-input\s*\{[\s\S]*?position:\s*absolute;/)
})

test('diary form imports shared Android gallery images into the create upload list', () => {
  assert.match(source, /import\s*\{\s*consumeSharedImageFiles,\s*isShareTargetEntryRoute,\s*toSharedUploadItem\s*\}\s*from '@\/utils\/shareTargetFiles'/)
  assert.match(source, /const sharedImporting = ref\(false\)/)
  assert.match(source, /const loadSharedImages = async \(\) =>/)
  assert.match(source, /if \(isEdit\.value \|\| !isShareTargetEntryRoute\(route\)\) return/)
  assert.match(source, /const sharedFiles = await consumeSharedImageFiles\(\)/)
  assert.match(source, /fileList\.value = \[\.\.\.fileList\.value,\s*\.\.\.acceptedFiles\.map\(\(file, index\) => toSharedUploadItem\(file, index\)\)\]/)
  assert.match(source, /ElMessage\.success\(`已从系统分享载入 \$\{acceptedFiles\.length\} 张照片`\)/)
  assert.match(source, /router\.replace\(\{ path: '\/diaries\/create' \}\)/)
  assert.match(source, /await loadSharedImages\(\)/)
})
