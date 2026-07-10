import request from '@/utils/request'
import { cachedRequest, invalidateApiCache, stableStringify } from '@/utils/apiCache'

export const diaryApi = {
  getDiaryList(params = {}, options = {}) {
    const requestParams = {
      summary: true,
      ...params
    }
    return cachedRequest(
      `diaries:list:${stableStringify(requestParams)}`,
      () => request.get('/api/diaries', { params: requestParams }),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },

  getDiary(id, options = {}) {
    return cachedRequest(
      `diaries:detail:${id}`,
      () => request.get(`/api/diaries/${id}`),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },

  async createDiary(formData) {
    const response = await request.post('/api/diaries', formData)
    invalidateDiaryReads()
    return response
  },

  async updateDiary(id, formData) {
    const response = await request.post(`/api/diaries/${id}/update`, formData)
    invalidateDiaryReads()
    invalidateApiCache(`diaries:detail:${id}`)
    return response
  },

  async deleteDiary(id) {
    const response = await request.delete(`/api/diaries/${id}`)
    invalidateDiaryReads()
    invalidateApiCache(`diaries:detail:${id}`)
    return response
  },

  exportImages(startDate, endDate) {
    return request.get('/api/diaries/export', {
      params: { startDate, endDate },
      responseType: 'blob'
    })
  },

  getTimeline(params = {}, options = {}) {
    return cachedRequest(
      `diaries:timeline:${stableStringify(params)}`,
      () => request.get('/api/diaries/timeline', { params }),
      { ttl: options.ttl ?? 120000, force: options.force }
    )
  },

  getCalendar(params = {}, options = {}) {
    return cachedRequest(
      `diaries:calendar:${stableStringify(params)}`,
      () => request.get('/api/diaries/calendar', { params }),
      { ttl: options.ttl ?? 120000, force: options.force }
    )
  }
}

export function invalidateDiaryReads() {
  invalidateApiCache('diaries:')
  invalidateApiCache('photos:')
}
