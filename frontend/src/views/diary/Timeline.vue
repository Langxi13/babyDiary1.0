<template>
  <div class="page-shell">
    <main class="page-container">
      <div class="page-title-row">
        <div>
          <h1>时间轴</h1>
          <p>按月份回看两个人的日常片段</p>
        </div>
        <el-button class="timeline-write-button" type="primary" @click="router.push('/diaries/create')">
          <el-icon><Edit /></el-icon>
          写日记
        </el-button>
      </div>

      <section class="toolbar">
        <el-date-picker
          class="timeline-month-filter"
          v-model="monthValue"
          type="month"
          placeholder="选择月份"
          format="YYYY年MM月"
          value-format="YYYY-MM"
          clearable
          @change="fetchTimeline"
        />
        <el-select class="timeline-tag-filter" v-model="filters.tagId" placeholder="标签" clearable @change="fetchTimeline">
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
        <el-select class="timeline-mood-filter" v-model="filters.mood" placeholder="心情" clearable @change="fetchTimeline">
          <el-option v-for="mood in MOODS" :key="mood.key" :label="mood.label" :value="mood.key" />
        </el-select>
      </section>

      <div v-loading="loading" class="timeline">
        <el-empty v-if="timelineTree.length === 0" description="暂无日记" />
        <section v-for="year in timelineTree" :key="year.key" class="timeline-year">
          <button type="button" class="timeline-year-toggle" @click="toggleExpanded(year.key)">
            <span class="toggle-main">
              <el-icon class="toggle-icon" :class="{ expanded: isExpanded(year.key) }"><ArrowRight /></el-icon>
              <strong>{{ year.year }}年</strong>
            </span>
            <span class="timeline-summary">
              <span class="timeline-counts">{{ year.diaryCount }}篇</span>
              <span v-if="year.photoCount" class="timeline-counts">{{ year.photoCount }}张照片</span>
            </span>
          </button>

          <div v-if="isExpanded(year.key)" class="timeline-year-body">
            <section v-for="month in year.months" :key="month.key" class="month-block">
              <button type="button" class="timeline-month-toggle" @click="toggleExpanded(month.key)">
                <span class="toggle-main">
                  <el-icon class="toggle-icon" :class="{ expanded: isExpanded(month.key) }"><ArrowRight /></el-icon>
                  <strong>{{ formatChineseMonth(month.month) }}</strong>
                </span>
                <span class="timeline-summary">
                  <span class="timeline-counts">{{ month.diaryCount }}篇</span>
                  <span v-if="month.photoCount" class="timeline-counts">{{ month.photoCount }}张照片</span>
                  <span v-if="month.usesWeeks" class="timeline-counts">{{ month.weeks.length }}周</span>
                </span>
              </button>

              <div v-if="isExpanded(month.key)" class="month-items">
                <template v-if="month.usesWeeks">
                  <section v-for="week in month.weeks" :key="week.key" class="week-block">
                    <button type="button" class="timeline-week-toggle" @click="toggleExpanded(week.key)">
                      <span class="toggle-main">
                        <el-icon class="toggle-icon" :class="{ expanded: isExpanded(week.key) }"><ArrowRight /></el-icon>
                        <strong>{{ week.label }}</strong>
                      </span>
                      <span class="timeline-summary">
                        <span class="timeline-counts">{{ week.diaryCount }}篇</span>
                        <span v-if="week.photoCount" class="timeline-counts">{{ week.photoCount }}张照片</span>
                      </span>
                    </button>
                    <div v-if="isExpanded(week.key)" class="week-items">
                      <article v-for="diary in week.diaries" :key="diary.id" class="timeline-item" @click="router.push(`/diaries/${diary.id}`)">
                        <div class="date-chip">{{ formatChineseMonthDay(diary.diaryDate) }}</div>
                        <div class="item-body">
                          <div class="mobile-date-pill">{{ formatChineseMonthDay(diary.diaryDate) }}</div>
                          <div class="item-head">
                            <h2>{{ diary.title }}</h2>
                            <el-tag v-if="diary.mood" size="small" :color="moodColor(diary.mood)" effect="dark">
                              {{ moodLabel(diary.mood) }}
                            </el-tag>
                          </div>
                          <div class="tag-row" v-if="diary.tags?.length">
                            <el-tag v-for="tag in diary.tags" :key="tag.id" size="small" effect="plain" :color="tag.color">
                              {{ tag.name }}
                            </el-tag>
                          </div>
                          <p>{{ previewText(diary) }}</p>
                          <div class="thumbs" v-if="diaryImages(diary).length">
                            <img
                              v-for="img in diaryImages(diary).slice(0, 3)"
                              :key="img.id"
                              :src="mediaThumbnailUrl(img)"
                              alt=""
                              loading="lazy"
                              decoding="async"
                            />
                          </div>
                        </div>
                      </article>
                    </div>
                  </section>
                </template>

                <template v-else>
                  <article v-for="diary in month.diaries" :key="diary.id" class="timeline-item" @click="router.push(`/diaries/${diary.id}`)">
                    <div class="date-chip">{{ formatChineseMonthDay(diary.diaryDate) }}</div>
                    <div class="item-body">
                      <div class="mobile-date-pill">{{ formatChineseMonthDay(diary.diaryDate) }}</div>
                      <div class="item-head">
                        <h2>{{ diary.title }}</h2>
                        <el-tag v-if="diary.mood" size="small" :color="moodColor(diary.mood)" effect="dark">
                          {{ moodLabel(diary.mood) }}
                        </el-tag>
                      </div>
                      <div class="tag-row" v-if="diary.tags?.length">
                        <el-tag v-for="tag in diary.tags" :key="tag.id" size="small" effect="plain" :color="tag.color">
                          {{ tag.name }}
                        </el-tag>
                      </div>
                      <p>{{ previewText(diary) }}</p>
                      <div class="thumbs" v-if="diaryImages(diary).length">
                        <img
                          v-for="img in diaryImages(diary).slice(0, 3)"
                          :key="img.id"
                          :src="mediaThumbnailUrl(img)"
                          alt=""
                          loading="lazy"
                          decoding="async"
                        />
                      </div>
                    </div>
                  </article>
                </template>
              </div>
            </section>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ArrowRight, Edit } from '@element-plus/icons-vue'
