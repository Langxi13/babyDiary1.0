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

function parseSuccess(response, operation) {
  let payload
  try {
    payload = response.json()
  } catch (error) {
    fail(`${operation} returned non-JSON HTTP ${response.status}`)
  }
  if (response.status !== 200 || payload.code !== 200) {
    fail(`${operation} failed: HTTP ${response.status} ${response.body}`)
  }
  return payload
}

export function setup() {
  if (!invitationCode) fail('INVITATION_CODE is required')

  const runId = Date.now().toString(36).slice(-6)
  const users = []

  for (let index = 0; index < userCount; index += 1) {
    const username = `load${runId}${index.toString(36)}`
    const uuidSuffix = `${Date.now().toString(16).slice(-8)}${index.toString(16).padStart(4, '0')}`.slice(-12)
    parseSuccess(http.post(`${baseUrl}/api/auth/register`, JSON.stringify({
      username,
      password,
      confirmPassword: password,
      invitationCode
    }), jsonParams('setup')), `register ${username}`)

    const login = parseSuccess(http.post(`${baseUrl}/api/v2/auth/login`, JSON.stringify({
      username,
      password
    }), {
      ...jsonParams('setup'),
      headers: { ...jsonParams('setup').headers, 'X-Device-Name': 'k6 staging load' }
    }), `login ${username}`)
    const token = login.data.token

    const spaces = parseSuccess(http.get(`${baseUrl}/api/v2/spaces`, jsonParams('setup', token)), `spaces ${username}`)
    const spaceId = spaces.data[0].spaceId
    parseSuccess(http.post(`${baseUrl}/api/v2/spaces/${spaceId}/diaries`, JSON.stringify({
      clientId: `00000000-0000-4000-8000-${uuidSuffix}`,
      title: `合成负载日记 ${index + 1}`,
      date: '2026-07-11',
      content: '用于预发布性能验证的合成内容，不包含任何真实用户数据。',
      contentFormat: 'plain',
      moodKey: 'calm',
      visibility: 'SHARED',
      locked: false
    }), jsonParams('setup', token)), `seed diary ${username}`)

    users.push({ token, spaceId })
  }

  return { users }
}

export default function ({ users }) {
  const index = (exec.scenario.iterationInTest + __VU - 1) % users.length
  const user = users[index]
  const params = jsonParams('load', user.token)
  const responses = http.batch([
    ['GET', `${baseUrl}/api/v2/spaces`, null, params],
    ['GET', `${baseUrl}/api/v2/spaces/${user.spaceId}/diaries?page=0&size=10`, null, params],
    ['GET', `${baseUrl}/api/diaries?page=0&size=5&summary=true`, null, params],
    ['GET', `${baseUrl}/api/albums/groups`, null, params],
    ['GET', `${baseUrl}/api/albums/system/all/photos/page?page=0&size=24`, null, params]
  ])

  for (const response of responses) {
    serverErrors.add(response.status >= 500, { phase: 'load' })
    check(response, {
      'request succeeds': item => item.status === 200,
      'API envelope succeeds': item => {
        try {
          return item.json('code') === 200
        } catch (error) {
          return false
        }
      }
    }, { phase: 'load' })
  }

  sleep(0.5 + Math.random())
}
