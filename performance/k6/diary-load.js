import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:4173'
const peakUsers = Number(__ENV.K6_PEAK_VUS || 10)
const username = __ENV.K6_FIXTURE_USERNAME || 'performance-reader'
const password = __ENV.K6_FIXTURE_PASSWORD || 'synthetic-load-password'
const serverErrors = new Rate('server_errors')

export const options = {
  scenarios: {
    diary_journeys: {
      executor: 'ramping-vus',
      exec: 'readJourneys',
      startVUs: 0,
      stages: [
        { duration: __ENV.K6_RAMP_UP || '30s', target: peakUsers },
        { duration: __ENV.K6_STEADY || '3m', target: peakUsers },
        { duration: __ENV.K6_RAMP_DOWN || '30s', target: 0 }
      ],
      gracefulRampDown: '15s'
    },
    single_export: {
      executor: 'per-vu-iterations',
      exec: 'exportBook',
      vus: 1,
      iterations: 1,
      startTime: __ENV.K6_EXPORT_START || '45s',
      maxDuration: '5m'
    }
  },
  thresholds: {
    'checks{phase:load}': ['rate>0.99'],
    'http_req_failed{phase:load}': ['rate<0.01'],
    'http_req_duration{endpoint:core}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{endpoint:thumbnail}': ['p(95)<250', 'p(99)<600'],
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
  const login = parseJson(http.post(`${baseUrl}/api/v3/auth/login`, JSON.stringify({
    username,
    password
  }), {
    ...jsonParams('setup'),
    headers: { ...jsonParams('setup').headers, 'X-Device-Name': 'k6 staging load' }
  }), `login ${username}`)
  const spaces = parseJson(http.get(`${baseUrl}/api/v3/spaces`, jsonParams('setup', login.token)), `spaces ${username}`)
  if (!spaces.length) fail('Performance fixture has no space')
  return { token: login.token, spaceId: spaces[0].id }
}

const loadParams = (token, endpoint = 'core') => ({
  ...jsonParams('load', token),
  tags: { phase: 'load', endpoint }
})

const checkedJson = (response, operation) => {
  serverErrors.add(response.status >= 500, { phase: 'load' })
  check(response, {
    [`${operation} succeeds`]: item => item.status === 200,
    [`${operation} is JSON`]: item => {
      try { return item.json() !== null } catch { return false }
    }
  }, { phase: 'load' })
  return response.status === 200 ? response.json() : null
}

export function readJourneys({ token, spaceId }) {
  const params = loadParams(token)
  const home = checkedJson(http.get(`${baseUrl}/api/v3/spaces/${spaceId}/home`, params), 'home')
  const summaries = checkedJson(http.get(`${baseUrl}/api/v3/spaces/${spaceId}/diaries/summaries?size=20`, params), 'summary list')
  const timeline = checkedJson(http.get(`${baseUrl}/api/v3/spaces/${spaceId}/diaries/timeline`, params), 'timeline index')
  const newestMonth = timeline?.years?.[0]?.months?.[0]?.month
  if (newestMonth) {
    const [year, month] = newestMonth.split('-').map(Number)
    const endDate = new Date(Date.UTC(year, month, 0)).toISOString().slice(0, 10)
    checkedJson(http.get(
      `${baseUrl}/api/v3/spaces/${spaceId}/diaries/summaries?startDate=${newestMonth}-01&endDate=${endDate}&size=20&includeTotal=false`,
      params
    ), 'timeline month')
  }
  checkedJson(http.get(`${baseUrl}/api/v3/spaces/${spaceId}/album-groups`, params), 'album catalog')
  const album = checkedJson(http.get(
    `${baseUrl}/api/v3/spaces/${spaceId}/albums/system/all?page=0&size=24`, params
  ), 'album page')
  const thumbnailRequests = (album?.media || []).slice(0, 24).flatMap(item => {
    const url = item.representations?.thumbnail?.url
    return url ? [['GET', url.startsWith('http') ? url : `${baseUrl}${url}`, null, loadParams(token, 'thumbnail')]] : []
  })
  for (const response of thumbnailRequests.length ? http.batch(thumbnailRequests) : []) {
    serverErrors.add(response.status >= 500, { phase: 'load' })
    check(response, {
      'signed thumbnail succeeds': item => item.status === 200
    }, { phase: 'load' })
  }
  check(home, { 'home is bounded': value => (value?.recentDiaries?.length || 0) <= 4 })
  check(summaries, { 'summary page is bounded': value => (value?.items?.length || 0) <= 20 })
  sleep(0.5 + Math.random())
}

export function exportBook({ token, spaceId }) {
  const response = http.get(
    `${baseUrl}/api/v3/spaces/${spaceId}/books?format=epub&startDate=2025-01-01&endDate=2025-12-31`,
    loadParams(token, 'export')
  )
  serverErrors.add(response.status >= 500, { phase: 'load' })
  check(response, {
    'single export succeeds': item => item.status === 200,
    'single export is epub': item => String(item.headers['Content-Type'] || '').includes('application/epub+zip')
  }, { phase: 'load' })
}

export default readJourneys
