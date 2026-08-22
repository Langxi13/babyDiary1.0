import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:4173'
const username = __ENV.K6_FIXTURE_USERNAME || 'performance-reader'
const password = __ENV.K6_FIXTURE_PASSWORD || 'synthetic-load-password'
const serverErrors = new Rate('server_errors')

export const options = {
  vus: Number(__ENV.K6_PEAK_VUS || 10),
  duration: __ENV.K6_OUTAGE_DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<600'],
    server_errors: ['rate==0']
  }
}

const headers = token => ({ headers: {
  'Content-Type': 'application/json',
  ...(token ? { Authorization: `Bearer ${token}` } : {})
} })

export function setup() {
  const response = http.post(`${baseUrl}/api/v3/auth/login`, JSON.stringify({ username, password }), headers())
  if (response.status !== 200) fail(`Redis outage login failed: HTTP ${response.status}`)
  const token = response.json('token')
  const spaces = http.get(`${baseUrl}/api/v3/spaces`, headers(token))
  if (spaces.status !== 200) fail(`Redis outage space read failed: HTTP ${spaces.status}`)
  return { token, spaceId: spaces.json()[0].id }
}

export default function ({ token, spaceId }) {
  for (const path of [
    `/api/v3/spaces/${spaceId}/home`,
    `/api/v3/spaces/${spaceId}/diaries/summaries?size=20`,
    `/api/v3/spaces/${spaceId}/diaries/timeline`
  ]) {
    const response = http.get(`${baseUrl}${path}`, headers(token))
    serverErrors.add(response.status >= 500)
    check(response, { 'Redis fallback succeeds': value => value.status === 200 })
  }
  sleep(0.5)
}
