import { workspaceApi } from '@/api/workspace'
import {
  getOfflineMeta,
  listOfflineOperations,
  removeOfflineOperations,
  setOfflineMeta
} from '@/utils/offlineDb'
import { chunkOperations } from '@/utils/offlineQueue'

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
      const response = await workspaceApi.sync.push(spaceId, batch.map(toSyncOperation))
      const appliedIds = []
      let retryable = false
      response.data.forEach(result => {
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
    for (const media of mediaOperations) {
      try {
        const formData = new FormData()
        formData.append('file', media.file, media.filename || 'media')
        formData.append('diaryId', media.diaryId)
        if (media.caption) formData.append('caption', media.caption)
        await workspaceApi.media.upload(spaceId, formData)
        await removeOfflineOperations([media.id])
        synced += 1
      } catch {
        break
      }
    }

    let cursor = await getOfflineMeta(`cursor:${spaceId}`, 0)
    let hasMore = true
    while (hasMore) {
      const response = await workspaceApi.sync.pull(spaceId, cursor, 200)
      cursor = response.data.nextCursor
      hasMore = response.data.hasMore
      if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('workspace:changes', { detail: { spaceId, changes: response.data.changes } }))
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
