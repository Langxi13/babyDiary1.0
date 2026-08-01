import { workspaceApi } from '@/api/workspace'
import { diaryApi, invalidateDiaryReads } from '@/api/diary'
import { mediaApi } from '@/api/media'
import {
  getOfflineMeta,
  listOfflineOperations,
  removeOfflineOperations,
  setOfflineMeta,
  clearOfflineSessionCache
} from '@/utils/offlineDb'
import { chunkOperations } from '@/utils/offlineQueue'
import { getStepUpToken } from '@/utils/stepUp'
import { releaseNativeImage } from '@/platform/nativeImages'

const syncingSpaces = new Set()

export async function syncWorkspace(spaceId) {
  if (!spaceId || syncingSpaces.has(spaceId) || (typeof navigator !== 'undefined' && !navigator.onLine)) return { synced: 0, conflicts: [], failures: [] }
  syncingSpaces.add(spaceId)
  const conflicts = []
  const failures = []
  let synced = 0
  try {
    const queued = await listOfflineOperations(spaceId)
    const diaryOperations = queued.filter(item => item.kind === 'diary')
    for (const batch of chunkOperations(diaryOperations)) {
      const results = await workspaceApi.sync.push(spaceId, batch.map(toSyncOperation), getStepUpToken())
      const appliedIds = []
      let retryable = false
      results.forEach(result => {
        const local = batch.find(item => item.id === result.operationId)
        if (result.status === 'APPLIED') {
          appliedIds.push(result.operationId)
          synced += 1
        } else if (result.status === 'CONFLICT') {
          conflicts.push({ ...result, local })
        } else {
          failures.push({ ...result, local })
          retryable ||= result.status === 'RETRYABLE'
        }
      })
      await removeOfflineOperations(appliedIds)
      if (retryable) break
    }

    const mediaOperations = (await listOfflineOperations(spaceId)).filter(item => item.kind === 'media')
    const mediaByDiary = new Map()
    for (const item of mediaOperations) {
      const group = mediaByDiary.get(item.diaryId) || []
      group.push(item)
      mediaByDiary.set(item.diaryId, group)
    }
    for (const [diaryId, items] of mediaByDiary) {
      const uploaded = []
      try {
        for (const pendingMedia of items) {
          const source = pendingMedia.source || {
            file: pendingMedia.file,
            uploadId: pendingMedia.uploadId || pendingMedia.id
          }
          const uploadedMedia = await mediaApi.uploadSource(spaceId, source, {
            caption: pendingMedia.caption
          })
          uploaded.push({ id: uploadedMedia.id, source })
        }
        const stepUpToken = getStepUpToken()
        const current = await diaryApi.getDiary(spaceId, diaryId, { force: true })
        const mediaIds = [...new Set([
          ...(current.media || []).map(item => item.id).filter(Boolean),
          ...uploaded.map(item => item.id)
        ])]
        await diaryApi.update(spaceId, diaryId, { ...current, mediaIds }, current.version, stepUpToken)
        await removeOfflineOperations(items.map(item => item.id))
        await Promise.allSettled(items.map(item => releaseNativeImage(item.source || item.file)))
        synced += items.length
      } catch {
        await Promise.allSettled(uploaded.map(item => mediaApi.remove(spaceId, item.id)))
        break
      }
    }

    let cursor = await getOfflineMeta(`cursor:${spaceId}`, 0)
    let hasMore = true
    while (hasMore) {
      const result = await workspaceApi.sync.pull(spaceId, cursor, 200)
      if (result.resetRequired) {
        await clearOfflineSessionCache()
        invalidateDiaryReads(spaceId)
        cursor = result.baselineCursor
        hasMore = false
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('workspace:changes', {
            detail: { spaceId, changes: [], resetRequired: true }
          }))
        }
        break
      }
      cursor = result.nextCursor
      hasMore = result.hasMore
      if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('workspace:changes', { detail: { spaceId, changes: result.changes } }))
    }
    await setOfflineMeta(`cursor:${spaceId}`, cursor)
    if ((conflicts.length || failures.length) && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('workspace:sync-issues', { detail: { spaceId, conflicts, failures } }))
    }
    return { synced, conflicts, failures }
  } finally {
    syncingSpaces.delete(spaceId)
  }
}

function toSyncOperation(item) {
  return {
    operationId: item.id,
    entityType: 'DIARY',
    action: item.action,
    entityId: item.entityId,
    baseVersion: item.baseVersion,
    payload: item.payload
  }
}
