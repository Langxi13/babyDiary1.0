import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyPendingDiaryOperations,
  chunkOperations,
  mergeQueuedDiaryOperation
} from './offlineQueue.js'

test('offline sync operations are split to the backend batch limit', () => {
  const operations = Array.from({ length: 205 }, (_, index) => ({ id: String(index) }))
  const chunks = chunkOperations(operations)

  assert.deepEqual(chunks.map(chunk => chunk.length), [100, 100, 5])
})

test('editing an offline create replaces its payload instead of adding an update', () => {
  const previous = { id: 'one', action: 'CREATE', payload: { title: '旧标题' }, createdAt: 1 }
  const next = { id: 'two', action: 'UPDATE', payload: { title: '新标题' }, createdAt: 2 }

  const result = mergeQueuedDiaryOperation(previous, next)

  assert.equal(result.action, 'replace')
  assert.equal(result.operation.id, 'one')
  assert.equal(result.operation.action, 'CREATE')
  assert.equal(result.operation.payload.title, '新标题')
})

test('deleting a not-yet-synced create cancels the local create', () => {
  const result = mergeQueuedDiaryOperation(
    { id: 'one', action: 'CREATE' },
    { id: 'two', action: 'DELETE' }
  )

  assert.equal(result.action, 'cancel-create')
})

test('pending diary operations remain visible after cached data is reloaded', () => {
  const diaries = [{ publicId: 'existing', title: '线上标题', version: 3 }]
  const operations = [
    { kind: 'diary', entityId: 'existing', action: 'UPDATE', payload: { title: '本地标题' }, baseVersion: 3 },
    { kind: 'diary', entityId: 'new-entry', action: 'CREATE', payload: { title: '离线新日记' }, baseVersion: null }
  ]

  const merged = applyPendingDiaryOperations(diaries, operations)

  assert.equal(merged.find(item => item.publicId === 'existing').title, '本地标题')
  assert.equal(merged.find(item => item.publicId === 'new-entry').pendingAction, 'CREATE')
})
