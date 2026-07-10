<template>
  <div class="diary-list-container">
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>我的日记</h1>
          <p>搜索、筛选和整理每一次记录</p>
        </div>
        <el-button type="primary" @click="router.push('/diaries/create')">
          <el-icon><Plus /></el-icon>
          写日记
        </el-button>
      </div>

      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="日期">
            <el-date-picker
              v-model="filterForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY年MM月DD日"
              value-format="YYYY-MM-DD"
              @change="handleFilter"
            />
          </el-form-item>

          <el-form-item label="标签">
            <el-select v-model="filterForm.tagId" placeholder="全部标签" clearable @change="handleFilter">
              <el-option v-for="tag in tags" :key="tag.tagId" :label="tag.name" :value="tag.tagId" />
            </el-select>
          </el-form-item>

          <el-form-item label="心情">
            <el-select v-model="filterForm.moodKey" placeholder="全部心情" clearable @change="handleFilter">
              <el-option v-for="mood in MOODS" :key="mood.key" :label="mood.label" :value="mood.key" />
            </el-select>
          </el-form-item>

          <el-form-item label="搜索">
            <el-input
              v-model="filterForm.keyword"
              placeholder="标题或内容"
              clearable
              @input="scheduleKeywordFilter"
              @clear="handleFilter"
              @keyup.enter="handleFilter"
            >
              <template #append>
                <el-button :icon="Search" @click="handleFilter" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-button @click="resetFilters">重置</el-button>
            <el-button type="success" :loading="exporting" @click="handleExport">
              <el-icon><Download /></el-icon>
              导出图片
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="diary-list" v-loading="loading">
        <el-empty v-if="diaries.length === 0" description="暂无日记" />

        <article v-for="diary in diaries" :key="diary.diaryId" class="diary-card" @click="openDiary(diary.diaryId)">
          <div class="diary-content">
            <div class="diary-header">
              <div>
                <h2 class="diary-title">{{ diary.title }}</h2>
                <div class="meta-row">
                  <span class="diary-date">{{ formatChineseDate(diary.date) }}</span>
                  <el-tag v-if="diary.moodKey" size="small" :color="moodColor(diary.moodKey)" effect="dark">
                    {{ moodLabel(diary.moodKey) }}
                  </el-tag>
                </div>
              </div>
              <div class="diary-actions">
                <el-button type="primary" size="small" text @click.stop="openDiary(diary.diaryId)">
                  <el-icon><View /></el-icon>
                  查看详情
                </el-button>
                <el-button type="primary" size="small" text @click.stop="handleEdit(diary.diaryId)">
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-popconfirm
                  title="确定要删除这篇日记吗？"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="handleDelete(diary.diaryId)"
                >
                  <template #reference>
                    <el-button
                      type="danger"
                      size="small"
                      text
                      :loading="deletingId === diary.diaryId"
                      :disabled="!!deletingId"
                      @click.stop
                    >
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>

            <div class="tag-row" v-if="diary.tags?.length">
              <el-tag v-for="tag in diary.tags" :key="tag.tagId" size="small" effect="plain" :color="tag.color">
                {{ tag.name }}
              </el-tag>
            </div>

            <p class="diary-text">{{ previewContent(diary) }}</p>

            <div class="diary-images" v-if="diary.imagePathList?.length > 0" @click.stop>
              <el-image
                v-for="(img, index) in diary.imagePathList.slice(0, 4)"
                :key="index"
                :src="thumbnailImageUrl(img)"
                :preview-src-list="diary.imagePathList.map(originalImageUrl)"
                :initial-index="index"
                fit="cover"
                class="diary-image"
                :preview-teleported="true"
                lazy
              />
              <span v-if="diary.imagePathList.length > 4" class="more-images">
                +{{ diary.imagePathList.length - 4 }}
              </span>
            </div>

            <div class="diary-footer">
              <span class="created-time">创建于: {{ formatChineseDateTime(diary.createdAt) }}</span>
            </div>
          </div>
        </article>
      </div>

      <div class="pagination-section" v-if="pagination.totalPages > 1">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pagination.pageSize"
          :total="pagination.totalElements"
          :layout="paginationLayout"
          :pager-count="pagerCount"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
import { ElPopconfirm } from 'element-plus/es/components/popconfirm/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { Search, Plus, Download, Edit, Delete, View } from '@element-plus/icons-vue'
import { useDiaryStore } from '@/stores/diary'
import { tagApi } from '@/api/experience'
import { MOODS, moodColor, moodLabel, stripHtml } from '@/utils/diaryMeta'
import { formatChineseDate, formatChineseDateTime } from '@/utils/dateDisplay'
import { originalImageUrl, thumbnailImageUrl } from '@/utils/imageUrl'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/image/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/pagination/style/css.mjs'
import 'element-plus/es/components/popconfirm/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'