import { diaryApi } from '@/api/diary'
import { tagApi } from '@/api/experience'
import { mediaThumbnailUrl } from '@/api/models'
import { useWorkspaceStore } from '@/stores/workspace'
import { MOODS, moodColor, moodLabel, stripHtml } from '@/utils/diaryMeta'
import { formatChineseMonth, formatChineseMonthDay } from '@/utils/dateDisplay'
import { buildTimelineTree, initialExpandedTimelineKeys } from '@/utils/timelineGroups'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const loading = ref(false)
const groups = ref([])
const tags = ref([])
const monthValue = ref('')
const fetchSeq = ref(0)
const expandedKeys = ref(new Set())
const filters = reactive({
  tagId: null,
  mood: ''
})

const requireSpaceId = async () => {
  await workspaceStore.loadSpaces()
  if (!workspaceStore.activeSpaceId) throw new Error('当前账户没有可用日记空间')
  return workspaceStore.activeSpaceId
}

const timelineTree = computed(() => buildTimelineTree(groups.value))

const ensureDefaultExpansion = (tree) => {
  if (!tree.length) {
    expandedKeys.value = new Set()
    return
  }
  expandedKeys.value = new Set(initialExpandedTimelineKeys(tree))
}

const isExpanded = (key) => expandedKeys.value.has(key)

const toggleExpanded = (key) => {
  const next = new Set(expandedKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedKeys.value = next
}

const previewText = (diary) => {
  const text = diary.contentHtml ? stripHtml(diary.contentHtml) : diary.contentText
  return text?.slice(0, 160) || ''
}
const diaryImages = diary => diary?.media?.filter(item => item.mediaType === 'IMAGE') || []

const fetchTags = async () => {
  tags.value = await tagApi.list(await requireSpaceId())
}

const fetchTimeline = async () => {
  const seq = fetchSeq.value + 1
  fetchSeq.value = seq
  loading.value = true
  try {
    const params = {
      tagId: filters.tagId,
      mood: filters.mood || undefined
    }
    if (monthValue.value) {
      const [year, month] = monthValue.value.split('-')
      params.year = Number(year)
      params.month = Number(month)
    }
    const result = await diaryApi.getTimeline(await requireSpaceId(), params)
    if (seq === fetchSeq.value) {
      groups.value = result || []
    }
  } finally {
    if (seq === fetchSeq.value) {
      loading.value = false
    }
  }
}

onMounted(async () => {
  await Promise.all([fetchTags(), fetchTimeline()])
})

watch(timelineTree, ensureDefaultExpansion, { immediate: true })
</script>

<style src="./styles/Timeline.scss" scoped lang="scss"></style>
