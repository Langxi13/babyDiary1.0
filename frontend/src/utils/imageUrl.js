const encodeImagePath = (filename) => String(filename || '')
  .split('/')
  .filter(Boolean)
  .map(part => encodeURIComponent(part))
  .join('/')

export const originalImageUrl = (filename) => {
  const encoded = encodeImagePath(filename)
  return encoded ? `/images/${encoded}` : ''
}

export const thumbnailImageUrl = (filename) => {
  const encoded = encodeImagePath(filename)
  return encoded ? `/images/thumbs/480/${encoded}` : ''
}
