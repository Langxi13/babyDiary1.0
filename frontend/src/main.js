import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { vLoading } from 'element-plus/es/components/loading/index.mjs'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/es/components/loading/style/css.mjs'

import App from './App.vue'
import router from './router'
import './assets/styles/main.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.directive('loading', vLoading)

app.mount('#app')

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').then(registration => registration.update()).catch(() => {})
  })
}
