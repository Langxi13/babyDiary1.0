<template>
  <el-config-provider :locale="zhCn">
    <mobile-app-shell v-if="route.meta.requiresAuth && authStore.isLoggedIn" :key="authStore.sessionVersion">
      <router-view />
    </mobile-app-shell>
    <router-view v-else-if="!route.meta.requiresAuth" />
    <step-up-dialog />
    <native-update-gate />
  </el-config-provider>
</template>

<script setup>
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import MobileAppShell from '@/components/mobile/MobileAppShell.vue'
import StepUpDialog from '@/components/security/StepUpDialog.vue'
import NativeUpdateGate from '@/components/mobile/NativeUpdateGate.vue'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'
import { useAppUpdateStore } from '@/stores/appUpdate'
import { startNativeLifecycle } from '@/platform/nativeLifecycle'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const updateStore = useAppUpdateStore()
let syncTimer = null
let stopNativeLifecycle = async () => {}

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
  startNativeLifecycle({
    router,
    onResume: () => {
      refreshUserInfo()
      workspaceStore.initialize().then(() => workspaceStore.syncActive()).catch(() => {})
      updateStore.check(true).catch(() => {})
    },
    onNetworkChange: status => workspaceStore.setOnline(status.connected !== false)
  }).then(stop => { stopNativeLifecycle = stop })
  document.addEventListener('visibilitychange', refreshUserInfo)
  window.addEventListener('focus', refreshUserInfo)
})

onBeforeUnmount(() => {
  stopWorkspaceSync()
  stopNativeLifecycle()
  document.removeEventListener('visibilitychange', refreshUserInfo)
  window.removeEventListener('focus', refreshUserInfo)
})

watch(() => authStore.isLoggedIn, loggedIn => {
  if (loggedIn) startWorkspaceSync()
  else stopWorkspaceSync()
})
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
}
</style>
