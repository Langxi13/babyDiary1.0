import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { authApi } from '@/api/auth'

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
  const { value } = await ElMessageBox.prompt('请输入当前登录密码', '二次验证', {
    confirmButtonText: '验证',
    cancelButtonText: '取消',
    inputType: 'password',
    inputPattern: /.+/,
    inputErrorMessage: '请输入密码',
    closeOnClickModal: false
  })
  const response = await authApi.stepUp(value)
  sessionStorage.setItem('stepUpToken', response.data.token)
  sessionStorage.setItem('stepUpExpiresAt', String(new Date(response.data.expiresAt).getTime()))
  return response.data.token
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
