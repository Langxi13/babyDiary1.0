import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  addListener: vi.fn(),
  uploadFile: vi.fn(),
  downloadFile: vi.fn(),
  mkdir: vi.fn(),
  getUri: vi.fn(),
  readdir: vi.fn(),
  deleteFile: vi.fn(),
  share: vi.fn(),
  nativeAuth: vi.fn(),
  removeListener: vi.fn()
}))

vi.mock('@capacitor/file-transfer', () => ({
  FileTransfer: {
    addListener: mocks.addListener,
    uploadFile: mocks.uploadFile,
    downloadFile: mocks.downloadFile
  }
}))
vi.mock('@capacitor/filesystem', () => ({
  Directory: { Cache: 'CACHE' },
  Filesystem: {
    mkdir: mocks.mkdir,
    getUri: mocks.getUri,
    readdir: mocks.readdir,
    deleteFile: mocks.deleteFile
  }
}))
vi.mock('@capacitor/share', () => ({ Share: { share: mocks.share } }))
vi.mock('@/platform/runtimeConfig', () => ({
  getServerOrigin: () => 'https://diary.example.com',
  isNativeApp: () => true
}))
vi.mock('@/platform/appRelease', () => ({
  getClientRequestHeaders: () => Promise.resolve({
    'X-Client-Platform': 'android',
    'X-Client-Version-Code': '6',
    'X-Client-Version-Name': '1.0.0-beta.8'
  })
}))
vi.mock('@/platform/nativeAuth', () => ({
  nativeAuthResultRequest: mocks.nativeAuth
}))

import { downloadNativeFile, uploadNativeMedia } from './nativeFiles'

describe('native file transfer', () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => mock.mockReset())
    localStorage.clear()
    localStorage.setItem('token', 'old-token')
    mocks.removeListener.mockResolvedValue(undefined)
    mocks.addListener.mockResolvedValue({ remove: mocks.removeListener })
    mocks.mkdir.mockResolvedValue(undefined)
    mocks.getUri.mockResolvedValue({ uri: 'file:///cache/export.zip' })
    mocks.share.mockResolvedValue({})
  })

  it('streams a staged image and retries once with a refreshed native session', async () => {
    mocks.uploadFile
      .mockRejectedValueOnce({
        data: { httpStatus: 401, body: '{"code":"TOKEN_EXPIRED"}' }
      })
      .mockResolvedValueOnce({
        responseCode: '201',
        response: JSON.stringify({ id: 'media-1', mediaType: 'IMAGE' })
      })
    mocks.nativeAuth.mockResolvedValue({
      token: 'new-token',
      userInfo: { id: 'account-1', username: 'owner' }
    })
    const source = {
      kind: 'native-uri',
      uploadId: '52f117a1-adcc-46ad-92e4-4770d266d83d',
      uri: 'file:///private/pending.heic',
      type: 'image/heic'
    }

    await expect(uploadNativeMedia('space-1', source, { caption: '旅行' }))
      .resolves.toMatchObject({ id: 'media-1' })

    expect(mocks.uploadFile).toHaveBeenCalledTimes(2)
    expect(mocks.uploadFile.mock.calls[0][0]).toMatchObject({
      path: source.uri,
      fileKey: 'file',
      mimeType: 'image/heic',
      chunkedMode: true,
      headers: {
        Authorization: 'Bearer old-token',
        'Idempotency-Key': source.uploadId,
        'X-Client-Version-Name': '1.0.0-beta.8'
      }
    })
    expect(mocks.uploadFile.mock.calls[1][0].headers.Authorization).toBe('Bearer new-token')
    expect(mocks.nativeAuth).toHaveBeenCalledOnce()
    expect(mocks.removeListener).toHaveBeenCalledOnce()
  })

  it('downloads exports to app cache and opens the system save sheet', async () => {
    mocks.downloadFile.mockResolvedValue({ path: 'file:///cache/export.zip' })

    await expect(downloadNativeFile({
      path: '/api/v3/spaces/space-1/transfer/export',
      filename: 'Baby-Diary-export.zip'
    })).resolves.toEqual({
      native: true,
      path: 'file:///cache/export.zip',
      filename: 'Baby-Diary-export.zip'
    })

    expect(mocks.downloadFile).toHaveBeenCalledWith(expect.objectContaining({
      url: 'https://diary.example.com/api/v3/spaces/space-1/transfer/export',
      path: 'file:///cache/export.zip',
      progress: true
    }))
    expect(mocks.share).toHaveBeenCalledWith(expect.objectContaining({
      files: ['file:///cache/export.zip']
    }))
  })
})
