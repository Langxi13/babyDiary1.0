import { describe, expect, it } from 'vitest'
import { evaluateAndroidUpdate, trustedUpdateUrl } from './appRelease'

describe('Android release evaluation', () => {
  it('offers a newer signed release without forcing supported clients', () => {
    expect(evaluateAndroidUpdate(
      { platform: 'android', build: 1 },
      { enabled: true, latestVersionCode: 2, minimumVersionCode: 1, mandatory: false }
    )).toMatchObject({ supported: true, available: true, required: false })
  })

  it('requires an update below the supported version boundary', () => {
    expect(evaluateAndroidUpdate(
      { platform: 'android', build: 1 },
      { enabled: true, latestVersionCode: 3, minimumVersionCode: 2, mandatory: false }
    )).toMatchObject({ available: true, required: true })
  })

  it('does not offer Android packages to web clients or current versions', () => {
    expect(evaluateAndroidUpdate(
      { platform: 'web', build: 0 },
      { enabled: true, latestVersionCode: 2, minimumVersionCode: 1 }
    ).available).toBe(false)
    expect(evaluateAndroidUpdate(
      { platform: 'android', build: 2 },
      { enabled: true, latestVersionCode: 2, minimumVersionCode: 1 }
    ).available).toBe(false)
  })
})

describe('update URL validation', () => {
  it('accepts HTTPS downloads and rejects cleartext packages', () => {
    expect(trustedUpdateUrl('https://downloads.example.com/BabyDiary.apk')).toBe('https://downloads.example.com/BabyDiary.apk')
    expect(() => trustedUpdateUrl('http://downloads.example.com/BabyDiary.apk')).toThrow('HTTPS')
    expect(() => trustedUpdateUrl('https://user:secret@downloads.example.com/BabyDiary.apk')).toThrow('HTTPS')
  })
})
