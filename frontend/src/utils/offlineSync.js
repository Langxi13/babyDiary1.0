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
    const mediaByDiary = new Map()
    for (const item of mediaOperations) {
      const group = mediaByDiary.get(item.diaryId) || []
      group.push(item)
      mediaByDiary.set(item.diaryId, group)
    }
    for (const [diaryId, items] of mediaByDiary) {
      const uploaded = []
      try {
        for (const media of items) {
          const formData = new FormData()
          formData.append('file', media.file, media.filename || 'media')
          if (media.caption) formData.append('caption', media.caption)
          const response = await workspaceApi.media.upload(spaceId, formData)
          uploaded.push(response.data.assetId)
        }
        const current = (await workspaceApi.diaries.get(spaceId, diaryId)).data
        const mediaIds = [
          ...(current.media || []).map(item => item.assetId).filter(Boolean),
          ...uploaded
        ]
        await workspaceApi.diaries.update(spaceId, diaryId, { ...current, mediaIds })
        await removeOfflineOperations(items.map(item => item.id))
        synced += items.length
      } catch {
        await Promise.allSettled(uploaded.map(assetId => workspaceApi.media.remove(spaceId, assetId)))
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
