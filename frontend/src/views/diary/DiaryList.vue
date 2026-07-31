<template>
  <div class="diary-list-container">
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>我的日记</h1>
          <p>搜索、筛选和整理每一次记录</p>
        </div>
        <el-button class="create-diary-button" type="primary" @click="router.push('/diaries/create')">
          <el-icon><Plus /></el-icon>
          写日记
        </el-button>
      </div>

      <div class="filter-section">
        <diary-mobile-filters
          v-if="isMobileViewport"
          v-model:keyword="filterForm.keyword"
          v-model:start-date="filterForm.startDate"
          v-model:end-date="filterForm.endDate"
          v-model:tag-id="filterForm.tagId"
          v-model:mood="filterForm.mood"
          :tags="tags"
          :moods="MOODS"
          :exporting="exporting"
          @keyword-input="scheduleKeywordFilter"
          @filter="handleFilter"
          @reset="resetFilters"
          @export="handleExport"
        />

        <el-form v-else :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="日期" class="date-filter">
            <el-date-picker
              v-model="desktopDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY年MM月DD日"
              value-format="YYYY-MM-DD"
              @change="handleFilter"
            />
          </el-form-item>

          <el-form-item label="标签" class="tag-filter">
            <el-select v-model="filterForm.tagId" placeholder="全部标签" clearable @change="handleFilter">
              <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="心情" class="mood-filter">
            <el-select v-model="filterForm.mood" placeholder="全部心情" clearable @change="handleFilter">
              <el-option v-for="mood in MOODS" :key="mood.key" :label="mood.label" :value="mood.key" />
            </el-select>
          </el-form-item>

          <el-form-item label="搜索" class="search-filter">
            <el-input
              v-model="filterForm.keyword"
              placeholder="标题或内容"
              clearable
              @input="scheduleKeywordFilter"
              @clear="handleFilter"
              @keyup.enter="handleFilter"
            >
              <template #append>
                <el-button :icon="Search" aria-label="搜索日记" title="搜索日记" @click="handleFilter" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item class="filter-actions">
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

        <article v-for="diary in diaries" :key="diary.id" class="diary-card" @click="openDiary(diary.id)">
          <div class="diary-content">
            <div class="diary-header">
              <div class="diary-heading">
                <h2 class="diary-title">{{ diary.title }}</h2>
                <div class="meta-row">
                  <span class="diary-date">{{ formatChineseDate(diary.diaryDate) }}</span>
                  <el-tag v-if="diary.mood" size="small" :color="moodColor(diary.mood)" effect="dark">
                    {{ moodLabel(diary.mood) }}
                  </el-tag>
                </div>
              </div>
              <div class="diary-actions" role="group" aria-label="日记操作" @click.stop>
                <el-button class="view-action" type="primary" size="small" text @click.stop="openDiary(diary.id)">
                  <el-icon><View /></el-icon>
                  <span class="action-label">查看详情</span>
                </el-button>
                <el-button
                  class="edit-action"
                  type="primary"
                  size="small"
                  text
                  aria-label="编辑日记"
                  title="编辑日记"
                  @click.stop="handleEdit(diary.id)"
                >
                  <el-icon><Edit /></el-icon>
                  <span class="action-label">编辑</span>
                </el-button>
                <el-popconfirm
                  title="确定要删除这篇日记吗？"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="handleDelete(diary.id)"
                >
                  <template #reference>
                    <el-button
                      type="danger"
                      size="small"
                      text
                      class="delete-action"
                      :loading="deletingId === diary.id"
                      :disabled="!!deletingId"
                      aria-label="删除日记"
                      title="删除日记"
                      @click.stop
                    >
                      <el-icon><Delete /></el-icon>
                      <span class="action-label">删除</span>
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>

            <div class="tag-row" v-if="diary.tags?.length">
              <el-tag v-for="tag in diary.tags" :key="tag.id" size="small" effect="plain" :color="tag.color">
                {{ tag.name }}
              </el-tag>
            </div>

            <p class="diary-text">{{ previewContent(diary) }}</p>

            <div class="diary-images" v-if="diaryImages(diary).length > 0" @click.stop>
              <el-image
                v-for="(img, index) in diaryImages(diary).slice(0, 4)"
                :key="img.id"
                :src="mediaThumbnailUrl(img)"
                :preview-src-list="diaryImages(diary).map(mediaPreviewUrl).filter(Boolean)"
                :initial-index="index"
                fit="cover"
                class="diary-image"
                :preview-teleported="true"
                lazy
              />
              <span v-if="diaryImages(diary).length > 4" class="more-images">
                +{{ diaryImages(diary).length - 4 }}
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
import DiaryMobileFilters from '@/components/diary/DiaryMobileFilters.vue'
import { useDiaryStore } from '@/stores/diary'
import { useWorkspaceStore } from '@/stores/workspace'
import { tagApi } from '@/api/experience'
import { mediaPreviewUrl, mediaThumbnailUrl } from '@/api/models'
import { MOODS, moodColor, moodLabel, stripHtml } from '@/utils/diaryMeta'
import { formatChineseDate, formatChineseDateTime } from '@/utils/dateDisplay'
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
const workspaceStore = useWorkspaceStore()

