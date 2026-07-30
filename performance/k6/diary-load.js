import http from 'k6/http'
import exec from 'k6/execution'
import { check, fail, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:4173'
const invitationCode = __ENV.INVITATION_CODE
const userCount = Number(__ENV.K6_USER_COUNT || 50)
const peakUsers = Number(__ENV.K6_PEAK_VUS || 10)
const password = __ENV.K6_USER_PASSWORD || 'synthetic-load-password'
const serverErrors = new Rate('server_errors')

export const options = {
  scenarios: {
    diary_journeys: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.K6_RAMP_UP || '30s', target: peakUsers },
        { duration: __ENV.K6_STEADY || '3m', target: peakUsers },
        { duration: __ENV.K6_RAMP_DOWN || '30s', target: 0 }
      ],
      gracefulRampDown: '15s'
    }
  },
  thresholds: {
    'checks{phase:load}': ['rate>0.99'],
    'http_req_failed{phase:load}': ['rate<0.01'],
    'http_req_duration{phase:load}': ['p(95)<800', 'p(99)<1500'],
    'server_errors{phase:load}': ['rate==0']
  }
}

const jsonParams = (phase, token) => ({
  headers: {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  },
  tags: { phase }
})

function parseJson(response, operation, expectedStatus = 200) {
  let payload
  try {
    payload = response.json()
  } catch (error) {
    fail(`${operation} returned non-JSON HTTP ${response.status}`)
  }
  if (response.status !== expectedStatus) {
    fail(`${operation} failed: HTTP ${response.status} ${response.body}`)
  }
  return payload
}

function requireStatus(response, operation, expectedStatus) {
  if (response.status !== expectedStatus) {
    fail(`${operation} failed: HTTP ${response.status} ${response.body}`)
  }
}

export function setup() {
  if (!invitationCode) fail('INVITATION_CODE is required')

  const runId = Date.now().toString(36).slice(-6)
  const users = []

  for (let index = 0; index < userCount; index += 1) {
    const username = `load${runId}${index.toString(36)}`
    const uuidSuffix = `${Date.now().toString(16).slice(-8)}${index.toString(16).padStart(4, '0')}`.slice(-12)
    requireStatus(http.post(`${baseUrl}/api/v3/auth/register`, JSON.stringify({
      username,
      password,
      confirmPassword: password,
      invitationCode
    }), jsonParams('setup')), `register ${username}`, 204)

    const login = parseJson(http.post(`${baseUrl}/api/v3/auth/login`, JSON.stringify({
      username,
      password
    }), {
      ...jsonParams('setup'),
      headers: { ...jsonParams('setup').headers, 'X-Device-Name': 'k6 staging load' }
    }), `login ${username}`)
    const token = login.token

    const spaces = parseJson(http.get(`${baseUrl}/api/v3/spaces`, jsonParams('setup', token)), `spaces ${username}`)
    const spaceId = spaces[0].id
    parseJson(http.post(`${baseUrl}/api/v3/spaces/${spaceId}/diaries`, JSON.stringify({
      clientId: `00000000-0000-4000-8000-${uuidSuffix}`,
      title: `合成负载日记 ${index + 1}`,
      diaryDate: '2026-07-11',
      contentHtml: '<p>用于预发布性能验证的合成内容，不包含任何真实用户数据。</p>',
      mood: 'calm',
      visibility: 'PRIVATE',
      locked: false
    }), jsonParams('setup', token)), `seed diary ${username}`, 201)

    users.push({ token, spaceId })
  }

  return { users }
}

export default function ({ users }) {
  const index = (exec.scenario.iterationInTest + __VU - 1) % users.length
  const user = users[index]
  const params = jsonParams('load', user.token)
  const responses = http.batch([
    ['GET', `${baseUrl}/api/v3/spaces`, null, params],
    ['GET', `${baseUrl}/api/v3/spaces/${user.spaceId}/diaries?size=10`, null, params],
    ['GET', `${baseUrl}/api/v3/spaces/${user.spaceId}/diaries/timeline`, null, params],
    ['GET', `${baseUrl}/api/v3/spaces/${user.spaceId}/album-groups`, null, params],
    ['GET', `${baseUrl}/api/v3/spaces/${user.spaceId}/albums/system/all?page=0&size=24`, null, params]
  ])

  for (const response of responses) {
    serverErrors.add(response.status >= 500, { phase: 'load' })
    check(response, {
      'request succeeds': item => item.status === 200,
      'response is JSON': item => {
        try {
          return item.json() !== null
        } catch (error) {
          return false
        }
      }
    }, { phase: 'load' })
  }

  sleep(0.5 + Math.random())
}
