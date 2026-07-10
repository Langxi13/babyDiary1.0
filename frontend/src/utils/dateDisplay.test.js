import assert from 'node:assert/strict'
import test from 'node:test'

import {
  formatChineseDate,
  formatChineseDateRange,
  formatChineseDateTime,
  formatChineseMonth,
  formatChineseMonthDay,
  formatChineseReportPeriod
} from './dateDisplay.js'

test('formatChineseDate renders yyyy-MM-dd as Chinese date text', () => {
  assert.equal(formatChineseDate('2026-07-06'), '2026年7月6日')
})

test('formatChineseMonth renders yyyy-MM as Chinese month text', () => {
  assert.equal(formatChineseMonth('2026-07'), '2026年7月')
})

test('formatChineseMonthDay renders yyyy-MM-dd as Chinese month-day text', () => {
  assert.equal(formatChineseMonthDay('2026-07-06'), '7月6日')
})

test('formatChineseDateTime renders timestamps without English date tokens', () => {
  assert.equal(formatChineseDateTime('2026-07-06T02:27:49'), '2026年7月6日 02:27')
})

test('formatChineseDateRange renders Chinese range text', () => {
  assert.equal(formatChineseDateRange('2026-07-01', '2026-07-06'), '2026年7月1日 至 2026年7月6日')
})

test('formatChineseReportPeriod renders weekly and monthly periods in Chinese', () => {
  assert.equal(formatChineseReportPeriod('WEEKLY', '2026-W27'), '2026年第27周')
  assert.equal(formatChineseReportPeriod('MONTHLY', '2026-07'), '2026年7月')
})
