import test from 'node:test'
import assert from 'node:assert/strict'

import { formatLocalDate, formatMonthlyPeriod, formatWeeklyPeriod } from './aiReportPeriod.js'

test('formatWeeklyPeriod uses ISO-like week label', () => {
  assert.equal(formatWeeklyPeriod(new Date(2026, 6, 1)), '2026-W27')
})

test('formatMonthlyPeriod uses yyyy-MM', () => {
  assert.equal(formatMonthlyPeriod(new Date(2026, 6, 1)), '2026-07')
})

test('formatLocalDate uses local calendar date', () => {
  assert.equal(formatLocalDate(new Date(2026, 6, 4, 0, 30)), '2026-07-04')
})
