<template>
  <div class="diary-form-container">
    <div class="page-container">
      <div class="page-header" :class="{ 'has-draft-status': draftStatus }">
        <el-button class="page-back-button" text @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="title-block">
          <h1>{{ isEdit ? '编辑日记' : '写日记' }}</h1>
          <span v-if="draftStatus">{{ draftStatus }}</span>
        </div>
        <el-button class="header-submit" type="primary" :loading="loading || sharedImporting" :disabled="loading || sharedImporting" @click="handleSubmit">
          <el-icon><Check /></el-icon>
          {{ isEdit ? '更新' : '发布' }}
        </el-button>
      </div>

      <section class="form-panel">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="diary-form"
        >
          <div class="form-layout">
            <section class="editor-column">
              <el-form-item label="标题" prop="title" class="title-field">
                <el-input
                  v-model="form.title"
                  placeholder="这一页的名字"
                  maxlength="100"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item label="内容" prop="content" class="content-field">
                <div class="editor-wrap">
                  <div class="editor-toolbar">
                    <el-tooltip content="加粗">
                      <button
                        type="button"
                        :class="{ active: isEditorActive('bold') }"
                        @mousedown.prevent
                        @click="toggleBold"
                      >
                        <strong>B</strong>
                      </button>
                    </el-tooltip>
                    <el-tooltip content="斜体">
                      <button
                        type="button"
                        :class="{ active: isEditorActive('italic') }"
                        @mousedown.prevent
                        @click="toggleItalic"
                      >
                        <em>I</em>
                      </button>
                    </el-tooltip>
                    <el-tooltip content="引用">
                      <button
                        type="button"
                        :class="{ active: isEditorActive('blockquote') }"
                        @mousedown.prevent
                        @click="toggleBlockquote"
                      >
                        “”
                      </button>
                    </el-tooltip>
                    <el-tooltip content="列表">
                      <button
                        type="button"
                        :class="{ active: isEditorActive('bulletList') }"
                        @mousedown.prevent
                        @click="toggleBulletList"
                      >
                        •
                      </button>
                    </el-tooltip>
                    <el-tooltip content="分隔线">
                      <button type="button" @mousedown.prevent @click="insertHorizontalRule">─</button>
                    </el-tooltip>
                  </div>
                  <editor-content v-if="editor" :editor="editor" class="rich-editor" />
                </div>
              </el-form-item>
            </section>

            <aside class="meta-column">
              <div class="meta-card">
                <el-form-item label="日期" prop="date">
                  <el-date-picker
                    v-model="form.date"
                    type="date"
                    placeholder="选择日期"
                    format="YYYY年MM月DD日"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>

                <el-form-item label="心情">
                  <div class="mood-group" role="radiogroup" aria-label="心情">
                    <button
                      v-for="mood in MOODS"
                      :key="mood.key"
                      type="button"
                      class="mood-card"
                      :class="{ active: form.moodKey === mood.key }"
                      role="radio"
                      :aria-checked="form.moodKey === mood.key"
                      @click="form.moodKey = mood.key"
                    >
                      <span class="mood-option">
                        <span class="mood-emoji">{{ mood.emoji }}</span>
                        <span>{{ mood.label }}</span>
                      </span>
                    </button>
                  </div>
                </el-form-item>

                <el-form-item label="标签">
                  <div class="tag-picker">
                    <el-select v-model="form.tagIds" multiple placeholder="选择标签" collapse-tags collapse-tags-tooltip>
                      <el-option v-for="tag in tags" :key="tag.tagId" :label="tag.name" :value="tag.tagId" />
                    </el-select>
                    <el-tooltip content="新建标签">
                      <el-button @click="tagDialogVisible = true">
                        <el-icon><Plus /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </el-form-item>
              </div>

              <div class="meta-card image-card">
                <el-form-item label="图片">
                  <el-upload
                    ref="uploadRef"
                    class="diary-upload-uploader"
                    v-model:file-list="fileList"
                    action="#"
                    :auto-upload="false"
                    :show-file-list="false"
                    :on-change="handleImageChange"
                    :before-upload="beforeUpload"
                    accept="image/*"
                    :drag="true"
                    multiple
                  >
                    <div class="diary-upload-dropzone">
                      <el-icon><Plus /></el-icon>
                      <div>
                        <strong>拖拽照片到这里</strong>
                        <span>点击添加</span>
                      </div>
                    </div>
                  </el-upload>
                  <div v-if="fileList.length" class="image-sort-grid">
                    <article v-for="(file, index) in fileList" :key="file.uid || file.name" class="image-sort-card">
                      <button class="image-preview-button" type="button" @click="handlePreview(file)">
                        <img :src="file.url" :alt="file.name || '日记图片'" />
                      </button>
                      <div class="image-sort-actions">
                        <button type="button" :disabled="index === 0" aria-label="上移图片" @click.stop="moveImage(index, -1)">↑</button>
                        <button type="button" :disabled="index === fileList.length - 1" aria-label="下移图片" @click.stop="moveImage(index, 1)">↓</button>
                        <button type="button" aria-label="删除图片" @click.stop="removeImageAt(index)">×</button>
                      </div>
                    </article>
                  </div>
                  <native-image-actions
                    v-if="nativeApp"
                    :limit="Math.max(0, 50 - fileList.length)"
                    @selected="appendNativeFiles"
                  />
                  <div v-else-if="isAndroidDevice" class="android-native-upload">
                    <span class="android-upload-title">添加照片</span>
                    <input
                      class="android-native-file-input"
                      type="file"
                      aria-label="选择照片"
                      accept="image/*"
                      multiple
                      @change="handleNativeImageChange"
                    />
                    <button
                      class="android-upload-browser-link"
                      type="button"
                      @click="androidUploadHelpVisible = true"
                    >
                      复制链接到浏览器上传
                    </button>
                  </div>
                  <div v-else class="mobile-native-upload">
                    <input
                      class="mobile-native-file-input"
                      type="file"
                      aria-label="选择照片"
                      accept="image/*"
                      multiple
                      @change="handleNativeImageChange"
                    />
                    <div class="mobile-native-upload-visual" aria-hidden="true">
                      <el-icon><Plus /></el-icon>
                      <span>添加照片</span>
                    </div>
                  </div>

                  <el-dialog v-model="previewVisible">
                    <img :src="previewUrl" alt="preview" style="width: 100%" />
                  </el-dialog>
                </el-form-item>
              </div>

              <div class="form-actions">
                <el-button type="primary" size="large" :loading="loading || sharedImporting" :disabled="loading || sharedImporting" @click="handleSubmit">
                  <el-icon><Check /></el-icon>
                  {{ isEdit ? '更新' : '发布' }}
                </el-button>
                <el-button size="large" @click="router.back()">
                  <el-icon><Close /></el-icon>
                  取消
                </el-button>
              </div>
            </aside>
          </div>
        </el-form>
      </section>
    </div>

    <el-dialog v-model="tagDialogVisible" title="新建标签" width="360px">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="newTag.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="颜色">
          <div class="tag-color-swatches">
            <button
              v-for="color in DEFAULT_TAG_COLORS"
              :key="color"
              type="button"
              :class="{ active: newTag.color === color }"
              :style="{ backgroundColor: color }"
              :aria-label="`选择颜色 ${color}`"
              @click="newTag.color = color"
            />
          </div>
          <el-color-picker v-model="newTag.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tagSaving" @click="createTag">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="androidUploadHelpVisible"
      title="安卓上传提示"
      width="360px"
      class="android-upload-help"
      @opened="selectAndroidUploadUrl"
    >
      <p>当前安卓壳应用无法直接打开图片选择器。请复制下面的链接，到手机浏览器中打开后再上传照片。</p>
      <input
        ref="androidUploadUrlInput"
        class="android-upload-url"
        :value="browserUploadUrl"
        readonly
        inputmode="url"
        aria-label="浏览器上传链接"
        @focus="selectAndroidUploadUrl"
      />
      <template #footer>
        <el-button @click="androidUploadHelpVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyBrowserUploadUrl">复制链接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, shallowRef, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Editor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElColorPicker } from 'element-plus/es/components/color-picker/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElTooltip } from 'element-plus/es/components/tooltip/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import { ArrowLeft, Check, Close, Plus } from '@element-plus/icons-vue'