const loading = computed(() => diaryStore.loading)
const diaries = computed(() => diaryStore.diaries)
const pagination = computed(() => diaryStore.pagination)
const tags = ref([])
const exporting = ref(false)
const deletingId = ref(null)
const isMobileViewport = ref(typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches)
const paginationLayout = computed(() => isMobileViewport.value ? 'prev, pager, next' : 'total, prev, pager, next, jumper')
const pagerCount = computed(() => isMobileViewport.value ? 5 : 7)
let keywordDebounceTimer = null

const currentPage = ref(1)
const filterForm = reactive({
  startDate: '',
  endDate: '',
  keyword: '',
  tagId: null,
  mood: ''
})
const desktopDateRange = computed({
  get: () => filterForm.startDate && filterForm.endDate
    ? [filterForm.startDate, filterForm.endDate]
    : null,
  set: (value) => {
    filterForm.startDate = value?.[0] || ''
    filterForm.endDate = value?.[1] || ''
  }
})

const updateViewportMode = () => {
  if (typeof window === 'undefined') return
  isMobileViewport.value = window.matchMedia('(max-width: 768px)').matches
}

const previewContent = (diary) => {
  if (!diary) return ''
  return diary.contentHtml ? stripHtml(diary.contentHtml) : diary.contentText || ''
}
const diaryImages = diary => diary?.media?.filter(item => item.mediaType === 'IMAGE') || []

const requireSpaceId = async () => {
  await workspaceStore.loadSpaces()
  if (!workspaceStore.activeSpaceId) throw new Error('当前账户没有可用日记空间')
  return workspaceStore.activeSpaceId
}

const fetchTags = async () => {
  tags.value = await tagApi.list(await requireSpaceId())
}

const fetchDiaries = async () => {
  const params = {
    page: currentPage.value - 1,
    size: 5,
    tagId: filterForm.tagId,
    mood: filterForm.mood || undefined
  }

  if (filterForm.startDate) params.startDate = filterForm.startDate
  if (filterForm.endDate) params.endDate = filterForm.endDate

  if (filterForm.keyword.trim()) {
    params.keyword = filterForm.keyword.trim()
  }

  await diaryStore.fetchDiaries(params)
}

const firstQueryValue = (value) => Array.isArray(value) ? value[0] : value

const syncDateFromRoute = () => {
  const date = firstQueryValue(route.query.date)
  filterForm.startDate = date || ''
  filterForm.endDate = date || ''
  currentPage.value = 1
}

const handleFilter = () => {
  if (keywordDebounceTimer) {
    window.clearTimeout(keywordDebounceTimer)
    keywordDebounceTimer = null
  }
  if (filterForm.startDate && filterForm.endDate && filterForm.startDate > filterForm.endDate) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
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
    startDate: '',
    endDate: '',
    keyword: '',
    tagId: null,
    mood: ''
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
  if (!filterForm.startDate || !filterForm.endDate) {
    ElMessage.warning('请先选择日期范围')
    return
  }

  exporting.value = true
  try {
    const response = await diaryStore.exportImages(
      filterForm.startDate,
      filterForm.endDate
    )

    const blob = new Blob([response], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `diary_images_${filterForm.startDate}_${filterForm.endDate}.zip`
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
