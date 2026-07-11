<template>
  <div class="workspace-page">
    <main class="workspace-container">
      <header class="workspace-header">
        <div class="workspace-identity">
          <space-switcher />
          <div>
            <h1>{{ workspaceStore.activeSpace?.name || '共同空间' }}</h1>
            <p>{{ workspaceStore.activeSpace?.type === 'PERSONAL' ? '个人日记空间' : `${workspaceStore.activeSpace?.memberCount || 0} 位成员共同记录` }}</p>
          </div>
        </div>
        <div class="workspace-actions">
          <button type="button" class="sync-state" :class="{ offline: !workspaceStore.online, issue: syncIssues.length }" @click="syncIssues.length && (syncIssuesOpen = true)">
            <i />{{ syncText }}
          </button>
          <el-button @click="createSpaceDialog = true">
            <el-icon><Plus /></el-icon>新空间
          </el-button>
          <el-button @click="router.push('/spaces/settings')">
            <el-icon><Setting /></el-icon>设置
          </el-button>
          <el-button type="primary" @click="openEditor()">
            <el-icon><EditPen /></el-icon>写日记
          </el-button>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="workspace-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="共同日记" name="diaries">
          <section class="diary-toolbar">
            <el-input v-model="filters.keyword" clearable placeholder="搜索标题或正文" @keyup.enter="loadDiaries(true)">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-segmented v-model="filters.trash" :options="trashOptions" @change="loadDiaries(true)" />
            <el-button :icon="Refresh" circle title="刷新" @click="refreshWorkspace" />
          </section>

          <section v-loading="loadingDiaries" class="diary-stream">
            <el-empty v-if="!loadingDiaries && !diaries.length" :description="filters.trash ? '回收站为空' : '还没有共同日记'" />
            <article
              v-for="diary in diaries"
              :key="diary.publicId"
              class="space-diary-card"
              :class="{ pending: diary.pending, locked: diary.locked }"
              tabindex="0"
              @click="openDetail(diary)"
              @keyup.enter="openDetail(diary)"
            >
              <div class="diary-card-date">
                <strong>{{ dayOf(diary.date) }}</strong>
                <span>{{ monthOf(diary.date) }}</span>
              </div>
              <div class="diary-card-main">
                <div class="diary-card-meta">
                  <span v-if="diary.moodKey" class="mood-mark">{{ moodEmoji(diary.moodKey) }}</span>
                  <span>{{ diary.authorName || '我' }}</span>
                  <span>{{ diary.visibility === 'PRIVATE' ? '仅自己' : '共同可见' }}</span>
                  <span v-if="diary.pending">待同步</span>
                  <el-icon v-if="diary.locked"><Lock /></el-icon>
                </div>
                <h2>{{ diary.title }}</h2>
                <p>{{ diary.locked ? '这篇日记已锁定' : excerpt(diary.content) }}</p>
                <div v-if="diary.tags?.length" class="diary-card-tags">
                  <span v-for="tag in diary.tags.slice(0, 4)" :key="tag.tagId">
                    <i :style="{ background: tag.color }" />{{ tag.name }}
                  </span>
                </div>
              </div>
              <div class="diary-card-side" @click.stop>
                <div class="media-count" v-if="mediaCount(diary)">
                  <el-icon><Picture /></el-icon>{{ mediaCount(diary) }}
                </div>
                <el-dropdown trigger="click" @command="command => handleDiaryCommand(command, diary)">
                  <el-button :icon="MoreFilled" circle text aria-label="日记操作" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="!filters.trash" command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item v-if="!filters.trash" command="delete" divided>移入回收站</el-dropdown-item>
                      <el-dropdown-item v-else command="restore">恢复日记</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </article>
          </section>

          <el-pagination
            v-if="pagination.totalElements > pagination.pageSize"
            class="workspace-pagination"
            layout="prev, pager, next"
            :current-page="pagination.pageNumber + 1"
            :page-size="pagination.pageSize"
            :total="pagination.totalElements"
            :pager-count="5"
            @current-change="page => { pagination.pageNumber = page - 1; loadDiaries() }"
          />
        </el-tab-pane>

        <el-tab-pane label="搜索" name="search">
          <section class="search-surface">
            <div class="search-box">
              <el-input v-model="searchQuery" size="large" placeholder="搜索共同回忆" @keyup.enter="runSearch">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-button type="primary" :loading="searching" @click="runSearch">搜索</el-button>
            </div>
            <div class="search-results">
              <button v-for="item in searchResults" :key="item.entityId" type="button" @click="router.push(`/spaces/${activeSpaceId}/diaries/${item.entityId}`)">
                <time>{{ formatChineseDate(item.date) }}</time>
                <strong>{{ item.title }}</strong>
                <p>{{ excerpt(item.snippet, 180) }}</p>
              </button>
              <el-empty v-if="searchPerformed && !searchResults.length" description="没有找到相关回忆" />
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="记录洞察" name="insights">
          <section v-loading="loadingInsights" class="insight-surface">
            <div class="insight-heading">
              <h2>{{ insightYear }} 年记录</h2>
              <el-date-picker v-model="insightYear" type="year" value-format="YYYY" format="YYYY年" :clearable="false" @change="loadInsights" />
            </div>
            <div v-if="insights" class="metric-grid">
              <div><strong>{{ insights.diaryCount }}</strong><span>篇日记</span></div>
              <div><strong>{{ insights.activeDays }}</strong><span>个记录日</span></div>
              <div><strong>{{ insights.longestStreak }}</strong><span>最长连续天数</span></div>
              <div><strong>{{ insights.photoCount }}</strong><span>张照片</span></div>
            </div>
            <div v-if="insights" class="month-chart" aria-label="每月日记数量">
              <div v-for="month in completeMonths" :key="month.month">
                <span :style="{ height: monthHeight(month.count) }" />
                <small>{{ Number(month.month.slice(5)) }}月</small>
              </div>
            </div>
            <div v-if="insights?.moods?.length" class="mood-summary">
              <h3>这一年的心情</h3>
              <span v-for="mood in insights.moods" :key="mood.moodKey">{{ moodEmoji(mood.moodKey) }} {{ mood.count }}</span>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="AI 回顾" name="reports">
          <section class="report-surface">
            <div class="report-actions">
              <div>
                <h2>共同回顾</h2>
                <p>根据共同可见的日记生成</p>
              </div>
              <el-dropdown @command="generateReport">
                <el-button type="primary" :loading="generatingReport">
                  <el-icon><MagicStick /></el-icon>生成回顾<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="WEEKLY">本周周报</el-dropdown-item>
                    <el-dropdown-item command="MONTHLY">本月月报</el-dropdown-item>
                    <el-dropdown-item command="ANNUAL">本年回顾</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <article v-for="report in reports" :key="report.reportId" class="report-entry">
              <header><span>{{ report.type === 'WEEKLY' ? '周报' : report.type === 'MONTHLY' ? '月报' : '年报' }}</span><time>{{ formatChineseDate(report.createdAt) }}</time></header>
              <h3>{{ report.title }}</h3>
              <div class="report-markdown" v-html="renderMarkdownReport(report.contentMarkdown)" />
            </article>
            <el-empty v-if="!loadingReports && !reports.length" description="还没有共同回顾" />
          </section>
        </el-tab-pane>
      </el-tabs>
    </main>

    <space-diary-editor
      v-model="editorOpen"
      :space-id="activeSpaceId"
      :diary="editingDiary"
      :tags="tags"
      :templates="templates"
      :step-up-token="stepUpToken"
      @saved="handleSaved"
    />

    <el-dialog v-model="createSpaceDialog" title="创建共同空间" width="min(420px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="空间名称">
          <el-input v-model="newSpaceName" maxlength="100" @keyup.enter="createSpace" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createSpaceDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingSpace" @click="createSpace">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="syncIssuesOpen" title="待处理的同步问题" width="min(620px, 94vw)">
      <div class="sync-issue-list">
        <article v-for="issue in syncIssues" :key="issue.operationId">
          <div>
            <strong>{{ issue.status === 'CONFLICT' ? '内容版本冲突' : issue.status === 'RETRYABLE' ? '暂时无法同步' : '同步失败' }}</strong>
            <span>{{ issue.message || '请检查本地内容后重试' }}</span>
          </div>
          <el-button v-if="issue.entityId" size="small" @click="openSyncIssue(issue)">查看</el-button>
          <el-button size="small" type="danger" text @click="discardSyncIssue(issue)">放弃本地改动</el-button>
        </article>
        <el-empty v-if="!syncIssues.length" description="没有待处理问题" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
