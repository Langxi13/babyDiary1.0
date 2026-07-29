import { request } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const apiBaseURL = process.env.E2E_API_BASE_URL || 'http://127.0.0.1:11002'
const webBaseURL = process.env.E2E_WEB_BASE_URL || 'http://127.0.0.1:4173'
const authDirectory = path.resolve('e2e/.auth')

const expectSuccess = async (response, operation) => {
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null
  if (!response.ok()) {
    throw new Error(`${operation} failed: HTTP ${response.status()} ${JSON.stringify(payload)}`)
  }
  return payload
}

export default async function globalSetup() {
  await mkdir(authDirectory, { recursive: true })
  const api = await request.newContext({ baseURL: apiBaseURL })
  const username = 'e2e_user'
  const password = 'e2e_password_123'
  const secondUsername = 'e2e_user_b'

  await expectSuccess(await api.post('/api/v3/auth/register', {
    data: {
      username,
      password,
      confirmPassword: password,
      invitationCode: 'e2e-invitation-code'
    }
  }), 'registration')

  const login = await expectSuccess(await api.post('/api/v3/auth/login', {
    headers: { 'X-Device-Name': 'Playwright' },
    data: { username, password }
  }), 'login')
  const token = login.token
  const userInfo = login.userInfo
  const authorization = { Authorization: `Bearer ${token}` }

  await expectSuccess(await api.post('/api/v3/auth/register', {
    data: {
      username: secondUsername,
      password,
      confirmPassword: password,
      invitationCode: 'e2e-invitation-code'
    }
  }), 'second account registration')

  await expectSuccess(await api.post('/api/v3/admin/ai', {
    headers: authorization,
    data: {
      enabled: true,
      baseUrl: 'http://127.0.0.1:8090/v1',
      model: 'baby-diary-test-model',
      apiKey: 'e2e-api-key',
      timeoutSeconds: 10
    }
  }), 'AI mock configuration')

  const spaces = await expectSuccess(await api.get('/api/v3/spaces', { headers: authorization }), 'space list')
  const spaceId = spaces[0].id
  const diary = await expectSuccess(await api.post(`/api/v3/spaces/${spaceId}/diaries`, {
    headers: authorization,
    data: {
      clientId: '1cfd71c4-1a9c-4bb6-9bb0-28deaf4aa532',
      title: '浏览器端到端测试日记',
      diaryDate: '2026-07-11',
      contentHtml: '<p>用于验证导航、列表、时间轴、日历和相册页面。</p>',
      mood: 'happy',
      visibility: 'SHARED',
      locked: false,
      tagIds: [],
      mediaIds: []
    }
  }), 'diary seed')

  const browserState = await api.storageState()
  browserState.origins = [{
    origin: webBaseURL,
    localStorage: [
      { name: 'token', value: token },
      { name: 'userInfo', value: JSON.stringify(userInfo) }
    ]
  }]
  await writeFile(path.join(authDirectory, 'user.json'), JSON.stringify(browserState, null, 2))
  await writeFile(path.join(authDirectory, 'context.json'), JSON.stringify({
    username,
    secondUsername,
    spaceId,
    diaryId: diary.id
  }, null, 2))
  await api.dispose()
}
