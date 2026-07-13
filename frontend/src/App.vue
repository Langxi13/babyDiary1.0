<template>
  <el-config-provider :locale="zhCn">
    <mobile-app-shell v-if="route.meta.requiresAuth && authStore.isLoggedIn" :key="authStore.sessionVersion">
      <router-view />
    </mobile-app-shell>
    <router-view v-else-if="!route.meta.requiresAuth" />
    <step-up-dialog />
  </el-config-provider>
</template>

<script setup>
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index.mjs'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import MobileAppShell from '@/components/mobile/MobileAppShell.vue'
import StepUpDialog from '@/components/security/StepUpDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'
import { consumeNativeShareInbox, listenForNativeShares } from '@/platform/nativeShareInbox'
import { hasServerOrigin, isNativeApp } from '@/platform/runtimeConfig'
import 'element-plus/es/components/message/style/css.mjs'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
let syncTimer = null
let nativeShareListener = null

const importNativeShare = async () => {
  if (!isNativeApp() || !hasServerOrigin() || !authStore.isLoggedIn) return
  let result
  try {
    result = await consumeNativeShareInbox()
  } catch {
    ElMessage.warning('无法读取系统分享的照片，请从日记页面重新选择')
    return
  }
  const files = result.files || []
  if (result.rejected) {
    ElMessage.warning(`${result.rejected} 张照片因格式、大小或读取权限问题未载入`)
  }
  if (!files.length) return
  if (route.path === '/diaries/create') {
    window.dispatchEvent(new Event('native-share:ready'))
  } else {
    await router.push({ path: '/diaries/create', query: { nativeShared: '1' } })
  }
}

const startWorkspaceSync = () => {
  if (!authStore.isLoggedIn || syncTimer) return
  workspaceStore.initialize().catch(() => {})
  syncTimer = window.setInterval(() => workspaceStore.syncActive().catch(() => {}), 60000)
}

const stopWorkspaceSync = () => {
  if (syncTimer) window.clearInterval(syncTimer)
  syncTimer = null
}

const refreshUserInfo = () => {
  if (!authStore.isLoggedIn) return
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
  authStore.getUserInfo()
}

onMounted(() => {
  refreshUserInfo()
  startWorkspaceSync()
  document.addEventListener('visibilitychange', refreshUserInfo)
  window.addEventListener('focus', refreshUserInfo)
  listenForNativeShares(() => importNativeShare().catch(() => {}))
    .then(handle => { nativeShareListener = handle })
    .catch(() => {})
  importNativeShare().catch(() => {})
})

onBeforeUnmount(() => {
  stopWorkspaceSync()
  document.removeEventListener('visibilitychange', refreshUserInfo)
  window.removeEventListener('focus', refreshUserInfo)
  nativeShareListener?.remove()
})

watch(() => authStore.isLoggedIn, loggedIn => {
  if (loggedIn) {
    startWorkspaceSync()
    importNativeShare().catch(() => {})
  }
  else stopWorkspaceSync()
})
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
}
</style>
