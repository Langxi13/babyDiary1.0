<template>
  <div class="page-shell">
    <main class="page-container">
      <div class="page-title-row">
        <div>
          <h1>纪念日</h1>
          <p>固定日期、已过天数和倒计时</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新增
        </el-button>
      </div>

      <div v-loading="loading" class="anniversary-grid">
        <el-empty v-if="anniversaries.length === 0" description="暂无纪念日" />
        <article v-for="item in anniversaries" :key="item.anniversaryId" class="anniversary-card">
          <div class="cover" :style="coverStyle(item)">
            <span v-if="!item.coverImagePath">{{ formatChineseDate(item.date) }}</span>
          </div>
          <div class="card-body">
            <div class="card-head">
              <h2>{{ item.title }}</h2>
              <div class="actions">
                <el-button text size="small" @click="openEdit(item)">
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-popconfirm title="删除这个纪念日？" @confirm="removeItem(item.anniversaryId)">
                  <template #reference>
                    <el-button text type="danger" size="small" :loading="deletingId === item.anniversaryId" :disabled="!!deletingId">
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
            <p class="description">{{ item.description }}</p>
            <div class="stats">
              <div>
                <strong>{{ Math.max(item.daysPassed || 0, 0) }}</strong>
                <span>已过天</span>
              </div>
              <div>
                <strong>{{ Math.max(item.daysUntil || 0, 0) }}</strong>
                <span>倒计时</span>
              </div>
            </div>
          </div>
        </article>
      </div>
    </main>

    <el-dialog
      v-model="dialogVisible"
      class="anniversary-dialog"
      :title="editingId ? '编辑纪念日' : '新增纪念日'"
      width="760px"
      align-center
    >
      <el-form ref="formRef" class="anniversary-form" :model="form" :rules="rules" label-position="top">
        <div class="anniversary-dialog-body">
          <section class="anniversary-cover-panel">
            <div class="cover-panel-copy">
              <span>封面</span>
              <strong>{{ editingId ? '给这一天换个画面' : '给重要日子放一张照片' }}</strong>
              <p>拖拽或点击上传，保存后会自动生成缩略图。</p>
            </div>
            <el-form-item label="封面图片">
              <div class="cover-upload-field">
                <div
                  v-if="nativeApp"
                  class="cover-upload-preview native-cover-preview"
                  :class="{ 'has-cover': !!coverPreviewUrl }"
                  :style="coverPreviewStyle"
                >
                  <template v-if="coverPreviewUrl">
                    <span class="cover-preview-mask">当前封面</span>
                  </template>
                  <template v-else>
                    <el-icon><UploadFilled /></el-icon>
                    <strong>选择一张封面照片</strong>
                  </template>
                </div>
                <native-image-actions v-if="nativeApp" :limit="1" @selected="handleNativeCoverFiles" />
                <el-upload
                  v-else
                  class="cover-upload-card"
                  action="#"
                  accept="image/*"
                  :auto-upload="false"
                  :show-file-list="false"
                  :drag="true"
                  :on-change="handleCoverChange"
                >
                  <div class="cover-upload-preview" :class="{ 'has-cover': !!coverPreviewUrl }" :style="coverPreviewStyle">
                    <template v-if="coverPreviewUrl">
                      <span class="cover-preview-mask">拖拽或点击更换封面</span>
                    </template>
                    <template v-else>
                      <el-icon><UploadFilled /></el-icon>
                      <strong>拖拽图片到这里</strong>
                      <span>或点击选择一张封面</span>
                    </template>
                  </div>
                </el-upload>
                <el-button v-if="coverPreviewUrl" class="cover-remove-button" text type="danger" @click="removeCover">
                  <el-icon><Delete /></el-icon>
                  移除封面
                </el-button>
              </div>
            </el-form-item>
          </section>

          <section class="anniversary-fields-panel">
            <div class="fields-heading">
              <span>{{ editingId ? '正在编辑' : '新纪念日' }}</span>
              <p>标题和日期会显示在卡片、首页和回忆入口里。</p>
            </div>
            <el-form-item label="标题" prop="title">
              <el-input v-model="form.title" maxlength="100" placeholder="例如：第一次旅行" />
            </el-form-item>
            <el-form-item label="日期" prop="date">
              <el-date-picker v-model="form.date" type="date" format="YYYY年MM月DD日" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="写几句关于这一天的备注" />
            </el-form-item>
          </section>
        </div>
      </el-form>
      <template #footer>
        <div class="anniversary-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElPopconfirm } from 'element-plus/es/components/popconfirm/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import { Delete, Edit, Plus, UploadFilled } from '@element-plus/icons-vue'
