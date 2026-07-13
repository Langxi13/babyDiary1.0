import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { vLoading } from 'element-plus/es/components/loading/index.mjs'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/es/components/loading/style/css.mjs'

import App from './App.vue'
import router from './router'
import { initializeRuntimeConfig, isNativeApp } from './platform/runtimeConfig'
import './assets/styles/main.scss'

await initializeRuntimeConfig()

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