import { useDiaryStore } from '@/stores/diary'
import { draftApi, tagApi } from '@/api/experience'
import { useDiaryImages } from '@/composables/useDiaryImages'
import NativeImageActions from '@/components/mobile/NativeImageActions.vue'
import { isNativeApp } from '@/platform/runtimeConfig'
import { MOODS, stripHtml } from '@/utils/diaryMeta'
import {
  DEFAULT_TAG_COLORS,
  buildCreateDraftKey,
  draftKeyFromRoute,
  formatLocalDate,
  isDraftEntryRoute,
  nextTagColor
} from '@/utils/diaryFormState'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/color-picker/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/tooltip/style/css.mjs'
import 'element-plus/es/components/upload/style/css.mjs'

const router = useRouter()
const route = useRoute()
const diaryStore = useDiaryStore()

const formRef = ref(null)
const editor = shallowRef(null)
const loading = ref(false)
const tags = ref([])
const tagDialogVisible = ref(false)
const draftStatus = ref('')
const autosaveTimer = ref(null)
const loadingInitialData = ref(true)
const tagSaving = ref(false)
const nativeApp = isNativeApp()

const isEdit = computed(() => !!route.params.id)
const diaryId = computed(() => route.params.id)
const {
  fileList,
  previewVisible,
  previewUrl,
  isAndroidDevice,
  androidUploadHelpVisible,
  androidUploadUrlInput,
  sharedImporting,
  browserUploadUrl,
  selectAndroidUploadUrl,
  copyBrowserUploadUrl,
  beforeUpload,
  handlePreview,
  handleImageChange,
  removeImageAt,
  moveImage,
  handleNativeImageChange,
  appendNativeFiles,
  loadSharedImages,
  loadNativeSharedImages,
  appendImagesToFormData,
  setExistingImages,
  initializeImageUpload,
  disposeImages
} = useDiaryImages({ route, router, isEdit })
const createDraftKey = buildCreateDraftKey()
const fallbackDraftKey = computed(() => isEdit.value ? `edit-${diaryId.value}` : createDraftKey)
const draftKey = computed(() => draftKeyFromRoute(route, fallbackDraftKey.value))
const shouldLoadDraft = computed(() => isDraftEntryRoute(route))