import { anniversaryApi } from '@/api/experience'
import NativeImageActions from '@/components/mobile/NativeImageActions.vue'
import { isNativeApp } from '@/platform/runtimeConfig'
import { formatChineseDate } from '@/utils/dateDisplay'
import { formatLocalDate } from '@/utils/diaryFormState'
import { thumbnailImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/popconfirm/style/css.mjs'
import 'element-plus/es/components/upload/style/css.mjs'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const editingId = ref(null)
const anniversaries = ref([])
const deletingId = ref(null)
const coverFile = ref(null)
const coverPreviewUrl = ref('')
const coverObjectUrl = ref('')
const nativeApp = isNativeApp()

const form = reactive({
  title: '',
  date: '',
  description: '',
  coverImagePath: '',
  sort: 0
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

const coverStyle = (item) => item.coverImagePath ? { backgroundImage: `url(${thumbnailImageUrl(item.coverImagePath)})` } : {}
const coverPreviewStyle = computed(() => coverPreviewUrl.value ? { backgroundImage: `url(${coverPreviewUrl.value})` } : {})

const setCoverPreviewUrl = (url, isObjectUrl = false) => {
  if (coverObjectUrl.value) {
    URL.revokeObjectURL(coverObjectUrl.value)
    coverObjectUrl.value = ''
  }
  coverPreviewUrl.value = url
  if (isObjectUrl) {
    coverObjectUrl.value = url
  }
}

const fetchAnniversaries = async () => {
  loading.value = true
  try {
    const response = await anniversaryApi.list()
    anniversaries.value = response.data || []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  editingId.value = null
  coverFile.value = null
  setCoverPreviewUrl('')
  Object.assign(form, {
    title: '',
    date: formatLocalDate(),
    description: '',
    coverImagePath: '',
    sort: 0
  })
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (item) => {
  editingId.value = item.anniversaryId
  coverFile.value = null
  Object.assign(form, {
    title: item.title,
    date: item.date,
    description: item.description || '',
    coverImagePath: item.coverImagePath || '',
    sort: item.sort || 0
  })
  setCoverPreviewUrl(item.coverImagePath ? thumbnailImageUrl(item.coverImagePath) : '')
  dialogVisible.value = true
}

const handleCoverChange = (uploadFile) => {
  const file = uploadFile.raw
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return
  }
  coverFile.value = file
  setCoverPreviewUrl(URL.createObjectURL(file), true)
}

const handleNativeCoverFiles = (files) => {
  const file = files?.[0]
  if (file) handleCoverChange({ raw: file })
}

const removeCover = () => {
  coverFile.value = null
  form.coverImagePath = ''
  setCoverPreviewUrl('')
}

const submitForm = async () => {
  if (saving.value || !formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    const payload = { ...form }
    if (coverFile.value) {
      const coverResponse = await anniversaryApi.uploadCover(coverFile.value)
      payload.coverImagePath = coverResponse.data?.coverImagePath || ''
    }
    if (editingId.value) {
      await anniversaryApi.update(editingId.value, payload)
    } else {
      await anniversaryApi.create(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await fetchAnniversaries()
  } finally {
    saving.value = false
  }
}

const removeItem = async (id) => {
  if (deletingId.value) return
  deletingId.value = id
  try {
    await anniversaryApi.remove(id)
    ElMessage.success('删除成功')
    await fetchAnniversaries()
  } finally {
    deletingId.value = null
  }
}

onMounted(fetchAnniversaries)
onBeforeUnmount(() => setCoverPreviewUrl(''))
</script>

<style src="./styles/Anniversaries.scss" scoped lang="scss"></style>