const router = useRouter()
const route = useRoute()
const diaryStore = useDiaryStore()

const loading = computed(() => diaryStore.loading)
const diaries = computed(() => diaryStore.diaries)
const pagination = computed(() => diaryStore.pagination)
const tags = ref([])
const exporting = ref(false)
const deletingId = ref(null)
const isMobileViewport = ref(false)
const paginationLayout = computed(() => isMobileViewport.value ? 'prev, pager, next' : 'total, prev, pager, next, jumper')
const pagerCount = computed(() => isMobileViewport.value ? 5 : 7)
let keywordDebounceTimer = null

const currentPage = ref(1)
const filterForm = reactive({
  dateRange: null,
  keyword: '',
  tagId: null,
  moodKey: ''
})

const updateViewportMode = () => {
  if (typeof window === 'undefined') return
  isMobileViewport.value = window.matchMedia('(max-width: 768px)').matches
}

const previewContent = (diary) => {
  if (!diary?.content) return ''
  return diary.contentFormat === 'html' ? stripHtml(diary.content) : diary.content
}

const fetchTags = async () => {
  const response = await tagApi.list()
  tags.value = response.data || []
}

const fetchDiaries = async () => {
  const params = {
    page: currentPage.value - 1,
    size: 5,
    tagId: filterForm.tagId,
    moodKey: filterForm.moodKey || undefined
  }

  if (filterForm.dateRange) {
    params.startDate = filterForm.dateRange[0]
    params.endDate = filterForm.dateRange[1]
  }

  if (filterForm.keyword.trim()) {
    params.keyword = filterForm.keyword.trim()
  }

  await diaryStore.fetchDiaries(params)
}

const firstQueryValue = (value) => Array.isArray(value) ? value[0] : value

const syncDateFromRoute = () => {
  const date = firstQueryValue(route.query.date)
  filterForm.dateRange = date ? [date, date] : null
  currentPage.value = 1
}

const handleFilter = () => {
  if (keywordDebounceTimer) {
    window.clearTimeout(keywordDebounceTimer)
    keywordDebounceTimer = null
  }
  currentPage.value = 1
  fetchDiaries()
}

const scheduleKeywordFilter = () => {
  if (keywordDebounceTimer) {
    window.clearTimeout(keywordDebounceTimer)
  }
  keywordDebounceTimer = window.setTimeout(handleFilter, 350)
}

const resetFilters = () => {
  Object.assign(filterForm, {
    dateRange: null,
    keyword: '',
    tagId: null,
    moodKey: ''
  })
  handleFilter()
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchDiaries()
}

const handleEdit = (id) => {
  router.push(`/diaries/${id}/edit`)
}

const openDiary = (id) => {
  router.push(`/diaries/${id}`)
}

const handleDelete = async (id) => {
  if (deletingId.value) return
  deletingId.value = id
  try {
    await diaryStore.deleteDiary(id)
    ElMessage.success('删除成功')
    if (diaries.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await fetchDiaries()
  } catch (error) {
    if (!error?.message) {
      ElMessage.error('删除失败')
    }
  } finally {
    deletingId.value = null
  }
}

const handleExport = async () => {
  if (exporting.value) return
  if (!filterForm.dateRange) {
    ElMessage.warning('请先选择日期范围')
    return
  }

  exporting.value = true
  try {
    const response = await diaryStore.exportImages(
      filterForm.dateRange[0],
      filterForm.dateRange[1]
    )

    const blob = new Blob([response], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `diary_images_${filterForm.dateRange[0]}_${filterForm.dateRange[1]}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.setTimeout(() => window.URL.revokeObjectURL(url), 0)
    ElMessage.success('导出成功')
  } catch (error) {
    if (!error?.message) {
      ElMessage.error('导出失败')
    }
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  updateViewportMode()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', updateViewportMode)
  }
  syncDateFromRoute()
  await Promise.all([fetchTags(), fetchDiaries()])
})

onBeforeUnmount(() => {
  if (keywordDebounceTimer) {
    window.clearTimeout(keywordDebounceTimer)
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', updateViewportMode)
  }
})

watch(() => route.query.date, async () => {
  syncDateFromRoute()
  await fetchDiaries()
})
</script>

<style src="./styles/DiaryList.scss" scoped lang="scss"></style>
