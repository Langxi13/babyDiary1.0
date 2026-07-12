import { openStepUpDialog } from '@/utils/stepUpDialog'

export function getStepUpToken() {
  const token = sessionStorage.getItem('stepUpToken') || ''
  const expiresAt = Number(sessionStorage.getItem('stepUpExpiresAt') || 0)
  if (!token || expiresAt <= Date.now()) {
    sessionStorage.removeItem('stepUpToken')
    sessionStorage.removeItem('stepUpExpiresAt')
    return ''
  }
  return token
}

export async function requestStepUp() {
  const result = await openStepUpDialog()
  sessionStorage.setItem('stepUpToken', result.token)
  sessionStorage.setItem('stepUpExpiresAt', String(new Date(result.expiresAt).getTime()))
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