const form = reactive({
  title: '',
  date: '',
  content: '',
  contentFormat: 'html',
  moodKey: '',
  tagIds: []
})

const newTag = reactive({
  name: '',
  color: DEFAULT_TAG_COLORS[0]
})

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100个字符', trigger: 'blur' }
  ],
  date: [
    { required: true, message: '请选择日期', trigger: 'change' }
  ],
  content: [
    {
      validator: (rule, value, callback) => {
        if (isBlankRichText(value)) {
          callback(new Error('请输入内容'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const isBlankRichText = (html = '') => {
  const text = stripHtml(html).replace(/\u00a0/g, ' ').trim()
  const mediaOrRule = /<(img|video|audio|hr)\b/i.test(html)
  return !text && !mediaOrRule
}

const escapeHtml = (value = '') => String(value)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const normalizeEditorContent = (content = '', format = 'html') => {
  if (format === 'plain') {
    return escapeHtml(content).replace(/\r?\n/g, '<br>')
  }
  return content || ''
}

const setEditorContent = (content = '', format = 'html') => {
  const normalizedContent = normalizeEditorContent(content, format)
  form.content = normalizedContent
  form.contentFormat = 'html'

  if (editor.value && editor.value.getHTML() !== normalizedContent) {
    editor.value.commands.setContent(normalizedContent || '<p></p>', { emitUpdate: false })
  }
}

const syncContentFromEditor = () => {
  if (!editor.value) return
  form.content = editor.value.getHTML()
  form.contentFormat = 'html'
}

const initEditor = () => {
  editor.value = new Editor({
    extensions: [
      StarterKit
    ],
    content: form.content || '<p></p>',
    editorProps: {
      attributes: {
        'aria-label': '日记内容'
      }
    },
    onUpdate: ({ editor: currentEditor }) => {
      form.content = currentEditor.getHTML()
      form.contentFormat = 'html'
    }
  })
}

const isEditorActive = (type) => editor.value?.isActive(type) || false

const toggleBold = () => {
  editor.value?.chain().focus().toggleBold().run()
}

const toggleItalic = () => {
  editor.value?.chain().focus().toggleItalic().run()
}

const toggleBlockquote = () => {
  editor.value?.chain().focus().toggleBlockquote().run()
}

const toggleBulletList = () => {
  editor.value?.chain().focus().toggleBulletList().run()
}

const insertHorizontalRule = () => {
  editor.value?.chain().focus().setHorizontalRule().run()
}

const appendFormData = () => {
  const formData = new FormData()
  formData.append('title', form.title)
  formData.append('date', form.date)
  formData.append('content', form.content)
  formData.append('contentFormat', 'html')
  formData.append('moodKey', form.moodKey || '')
  formData.append('tagIds', form.tagIds.join(','))
  return formData
}

const removeDraftSilently = async () => {
  try {
    await draftApi.removeByKey(draftKey.value)
  } catch {
    draftStatus.value = '草稿清理失败'
  }
}

const handleSubmit = async () => {
  if (loading.value || !formRef.value) return

  syncContentFromEditor()
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const formData = appendFormData()
    appendImagesToFormData(formData)

    if (isEdit.value) {
      const response = await diaryStore.updateDiary(diaryId.value, formData)
      await removeDraftSilently()
      ElMessage.success('更新成功')
      router.push(`/diaries/${response.data?.diaryId || diaryId.value}`)
    } else {
      const response = await diaryStore.createDiary(formData)
      await removeDraftSilently()
      ElMessage.success('发布成功')
      router.push(`/diaries/${response.data?.diaryId || ''}`)
    }
  } catch (error) {
    if (!error?.message) {
      ElMessage.error('操作失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

const fetchTags = async () => {
  const response = await tagApi.list()
  tags.value = response.data || []
  if (!newTag.name.trim()) {
    newTag.color = nextTagColor(tags.value.length)
  }
}

const createTag = async () => {
  if (tagSaving.value) return
  const tagName = newTag.name.trim()
  if (!tagName) {
    ElMessage.warning('请输入标签名称')
    return
  }
  tagSaving.value = true
  try {
    const response = await tagApi.create({ name: tagName, color: newTag.color })
    await fetchTags()
    if (!form.tagIds.includes(response.data.tagId)) {
      form.tagIds.push(response.data.tagId)
    }
    Object.assign(newTag, { name: '', color: nextTagColor(tags.value.length) })
    tagDialogVisible.value = false
  } finally {
    tagSaving.value = false
  }
}

const applyDraft = (draft) => {
  if (!draft) return
  form.title = draft.title || form.title
  form.date = draft.date || form.date
  setEditorContent(draft.content || form.content, draft.contentFormat || 'html')
  form.moodKey = draft.moodKey || form.moodKey
  form.tagIds = draft.tagIds || form.tagIds
}

const loadDraft = async () => {
  if (!shouldLoadDraft.value) return
  const response = await draftApi.get(draftKey.value)
  if (response.data) {
    applyDraft(response.data)
    draftStatus.value = '已载入草稿'
  }
}

const saveDraft = async () => {
  if (loadingInitialData.value) return
  syncContentFromEditor()
  const hasContent = form.title || !isBlankRichText(form.content) || form.moodKey || form.tagIds.length
  if (!hasContent) return

  await draftApi.save({
    draftKey: draftKey.value,
    diaryId: isEdit.value ? Number(diaryId.value) : null,
    title: form.title,
    date: form.date,
    content: form.content,
    contentFormat: form.contentFormat,
    moodKey: form.moodKey,
    tagIds: form.tagIds
  })
  draftStatus.value = `草稿已保存 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
}

const scheduleAutosave = () => {
  if (loadingInitialData.value) return
  window.clearTimeout(autosaveTimer.value)
  autosaveTimer.value = window.setTimeout(() => {
    saveDraft().catch(() => {
      draftStatus.value = '草稿保存失败'
    })
  }, 1200)
}

const loadDiary = async () => {
  if (!isEdit.value) {
    form.date = formatLocalDate()
    return
  }
  const response = await diaryStore.fetchDiary(diaryId.value)
  if (response.code === 200) {
    const diary = diaryStore.currentDiary
    form.title = diary.title
    form.date = diary.date
    setEditorContent(diary.content, diary.contentFormat || 'html')
    form.moodKey = diary.moodKey || ''
    form.tagIds = diary.tags?.map(tag => tag.tagId) || []

    setExistingImages(diary.imagePathList || [])
  }
}

watch(form, scheduleAutosave, { deep: true })

onMounted(async () => {
  initializeImageUpload()
  loadingInitialData.value = true
  await Promise.all([fetchTags(), loadDiary()])
  await loadDraft()
  loadNativeSharedImages()
  window.addEventListener('native-share:ready', loadNativeSharedImages)
  await loadSharedImages()
  initEditor()
  loadingInitialData.value = false
})

onBeforeUnmount(() => {
  window.removeEventListener('native-share:ready', loadNativeSharedImages)
  window.clearTimeout(autosaveTimer.value)
  disposeImages()
  editor.value?.destroy()
})
</script>

<style src="./styles/DiaryForm.scss" scoped lang="scss"></style>