import { ElSegmented } from 'element-plus/es/components/segmented/index.mjs'
import { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
import { ArrowDown, EditPen, Lock, MagicStick, MoreFilled, Picture, Plus, Refresh, Search, Setting } from '@element-plus/icons-vue'
import SpaceSwitcher from '@/components/common/SpaceSwitcher.vue'
import SpaceDiaryEditor from '@/components/workspace/SpaceDiaryEditor.vue'
import { workspaceApi } from '@/api/workspace'
import { useWorkspaceStore } from '@/stores/workspace'
import { getOfflineCache, listOfflineOperations, queueOfflineDiaryOperation, removeOfflineOperations, setOfflineCache } from '@/utils/offlineDb'
import { applyPendingDiaryOperations } from '@/utils/offlineQueue'
import { withStepUpRetry } from '@/utils/stepUp'
import { formatChineseDate } from '@/utils/dateDisplay'
import { renderMarkdownReport } from '@/utils/markdownReport'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/dropdown/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/message-box/style/css.mjs'
import 'element-plus/es/components/pagination/style/css.mjs'
import 'element-plus/es/components/segmented/style/css.mjs'
import 'element-plus/es/components/tabs/style/css.mjs'

const router = useRouter()
const route = useRoute()
const workspaceStore = useWorkspaceStore()
const activeTab = ref(route.query.tab || 'diaries')
const loadingDiaries = ref(false)
const diaries = ref([])
const tags = ref([])
const templates = ref([])
const editorOpen = ref(false)
const editingDiary = ref(null)
const createSpaceDialog = ref(false)
const newSpaceName = ref('')
const creatingSpace = ref(false)
const searchQuery = ref('')
const searching = ref(false)
const searchPerformed = ref(false)
const searchResults = ref([])
const loadingInsights = ref(false)
const insightYear = ref(String(new Date().getFullYear()))
const insights = ref(null)
const reports = ref([])
const loadingReports = ref(false)
const generatingReport = ref(false)
const syncIssues = ref([])
const syncIssuesOpen = ref(false)
const stepUpToken = ref(sessionStorage.getItem('stepUpToken') || '')
const filters = reactive({ keyword: '', trash: false })
const pagination = reactive({ pageNumber: 0, pageSize: 10, totalElements: 0 })
const trashOptions = [{ label: '日记', value: false }, { label: '回收站', value: true }]
let diaryRequestId = 0

const activeSpaceId = computed(() => workspaceStore.activeSpaceId)
const syncText = computed(() => {
  if (!workspaceStore.online) return `离线${workspaceStore.pendingCount ? ` · ${workspaceStore.pendingCount}项待同步` : ''}`
  if (syncIssues.value.length) return `${syncIssues.value.length} 项待处理`
  if (workspaceStore.pendingCount) return `${workspaceStore.pendingCount} 项待同步`
  return '已同步'
})
const completeMonths = computed(() => Array.from({ length: 12 }, (_, index) => {
  const key = `${insightYear.value}-${String(index + 1).padStart(2, '0')}`
  return insights.value?.months?.find(item => item.month === key) || { month: key, count: 0 }
}))

const loadDiaries = async reset => {
  const requestedSpaceId = activeSpaceId.value
  if (!requestedSpaceId) return
  const requestId = ++diaryRequestId
  if (reset) pagination.pageNumber = 0
  loadingDiaries.value = true
  const params = { page: pagination.pageNumber, size: pagination.pageSize, keyword: filters.keyword || undefined, trash: filters.trash }
  const cacheKey = `workspace:${requestedSpaceId}:diaries:${JSON.stringify(params)}`
  try {
    const response = await workspaceApi.diaries.list(requestedSpaceId, params)
    const operations = await listOfflineOperations(requestedSpaceId)
    if (requestId !== diaryRequestId || activeSpaceId.value !== requestedSpaceId) return
    diaries.value = applyPendingDiaryOperations(response.data.content || [], operations, filters.trash)
    Object.assign(pagination, response.data)
    pagination.totalElements = Math.max(pagination.totalElements || 0, diaries.value.length)
    await setOfflineCache(cacheKey, response.data)
  } catch (error) {
    if (!error.response) {
      const cached = await getOfflineCache(cacheKey)
      const operations = await listOfflineOperations(requestedSpaceId)
      if (requestId === diaryRequestId && activeSpaceId.value === requestedSpaceId && cached) {
        diaries.value = applyPendingDiaryOperations(cached.content || [], operations, filters.trash)
        Object.assign(pagination, cached)
        pagination.totalElements = Math.max(pagination.totalElements || 0, diaries.value.length)
      }
    }
  } finally {
    if (requestId === diaryRequestId) loadingDiaries.value = false
  }
}

const loadWorkspaceData = async () => {
  const requestedSpaceId = activeSpaceId.value
  if (!requestedSpaceId) return
  const [, tagResponse, templateResponse] = await Promise.all([
    loadDiaries(true),
    workspaceApi.spaces.tags(requestedSpaceId),
    workspaceApi.templates.list(requestedSpaceId)
  ])
  if (activeSpaceId.value !== requestedSpaceId) return
  tags.value = tagResponse.data || []
  templates.value = templateResponse.data || []
}

const refreshWorkspace = async () => {
  const result = await workspaceStore.syncActive().catch(() => null)
  if (result) syncIssues.value = [...result.conflicts, ...result.failures]
  await loadWorkspaceData()
}

const openEditor = diary => {
  editingDiary.value = diary || null
  editorOpen.value = true
}

const openDetail = diary => {
  if (diary.pending) return openEditor(diary)
  router.push(`/spaces/${activeSpaceId.value}/diaries/${diary.publicId}`)
}

const handleSaved = saved => {
  const index = diaries.value.findIndex(item => item.publicId === saved.publicId)
  if (index >= 0) diaries.value.splice(index, 1, saved)
  else diaries.value.unshift(saved)
  workspaceStore.refreshPendingCount()
  if (!saved.pending) loadDiaries()
}

const handleDiaryCommand = async (command, diary) => {
  if (command === 'edit') {
    if (diary.locked) return openDetail(diary)
    return openEditor(diary)
  }
  if (command === 'delete') {
    await ElMessageBox.confirm('这篇日记会在回收站保留30天。', '移入回收站', { confirmButtonText: '移入', cancelButtonText: '取消', type: 'warning' })
    await applyDiaryStateChange('DELETE', diary)
  } else if (command === 'restore') {
    await applyDiaryStateChange('RESTORE', diary)
  }
  await loadDiaries()
}

const applyDiaryStateChange = async (action, diary) => {
  if (!workspaceStore.online) {
    if (diary.locked) return ElMessage.warning('锁定日记需要联网完成二次验证')
    await queueDiaryStateChange(action, diary)
    return
  }
  try {
    await withStepUpRetry(token => action === 'DELETE'
      ? workspaceApi.diaries.remove(activeSpaceId.value, diary.publicId, diary.version, token)
      : workspaceApi.diaries.restore(activeSpaceId.value, diary.publicId, diary.version, token))
  } catch (error) {
    if (error.response) throw error
    await queueDiaryStateChange(action, diary)
  }
}

const queueDiaryStateChange = async (action, diary) => {
  await queueOfflineDiaryOperation({
    id: crypto.randomUUID(),
    spaceId: activeSpaceId.value,
    action,
    entityId: diary.publicId,
    baseVersion: diary.version,
    payload: null,
    localSnapshot: { ...diary }
  })
  await workspaceStore.refreshPendingCount()
  ElMessage.success('已保存到本机，联网后自动同步')
}

const createSpace = async () => {
  if (!newSpaceName.value.trim()) return
  creatingSpace.value = true
  try {
    await workspaceStore.createSpace(newSpaceName.value.trim())
    newSpaceName.value = ''
    createSpaceDialog.value = false
    await loadWorkspaceData()
  } finally { creatingSpace.value = false }
}

const runSearch = async () => {
  if (!searchQuery.value.trim()) return
  searching.value = true
  try {
    const response = await workspaceApi.search(activeSpaceId.value, searchQuery.value.trim())
    searchResults.value = response.data.results || []
    searchPerformed.value = true
  } finally { searching.value = false }
}

const loadInsights = async () => {
  if (!activeSpaceId.value) return
  loadingInsights.value = true
  try {
    const response = await workspaceApi.insights(activeSpaceId.value, insightYear.value)
    insights.value = response.data
  } finally { loadingInsights.value = false }
}

const loadReports = async () => {
  if (!activeSpaceId.value) return
  loadingReports.value = true
  try {
    const response = await workspaceApi.ai.reports(activeSpaceId.value, { page: 0, size: 20 })
    reports.value = response.data.content || []
  } finally { loadingReports.value = false }
}

const generateReport = async type => {
  generatingReport.value = true
  try {
    const now = new Date()
    let period
    if (type === 'ANNUAL') period = String(now.getFullYear())
    else if (type === 'MONTHLY') period = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    else period = isoWeek(now)
    await workspaceApi.ai.generate(activeSpaceId.value, { type, period })
    ElMessage.success('共同回顾已生成')
    await loadReports()
  } finally { generatingReport.value = false }
}

const handleTabChange = name => {
  router.replace({ query: name === 'diaries' ? {} : { tab: name } })
  if (name === 'insights' && !insights.value) loadInsights()
  if (name === 'reports' && !reports.value.length) loadReports()
}

watch(activeSpaceId, (next, previous) => {
  if (next && next !== previous) {
    searchResults.value = []
    insights.value = null
    reports.value = []
    loadWorkspaceData()
  }
})

const handleWorkspaceChanges = event => {
  if (!event.detail?.spaceId || event.detail.spaceId === activeSpaceId.value) loadDiaries()
}

const handleSyncIssues = event => {
  if (event.detail?.spaceId !== activeSpaceId.value) return
  syncIssues.value = [...(event.detail.conflicts || []), ...(event.detail.failures || [])]
  if (syncIssues.value.length) syncIssuesOpen.value = true
}

const openSyncIssue = async issue => {
  syncIssuesOpen.value = false
  const local = issue.local
  if (local?.action === 'CREATE') {
    await removeOfflineOperations([issue.operationId])
    syncIssues.value = syncIssues.value.filter(item => item.operationId !== issue.operationId)
    openEditor({ ...local.localSnapshot, ...local.payload, publicId: issue.entityId, version: 0, pending: true, pendingAction: 'CREATE' })
  } else {
    router.push(`/spaces/${activeSpaceId.value}/diaries/${issue.entityId}`)
  }
}

const discardSyncIssue = async issue => {
  await removeOfflineOperations([issue.operationId])
  syncIssues.value = syncIssues.value.filter(item => item.operationId !== issue.operationId)
  await workspaceStore.refreshPendingCount()
  await loadDiaries()
}

onMounted(async () => {
  await workspaceStore.initialize()
  await loadWorkspaceData()
  if (activeTab.value === 'insights') loadInsights()
  if (activeTab.value === 'reports') loadReports()
  window.addEventListener('workspace:changes', handleWorkspaceChanges)
  window.addEventListener('workspace:sync-issues', handleSyncIssues)
})

onBeforeUnmount(() => {
  window.removeEventListener('workspace:changes', handleWorkspaceChanges)
  window.removeEventListener('workspace:sync-issues', handleSyncIssues)
})

const excerpt = (value, limit = 220) => {
  const text = new DOMParser().parseFromString(value || '', 'text/html').body.textContent.trim()
  return text.length > limit ? `${text.slice(0, limit)}...` : text
}
const moodEmoji = key => ({ happy: '😊', calm: '😌', loved: '🥰', excited: '🤩', tired: '😴', sad: '🥺' }[key] || '🙂')
const mediaCount = diary => (diary.imagePathList?.length || 0) + (diary.media?.length || 0)
const dayOf = value => String(Number(value?.slice(8, 10) || 0)).padStart(2, '0')
const monthOf = value => `${Number(value?.slice(5, 7) || 0)}月`
const monthHeight = count => `${Math.max(5, Math.min(100, count * 10))}%`

function isoWeek(date) {
  const target = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const day = target.getUTCDay() || 7
  target.setUTCDate(target.getUTCDate() + 4 - day)
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1))
  const week = Math.ceil((((target - yearStart) / 86400000) + 1) / 7)
  return `${target.getUTCFullYear()}-W${String(week).padStart(2, '0')}`
}
</script>

<style src="./styles/Workspace.scss" scoped lang="scss"></style>
