export const SYNC_BATCH_SIZE = 100

export function chunkOperations(items, size = SYNC_BATCH_SIZE) {
  if (!Array.isArray(items) || !items.length) return []
  const normalizedSize = Math.max(1, size)
  const chunks = []
  for (let index = 0; index < items.length; index += normalizedSize) {
    chunks.push(items.slice(index, index + normalizedSize))
  }
  return chunks
}

export function mergeQueuedDiaryOperation(previous, next) {
  if (!previous) return { action: 'append', operation: next }

  if (previous.action === 'CREATE' && next.action === 'UPDATE') {
    return {
      action: 'replace',
      operation: { ...previous, payload: next.payload, localSnapshot: next.localSnapshot || previous.localSnapshot }
    }
  }
  if (previous.action === 'UPDATE' && next.action === 'UPDATE') {
    return {
      action: 'replace',
      operation: { ...previous, payload: next.payload, localSnapshot: next.localSnapshot || previous.localSnapshot }
    }
  }
  if (previous.action === 'CREATE' && next.action === 'DELETE') return { action: 'cancel-create' }
  if (previous.action === 'UPDATE' && next.action === 'DELETE') {
    return {
      action: 'replace',
      operation: { ...previous, action: 'DELETE', payload: null, localSnapshot: next.localSnapshot }
    }
  }
  if (previous.action === 'DELETE' && next.action === 'RESTORE') return { action: 'remove-previous' }
  return { action: 'append', operation: next }
}

export function applyPendingDiaryOperations(diaries, operations, trash = false) {
  const result = new Map((diaries || []).map(diary => [diary.publicId, { ...diary }]))
  for (const operation of (operations || []).filter(item => item.kind === 'diary')) {
    const existing = result.get(operation.entityId)
    if (operation.action === 'CREATE' || operation.action === 'UPDATE') {
      if (trash) {
        result.delete(operation.entityId)
        continue
      }
      result.set(operation.entityId, {
        ...existing,
        ...operation.payload,
        publicId: operation.entityId,
        version: existing?.version ?? operation.baseVersion ?? 0,
        pending: true,
        pendingAction: operation.action
      })
    } else if (operation.action === 'DELETE') {
      if (trash) {
        result.set(operation.entityId, {
          ...existing,
          ...operation.localSnapshot,
          publicId: operation.entityId,
          pending: true,
          pendingAction: 'DELETE'
        })
      } else {
        result.delete(operation.entityId)
      }
    } else if (operation.action === 'RESTORE') {
      if (trash) result.delete(operation.entityId)
      else if (operation.localSnapshot) {
        result.set(operation.entityId, { ...operation.localSnapshot, pending: true, pendingAction: 'RESTORE' })
      }
    }
  }
  return Array.from(result.values())
}
