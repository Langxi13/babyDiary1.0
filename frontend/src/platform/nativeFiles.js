import { FileTransfer } from '@capacitor/file-transfer'
import { Directory, Filesystem } from '@capacitor/filesystem'
import { Share } from '@capacitor/share'
import { API_ROOT } from '@/api/contract'
import { getClientRequestHeaders } from '@/platform/appRelease'
import { getServerOrigin, isNativeApp } from '@/platform/runtimeConfig'
import { nativeAuthResultRequest } from '@/platform/nativeAuth'

const EXPORT_DIRECTORY = 'exports'

const authHeaders = async extra => ({
  Accept: 'application/json',
  ...await getClientRequestHeaders(),
  ...(localStorage.getItem('token')
    ? { Authorization: `Bearer ${localStorage.getItem('token')}` }
    : {}),
  ...extra
})

const apiUrl = path => `${getServerOrigin()}${path}`

let refreshPromise = null

const transferError = cause => {
  const status = Number(cause?.data?.httpStatus || cause?.httpStatus || 0)
  if (!status) return cause
  const body = cause?.data?.body || cause?.body
  let data = body
  try { data = typeof body === 'string' ? JSON.parse(body) : body } catch { data = { detail: body } }
  const error = new Error(data?.message || data?.detail || cause.message || `请求失败（${status}）`)
  error.response = { status, data }
  return error
}

const persistRefreshedSession = session => {
  localStorage.setItem('token', session.token)
  localStorage.setItem('userInfo', JSON.stringify(session.userInfo || null))
  window.dispatchEvent(new CustomEvent('auth:refreshed', { detail: session }))
}

const refreshNativeSession = async () => {
  if (!refreshPromise) {
    refreshPromise = nativeAuthResultRequest('POST', `${API_ROOT}/auth/refresh`, null)
      .then(session => {
        persistRefreshedSession(session)
        return session
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

const withNativeSessionRetry = async action => {
  try {
    return await action()
  } catch (cause) {
    const error = transferError(cause)
    if (error?.response?.status !== 401 || !localStorage.getItem('token')) throw error
    try {
      await refreshNativeSession()
    } catch {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.dispatchEvent(new Event('auth:expired'))
      throw error
    }
    try {
      return await action()
    } catch (retryCause) {
      throw transferError(retryCause)
    }
  }
}

export const uploadNativeMedia = async (spaceId, source, metadata = {}) => {
  if (!isNativeApp() || source?.kind !== 'native-uri') {
    throw new Error('原生媒体来源无效')
  }
  const url = apiUrl(`${API_ROOT}/spaces/${spaceId}/media`)
  const listener = await FileTransfer.addListener('progress', progress => {
    if (progress.type !== 'upload' || progress.url !== url) return
    window.dispatchEvent(new CustomEvent('native:transfer-progress', {
      detail: {
        type: 'upload',
        uploadId: source.uploadId,
        bytes: progress.bytes,
        contentLength: progress.contentLength
      }
    }))
  })
  try {
    const result = await withNativeSessionRetry(async () => FileTransfer.uploadFile({
      url,
      path: source.uri,
      method: 'POST',
      fileKey: 'file',
      mimeType: source.type,
      chunkedMode: true,
      progress: true,
      params: {
        ...(metadata.caption ? { caption: metadata.caption } : {}),
        ...(metadata.takenAt ? { takenAt: metadata.takenAt } : {})
      },
      headers: await authHeaders({ 'Idempotency-Key': source.uploadId }),
      connectTimeout: 30000,
      readTimeout: 10 * 60 * 1000
    }))
    const payload = typeof result.response === 'string'
      ? JSON.parse(result.response || '{}')
      : result.response
    if (!payload?.id) throw new Error('服务器没有返回有效媒体记录')
    return payload
  } finally {
    await listener.remove()
  }
}

export const downloadNativeFile = async ({ path, params = {}, headers = {}, filename }) => {
  if (!isNativeApp()) throw new Error('当前不在原生应用中')
  await Filesystem.mkdir({
    directory: Directory.Cache,
    path: EXPORT_DIRECTORY,
    recursive: true
  }).catch(error => {
    if (!String(error?.message || '').toLowerCase().includes('exist')) throw error
  })
  const safeName = String(filename || 'Baby-Diary-export')
    .replace(/[^A-Za-z0-9._-]/g, '-')
    .slice(0, 120)
  const localPath = `${EXPORT_DIRECTORY}/${Date.now()}-${safeName}`
  const target = await Filesystem.getUri({ directory: Directory.Cache, path: localPath })
  const result = await withNativeSessionRetry(async () => FileTransfer.downloadFile({
    url: apiUrl(path),
    path: target.uri,
    params,
    headers: await authHeaders(headers),
    progress: true,
    connectTimeout: 30000,
    readTimeout: 10 * 60 * 1000
  }))
  const downloadedPath = result.path || target.uri
  await Share.share({
    title: filename,
    dialogTitle: '保存或分享文件',
    files: [downloadedPath]
  })
  return { native: true, path: downloadedPath, filename }
}

export const cleanupNativeExports = async (now = Date.now()) => {
  const directory = await Filesystem.readdir({
    directory: Directory.Cache,
    path: EXPORT_DIRECTORY
  }).catch(() => ({ files: [] }))
  await Promise.allSettled((directory.files || []).map(async file => {
    const modified = Number(file.mtime || file.ctime || 0)
    if (modified && now - modified < 24 * 60 * 60 * 1000) return
    await Filesystem.deleteFile({
      directory: Directory.Cache,
      path: `${EXPORT_DIRECTORY}/${file.name}`
    })
  }))
}
