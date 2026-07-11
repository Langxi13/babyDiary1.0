<template>
  <div class="page-shell">
    <main class="page-container">
      <div class="page-title-row ai-page-hero">
        <div>
          <span class="hero-kicker">智能回忆报告</span>
          <h1>AI 报告</h1>
          <p>把一周或一个月的日记整理成温暖回忆</p>
        </div>
        <div class="report-stats">
          <div>
            <span>当前类型</span>
            <strong>{{ generateForm.type === 'WEEKLY' ? '周报' : '月报' }}</strong>
          </div>
          <div>
            <span>周期</span>
            <strong>{{ reportPeriodText }}</strong>
          </div>
          <div>
            <span>历史</span>
            <strong>{{ totalReports }} 份</strong>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="report-tabs">
        <el-tab-pane label="生成报告" name="generate">
          <section class="generate-shell">
            <aside class="panel control-panel generate-assistant-card">
              <div class="assistant-card-head">
                <el-icon><MagicStick /></el-icon>
                <div>
                  <strong>生成助手</strong>
                  <span>选择周期后生成一份可复制的 Markdown 报告。</span>
                </div>
              </div>
              <el-form label-position="top" class="generate-form">
                <el-form-item label="报告类型">
                  <el-radio-group v-model="generateForm.type" class="type-switch">
                    <el-radio-button label="WEEKLY">周报</el-radio-button>
                    <el-radio-button label="MONTHLY">月报</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <el-form-item :label="generateForm.type === 'WEEKLY' ? '选择周内任意一天' : '选择月份'">
                  <el-date-picker
                    v-if="generateForm.type === 'WEEKLY'"
                    v-model="generateForm.date"
                    type="date"
                    format="YYYY年MM月DD日"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                  <el-date-picker
                    v-else
                    v-model="generateForm.month"
                    type="month"
                    format="YYYY年MM月"
                    value-format="YYYY-MM"
                    style="width: 100%"
                  />
                </el-form-item>

                <div class="period-chip">
                  <span>生成周期</span>
                  <strong>{{ periodLabel }}</strong>
                </div>

                <el-button class="generate-button" type="primary" size="large" :loading="generating" @click="generateReport">
                  <el-icon><MagicStick /></el-icon>
                  生成报告
                </el-button>
                <div v-if="generating" class="generation-status" role="status" aria-live="polite">
                  <span class="generation-pulse" />
                  <p>正在读取日记并整理内容，已等待 {{ generationElapsed }} 秒，请保持页面打开。</p>
                </div>
              </el-form>
            </aside>

            <article v-if="currentReport" class="panel report-preview report-preview-surface">
              <div class="preview-head">
                <div>
                  <h2>{{ currentReport.title }}</h2>
                  <span>{{ formatChineseDateRange(currentReport.periodStart, currentReport.periodEnd) }} · {{ currentReport.diaryCount }} 篇日记</span>
                </div>
                <el-button @click="copyReport(currentReport.contentMarkdown)">
                  <el-icon><DocumentCopy /></el-icon>
                  复制
                </el-button>
              </div>
              <div class="markdown-report" v-html="renderedReportHtml" />
            </article>

            <section v-else class="panel report-empty report-preview-surface">
              <el-icon><MagicStick /></el-icon>
              <strong>选择周期后生成报告</strong>
              <span>生成后的 Markdown 内容会显示在这里。</span>
            </section>
          </section>
        </el-tab-pane>

        <el-tab-pane label="历史报告" name="history">
          <section class="panel history-panel" v-loading="historyLoading">
            <div class="section-heading">
              <div>
                <span>报告归档</span>
                <strong>历史报告</strong>
              </div>
              <p>点击任意报告可以回到生成页查看完整内容。</p>
            </div>
            <div class="history-toolbar">
              <el-radio-group v-model="historyType" @change="resetReports">
                <el-radio-button label="">全部</el-radio-button>
                <el-radio-button label="WEEKLY">周报</el-radio-button>
                <el-radio-button label="MONTHLY">月报</el-radio-button>
              </el-radio-group>
              <el-button @click="resetReports">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>

            <el-empty v-if="reports.length === 0" description="暂无报告" />
            <div v-else class="report-list">
              <article v-for="report in reports" :key="report.reportId" class="report-item" @click="openReport(report.reportId)">
                <div>
                  <div class="report-item-title">
                    <strong>{{ report.title }}</strong>
                    <em>{{ formatReportType(report.type) }}</em>
                  </div>
                  <span>{{ formatChineseDateRange(report.periodStart, report.periodEnd) }} · {{ report.diaryCount }} 篇</span>
                  <small>{{ formatChineseDateTime(report.createdAt) }}</small>
                </div>
                <el-popconfirm
                  title="确定删除这份报告吗？"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="deleteReport(report.reportId)"
                >
                  <template #reference>
                    <el-button
                      type="danger"
                      text
                      :loading="deletingReportId === report.reportId"
                      :disabled="!!deletingReportId"
                      @click.stop
                    >
                      <el-icon><Delete /></el-icon>
                      <span class="report-delete-label">删除</span>
                    </el-button>
                  </template>
                </el-popconfirm>
              </article>
            </div>
            <div v-if="reports.length" class="history-footer">
              <span>已显示 {{ reports.length }} / {{ totalReports }} 份</span>
              <el-button v-if="hasMoreReports" :loading="historyLoadingMore" @click="loadMoreReports">
                加载更多
              </el-button>
              <strong v-else>已显示全部</strong>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="AI 配置" name="config">
          <section class="config-shell">
            <el-form label-position="top" class="config-form">
              <div class="panel config-card">
                <div class="config-card-head">
                  <el-icon><Setting /></el-icon>
                  <div>
                    <strong>服务配置</strong>
                    <span>模型和接口地址会影响报告质量与生成速度。</span>
                  </div>
                </div>
                <el-form-item label="启用 AI">
                  <el-switch v-model="configForm.enabled" />
                </el-form-item>
                <el-form-item label="Base URL">
                  <el-input v-model="configForm.baseUrl" placeholder="https://api.openai.com/v1" />
                </el-form-item>
                <el-form-item label="模型">
                  <div class="model-picker">
                    <el-select
                      v-model="configForm.model"
                      filterable
                      allow-create
                      default-first-option
                      clearable
                      placeholder="选择或输入模型名"
                    >
                      <el-option v-for="model in modelOptions" :key="model" :label="model" :value="model" />
                    </el-select>
                    <el-button :loading="loadingModels" @click="loadModels">
                      <el-icon><Refresh /></el-icon>
                      加载模型
                    </el-button>
                  </div>
                </el-form-item>
              </div>

              <div class="panel config-card">
                <div class="config-card-head">
                  <el-icon><MagicStick /></el-icon>
                  <div>
                    <strong>连接设置</strong>
                    <span>保存密钥后可测试连接，再回到生成页使用。</span>
                  </div>
                </div>
                <el-form-item label="API Key">
                  <el-input v-model="configForm.apiKey" type="password" show-password :placeholder="configForm.apiKeyMasked || '保存后不再明文显示'" />
                </el-form-item>
                <el-form-item label="超时时间（秒）">
                  <el-input-number v-model="configForm.timeoutSeconds" :min="5" :max="120" />
                </el-form-item>
                <div class="config-actions">
                  <el-button type="primary" :loading="savingConfig" @click="saveConfig">保存配置</el-button>
                  <el-button :loading="testingConfig" @click="testConfig">测试连接</el-button>
                </div>
              </div>
            </el-form>
          </section>
        </el-tab-pane>
      </el-tabs>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElPopconfirm } from 'element-plus/es/components/popconfirm/index.mjs'
