import { Capacitor } from '@capacitor/core'
import { Camera, EncodingType, MediaType, MediaTypeSelection } from '@capacitor/camera'
import { Directory, Filesystem } from '@capacitor/filesystem'

export const NATIVE_IMAGE_MAX_BYTES = 25 * 1024 * 1024
export const NATIVE_IMAGE_QUALITY = 100
const STAGING_DIRECTORY = 'pending-media'
const STAGING_RETENTION_MS = 30 * 24 * 60 * 60 * 1000

const IMAGE_FORMATS = new Map([
  ['jpg', { type: 'image/jpeg', extension: 'jpg' }],
  ['jpeg', { type: 'image/jpeg', extension: 'jpg' }],
  ['png', { type: 'image/png', extension: 'png' }],
  ['gif', { type: 'image/gif', extension: 'gif' }],
  ['webp', { type: 'image/webp', extension: 'webp' }],
  ['heic', { type: 'image/heic', extension: 'heic' }],
  ['heif', { type: 'image/heif', extension: 'heif' }]
])

const mediaUrl = (result) => result.webPath || (result.uri ? Capacitor.convertFileSrc(result.uri) : '')

const normalizedFormat = (value) => String(value || '').trim().toLowerCase().replace(/^image\//, '')

const imageType = (result, blob) => {
  const blobType = normalizedFormat(blob?.type)
  if (blobType) return IMAGE_FORMATS.get(blobType) || null
  return IMAGE_FORMATS.get(normalizedFormat(result.metadata?.format)) || null
}

const jpegPreviewUrl = thumbnail => {
  const value = String(thumbnail || '')
  if (!value) return ''
  return value.startsWith('data:') ? value : `data:image/jpeg;base64,${value}`
}

const thumbnailBlob = (thumbnail) => {
  const value = String(thumbnail || '')
  if (!value) return null
  const commaIndex = value.indexOf(',')
  const encoded = commaIndex >= 0 ? value.slice(commaIndex + 1) : value
  const binary = window.atob(encoded)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return new Blob([bytes], { type: 'image/jpeg' })
}

const fetchedImage = async (result) => {
  const url = mediaUrl(result)
  if (!url) return null
  try {
    const response = await fetch(url)
    if (!response.ok) return null
    const blob = await response.blob()
    const type = imageType(result, blob)
    if (!type || !blob.size || blob.size > NATIVE_IMAGE_MAX_BYTES) return null
    return { blob, ...type }
  } catch {
    return null
  }
}

const toImageFile = async (result, index, prefix) => {
  if (result.type !== MediaType.Photo) throw new Error('只能选择图片')
  const declaredType = IMAGE_FORMATS.get(normalizedFormat(result.metadata?.format))
  const source = declaredType ? await fetchedImage(result) : null
  const blob = source?.blob || thumbnailBlob(result.thumbnail)
  const type = source?.type || 'image/jpeg'
  const extension = source?.extension || 'jpg'
  if (!blob?.size) throw new Error('无法读取所选图片')
  if (blob.size > NATIVE_IMAGE_MAX_BYTES) throw new Error('单张图片不能超过25MB')
  const timestamp = Date.now()
  return new File([blob], `${prefix}-${timestamp}-${index + 1}.${extension}`, {
    type,
    lastModified: timestamp
  })
}

const convertResults = async (results, prefix) => {
  const files = []
  for (const [index, result] of results.entries()) {
    files.push(await toImageSource(result, index, prefix))
  }
  return files
}

const ensureStagingDirectory = () => Filesystem.mkdir({
  directory: Directory.Data,
  path: STAGING_DIRECTORY,
  recursive: true
}).catch(error => {
  if (!String(error?.message || '').toLowerCase().includes('exist')) throw error
})

const nativeSize = async result => {
  const declared = Number(result.metadata?.size)
  if (Number.isFinite(declared) && declared > 0) return declared
  if (!result.uri) return 0
  const stat = await Filesystem.stat({ path: result.uri })
  return Number(stat.size) || 0
}

const toImageSource = async (result, index, prefix) => {
  if (!result.uri) return toImageFile(result, index, prefix)
  if (result.type !== MediaType.Photo) throw new Error('只能选择图片')
  const format = IMAGE_FORMATS.get(normalizedFormat(result.metadata?.format))
  if (!format) throw new Error('暂不支持所选图片格式')
  const size = await nativeSize(result)
  if (!size) throw new Error('无法读取所选图片')
  if (size > NATIVE_IMAGE_MAX_BYTES) throw new Error('单张图片不能超过25MB')

  await ensureStagingDirectory()
  const uploadId = crypto.randomUUID()
  const name = `${prefix}-${Date.now()}-${index + 1}.${format.extension}`
  const stagedPath = `${STAGING_DIRECTORY}/${uploadId}.${format.extension}`
  await Filesystem.copy({
    from: result.uri,
    to: stagedPath,
    toDirectory: Directory.Data
  })
  const staged = await Filesystem.getUri({ directory: Directory.Data, path: stagedPath })
  return {
    kind: 'native-uri',
    uploadId,
    uri: staged.uri,
    stagedPath,
    previewUrl: jpegPreviewUrl(result.thumbnail) || result.webPath || Capacitor.convertFileSrc(staged.uri),
    name,
    type: format.type,
    size,
    lastModified: Date.now()
  }
}

export const chooseNativeImages = async (limit = 20) => {
  const boundedLimit = Math.max(1, Math.floor(Number(limit) || 1))
  const result = await Camera.chooseFromGallery({
    mediaType: MediaTypeSelection.Photo,
    allowMultipleSelection: boundedLimit > 1,
    limit: boundedLimit,
    includeMetadata: true,
    editable: 'no',
    quality: NATIVE_IMAGE_QUALITY,
    correctOrientation: false
  })
  return convertResults((result.results || []).slice(0, boundedLimit), 'album')
}

export const takeNativePhoto = async () => {
  const result = await Camera.takePhoto({
    quality: 95,
    correctOrientation: true,
    encodingType: EncodingType.JPEG,
    saveToGallery: false,
    editable: 'no',
    includeMetadata: true
  })
  return convertResults([result], 'camera')
}

export const isNativeImageCancellation = (error) => {
  const code = String(error?.code || '')
  const message = String(error?.message || '').toLowerCase()
  return ['OS-PLUG-CAMR-0006', 'OS-PLUG-CAMR-0020'].includes(code) || message.includes('cancel')
}

export const releaseNativeImage = async source => {
  if (source?.kind !== 'native-uri' || !source.stagedPath) return
  await Filesystem.deleteFile({ directory: Directory.Data, path: source.stagedPath }).catch(() => {})
}

export const cleanupExpiredNativeImages = async (now = Date.now()) => {
  await ensureStagingDirectory()
  const directory = await Filesystem.readdir({ directory: Directory.Data, path: STAGING_DIRECTORY })
  await Promise.allSettled((directory.files || []).map(async file => {
    const modified = Number(file.mtime || file.ctime || 0)
    if (modified && now - modified < STAGING_RETENTION_MS) return
    await Filesystem.deleteFile({
      directory: Directory.Data,
      path: `${STAGING_DIRECTORY}/${file.name}`
    })
  }))
}
