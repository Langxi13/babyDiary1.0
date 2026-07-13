import { resolveServerUrl } from '../platform/runtimeConfig.js'

const encodeImagePath = (filename) => String(filename || '')
  .split('/')
  .filter(Boolean)
  .map(part => encodeURIComponent(part))
  .join('/')

export const originalImageUrl = (filename) => {
  const encoded = encodeImagePath(filename)
  return encoded ? resolveServerUrl(`/images/${encoded}`) : ''
}

export const thumbnailImageUrl = (filename) => {
  const encoded = encodeImagePath(filename)
  return encoded ? resolveServerUrl(`/images/thumbs/480/${encoded}`) : ''
}
