<template>
  <div class="settings-page">
    <main class="settings-container">
      <header class="settings-header">
        <div><h1>空间设置</h1><p>{{ workspaceStore.activeSpace?.name }}</p></div>
        <space-switcher />
      </header>

      <nav class="settings-nav">
        <button v-for="item in sections" :key="item.id" type="button" :class="{ active: activeSection === item.id }" @click="activeSection = item.id">
          <el-icon><component :is="item.icon" /></el-icon>{{ item.label }}
        </button>
      </nav>

      <section v-if="activeSection === 'members'" class="settings-section">
        <header><div><h2>成员</h2><p>共同空间的访问成员</p></div><el-button v-if="isOwner && !isPersonal" type="primary" @click="inviteOpen = true"><el-icon><Plus /></el-icon>邀请</el-button></header>
        <div class="member-list">
          <article v-for="member in members" :key="member.userId">
            <el-avatar :size="42" :src="originalImageUrl(member.avatarPath)">{{ member.username?.slice(0, 1) }}</el-avatar>
            <div><strong>{{ member.username }}</strong><span>{{ member.role === 'OWNER' ? '所有者' : '成员' }}</span></div>
            <el-dropdown v-if="isOwner && !isPersonal && member.userId !== authStore.userInfo?.userId" @command="command => memberCommand(command, member)">
              <el-button :icon="MoreFilled" circle text aria-label="成员操作" />
              <template #dropdown><el-dropdown-menu><el-dropdown-item :command="member.role === 'OWNER' ? 'member' : 'owner'">{{ member.role === 'OWNER' ? '设为成员' : '设为所有者' }}</el-dropdown-item><el-dropdown-item command="remove" divided>移除成员</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </article>
        </div>
      </section>

      <section v-if="activeSection === 'labels'" class="settings-section">
        <header><div><h2>标签与模板</h2><p>空间成员共同使用</p></div></header>
        <div class="subsection">
          <div class="subsection-heading"><h3>标签</h3><el-button @click="tagOpen = true"><el-icon><Plus /></el-icon>新标签</el-button></div>
          <div class="tag-list"><span v-for="tag in tags" :key="tag.tagId"><i :style="{ background: tag.color }" />{{ tag.name }}</span><em v-if="!tags.length">暂无标签</em></div>
        </div>
        <div class="subsection">
          <div class="subsection-heading"><h3>日记模板</h3><el-button @click="openTemplate()"><el-icon><Plus /></el-icon>新模板</el-button></div>
          <div class="template-list">
            <article v-for="template in templates" :key="template.templateId">
              <el-icon><Notebook /></el-icon><div><strong>{{ template.name }}</strong><span>{{ template.description || template.promptText }}</span></div>
              <el-button v-if="template.editable" :icon="Edit" circle text title="编辑模板" @click="openTemplate(template)" />
            </article>
          </div>
        </div>
      </section>

      <section v-if="activeSection === 'automation'" class="settings-section">
        <header><div><h2>提醒与自动回顾</h2><p>按你的时区安排记录提醒和 AI 回顾</p></div></header>
        <div class="subsection reminder-settings">
          <div class="subsection-heading"><h3>记录提醒</h3><el-button :loading="savingReminders" @click="saveReminders">保存提醒</el-button></div>
          <div class="reminder-list">
            <label>
              <div><strong>每日提醒</strong><span>每天固定时间提醒你记录</span></div>
              <input v-model="reminders.daily.time" type="time" aria-label="每日提醒时间" />
              <el-switch v-model="reminders.daily.enabled" />
            </label>
            <label>
              <div><strong>每周提醒</strong><span>每周回顾并补充这一周</span></div>
              <select v-model.number="reminders.weekly.dayOfWeek" aria-label="每周提醒星期">
                <option v-for="day in weekDays" :key="day.value" :value="day.value">{{ day.label }}</option>
              </select>
              <input v-model="reminders.weekly.time" type="time" aria-label="每周提醒时间" />
              <el-switch v-model="reminders.weekly.enabled" />
            </label>
          </div>
        </div>
        <div class="subsection">
          <div class="subsection-heading"><h3>AI 自动回顾</h3><span class="owner-note">仅空间所有者可以调整</span></div>
          <div class="toggle-list">
            <label><div><strong>每周回顾</strong><span>每周一整理上一周</span></div><el-switch v-model="schedule.weeklyEnabled" :disabled="!isOwner" /></label>
            <label><div><strong>每月回顾</strong><span>每月第一天整理上个月</span></div><el-switch v-model="schedule.monthlyEnabled" :disabled="!isOwner" /></label>
            <label><div><strong>年度回顾</strong><span>每年第一天整理上一年</span></div><el-switch v-model="schedule.annualEnabled" :disabled="!isOwner" /></label>
          </div>
          <el-button v-if="isOwner" type="primary" :loading="savingSchedule" @click="saveSchedule">保存计划</el-button>
        </div>
      </section>

      <section v-if="activeSection === 'data'" class="settings-section">
        <header><div><h2>数据与导出</h2><p>归档文件包含正文、标签、评论和原图</p></div></header>
        <div class="data-actions">
          <article><el-icon><Download /></el-icon><div><strong>完整归档</strong><span>Baby Diary ZIP</span></div><el-button :loading="exporting" @click="exportArchive">导出</el-button></article>
          <article><el-icon><Document /></el-icon><div><strong>日记书</strong><span>PDF 或 EPUB</span></div><el-dropdown @command="exportBook"><el-button>导出<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="pdf">PDF</el-dropdown-item><el-dropdown-item command="epub">EPUB</el-dropdown-item></el-dropdown-menu></template></el-dropdown></article>
          <article><el-icon><Upload /></el-icon><div><strong>导入归档</strong><span>兼容 Baby Diary ZIP</span></div><label class="file-button"><span>{{ importing ? '导入中' : '选择文件' }}</span><input type="file" accept=".zip,application/zip" :disabled="importing" @change="importArchive" /></label></article>
        </div>
      </section>
    </main>

    <el-dialog v-model="inviteOpen" title="邀请成员" width="min(460px, 92vw)">
      <el-form label-position="top" class="dialog-form">
        <el-form-item label="指定邮箱"><el-input v-model="inviteForm.email" placeholder="可留空生成通用邀请" /></el-form-item>
        <el-form-item label="角色"><el-segmented v-model="inviteForm.role" :options="[{ label: '成员', value: 'MEMBER' }, { label: '所有者', value: 'OWNER' }]" /></el-form-item>
      </el-form>
      <div v-if="inviteLink" class="invite-link"><el-input :model-value="inviteLink" readonly /><el-button :icon="CopyDocument" circle @click="copyInvite" /></div>
      <template #footer><el-button @click="inviteOpen = false">关闭</el-button><el-button type="primary" :loading="inviting" @click="createInvite">生成邀请</el-button></template>
    </el-dialog>

    <el-dialog v-model="tagOpen" title="新建标签" width="min(400px, 92vw)">
      <el-form label-position="top" class="dialog-form"><el-form-item label="名称"><el-input v-model="tagForm.name" maxlength="32" /></el-form-item><el-form-item label="颜色"><el-color-picker v-model="tagForm.color" :predefine="tagColors" /></el-form-item></el-form>
      <template #footer><el-button @click="tagOpen = false">取消</el-button><el-button type="primary" @click="createTag">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="templateOpen" :title="editingTemplate ? '编辑模板' : '新建模板'" width="min(600px, 94vw)">
      <el-form label-position="top" class="dialog-form"><el-form-item label="名称"><el-input v-model="templateForm.name" /></el-form-item><el-form-item label="说明"><el-input v-model="templateForm.description" /></el-form-item><el-form-item label="引导问题"><el-input v-model="templateForm.promptText" /></el-form-item><el-form-item label="正文结构"><el-input v-model="templateForm.contentHtml" type="textarea" :rows="8" /></el-form-item></el-form>
      <template #footer><el-button v-if="editingTemplate" type="danger" text @click="removeTemplate">删除</el-button><span class="footer-spacer" /><el-button @click="templateOpen = false">取消</el-button><el-button type="primary" @click="saveTemplate">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElColorPicker } from 'element-plus/es/components/color-picker/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElSegmented } from 'element-plus/es/components/segmented/index.mjs'
