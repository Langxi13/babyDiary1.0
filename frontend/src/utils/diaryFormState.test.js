import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_TAG_COLORS,
  buildCreateDraftKey,
  draftKeyFromRoute,
  formatLocalDate,
  isDraftEntryRoute,
  nextTagColor
} from './diaryFormState.js'
import { MOODS, moodLabel } from './diaryMeta.js'

test('formatLocalDate uses the local calendar day', () => {
  assert.equal(formatLocalDate(new Date(2026, 6, 3, 1, 2, 3)), '2026-07-03')
})

test('normal create route gets a fresh draft key but does not load a draft', () => {
  const route = { name: 'DiaryCreate', query: {} }

  assert.equal(isDraftEntryRoute(route), false)
  assert.match(draftKeyFromRoute(route, 'create-1783090000000'), /^create-\d+$/)
})

test('draft routes load the selected draft key', () => {
  const route = { name: 'DiaryCreate', query: { draftKey: 'create-1783090000000' } }

  assert.equal(isDraftEntryRoute(route), true)
  assert.equal(draftKeyFromRoute(route, 'ignored'), 'create-1783090000000')
})

test('new create draft keys are unique and readable', () => {
  assert.equal(buildCreateDraftKey(1783090000000), 'create-1783090000000')
})

test('default tag colors are varied and cycle by existing tag count', () => {
  assert.ok(DEFAULT_TAG_COLORS.length >= 8)
  assert.equal(nextTagColor(0), DEFAULT_TAG_COLORS[0])
  assert.equal(nextTagColor(DEFAULT_TAG_COLORS.length), DEFAULT_TAG_COLORS[0])
})

test('mood labels include emoji symbols', () => {
  assert.ok(MOODS.every(mood => mood.emoji))
  assert.equal(moodLabel('happy'), '😊 开心')
  assert.equal(moodLabel('tired'), '😴 疲惫')
})
