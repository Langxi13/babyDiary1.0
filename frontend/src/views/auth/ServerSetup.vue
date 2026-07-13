<template>
  <div class="server-setup-page">
    <main class="server-setup-panel">
      <img src="/app-icon.png" alt="" class="server-setup-logo" />
      <div class="server-setup-heading">
        <span>连接你的私人日记</span>
        <h1>设置服务器</h1>
        <p>输入 Baby Diary 的 HTTPS 地址。地址只保存在当前设备中。</p>
      </div>

      <form class="server-setup-form" @submit.prevent="connect">
        <label>
          <span>服务器地址</span>
          <el-input
            v-model="serverUrl"
            size="large"
            inputmode="url"
            autocapitalize="none"
            autocomplete="url"
            :disabled="loading"
            placeholder="https://diary.example.com"
          />
        </label>
        <p v-if="errorMessage" class="server-setup-error" role="alert">{{ errorMessage }}</p>
        <el-button type="primary" size="large" native-type="submit" :loading="loading">
          连接服务器
        </el-button>
      </form>

      <p class="server-setup-note">仅支持部署在域名根路径、启用 HTTPS 且版本兼容的 Baby Diary 服务。</p>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CapacitorCookies } from '@capacitor/core'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { saveServerOrigin, testServerConnection } from '@/platform/runtimeConfig'
import { useAuthStore } from '@/stores/auth'
import { clearOfflineData } from '@/utils/offlineDb'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'

const router = useRouter()
const authStore = useAuthStore()
const serverUrl = ref('')
const loading = ref(false)
const errorMessage = ref('')

const connect = async () => {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const { origin } = await testServerConnection(serverUrl.value)
    authStore.clearAuth('server-connect')
    await Promise.all([
      clearOfflineData().catch(() => {}),
      CapacitorCookies.clearAllCookies().catch(() => {})
    ])
    await saveServerOrigin(origin)
    await router.replace('/login')
  } catch (error) {
    errorMessage.value = error.message || '无法连接服务器，请检查地址和网络'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.server-setup-page {
  min-height: 100dvh;
  padding: max(28px, env(safe-area-inset-top)) max(18px, env(safe-area-inset-right)) max(28px, env(safe-area-inset-bottom)) max(18px, env(safe-area-inset-left));
  display: grid;
  place-items: center;
  background: #edf3f0;
}

.server-setup-panel {
  width: min(430px, 100%);
  padding: 32px;
  border: 1px solid #dce6e1;
  border-radius: 10px;
  background: #fffdfa;
  box-shadow: 0 18px 46px rgba(43, 61, 55, 0.12);
}

.server-setup-logo {
  width: 66px;
  height: 66px;
  border-radius: 15px;
  box-shadow: 0 8px 18px rgba(57, 73, 67, 0.14);
}

.server-setup-heading {
  margin-top: 20px;

  span { color: #2f8f83; font-size: 12px; font-weight: 700; }
  h1 { margin: 5px 0 0; color: #26312e; font-size: 28px; line-height: 1.2; }
  p { margin: 8px 0 0; color: #68746f; font-size: 14px; line-height: 1.65; }
}

.server-setup-form {
  margin-top: 26px;
  display: grid;
  gap: 16px;

  label { display: grid; gap: 8px; color: #46514d; font-size: 14px; font-weight: 650; }
  :deep(.el-input__wrapper) { min-height: 48px; border-radius: 9px; }
  :deep(.el-input__inner) { font-size: 16px; }
  :deep(.el-button) { width: 100%; min-height: 48px; border-radius: 9px; font-weight: 700; }
}

.server-setup-error {
  margin: -3px 0 0;
  padding: 9px 11px;
  border-left: 3px solid #b4233f;
  background: #fff1f3;
  color: #8f1f35;
  font-size: 13px;
  line-height: 1.45;
}

.server-setup-note {
  margin: 18px 0 0;
  color: #7d8783;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 600px) {
  .server-setup-page { place-items: start center; background: #fffdfa; }
  .server-setup-panel { padding: 8px 0 20px; border: 0; box-shadow: none; }
}
</style>
