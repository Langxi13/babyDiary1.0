import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'
import { normalizeAnniversary, normalizeDiary, normalizeDraft, normalizeMedia } from '@/api/models'
import { cachedRequest, stableStringify } from '@/utils/apiCache'
import { getStepUpToken } from '@/utils/stepUp'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

export const homeApi = {
  get(spaceId, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(
      `spaces:${spaceId}:home:projection:${stableStringify({ elevated: !!stepUpToken })}`,
      async () => {
        const result = await request.get(`${API_ROOT}/spaces/${spaceId}/home`, {
          headers: stepHeader(stepUpToken)
        })
        return {
          diaryTotal: Number(result.diaryTotal) || 0,
          recentDiaries: (result.recentDiaries || []).map(normalizeDiary),
          drafts: (result.drafts || []).map(normalizeDraft),
          anniversaries: (result.anniversaries || []).map(normalizeAnniversary),
          favorites: (result.favorites || []).map(item => ({
            ...item,
            media: normalizeMedia(item.media || {})
          }))
        }
      },
      { ttl: options.ttl ?? 30000, force: options.force, cacheIf: () => !stepUpToken }
    )
  }
}
