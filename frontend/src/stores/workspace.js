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

  const activeSpace = computed(() => spaces.value.find(space => space.spaceId === activeSpaceId.value) || null)
  const isPersonalSpace = computed(() => activeSpace.value?.type === 'PERSONAL')

  async function loadSpaces(force = false) {
    if (loading.value || (!force && spaces.value.length)) return spaces.value
    loading.value = true
    try {
      const response = await workspaceApi.spaces.list()
      spaces.value = response.data || []
      if (!spaces.value.some(space => space.spaceId === activeSpaceId.value)) {
        activeSpaceId.value = spaces.value[0]?.spaceId || ''
      }
      persistActiveSpace()
      return spaces.value
    } finally {
      loading.value = false
    }
  }

  function selectSpace(spaceId) {
    if (!spaces.value.some(space => space.spaceId === spaceId)) return
    activeSpaceId.value = spaceId
    persistActiveSpace()
    window.dispatchEvent(new CustomEvent('workspace:selected', { detail: { spaceId } }))
  }

  async function createSpace(name) {
    const response = await workspaceApi.spaces.create({ name })
    await loadSpaces(true)
    selectSpace(response.data.spaceId)
    return response.data
  }

  async function refreshPendingCount() {
    pendingCount.value = await pendingOfflineCount()
  }

  async function refreshUnread() {
    try {
      const response = await workspaceApi.notifications.unread()
      unreadNotifications.value = response.data || 0
    } catch {
      unreadNotifications.value = 0
    }
  }

  async function syncActive() {
    if (!activeSpaceId.value || !online.value) return
    const result = await syncWorkspace(activeSpaceId.value)
    conflictCount.value = result.conflicts.length
    await refreshPendingCount()
    return result
  }

  async function initialize() {
    if (initialized) return
    initialized = true
    await Promise.all([loadSpaces(), refreshPendingCount(), refreshUnread()])
    syncActive().catch(() => {})
  }

  function persistActiveSpace() {
    if (activeSpaceId.value) localStorage.setItem('activeSpaceId', activeSpaceId.value)
    else localStorage.removeItem('activeSpaceId')
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('online', () => {
      online.value = true
      syncActive().catch(() => {})
    })
    window.addEventListener('offline', () => { online.value = false })
    window.addEventListener('offline-queue:changed', refreshPendingCount)
    window.addEventListener('workspace:sync-issues', event => {
      conflictCount.value = (event.detail?.conflicts?.length || 0) + (event.detail?.failures?.length || 0)
    })
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
    initialize
  }
})
