import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { vLoading } from 'element-plus/es/components/loading/index.mjs'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/es/components/loading/style/css.mjs'

import App from './App.vue'
import router from './router'
import { initializeRuntimeConfig, isNativeApp } from './platform/runtimeConfig'
import { nativePlatformClass } from './platform/nativeLifecycle'
import { cleanupExpiredNativeImages } from './platform/nativeImages'
import { cleanupNativeExports } from './platform/nativeFiles'
import './assets/styles/main.scss'

await initializeRuntimeConfig()
document.documentElement.classList.add(...nativePlatformClass().split(' ').filter(Boolean))
if (isNativeApp()) {
  cleanupExpiredNativeImages().catch(() => {})
  cleanupNativeExports().catch(() => {})
}

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.directive('loading', vLoading)

app.mount('#app')

if (!isNativeApp() && 'serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').then(registration => registration.update()).catch(() => {})
  })
}
