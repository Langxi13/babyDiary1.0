<template>
  <div class="app-shell">
    <nav-bar class="desktop-navbar" />

    <header class="mobile-topbar">
      <button v-if="showBack" type="button" class="topbar-icon" aria-label="返回" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div v-else class="topbar-brand">
        <el-icon><Notebook /></el-icon>
      </div>

      <div class="topbar-title">
        <strong>{{ title }}</strong>
        <span>Baby Diary</span>
      </div>

      <button type="button" class="topbar-icon" aria-label="更多" @click="secondarySheetOpen = true">
        <el-icon><MoreFilled /></el-icon>
      </button>
    </header>

    <main class="mobile-shell-content">
      <slot />
    </main>

    <nav class="mobile-tabbar" aria-label="手机端主导航">
      <button
        v-for="tab in MOBILE_PRIMARY_TABS"
        :key="tab.path"
        type="button"
        class="tabbar-item"
        :class="{ active: isMobileTabActive(tab, route.path), primary: tab.primary }"
        @touchstart.passive="preload(tab.path)"
        @click="router.push(tab.path)"
      >
        <el-icon class="tabbar-icon">
          <component :is="iconMap[tab.icon]" />
        </el-icon>
        <span>{{ tab.label }}</span>
      </button>
    </nav>

    <transition name="mobile-sheet">
      <div v-if="secondarySheetOpen" class="mobile-sheet-mask" @click.self="secondarySheetOpen = false">
        <section class="mobile-sheet" aria-label="更多入口">
          <div class="sheet-grabber" />
          <div class="sheet-section">
            <h2>回忆</h2>
            <button
              v-for="link in memoryLinks"
              :key="link.path"
              type="button"
              @touchstart.passive="preload(link.path)"
              @click="openLink(link.path)"
            >
              {{ link.label }}
            </button>
          </div>
          <div class="sheet-section">
            <h2>我的</h2>
            <button
              v-for="link in accountLinks"
              :key="link.path"
              type="button"
              @touchstart.passive="preload(link.path)"
              @click="openLink(link.path)"
            >
              {{ link.label }}
            </button>
            <button type="button" @click="openInstall">
              添加到桌面
            </button>
            <button type="button" class="logout-button" @click="authStore.logout()">退出登录</button>
          </div>
        </section>
      </div>
    </transition>

    <transition name="mobile-sheet">
      <div v-if="installSheetOpen" class="mobile-sheet-mask" @click.self="installSheetOpen = false">
        <section class="mobile-sheet install-sheet" aria-label="添加到桌面">
          <div class="sheet-grabber" />
          <h2>添加到桌面</h2>
          <p v-if="isIosSafari">
            在 Safari 底部点分享按钮，然后选择“添加到主屏幕”。
          </p>
          <p v-else>
            当前浏览器没有提供自动安装提示，可以从浏览器菜单选择添加到桌面。
          </p>
          <button type="button" class="sheet-primary-button" @click="installSheetOpen = false">知道了</button>
        </section>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import {
  ArrowLeft,
  Document,
  Edit,
  HomeFilled,
  MoreFilled,
  Notebook,
  Picture,
  User
} from '@element-plus/icons-vue'
import NavBar from '@/components/common/NavBar.vue'
import { preloadRouteComponent } from '@/router'
import { useAuthStore } from '@/stores/auth'
import {
  MOBILE_PRIMARY_TABS,
  MOBILE_SECONDARY_LINKS,
  getMobileRouteTitle,
  isMobileTabActive,
  isPrimaryMobileTab
} from './mobileNavigation'
import 'element-plus/es/components/icon/style/css.mjs'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const secondarySheetOpen = ref(false)
const installSheetOpen = ref(false)
const deferredInstallPrompt = ref(null)

const iconMap = {
  HomeFilled,
  Document,
  Edit,
  Picture,
  User
}
const preload = (path) => preloadRouteComponent(path)

const title = computed(() => route.meta.mobileTitle || getMobileRouteTitle(route.path))
const showBack = computed(() => !isPrimaryMobileTab(route.path) && route.path !== '/')
const memoryLinks = computed(() => MOBILE_SECONDARY_LINKS.filter(link => link.group === 'memory'))
const accountLinks = computed(() => MOBILE_SECONDARY_LINKS.filter(link => link.group === 'account'))
const isIosSafari = computed(() => {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent || ''
  return /iP(hone|ad|od)/.test(ua) && /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua)
})

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

const openLink = (path) => {
  secondarySheetOpen.value = false
  router.push(path)
}

const handleBeforeInstallPrompt = (event) => {
  event.preventDefault()
  deferredInstallPrompt.value = event
}

const openInstall = async () => {
  secondarySheetOpen.value = false
  if (deferredInstallPrompt.value) {
    const promptEvent = deferredInstallPrompt.value
    deferredInstallPrompt.value = null
    await promptEvent.prompt()
    return
  }
  installSheetOpen.value = true
}

onMounted(() => {
  window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
})
</script>

<style src="./styles/MobileAppShell.scss" scoped lang="scss"></style>
