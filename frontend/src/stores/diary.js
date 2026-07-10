import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '@/api/diary'

export const useDiaryStore = defineStore('diary', () => {
  const diaries = ref([])
  const currentDiary = ref(null)
  const pagination = ref({
    pageNumber: 0,
    pageSize: 5,
    totalElements: 0,
    totalPages: 0
  })
  const loading = ref(false)
  let diaryListRequestId = 0

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
    loading.value = true
    try {
      const response = await diaryApi.getDiary(id)
      if (response.code === 200) {
        currentDiary.value = response.data
      }
      return response
    } finally {
      loading.value = false
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
    clearCurrentDiary
  }
})
