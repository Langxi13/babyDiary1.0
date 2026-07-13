import { Capacitor } from '@capacitor/core'
import { Camera, EncodingType, MediaType, MediaTypeSelection } from '@capacitor/camera'

export const NATIVE_IMAGE_MAX_BYTES = 10 * 1024 * 1024
export const NATIVE_IMAGE_MAX_DIMENSION = 1920
export const NATIVE_IMAGE_QUALITY = 85

const IMAGE_FORMATS = new Map([
  ['jpg', { type: 'image/jpeg', extension: 'jpg' }],
  ['jpeg', { type: 'image/jpeg', extension: 'jpg' }],
  ['png', { type: 'image/png', extension: 'png' }],
  ['gif', { type: 'image/gif', extension: 'gif' }],
  ['webp', { type: 'image/webp', extension: 'webp' }]
])
const IMAGE_TYPES = new Map(Array.from(IMAGE_FORMATS.values()).map(item => [item.type, item]))

const mediaUrl = (result) => result.webPath || (result.uri ? Capacitor.convertFileSrc(result.uri) : '')

const normalizedFormat = (value) => String(value || '').trim().toLowerCase().replace(/^image\//, '')

const imageType = (result, blob) => {
  const blobType = String(blob?.type || '').trim().toLowerCase()
  if (blobType) return IMAGE_TYPES.get(blobType) || null
  return IMAGE_FORMATS.get(normalizedFormat(result.metadata?.format)) || null
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
  if (blob.size > NATIVE_IMAGE_MAX_BYTES) throw new Error('单张图片不能超过10MB')
  const timestamp = Date.now()
  return new File([blob], `${prefix}-${timestamp}-${index + 1}.${extension}`, {
    type,
    lastModified: timestamp
  })
}

const convertResults = async (results, prefix) => {
  const files = []
  for (const [index, result] of results.entries()) {
    files.push(await toImageFile(result, index, prefix))
  }
  return files
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
    targetWidth: NATIVE_IMAGE_MAX_DIMENSION,
    targetHeight: NATIVE_IMAGE_MAX_DIMENSION,
    correctOrientation: true
  })
  return convertResults((result.results || []).slice(0, boundedLimit), 'album')
}

export const takeNativePhoto = async () => {
  const result = await Camera.takePhoto({
    quality: NATIVE_IMAGE_QUALITY,
    targetWidth: NATIVE_IMAGE_MAX_DIMENSION,
    targetHeight: NATIVE_IMAGE_MAX_DIMENSION,
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
