<template>
  <el-drawer
    :model-value="modelValue"
    :title="diary ? '编辑共同日记' : '写共同日记'"
    class="space-diary-drawer"
    :size="drawerSize"
    destroy-on-close
    @close="closeEditor"
  >
    <el-form class="space-diary-form" label-position="top" @submit.prevent="save">
      <div class="editor-grid">
        <el-form-item label="日期">
        <el-date-picker v-model="form.diaryDate" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" :clearable="false" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-segmented v-model="form.visibility" :options="visibilityOptions" />
        </el-form-item>
      </div>

      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="255" show-word-limit placeholder="这一页想叫什么名字" />
      </el-form-item>

      <el-form-item label="心情">
        <diary-mood-picker v-model="form.mood" :moods="moods" clearable />
      </el-form-item>

      <el-form-item v-if="templates.length" label="模板">
        <el-select v-model="selectedTemplate" clearable placeholder="选择模板" @change="applyTemplate">
          <el-option v-for="template in templates" :key="template.id" :label="template.name" :value="template.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="正文">
        <el-input v-model="form.contentHtml" type="textarea" :autosize="{ minRows: 10, maxRows: 24 }" maxlength="1000000" />
      </el-form-item>

      <el-form-item v-if="tags.length" label="标签">
        <el-checkbox-group v-model="form.tagIds" class="tag-options">
          <el-checkbox-button v-for="tag in tags" :key="tag.id" :value="tag.id">
            <span class="tag-swatch" :style="{ background: tag.color }" />{{ tag.name }}
          </el-checkbox-button>
        </el-checkbox-group>
      </el-form-item>

      <section class="media-picker">
        <div class="media-picker-head">
          <div>
            <strong>照片与音视频</strong>
            <span v-if="files.length">{{ files.length }} 个文件</span>
          </div>
          <label v-if="!nativeApp" class="media-add-button">
            <el-icon><Plus /></el-icon>
            添加
            <input type="file" multiple accept="image/*,audio/*,video/mp4,video/webm,video/quicktime" @change="selectFiles" />
          </label>
          <label v-else class="media-add-button">
            <el-icon><Plus /></el-icon>
            音视频
            <input type="file" multiple accept="audio/*,video/mp4,video/webm,video/quicktime" @change="selectFiles" />
          </label>
        </div>
        <native-image-actions v-if="nativeApp" compact :limit="remainingMediaSlots" @selected="selectNativeImages" />
        <div v-if="files.length" class="selected-media-list">
          <div
            v-for="(file, index) in files"
            :key="file.key"
            class="selected-media"
            draggable="true"
            @dragstart="dragIndex = index"
            @dragover.prevent
            @drop="moveFile(index)"
          >
            <el-icon class="drag-handle"><Rank /></el-icon>
            <div class="media-file-copy">
              <strong>{{ file.raw.name }}</strong>
              <span>{{ formatBytes(file.raw.size) }}</span>
            </div>
            <button type="button" title="移除" aria-label="移除文件" @click="removeSelectedFile(index)">
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </div>
      </section>

      <div class="privacy-row">
        <div>
          <strong>日记锁</strong>
          <span>打开时查看正文需要再次验证密码</span>
        </div>
        <el-switch v-model="form.locked" />
      </div>
    </el-form>

    <template #footer>
      <div class="editor-actions">
        <el-button @click="closeEditor">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          <el-icon><Check /></el-icon>
          {{ offline ? '保存到本机' : '保存日记' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElCheckboxButton, ElCheckboxGroup } from 'element-plus/es/components/checkbox/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElSegmented } from 'element-plus/es/components/segmented/index.mjs'
import { ElSwitch } from 'element-plus/es/components/switch/index.mjs'
import { Check, Close, Plus, Rank } from '@element-plus/icons-vue'
import { diaryApi } from '@/api/diary'
import { mediaApi } from '@/api/media'
import { queueOfflineDiaryOperation, queueOfflineOperation } from '@/utils/offlineDb'
import { withStepUpRetry } from '@/utils/stepUp'
import NativeImageActions from '@/components/mobile/NativeImageActions.vue'
import DiaryMoodPicker from '@/components/diary/DiaryMoodPicker.vue'
import { isNativeApp } from '@/platform/runtimeConfig'
import { releaseNativeImage } from '@/platform/nativeImages'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/checkbox/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/drawer/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/segmented/style/css.mjs'
import 'element-plus/es/components/switch/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'

const props = defineProps({
  modelValue: Boolean,
  spaceId: { type: String, required: true },
  diary: { type: Object, default: null },
  tags: { type: Array, default: () => [] },
  templates: { type: Array, default: () => [] },
  stepUpToken: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const form = reactive(emptyForm())
const files = ref([])
const saving = ref(false)
const selectedTemplate = ref('')
const dragIndex = ref(-1)
const offline = computed(() => typeof navigator !== 'undefined' && !navigator.onLine)
const nativeApp = isNativeApp()
const MAX_SELECTED_MEDIA = 20
const remainingMediaSlots = computed(() => Math.max(0, MAX_SELECTED_MEDIA - files.value.length))
const drawerSize = computed(() => window.innerWidth <= 768 ? '100%' : 'min(720px, 92vw)')
const visibilityOptions = [
  { label: '共同可见', value: 'SHARED' },
  { label: '仅自己', value: 'PRIVATE' }
]
const uploadSource = item => item.raw?.kind === 'native-uri'
  ? item.raw
  : { file: item.raw, uploadId: item.uploadId }
const moods = [
  { key: 'happy', emoji: '😊', label: '开心' },
  { key: 'calm', emoji: '😌', label: '平静' },
  { key: 'loved', emoji: '🥰', label: '幸福' },
  { key: 'excited', emoji: '🤩', label: '期待' },
  { key: 'tired', emoji: '😴', label: '疲惫' },
  { key: 'sad', emoji: '🥺', label: '难过' }
]

watch(() => props.modelValue, visible => {
  if (!visible) return
  Object.assign(form, emptyForm(), props.diary ? {
    title: props.diary.title,
    diaryDate: props.diary.diaryDate,
    contentHtml: props.diary.contentHtml || props.diary.contentText || '',
    mood: props.diary.mood || '',
    visibility: props.diary.visibility || 'SHARED',
    locked: !!props.diary.locked,
    tagIds: props.diary.tags?.map(tag => tag.id) || []
  } : {})
  files.value = []
  selectedTemplate.value = ''
})

const save = async () => {
  if (saving.value) return
  if (!form.title.trim() || !form.contentHtml.trim()) {
    ElMessage.warning('请填写标题和正文')
    return
  }
  if (offline.value && form.locked) {
    ElMessage.warning('锁定日记需要联网完成二次验证')
    return
  }
  saving.value = true
  const entityId = props.diary?.id || crypto.randomUUID()
  const creating = !props.diary || (props.diary.pending && props.diary.pendingAction === 'CREATE')
  const payload = {
    clientId: creating ? entityId : undefined,
    title: form.title.trim(),
    diaryDate: form.diaryDate,
    contentHtml: form.contentHtml,
    mood: form.mood || null,
    visibility: form.visibility,
    locked: form.locked,
    baseVersion: props.diary?.version,
    tagIds: form.tagIds,
    mediaIds: props.diary?.media?.map(item => item.id).filter(Boolean) || []
  }
  const uploadedAssetIds = []
  let uploadsCompleted = false
  try {
    let saved
    if (offline.value) {
      await queueDiary(entityId, payload)
      saved = { ...props.diary, ...payload, id: entityId, version: props.diary?.version || 0, pending: true, pendingAction: creating ? 'CREATE' : 'UPDATE' }
    } else {
      try {
        for (const item of files.value) {
          const upload = await mediaApi.uploadSource(props.spaceId, uploadSource(item))
          uploadedAssetIds.push(upload.id)
        }
        payload.mediaIds = [...payload.mediaIds, ...uploadedAssetIds]
        uploadsCompleted = true
        saved = creating
          ? await diaryApi.create(props.spaceId, payload)
          : await withStepUpRetry(token => diaryApi.update(
            props.spaceId, entityId, payload, props.diary.version, token || props.stepUpToken
          ))
      } catch (error) {
        if (!error.response) {
          if (!uploadsCompleted) {
            await Promise.allSettled(uploadedAssetIds.map(mediaId => mediaApi.remove(props.spaceId, mediaId)))
          }
          await queueDiary(entityId, payload)
          saved = { ...props.diary, ...payload, id: entityId, version: props.diary?.version || 0, pending: true, pendingAction: creating ? 'CREATE' : 'UPDATE' }
        } else {
          await Promise.allSettled(uploadedAssetIds.map(mediaId => mediaApi.remove(props.spaceId, mediaId)))
          throw error
        }
      }
    }
    if ((offline.value || saved.pending) && !uploadsCompleted) await queueMedia(entityId)
    ElMessage.success(offline.value || saved.pending ? '已保存到本机，联网后自动同步' : '日记已保存')
    if (uploadsCompleted || (!offline.value && !saved.pending)) {
      await Promise.allSettled(files.value.map(item => releaseNativeImage(item.raw)))
    }
    files.value = []
    emit('saved', saved)
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

const queueDiary = (entityId, payload) => queueOfflineDiaryOperation({
  id: crypto.randomUUID(),
  kind: 'diary',
  spaceId: props.spaceId,
  action: !props.diary || (props.diary.pending && props.diary.pendingAction === 'CREATE') ? 'CREATE' : 'UPDATE',
  entityId,
  baseVersion: props.diary?.version,
  payload,
  localSnapshot: { ...props.diary, ...payload, id: entityId }
})

const queueMedia = async diaryId => {
  for (const item of files.value) {
    await queueOfflineOperation({
      id: crypto.randomUUID(),
      kind: 'media',
      spaceId: props.spaceId,
      diaryId,
      filename: item.raw.name,
      file: item.raw,
      source: uploadSource(item),
      uploadId: item.uploadId
    })
  }
}

const selectFiles = event => {
  appendSelectedFiles(Array.from(event.target.files || []))
  event.target.value = ''
}

const appendSelectedFiles = selected => {
  const available = remainingMediaSlots.value
  const selectedFiles = Array.from(selected || [])
  if (selectedFiles.length > available) {
    ElMessage.warning(`每次编辑最多添加 ${MAX_SELECTED_MEDIA} 个媒体文件`)
  }
  const next = selectedFiles
    .slice(0, available)
    .map(raw => ({
      key: `${raw.name}-${raw.size}-${raw.lastModified}-${crypto.randomUUID()}`,
      uploadId: raw.uploadId || crypto.randomUUID(),
      raw
    }))
  files.value.push(...next)
}

const selectNativeImages = selected => appendSelectedFiles(selected)

const removeSelectedFile = index => {
  const [removed] = files.value.splice(index, 1)
  releaseNativeImage(removed?.raw).catch(() => {})
}

const discardSelectedFiles = () => {
  files.value.forEach(item => releaseNativeImage(item.raw).catch(() => {}))
  files.value = []
}

const closeEditor = () => {
  discardSelectedFiles()
  emit('update:modelValue', false)
}

const moveFile = targetIndex => {
  if (dragIndex.value < 0 || dragIndex.value === targetIndex) return
  const [item] = files.value.splice(dragIndex.value, 1)
  files.value.splice(targetIndex, 0, item)
  dragIndex.value = -1
}

const applyTemplate = templateId => {
  const template = props.templates.find(item => item.id === templateId)
  if (!template) return
  if (!form.title) form.title = template.name
  const text = new DOMParser().parseFromString(template.contentHtml || '', 'text/html').body.innerText
  form.contentHtml = form.contentHtml ? `${form.contentHtml}\n\n${text}` : text
}

function emptyForm() {
  const now = new Date()
  const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return { title: '', diaryDate: date, contentHtml: '', mood: '', visibility: 'SHARED', locked: false, tagIds: [] }
}

function formatBytes(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onBeforeUnmount(discardSelectedFiles)
</script>

<style scoped lang="scss">
.space-diary-form {
  display: grid;
  gap: 20px;

  :deep(.el-form-item) { margin-bottom: 0; }
  :deep(.el-form-item__label) { padding-bottom: 8px; line-height: 1.35; font-weight: 650; color: #49413d; }
  :deep(.el-input), :deep(.el-select), :deep(.el-date-editor), :deep(.el-segmented) { width: 100%; }
}

.editor-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.tag-options { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-swatch { width: 9px; height: 9px; margin-right: 6px; border-radius: 50%; display: inline-block; }
.media-picker { border: 1px solid #e8ddd7; border-radius: 8px; padding: 14px; background: #fbfaf8; }
.media-picker-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.media-picker-head > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.media-picker-head strong { color: #49413d; }
.media-picker-head span { color: #918680; font-size: 12px; }
.media-add-button { min-height: 36px; padding: 0 12px; border: 1px solid #d4b0a6; border-radius: 8px; color: #92594f; background: #fff; display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
.media-add-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.selected-media-list { margin-top: 12px; display: grid; gap: 7px; }
.selected-media { min-height: 48px; padding: 6px 8px; border: 1px solid #e7ddd8; border-radius: 7px; background: #fff; display: grid; grid-template-columns: 28px minmax(0, 1fr) 34px; align-items: center; gap: 7px; }
.drag-handle { color: #a79b94; cursor: grab; }
.media-file-copy { min-width: 0; display: flex; flex-direction: column; }
.media-file-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.media-file-copy span { color: #99908b; font-size: 11px; }
.selected-media button { width: 32px; height: 32px; border: 0; border-radius: 7px; background: transparent; color: #8d7d75; cursor: pointer; }
.privacy-row { min-height: 62px; padding: 12px 14px; border: 1px solid #e8ddd7; border-radius: 8px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.privacy-row > div { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.privacy-row span { color: #8c817b; font-size: 12px; line-height: 1.45; }
.editor-actions { display: flex; justify-content: flex-end; gap: 10px; }

@media (max-width: 768px) {
  .space-diary-form { gap: 18px; }
  .editor-grid { grid-template-columns: 1fr; gap: 18px; }
  .editor-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .editor-actions :deep(.el-button) { width: 100%; margin: 0; }
}
</style>
