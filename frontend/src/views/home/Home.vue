<template>
  <div class="home-container">
    <main class="home-content">
      <section class="daily-hero">
        <div>
          <span class="eyebrow">{{ todayText }}</span>
          <h1>今天也记录一点我们</h1>
          <p>{{ heroLine }}</p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="router.push('/diaries/create')">
            <el-icon><Edit /></el-icon>
            写日记
          </el-button>
          <el-button size="large" @click="router.push('/drafts')">
            <el-icon><Tickets /></el-icon>
            草稿
          </el-button>
        </div>
      </section>

      <section class="hub-grid">
        <div class="main-column">
          <section class="panel">
            <div class="section-head">
              <div>
                <h2>最近日记</h2>
                <p>先从最近的片段继续看</p>
              </div>
              <el-button text @click="router.push('/diaries')">全部</el-button>
            </div>

            <div v-loading="loading.diaries" class="recent-list">
              <el-empty v-if="recentDiaries.length === 0" description="暂无日记" />
              <article
                v-for="diary in recentDiaries"
                :key="diary.id"
                class="recent-item"
                :class="{ 'has-image': firstDiaryImage(diary) }"
                @click="router.push(`/diaries/${diary.id}`)"
              >
                <div class="recent-copy">
                  <div class="meta-row">
                    <span>{{ formatChineseDate(diary.diaryDate) }}</span>
                    <el-tag v-if="diary.mood" size="small" :color="moodColor(diary.mood)" effect="dark">
                      {{ moodLabel(diary.mood) }}
                    </el-tag>
                  </div>
                  <h3>{{ diary.title }}</h3>
                  <p>{{ previewText(diary, 120) }}</p>
                </div>
                <img
                  v-if="firstDiaryImage(diary)"
                  :src="mediaThumbnailUrl(firstDiaryImage(diary))"
                  alt=""
                  loading="lazy"
                  decoding="async"
                />
              </article>
            </div>
          </section>

          <section class="panel">
            <div class="section-head">
              <div>
                <h2>收藏照片</h2>
                <p>常想回看的那些画面</p>
              </div>
              <el-button text @click="router.push('/album')">相册</el-button>
            </div>
            <div v-loading="loading.photos" class="photo-strip">
              <el-empty v-if="favoritePhotos.length === 0" description="暂无收藏照片" />
              <button
                v-for="photo in favoritePhotos"
                :key="photo.id"
                class="photo-tile"
                @click="router.push('/album/system/favorites')"
              >
                <img :src="mediaThumbnailUrl(photo.media)" alt="" loading="lazy" decoding="async" />
                <span>{{ formatChineseDate(photo.date) }}</span>
              </button>
            </div>
          </section>
        </div>

        <aside class="side-column">
          <section class="panel compact-panel today-card">
            <el-icon><Notebook /></el-icon>
            <div>
              <h2>{{ diaryCountText }}</h2>
              <p>已经留下的共同记录</p>
            </div>
          </section>

          <section class="panel compact-panel">
            <div class="section-head tight">
              <h2>草稿</h2>
              <el-button text @click="router.push('/drafts')">管理</el-button>
            </div>
            <div v-loading="loading.drafts" class="mini-list">
              <el-empty v-if="drafts.length === 0" description="暂无草稿" />
              <button v-for="draft in drafts" :key="draft.id" @click="openDraft(draft)">
                <strong>{{ draft.payload?.title || draftTypeLabel(draft) }}</strong>
                <span>{{ formatChineseDateTime(draft.updatedAt) }}</span>
              </button>
            </div>
          </section>

          <section class="panel compact-panel">
            <div class="section-head tight">
              <h2>纪念日</h2>
              <el-button text @click="router.push('/anniversaries')">查看</el-button>
            </div>
            <div v-loading="loading.anniversaries" class="mini-list">
              <el-empty v-if="anniversaries.length === 0" description="暂无纪念日" />
              <button v-for="item in anniversaries" :key="item.id" @click="router.push('/anniversaries')">
                <strong>{{ item.title }}</strong>
                <span>{{ anniversaryText(item) }}</span>
              </button>
            </div>
          </section>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { Edit, Notebook, Tickets } from '@element-plus/icons-vue'
