import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '@/api/diary'
import { useWorkspaceStore } from '@/stores/workspace'

const emptyPagination = () => ({
  pageNumber: 0,
  pageSize: 5,
  totalElements: 0,
  totalPages: 0
})

export const useDiaryStore = defineStore('diary', () => {
  const diaries = ref([])
  const currentDiary = ref(null)
  const pagination = ref(emptyPagination())
  const loading = ref(false)
  let diaryListRequestId = 0
  let diaryDetailRequestId = 0

  async function resolveSpaceId() {
    const workspaceStore = useWorkspaceStore()
    await workspaceStore.loadSpaces()
    return workspaceStore.activeSpaceId
  }

  async function requireSpaceId() {
    const spaceId = await resolveSpaceId()
    if (!spaceId) throw new Error('当前账户没有可用日记空间')
    return spaceId
  }

  async function fetchDiaries(params = {}) {
    const requestId = ++diaryListRequestId
    loading.value = true
    try {
      const spaceId = await resolveSpaceId()
      if (requestId !== diaryListRequestId) return null
      if (!spaceId) throw new Error('当前账户没有可用日记空间')
      const result = await diaryApi.getDiaryList(spaceId, {
        page: params.page ?? 0,
        size: params.size ?? 5,
        startDate: params.startDate,
        endDate: params.endDate,
        keyword: params.keyword,
        tagId: params.tagId,
        mood: params.mood
      })
      
      if (requestId === diaryListRequestId) {
        diaries.value = result.content
        pagination.value = {
          pageNumber: result.pageNumber,
          pageSize: result.pageSize,
          totalElements: result.totalElements,
          totalPages: result.totalPages
        }
      }
      return result
    } finally {
      if (requestId === diaryListRequestId) {
        loading.value = false
      }
    }
  }

  async function fetchDiary(id) {
    const requestId = ++diaryDetailRequestId
    loading.value = true
    try {
      const spaceId = await resolveSpaceId()
      if (requestId !== diaryDetailRequestId) return null
      if (!spaceId) throw new Error('当前账户没有可用日记空间')
      const diary = await diaryApi.getDiary(spaceId, id)
      if (requestId === diaryDetailRequestId) {
        currentDiary.value = diary
      }
      return diary
    } finally {
      if (requestId === diaryDetailRequestId) {
        loading.value = false
      }
    }
  }

  async function createDiary(formData) {
    return diaryApi.createDiary(await requireSpaceId(), formData)
  }

  async function updateDiary(id, formData) {
    return diaryApi.updateDiary(await requireSpaceId(), id, formData)
  }

  async function deleteDiary(id) {
    return diaryApi.deleteDiary(await requireSpaceId(), id)
  }

  async function exportImages(startDate, endDate) {
    return diaryApi.exportImages(await requireSpaceId(), startDate, endDate)
  }

  async function fetchTimeline(params = {}) {
    return diaryApi.getTimeline(await requireSpaceId(), params)
  }

  async function fetchCalendar(params = {}) {
    return diaryApi.getCalendar(await requireSpaceId(), params)
  }

  function clearCurrentDiary() {
    currentDiary.value = null
  }

  function reset() {
    diaryListRequestId += 1
    diaryDetailRequestId += 1
    diaries.value = []
    currentDiary.value = null
    pagination.value = emptyPagination()
    loading.value = false
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('auth:session-reset', reset)
  }

  return {
    diaries,
    currentDiary,
    pagination,
    loading,
    fetchDiaries,
    fetchDiary,
    createDiary,
    updateDiary,
    deleteDiary,
    exportImages,
    fetchTimeline,
    fetchCalendar,
    clearCurrentDiary,
    reset
  }
})
