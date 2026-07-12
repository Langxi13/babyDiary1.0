import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '@/api/diary'

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

  async function fetchDiaries(params = {}) {
    const requestId = ++diaryListRequestId
    loading.value = true
    try {
      const response = await diaryApi.getDiaryList({
        page: params.page ?? 0,
        size: params.size ?? 5,
        startDate: params.startDate,
        endDate: params.endDate,
        keyword: params.keyword,
        tagId: params.tagId,
        moodKey: params.moodKey
      })
      
      if (response.code === 200 && requestId === diaryListRequestId) {
        diaries.value = response.data.content
        pagination.value = {
          pageNumber: response.data.pageNumber,
          pageSize: response.data.pageSize,
          totalElements: response.data.totalElements,
          totalPages: response.data.totalPages
        }
      }
      return response
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
      const response = await diaryApi.getDiary(id)
      if (response.code === 200 && requestId === diaryDetailRequestId) {
        currentDiary.value = response.data
      }
      return response
    } finally {
      if (requestId === diaryDetailRequestId) {
        loading.value = false
      }
    }
  }

  async function createDiary(formData) {
    return diaryApi.createDiary(formData)
  }

  async function updateDiary(id, formData) {
    return diaryApi.updateDiary(id, formData)
  }

  async function deleteDiary(id) {
    return diaryApi.deleteDiary(id)
  }

  async function exportImages(startDate, endDate) {
    return diaryApi.exportImages(startDate, endDate)
  }

  async function fetchTimeline(params = {}) {
    return diaryApi.getTimeline(params)
  }

  async function fetchCalendar(params = {}) {
    return diaryApi.getCalendar(params)
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
