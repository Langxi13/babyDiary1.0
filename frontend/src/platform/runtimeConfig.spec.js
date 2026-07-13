import { describe, expect, it } from 'vitest'
import { normalizeServerOrigin } from './runtimeConfig.js'

describe('native server origin validation', () => {
  it('normalizes a secure root origin', () => {
    expect(normalizeServerOrigin('https://diary.example.com/')).toBe('https://diary.example.com')
  })

  it('rejects insecure production, credentials, query strings, and subpaths', () => {
    expect(() => normalizeServerOrigin('http://diary.example.com', { allowDebugHttp: false })).toThrow('HTTPS')
    expect(() => normalizeServerOrigin('https://user:pass@diary.example.com')).toThrow('账号')
    expect(() => normalizeServerOrigin('https://diary.example.com?token=private')).toThrow('查询参数')
    expect(() => normalizeServerOrigin('https://diary.example.com/subpath')).toThrow('根路径')
  })

  it('allows local and private-network HTTP only for debug builds', () => {
    expect(normalizeServerOrigin('http://10.0.2.2:10002', { allowDebugHttp: true })).toBe('http://10.0.2.2:10002')
    expect(() => normalizeServerOrigin('http://10.0.2.2:10002', { allowDebugHttp: false })).toThrow('HTTPS')
  })
})
