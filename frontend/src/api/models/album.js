import { normalizeMedia } from './media'

export const normalizeAlbum = (album = {}) => ({
  ...album,
  editable: album.type !== 'SYSTEM',
  coverMedia: album.coverMedia ? normalizeMedia(album.coverMedia) : null
})

export const normalizeAlbumGroup = (group = {}) => ({
  ...group,
  editable: group.type !== 'SYSTEM',
  albums: (group.albums || []).map(normalizeAlbum)
})