import { ElRadioButton, ElRadioGroup } from 'element-plus/es/components/radio/index.mjs'
import { ElSwitch } from 'element-plus/es/components/switch/index.mjs'
import { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
import { Delete, DocumentCopy, MagicStick, Refresh, Setting } from '@element-plus/icons-vue'
import { aiApi } from '@/api/ai'
import { formatLocalDate, formatMonthlyPeriod, formatWeeklyPeriod } from '@/utils/aiReportPeriod'
import { formatChineseDateRange, formatChineseDateTime, formatChineseReportPeriod } from '@/utils/dateDisplay'
import { renderMarkdownReport } from '@/utils/markdownReport'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/input-number/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/popconfirm/style/css.mjs'
import 'element-plus/es/components/radio-button/style/css.mjs'
import 'element-plus/es/components/radio-group/style/css.mjs'
import 'element-plus/es/components/radio/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/switch/style/css.mjs'
import 'element-plus/es/components/tabs/style/css.mjs'

const activeTab = ref('generate')
const generating = ref(false)
const savingConfig = ref(false)
const testingConfig = ref(false)
const loadingModels = ref(false)
const historyLoading = ref(false)
const historyLoadingMore = ref(false)
const currentReport = ref(null)
const reports = ref([])
const historyType = ref('')
const modelOptions = ref([])
const historyPage = ref(0)
const totalReports = ref(0)
const deletingReportId = ref(null)
const generationElapsed = ref(0)
let generationTimer = null
let historyLoadVersion = 0

const today = new Date()
const generateForm = reactive({
  type: 'WEEKLY',
  date: formatLocalDate(today),
  month: formatMonthlyPeriod(today)
})

const configForm = reactive({
  enabled: false,
  baseUrl: '',
  model: '',
  apiKey: '',
  apiKeyMasked: '',
  timeoutSeconds: 30
})

const reportPeriod = computed(() => {
  if (generateForm.type === 'MONTHLY') {
    return generateForm.month || formatMonthlyPeriod(new Date())
  }
  return formatWeeklyPeriod(generateForm.date ? new Date(`${generateForm.date}T00:00:00`) : new Date())
})

const reportPeriodText = computed(() => formatChineseReportPeriod(generateForm.type, reportPeriod.value))

const periodLabel = computed(() => {
  return `${reportPeriodText.value} ${generateForm.type === 'MONTHLY' ? '月报' : '周报'}`
})

const renderedReportHtml = computed(() => renderMarkdownReport(currentReport.value?.contentMarkdown || ''))
const hasMoreReports = computed(() => reports.value.length < totalReports.value)

const formatReportType = (type) => type === 'MONTHLY' ? '月报' : '周报'

const stopGenerationTimer = () => {
  if (generationTimer) {
    window.clearInterval(generationTimer)
    generationTimer = null
  }
}

const startGenerationTimer = () => {
  stopGenerationTimer()
  generationElapsed.value = 0
  generationTimer = window.setInterval(() => {
    generationElapsed.value += 1
  }, 1000)
}

const loadConfig = async () => {
  const response = await aiApi.getConfig()
  const data = response.data || {}
  Object.assign(configForm, {
    enabled: !!data.enabled,
    baseUrl: data.baseUrl || '',
    model: data.model || '',
    apiKey: '',
    apiKeyMasked: data.apiKeyMasked || '',
    timeoutSeconds: data.timeoutSeconds || 30
  })
}

const persistConfig = async (showMessage = true) => {
  savingConfig.value = true
  try {
    const payload = {
      enabled: configForm.enabled,
      baseUrl: configForm.baseUrl,
      model: configForm.model,
      apiKey: configForm.apiKey,
      timeoutSeconds: configForm.timeoutSeconds
    }
    const response = await aiApi.saveConfig(payload)
    Object.assign(configForm, {
      ...configForm,
      apiKey: '',
      apiKeyMasked: response.data?.apiKeyMasked || ''
    })
    if (showMessage) {
      ElMessage.success('AI配置已保存')
    }
  } finally {
    savingConfig.value = false
  }
}

const saveConfig = () => persistConfig(true)

const loadModels = async () => {
  loadingModels.value = true
  try {
    await persistConfig(false)
    const response = await aiApi.listModels(configForm.timeoutSeconds)
    modelOptions.value = response.data || []
    ElMessage.success(`已加载 ${modelOptions.value.length} 个模型`)
  } finally {
    loadingModels.value = false
  }
}

const testConfig = async () => {
  testingConfig.value = true
  try {
    await aiApi.testConfig(configForm.timeoutSeconds)
    ElMessage.success('连接成功')
  } finally {
    testingConfig.value = false
  }
}

const generateReport = async () => {
  if (generating.value) return
  generating.value = true
  startGenerationTimer()
  try {
    const response = await aiApi.generateReport({
      type: generateForm.type,
      period: reportPeriod.value
    }, configForm.timeoutSeconds)
    currentReport.value = response.data
    ElMessage.success('报告已生成')
    await resetReports()
  } finally {
    generating.value = false
    stopGenerationTimer()
  }
}

const loadReports = async ({ append = false } = {}) => {
  if (append && (historyLoading.value || historyLoadingMore.value || !hasMoreReports.value)) return
  const requestVersion = append ? historyLoadVersion : ++historyLoadVersion
  const nextPage = append ? historyPage.value + 1 : 0
  if (append) {
    historyLoadingMore.value = true
  } else {
    historyLoading.value = true
  }
  try {
    const response = await aiApi.listReports({ type: historyType.value || undefined, page: nextPage, size: 10 })
    if (requestVersion !== historyLoadVersion) return

    const content = response.data?.content || []
    if (append) {
      const existingIds = new Set(reports.value.map(report => report.reportId))
      reports.value = reports.value.concat(content.filter(report => !existingIds.has(report.reportId)))
    } else {
      reports.value = content
    }
    historyPage.value = response.data?.pageNumber ?? nextPage
    totalReports.value = response.data?.totalElements || 0
  } finally {
    if (requestVersion === historyLoadVersion) {
      historyLoading.value = false
      historyLoadingMore.value = false
    }
  }
}

const resetReports = () => loadReports()
const loadMoreReports = () => loadReports({ append: true })

const openReport = async (reportId) => {
  const response = await aiApi.getReport(reportId)
  currentReport.value = response.data
  activeTab.value = 'generate'
}

const deleteReport = async (reportId) => {
  if (deletingReportId.value) return
  deletingReportId.value = reportId
  try {
    await aiApi.deleteReport(reportId)
    if (currentReport.value?.reportId === reportId) {
      currentReport.value = null
    }
    await resetReports()
    ElMessage.success('报告已删除')
  } finally {
    deletingReportId.value = null
  }
}

const copyReport = async (content) => {
  await navigator.clipboard.writeText(content || '')
  ElMessage.success('已复制')
}

onMounted(async () => {
  await Promise.allSettled([loadConfig(), resetReports()])
})

onBeforeUnmount(stopGenerationTimer)
</script>

<style src="./styles/AiReports.scss" scoped lang="scss"></style>
