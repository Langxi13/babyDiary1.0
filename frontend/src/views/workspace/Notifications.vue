<template>
  <div class="notifications-page">
    <main class="notifications-container">
      <header><div><h1>通知</h1><p>{{ unread }} 条未读</p></div><div><el-button @click="togglePush"><el-icon><Bell /></el-icon>{{ pushEnabled ? '关闭推送' : '开启推送' }}</el-button><el-button type="primary" plain @click="markAll">全部已读</el-button></div></header>
      <section v-loading="loading" class="notification-list">
        <button v-for="item in notifications" :key="item.publicId" type="button" :class="{ unread: !item.readAt }" @click="openNotification(item)">
          <span class="notification-icon"><el-icon><component :is="iconFor(item.type)" /></el-icon></span>
          <span class="notification-copy"><strong>{{ item.title }}</strong><span>{{ item.body }}</span></span>
          <time>{{ formatChineseDateTime(item.createdAt) }}</time>
        </button>
        <el-empty v-if="!loading && !notifications.length" description="暂无通知" />
      </section>
    </main>
  </div>
</template>

<script setup>
import { markRaw, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { Bell, ChatDotRound, DocumentAdd, EditPen, Star } from '@element-plus/icons-vue'
import { workspaceApi } from '@/api/workspace'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatChineseDateTime } from '@/utils/dateDisplay'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/message/style/css.mjs'

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const notifications = ref([])
const unread = ref(0)
const loading = ref(false)
const pushEnabled = ref(false)

const load = async () => {
  loading.value = true
  try {
    const [listResponse, unreadResponse] = await Promise.all([
      workspaceApi.notifications.list({ page: 0, size: 50 }), workspaceApi.notifications.unread()
    ])
    notifications.value = listResponse.data.content || []
    unread.value = unreadResponse.data || 0
    workspaceStore.unreadNotifications = unread.value
  } finally { loading.value = false }
}

const openNotification = async item => {
  if (!item.readAt) await workspaceApi.notifications.read(item.publicId)
  if (item.targetPath) router.push(item.targetPath)
  else load()
}
const markAll = async () => { await workspaceApi.notifications.readAll(); await load() }

const togglePush = async () => {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    ElMessage.warning('当前浏览器不支持推送通知')
    return
  }
  const registration = await navigator.serviceWorker.ready
  const existing = await registration.pushManager.getSubscription()
  if (existing) {
    await workspaceApi.notifications.unsubscribe(existing.endpoint)
    await existing.unsubscribe()
    pushEnabled.value = false
    return
  }
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return
  const keyResponse = await workspaceApi.notifications.publicKey()
  if (!keyResponse.data) {
    ElMessage.warning('服务器尚未配置推送密钥')
    return
  }
  const subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(keyResponse.data) })
  const json = subscription.toJSON()
  await workspaceApi.notifications.subscribe({ endpoint: json.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth })
  pushEnabled.value = true
}

const iconFor = type => {
  if (type === 'DIARY_CREATED') return markRaw(DocumentAdd)
  if (type === 'DIARY_UPDATED') return markRaw(EditPen)
  if (type === 'DIARY_COMMENT') return markRaw(ChatDotRound)
  return markRaw(Star)
}

function urlBase64ToUint8Array(value) {
  const padding = '='.repeat((4 - value.length % 4) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64), character => character.charCodeAt(0))
}

onMounted(async () => {
  await load()
  if ('serviceWorker' in navigator) {
    const registration = await navigator.serviceWorker.ready
    pushEnabled.value = !!(await registration.pushManager?.getSubscription())
  }
})
</script>

<style scoped lang="scss">
.notifications-page { min-height: 100vh; background: #f6f3f0; }
.notifications-container { width: min(820px, calc(100% - 32px)); margin: 0 auto; padding: 28px 0 54px; }
.notifications-container > header { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }.notifications-container h1 { margin: 0; font-size: 28px; }.notifications-container header p { margin: 4px 0 0; color: #8a7f79; }.notifications-container header > div:last-child { display: flex; gap: 8px; }
.notification-list { min-height: 380px; border: 1px solid #e5dbd6; border-radius: 8px; overflow: hidden; background: #fff; }
.notification-list > button { width: 100%; min-height: 78px; padding: 12px 14px; border: 0; border-bottom: 1px solid #eee5e0; background: #fff; display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; align-items: center; gap: 12px; text-align: left; cursor: pointer; }.notification-list > button:last-child { border-bottom: 0; }.notification-list > button.unread { background: #fff8f6; }.notification-list > button.unread:before { content: ''; position: absolute; width: 4px; height: 38px; margin-left: -14px; border-radius: 0 4px 4px 0; background: #c26963; }
.notification-icon { width: 40px; height: 40px; border-radius: 8px; background: #edf6f3; color: #347b71; display: inline-flex; align-items: center; justify-content: center; font-size: 18px; }.notification-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }.notification-copy strong, .notification-copy span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.notification-copy span, time { color: #6f6661; font-size: 12px; }
@media (max-width: 768px) { .notifications-container { width: 100%; padding: 0 12px 28px; }.notifications-container > header { align-items: flex-start; }.notifications-container h1 { font-size: 22px; }.notifications-container header > div:last-child { flex-direction: column; }.notification-list > button { grid-template-columns: 38px minmax(0, 1fr); }.notification-list time { grid-column: 2; }.notification-icon { width: 36px; height: 36px; } }
</style>
