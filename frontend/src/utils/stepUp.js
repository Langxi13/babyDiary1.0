import { openStepUpDialog } from '@/utils/stepUpDialog'
import { clearApiCache } from '@/utils/apiCache'

export function getStepUpToken() {
  const token = sessionStorage.getItem('stepUpToken') || ''
  const expiresAt = Number(sessionStorage.getItem('stepUpExpiresAt') || 0)
  if (!token || expiresAt <= Date.now()) {
    const hadStepUpState = !!token || expiresAt > 0
    sessionStorage.removeItem('stepUpToken')
    sessionStorage.removeItem('stepUpExpiresAt')
    if (hadStepUpState) clearApiCache()
    return ''
  }
  return token
}

export async function requestStepUp() {
  const result = await openStepUpDialog()
  sessionStorage.setItem('stepUpToken', result.token)
  sessionStorage.setItem('stepUpExpiresAt', String(new Date(result.expiresAt).getTime()))
  clearApiCache()
  return result.token
}

export async function withStepUpRetry(action) {
  let token = getStepUpToken()
  try {
    return await action(token)
  } catch (error) {
    if (error.response?.status !== 423) throw error
    token = await requestStepUp()
    return action(token)
  }
}
