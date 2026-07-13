import { Capacitor, registerPlugin } from '@capacitor/core'
import { NATIVE_IMAGE_MAX_BYTES } from '@/platform/nativeImages'
import { isNativeApp } from '@/platform/runtimeConfig'

const NativeShareReceiver = registerPlugin('NativeShareReceiver')
let stagedFiles = []
const MAX_STAGED_FILES = 50

const toFile = async (item, index) => {
  if (!item?.uri || !String(item.type || '').startsWith('image/')) return null
  const response = await fetch(Capacitor.convertFileSrc(item.uri))
  if (!response.ok) return null
  const blob = await response.blob()
  if (!blob.size || blob.size > NATIVE_IMAGE_MAX_BYTES) return null
  return new File([blob], item.name || `shared-image-${index + 1}`, {
    type: item.type,
    lastModified: Number(item.lastModified) || Date.now()
  })
}

export const consumeNativeShareInbox = async () => {
  if (!isNativeApp() || Capacitor.getPlatform() !== 'android') return { files: [], rejected: 0 }
  const result = await NativeShareReceiver.consumeSharedImages()
  const files = []
  let rejected = Number(result.rejected) || 0
  for (const [index, item] of (result.files || []).entries()) {
    try {
      const file = await toFile(item, index)
      if (file) files.push(file)
      else rejected += 1
    } catch {
      rejected += 1
    }
  }
  await NativeShareReceiver.releaseSharedImages({
    uris: (result.files || []).map(item => item.uri).filter(Boolean)
  }).catch(() => {})
  if (files.length) stagedFiles = [...stagedFiles, ...files].slice(-MAX_STAGED_FILES)
  return { files, rejected }
}

export const takeStagedNativeShareFiles = () => {
  const files = stagedFiles
  stagedFiles = []
  return files
}

export const listenForNativeShares = async (listener) => {
  if (!isNativeApp() || Capacitor.getPlatform() !== 'android') return null
  return NativeShareReceiver.addListener('shareAvailable', listener)
}