import { ElSwitch } from 'element-plus/es/components/switch/index.mjs'
import { ArrowDown, Connection, CopyDocument, Document, Download, Edit, MagicStick, MoreFilled, Notebook, Plus, PriceTag, Upload } from '@element-plus/icons-vue'
import SpaceSwitcher from '@/components/common/SpaceSwitcher.vue'
import { workspaceApi } from '@/api/workspace'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'
import { originalImageUrl } from '@/utils/imageUrl'
import { withStepUpRetry } from '@/utils/stepUp'
import { copyText } from '@/utils/copyText'

const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const activeSection = ref('members')
const members = ref([])
const tags = ref([])
const templates = ref([])
const schedule = reactive({ weeklyEnabled: false, monthlyEnabled: false, annualEnabled: false })
const savingSchedule = ref(false)
const reminders = reactive({
  daily: { enabled: false, time: '20:30' },
  weekly: { enabled: false, time: '20:30', dayOfWeek: 7 }
})
const savingReminders = ref(false)
const inviteOpen = ref(false)
const inviting = ref(false)
const inviteLink = ref('')
const inviteForm = reactive({ email: '', role: 'MEMBER' })
const tagOpen = ref(false)
const tagForm = reactive({ name: '', color: '#5b9d8f' })
const templateOpen = ref(false)
const editingTemplate = ref(null)
const templateForm = reactive({ name: '', description: '', promptText: '', contentHtml: '<h2>今天发生了什么</h2><p></p>', icon: 'Notebook' })
const exporting = ref(false)
const importing = ref(false)
const sections = [
  { id: 'members', label: '成员', icon: markRaw(Connection) },
  { id: 'labels', label: '标签与模板', icon: markRaw(PriceTag) },
  { id: 'automation', label: '提醒与回顾', icon: markRaw(MagicStick) },
  { id: 'data', label: '数据', icon: markRaw(Download) }
]
const tagColors = ['#5b9d8f', '#c96f62', '#d4a23e', '#6f86bb', '#9a71a8', '#70945b', '#c27b9d', '#688a98']
const weekDays = [
  { value: 1, label: '周一' }, { value: 2, label: '周二' }, { value: 3, label: '周三' },
  { value: 4, label: '周四' }, { value: 5, label: '周五' }, { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]
const activeSpaceId = computed(() => workspaceStore.activeSpaceId)
const isOwner = computed(() => workspaceStore.activeSpace?.role === 'OWNER')
const isPersonal = computed(() => workspaceStore.activeSpace?.type === 'PERSONAL')

const load = async () => {
  if (!activeSpaceId.value) return
  const [memberResponse, tagResponse, templateResponse, scheduleResponse, reminderResponse] = await Promise.all([
    workspaceApi.spaces.members(activeSpaceId.value), workspaceApi.spaces.tags(activeSpaceId.value),
    workspaceApi.templates.list(activeSpaceId.value), workspaceApi.ai.schedule(activeSpaceId.value),
    workspaceApi.reminders.list(activeSpaceId.value)
  ])
  members.value = memberResponse.data || []
  tags.value = tagResponse.data || []
  templates.value = templateResponse.data || []
  Object.assign(schedule, scheduleResponse.data || {})
  for (const reminder of reminderResponse.data || []) {
    if (reminder.type === 'DAILY') Object.assign(reminders.daily, reminder)
    if (reminder.type === 'WEEKLY') Object.assign(reminders.weekly, reminder)
  }
}

const memberCommand = async (command, member) => {
  if (command === 'remove') {
    await ElMessageBox.confirm(`确认移除 ${member.username}？`, '移除成员', { type: 'warning' })
    await workspaceApi.spaces.removeMember(activeSpaceId.value, member.userId)
  } else await workspaceApi.spaces.updateRole(activeSpaceId.value, member.userId, command === 'owner' ? 'OWNER' : 'MEMBER')
  await load()
}

const createInvite = async () => {
  inviting.value = true
  try {
    const response = await workspaceApi.spaces.invite(activeSpaceId.value, { email: inviteForm.email || null, role: inviteForm.role })
    inviteLink.value = `${window.location.origin}/spaces/invitations/${response.data.token}`
  } finally { inviting.value = false }
}
const copyInvite = async () => {
  if (await copyText(inviteLink.value)) ElMessage.success('邀请链接已复制')
  else ElMessage.warning('复制失败，请长按链接手动复制')
}

const createTag = async () => {
  if (!tagForm.name.trim()) return
  await workspaceApi.spaces.createTag(activeSpaceId.value, { ...tagForm, name: tagForm.name.trim() })
  tagForm.name = ''
  tagOpen.value = false
  await load()
}

const openTemplate = template => {
  editingTemplate.value = template || null
  Object.assign(templateForm, template ? { name: template.name, description: template.description || '', promptText: template.promptText || '', contentHtml: template.contentHtml, icon: 'Notebook' } : { name: '', description: '', promptText: '', contentHtml: '<h2>今天发生了什么</h2><p></p>', icon: 'Notebook' })
  templateOpen.value = true
}
const saveTemplate = async () => {
  if (!templateForm.name.trim() || !templateForm.contentHtml.trim()) return
  if (editingTemplate.value) await workspaceApi.templates.update(activeSpaceId.value, editingTemplate.value.templateId, templateForm)
  else await workspaceApi.templates.create(activeSpaceId.value, templateForm)
  templateOpen.value = false
  await load()
}
const removeTemplate = async () => { await workspaceApi.templates.remove(activeSpaceId.value, editingTemplate.value.templateId); templateOpen.value = false; await load() }

const saveSchedule = async () => {
  savingSchedule.value = true
  try { await workspaceApi.ai.updateSchedule(activeSpaceId.value, { ...schedule }); ElMessage.success('自动回顾计划已保存') }
  finally { savingSchedule.value = false }
}

const saveReminders = async () => {
  savingReminders.value = true
  try {
    await Promise.all([
      workspaceApi.reminders.save(activeSpaceId.value, 'DAILY', { ...reminders.daily, dayOfWeek: null }),
      workspaceApi.reminders.save(activeSpaceId.value, 'WEEKLY', { ...reminders.weekly })
    ])
    ElMessage.success('记录提醒已保存')
    await load()
  } finally { savingReminders.value = false }
}

const exportArchive = async () => {
  exporting.value = true
  try { const blob = await withStepUpRetry(token => workspaceApi.transfer.exportSpace(activeSpaceId.value, token)); download(blob, 'Baby-Diary-export.zip') }
  finally { exporting.value = false }
}
const exportBook = async format => { const blob = await withStepUpRetry(token => workspaceApi.transfer.exportBook(activeSpaceId.value, { format }, token)); download(blob, `Baby-Diary.${format}`) }
const importArchive = async event => {
  const file = event.target.files?.[0]
  if (!file) return
  importing.value = true
  try { const data = new FormData(); data.append('archive', file); const response = await withStepUpRetry(token => workspaceApi.transfer.importSpace(activeSpaceId.value, data, token)); ElMessage.success(`已导入 ${response.data.importedDiaries} 篇日记`) }
  finally { importing.value = false; event.target.value = '' }
}
const download = (blob, filename) => { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }

watch(activeSpaceId, load)
onMounted(async () => { await workspaceStore.initialize(); await load() })
</script>

<style scoped lang="scss">
.settings-page { min-height: 100vh; background: #f6f3f0; }
.settings-container { width: min(1040px, calc(100% - 32px)); margin: 0 auto; padding: 28px 0 56px; }
.settings-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }.settings-header h1 { margin: 0; font-size: 28px; }.settings-header p { margin: 5px 0 0; color: #81766f; }
.settings-nav { margin-bottom: 14px; padding: 6px; border: 1px solid #e4dad5; border-radius: 8px; background: #fff; display: flex; gap: 4px; overflow-x: auto; }.settings-nav button { min-height: 38px; padding: 0 13px; border: 0; border-radius: 7px; background: transparent; color: #6f6560; display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; cursor: pointer; }.settings-nav button.active { color: #8e4c52; background: #fff0f1; }
.settings-section { min-height: 460px; padding: 24px; border: 1px solid #e4dad5; border-radius: 8px; background: #fff; }.settings-section > header { min-height: 52px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid #eee5e0; display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.settings-section h2 { margin: 0; font-size: 22px; }.settings-section header p { margin: 5px 0 0; color: #8b807a; font-size: 13px; }
.member-list, .template-list, .toggle-list, .data-actions { display: grid; gap: 8px; }.member-list article, .template-list article, .data-actions article { min-height: 66px; padding: 10px 12px; border: 1px solid #e8dfda; border-radius: 8px; display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; }.member-list article > div, .template-list article > div, .data-actions article > div { min-width: 0; display: flex; flex-direction: column; gap: 4px; }.member-list span, .template-list span, .data-actions span { overflow: hidden; text-overflow: ellipsis; color: #8b807a; font-size: 12px; }.template-list article > .el-icon, .data-actions article > .el-icon { width: 38px; height: 38px; border-radius: 8px; background: #eef6f3; color: #347d72; font-size: 19px; }
.subsection + .subsection { margin-top: 28px; padding-top: 24px; border-top: 1px solid #eee5e0; }.subsection-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }.subsection-heading h3 { margin: 0; font-size: 17px; }.tag-list { display: flex; flex-wrap: wrap; gap: 8px; }.tag-list span { padding: 7px 10px; border-radius: 7px; background: #f6f2ef; color: #655d58; font-size: 13px; }.tag-list i { width: 8px; height: 8px; margin-right: 6px; border-radius: 50%; display: inline-block; }.tag-list em { color: #99908a; font-style: normal; }
.toggle-list { margin-bottom: 20px; }.toggle-list label { min-height: 74px; padding: 12px 14px; border: 1px solid #e8dfda; border-radius: 8px; display: flex; align-items: center; justify-content: space-between; gap: 18px; }.toggle-list label > div { min-width: 0; display: flex; flex-direction: column; gap: 5px; }.toggle-list span { color: #8b807a; font-size: 12px; }
.owner-note { color: #8b807a; font-size: 12px; }.reminder-list { display: grid; gap: 8px; }.reminder-list label { min-height: 72px; padding: 11px 13px; border: 1px solid #e8dfda; border-radius: 8px; display: grid; grid-template-columns: minmax(0, 1fr) auto auto auto; align-items: center; gap: 10px; }.reminder-list label:first-child { grid-template-columns: minmax(0, 1fr) auto auto; }.reminder-list label > div { min-width: 0; display: flex; flex-direction: column; gap: 4px; }.reminder-list label span { color: #8b807a; font-size: 12px; }.reminder-list input, .reminder-list select { height: 34px; padding: 0 9px; border: 1px solid #d8dce5; border-radius: 6px; color: #4f4844; background: #fff; font: inherit; }
.file-button { min-height: 32px; padding: 0 12px; border: 1px solid #d8dce5; border-radius: 6px; display: inline-flex; align-items: center; cursor: pointer; }.file-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.dialog-form { display: grid; gap: 16px; }.dialog-form :deep(.el-form-item) { margin-bottom: 0; }.dialog-form :deep(.el-segmented), .dialog-form :deep(.el-color-picker) { width: 100%; }.invite-link { margin-top: 18px; display: grid; grid-template-columns: minmax(0, 1fr) 40px; gap: 8px; }.footer-spacer { flex: 1; }

@media (max-width: 768px) {
  .settings-container { width: 100%; padding: 0 12px 28px; }.settings-header { align-items: flex-start; }.settings-header h1 { font-size: 22px; }.settings-header :deep(.space-switcher) { max-width: 150px; }
  .settings-nav { margin-right: -12px; margin-left: -12px; border-right: 0; border-left: 0; border-radius: 0; padding-right: 12px; padding-left: 12px; }
  .settings-section { min-height: 420px; padding: 16px 14px; }.settings-section > header { align-items: center; }.member-list article, .template-list article, .data-actions article { padding: 9px; gap: 9px; }
  .data-actions article { grid-template-columns: 38px minmax(0, 1fr) auto; }.data-actions :deep(.el-button) { margin: 0; }
  .reminder-list label, .reminder-list label:first-child { grid-template-columns: minmax(0, 1fr) auto; }.reminder-list label > div { grid-column: 1 / -1; }.reminder-list input, .reminder-list select { min-width: 0; width: 100%; }
}
</style>
