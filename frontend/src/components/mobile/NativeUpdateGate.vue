<template>
  <div v-if="nativeApp && updateStore.updateRequired" class="update-gate" role="dialog" aria-modal="true">
    <section>
      <el-icon><WarningFilled /></el-icon>
      <h1>需要更新应用</h1>
      <p>当前版本与服务器数据格式不再兼容。更新完成后即可继续使用，日记数据不会受影响。</p>
      <dl>
        <div><dt>当前版本</dt><dd>{{ updateStore.clientInfo?.version || '未知' }}</dd></div>
        <div><dt>最新版本</dt><dd>{{ updateStore.manifest?.latestVersionName || '可用更新' }}</dd></div>
      </dl>
      <button type="button" :disabled="opening" @click="openUpdate">
        {{ opening ? '正在打开...' : '下载并安装更新' }}
      </button>
      <small v-if="error">{{ error }}</small>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { WarningFilled } from '@element-plus/icons-vue'
import { isNativeApp } from '@/platform/runtimeConfig'
import { useAppUpdateStore } from '@/stores/appUpdate'
import 'element-plus/es/components/icon/style/css.mjs'

const nativeApp = isNativeApp()
const updateStore = useAppUpdateStore()
const opening = ref(false)
const error = ref('')

const check = () => nativeApp && updateStore.check(true).catch(() => {})
const openUpdate = async () => {
  opening.value = true
  error.value = ''
  try {
    await updateStore.openUpdate()
  } catch (cause) {
    error.value = cause?.message || '无法打开更新地址'
  } finally {
    opening.value = false
  }
}

onMounted(() => {
  check()
  window.addEventListener('app:update-required', check)
  window.addEventListener('native:server-changed', check)
})
onBeforeUnmount(() => {
  window.removeEventListener('app:update-required', check)
  window.removeEventListener('native:server-changed', check)
})
</script>

<style scoped>
.update-gate { position: fixed; inset: 0; z-index: 10000; display: grid; place-items: center; padding: 24px; background: #f5f1ee; color: #302c29; }
.update-gate section { width: min(420px, 100%); display: grid; gap: 18px; text-align: center; }
.update-gate .el-icon { margin: 0 auto; font-size: 48px; color: #b34a4a; }
.update-gate h1, .update-gate p { margin: 0; }
.update-gate h1 { font-size: 26px; }
.update-gate p { color: #706762; line-height: 1.7; }
.update-gate dl { margin: 0; padding: 14px 0; border-top: 1px solid #ddd3ce; border-bottom: 1px solid #ddd3ce; display: grid; grid-template-columns: repeat(2, 1fr); }
.update-gate dl div { display: grid; gap: 5px; }
.update-gate dt { color: #887d77; font-size: 13px; }
.update-gate dd { margin: 0; font-weight: 700; }
.update-gate button { min-height: 48px; border: 0; border-radius: 8px; background: #276f67; color: #fff; font-size: 16px; font-weight: 700; }
.update-gate button:disabled { opacity: .65; }
.update-gate small { color: #a33b3b; }
</style>
