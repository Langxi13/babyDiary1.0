import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { clientApi } from '@/api/client'
import { evaluateAndroidUpdate, getCurrentClientInfo, openUpdateDownload } from '@/platform/appRelease'

export const useAppUpdateStore = defineStore('appUpdate', () => {
  const clientInfo = ref(null)
  const bootstrap = ref(null)
  const checking = ref(false)
  const checked = ref(false)
  const error = ref('')
  let generation = 0

  const manifest = computed(() => bootstrap.value?.androidUpdate || null)
  const evaluation = computed(() => evaluateAndroidUpdate(clientInfo.value, manifest.value))
  const updateAvailable = computed(() => evaluation.value.available)
  const updateRequired = computed(() => evaluation.value.required)

  async function check(force = false) {
    if (checking.value || (checked.value && !force)) return
    const requestGeneration = generation
    checking.value = true
    error.value = ''
    try {
      const [info, response] = await Promise.all([
        getCurrentClientInfo(),
        clientApi.bootstrap()
      ])
      if (requestGeneration !== generation) return
      clientInfo.value = info
      bootstrap.value = response.data || null
      checked.value = true
    } catch (cause) {
      if (requestGeneration !== generation) return
      error.value = cause?.message || '暂时无法检查更新'
    } finally {
      if (requestGeneration === generation) checking.value = false
    }
  }

  async function openUpdate() {
    if (!updateAvailable.value || !manifest.value?.downloadUrl) {
      throw new Error('当前没有可安装的新版本')
    }
    return openUpdateDownload(manifest.value.downloadUrl)
  }

  function reset() {
    generation += 1
    clientInfo.value = null
    bootstrap.value = null
    checking.value = false
    checked.value = false
    error.value = ''
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('native:server-changed', reset)
  }

  return {
    clientInfo,
    bootstrap,
    manifest,
    checking,
    checked,
    error,
    evaluation,
    updateAvailable,
    updateRequired,
    check,
    openUpdate,
    reset
  }
})
