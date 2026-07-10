<template>
  <el-config-provider :locale="zhCn">
    <mobile-app-shell v-if="route.meta.requiresAuth">
      <router-view />
    </mobile-app-shell>
    <router-view v-else />
  </el-config-provider>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import MobileAppShell from '@/components/mobile/MobileAppShell.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const refreshUserInfo = () => {
  if (!authStore.isLoggedIn) return
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
  authStore.getUserInfo()
}

onMounted(() => {
  refreshUserInfo()
  document.addEventListener('visibilitychange', refreshUserInfo)
  window.addEventListener('focus', refreshUserInfo)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', refreshUserInfo)
  window.removeEventListener('focus', refreshUserInfo)
})
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
}
</style>