import { useDiaryStore } from '@/stores/diary'
import { useWorkspaceStore } from '@/stores/workspace'
import { anniversaryApi, draftApi } from '@/api/experience'
import { albumApi } from '@/api/album'
import { mediaThumbnailUrl } from '@/api/v3Adapters'
import { moodColor, moodLabel, stripHtml } from '@/utils/diaryMeta'
import { formatChineseDate, formatChineseDateTime } from '@/utils/dateDisplay'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'

const router = useRouter()
const diaryStore = useDiaryStore()
const workspaceStore = useWorkspaceStore()

const recentDiaries = ref([])
const drafts = ref([])
const anniversaries = ref([])
const favoritePhotos = ref([])
const totalDiaries = ref(0)
const loading = reactive({
  diaries: false,
  drafts: false,
  anniversaries: false,
  photos: false
})

const todayText = new Date().toLocaleDateString('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})
const heroLine = computed(() => {
  if (drafts.value.length) return `你们还有 ${drafts.value.length} 份草稿可以继续写。`
  if (recentDiaries.value.length) return '最近的日记已经在下面准备好了。'
  return '从第一篇开始，把日常慢慢写成回忆。'
})
const diaryCountText = computed(() => totalDiaries.value ? `${totalDiaries.value} 篇` : '还没有日记')

const previewText = (diary, limit = 100) => {
  const text = diary.contentHtml ? stripHtml(diary.contentHtml) : diary.contentText
  return `${(text || '').slice(0, limit)}${text?.length > limit ? '...' : ''}`
}
const firstDiaryImage = diary => diary?.media?.find(item => item.mediaType === 'IMAGE')

const requireSpaceId = async () => {
  await workspaceStore.loadSpaces()
  if (!workspaceStore.activeSpaceId) throw new Error('当前账户没有可用日记空间')
  return workspaceStore.activeSpaceId
}

const draftTypeLabel = (draft) => String(draft.draftKey || '').startsWith('create') ? '新日记草稿' : '编辑草稿'
const draftDiaryId = (draft) => {
  if (draft.diaryId) return draft.diaryId
  const match = String(draft.draftKey || '').match(/^edit-(.+)$/)
  return match ? match[1] : null
}
const openDraft = (draft) => {
  if (String(draft.draftKey || '').startsWith('create')) {
    router.push({ path: '/diaries/create', query: { draftKey: draft.draftKey } })
    return
  }
  const diaryId = draftDiaryId(draft)
  if (diaryId) {
    router.push(`/diaries/${diaryId}/edit`)
  } else {
    ElMessage.warning('草稿缺少关联日记，无法继续编辑')
  }
}

const anniversaryText = (item) => {
  if (item.daysUntil === 0) return '就是今天'
  if (item.daysUntil > 0) return `${item.daysUntil} 天后`
  return `已经 ${item.daysPassed} 天`
}

const loadDiaries = async () => {
  loading.diaries = true
  try {
    await diaryStore.fetchDiaries({ page: 0, size: 4 })
    recentDiaries.value = diaryStore.diaries
    totalDiaries.value = diaryStore.pagination.totalElements
  } finally {
    loading.diaries = false
  }
}

const loadDrafts = async () => {
  loading.drafts = true
  try {
    drafts.value = (await draftApi.list(await requireSpaceId())).slice(0, 3)
  } finally {
    loading.drafts = false
  }
}

const loadAnniversaries = async () => {
  loading.anniversaries = true
  try {
    anniversaries.value = (await anniversaryApi.list(await requireSpaceId())).slice(0, 3)
  } finally {
    loading.anniversaries = false
  }
}

const loadFavoritePhotos = async () => {
  loading.photos = true
  try {
    favoritePhotos.value = (await albumApi.getSystemPhotoPage(
      await requireSpaceId(), 'favorites', { page: 0, size: 6 }
    )).content || []
  } finally {
    loading.photos = false
  }
}

onMounted(async () => {
  await Promise.allSettled([loadDiaries(), loadDrafts(), loadAnniversaries(), loadFavoritePhotos()])
})
</script>

<style src="./styles/Home.scss" scoped lang="scss"></style>
