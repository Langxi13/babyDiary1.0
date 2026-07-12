import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { workspaceApi } from '@/api/workspace'
import { pendingOfflineCount } from '@/utils/offlineDb'
import { syncWorkspace } from '@/utils/offlineSync'

export const useWorkspaceStore = defineStore('workspace', () => {
  const spaces = ref([])
  const activeSpaceId = ref(localStorage.getItem('activeSpaceId') || '')
  const loading = ref(false)
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
  const pendingCount = ref(0)
  const conflictCount = ref(0)
  const unreadNotifications = ref(0)
  let initialized = false
  let storeGeneration = 0

  const activeSpace = computed(() => spaces.value.find(space => space.spaceId === activeSpaceId.value) || null)
  const isPersonalSpace = computed(() => activeSpace.value?.type === 'PERSONAL')

  async function loadSpaces(force = false) {
    if (loading.value || (!force && spaces.value.length)) return spaces.value
    const generation = storeGeneration
    loading.value = true
    try {
      const response = await workspaceApi.spaces.list()
      if (generation !== storeGeneration) return spaces.value
      spaces.value = response.data || []
      if (!spaces.value.some(space => space.spaceId === activeSpaceId.value)) {
        activeSpaceId.value = spaces.value[0]?.spaceId || ''
      }
      persistActiveSpace()
      return spaces.value
    } finally {
      if (generation === storeGeneration) {
        loading.value = false
      }
    }
  }

  function selectSpace(spaceId) {
    if (!spaces.value.some(space => space.spaceId === spaceId)) return
    activeSpaceId.value = spaceId
    persistActiveSpace()
    window.dispatchEvent(new CustomEvent('workspace:selected', { detail: { spaceId } }))
  }

  async function createSpace(name) {
    const generation = storeGeneration
    const response = await workspaceApi.spaces.create({ name })
    if (generation !== storeGeneration) return null
    await loadSpaces(true)
    if (generation !== storeGeneration) return null
    selectSpace(response.data.spaceId)
    return response.data
  }

  async function refreshPendingCount() {
    const generation = storeGeneration
    const spaceIds = spaces.value.map(space => space.spaceId)
    const count = await pendingOfflineCount(spaceIds)
    if (generation === storeGeneration) {
      pendingCount.value = count
    }
  }

  async function refreshUnread() {
    const generation = storeGeneration
    try {
      const response = await workspaceApi.notifications.unread()
      if (generation === storeGeneration) {
        unreadNotifications.value = response.data || 0
      }
    } catch {
      if (generation === storeGeneration) {
        unreadNotifications.value = 0
      }
    }
  }

  async function syncActive() {
    if (!activeSpaceId.value || !online.value) return
    const generation = storeGeneration
    const requestedSpaceId = activeSpaceId.value
    const result = await syncWorkspace(requestedSpaceId)
    if (generation !== storeGeneration || activeSpaceId.value !== requestedSpaceId) return
    conflictCount.value = result.conflicts.length
    await refreshPendingCount()
    return result
  }

  async function initialize() {
    if (initialized) return
    const generation = storeGeneration
    initialized = true
    await loadSpaces()
    if (generation !== storeGeneration) return
    await Promise.all([refreshPendingCount(), refreshUnread()])
    if (generation !== storeGeneration) return
    syncActive().catch(() => {})
  }

  function persistActiveSpace() {
    if (activeSpaceId.value) localStorage.setItem('activeSpaceId', activeSpaceId.value)
    else localStorage.removeItem('activeSpaceId')
  }

  function reset() {
    storeGeneration += 1
    initialized = false
    spaces.value = []
    activeSpaceId.value = ''
    loading.value = false
    pendingCount.value = 0
    conflictCount.value = 0
    unreadNotifications.value = 0
    localStorage.removeItem('activeSpaceId')
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('online', () => {
      online.value = true
      syncActive().catch(() => {})
    })
    window.addEventListener('offline', () => { online.value = false })
    window.addEventListener('offline-queue:changed', refreshPendingCount)
    window.addEventListener('workspace:sync-issues', event => {
      if (!activeSpaceId.value || event.detail?.spaceId !== activeSpaceId.value) return
      conflictCount.value = (event.detail?.conflicts?.length || 0) + (event.detail?.failures?.length || 0)
    })
    window.addEventListener('auth:session-reset', reset)
  }

  return {
    spaces,
    activeSpaceId,
    activeSpace,
    isPersonalSpace,
    loading,
    online,
    pendingCount,
    conflictCount,
    unreadNotifications,
    loadSpaces,
    selectSpace,
    createSpace,
    refreshPendingCount,
    refreshUnread,
    syncActive,
    initialize,
    reset
  }
})
