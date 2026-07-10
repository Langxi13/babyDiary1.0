import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildTimelineTree,
  initialExpandedTimelineKeys,
  timelineKey
} from './timelineGroups.js'

const diary = (id, date, imageCount = 0) => ({
  diaryId: id,
  date,
  title: `日记 ${id}`,
  imagePathList: Array.from({ length: imageCount }, (_, index) => `image-${id}-${index}.jpg`)
})

test('buildTimelineTree groups months under years and counts diaries and photos', () => {
  const tree = buildTimelineTree([
    { month: '2026-07', diaries: [diary(1, '2026-07-02', 2), diary(2, '2026-07-09', 1)] },
    { month: '2025-12', diaries: [diary(3, '2025-12-31', 3)] }
  ])

  assert.deepEqual(tree.map(year => year.year), ['2026', '2025'])
  assert.equal(tree[0].diaryCount, 2)
  assert.equal(tree[0].photoCount, 3)
  assert.equal(tree[1].months[0].month, '2025-12')
  assert.equal(tree[1].months[0].diaryCount, 1)
})

test('dense months are grouped into week sections while sparse months keep direct diaries', () => {
  const denseDiaries = [
    diary(1, '2026-07-01'),
    diary(2, '2026-07-02'),
    diary(3, '2026-07-07'),
    diary(4, '2026-07-08'),
    diary(5, '2026-07-14'),
    diary(6, '2026-07-15'),
    diary(7, '2026-07-22'),
    diary(8, '2026-07-29')
  ]
  const tree = buildTimelineTree([
    { month: '2026-07', diaries: denseDiaries },
    { month: '2026-06', diaries: [diary(9, '2026-06-03'), diary(10, '2026-06-04')] }
  ], { weeklyThreshold: 8 })

  const denseMonth = tree[0].months[0]
  const sparseMonth = tree[0].months[1]

  assert.equal(denseMonth.usesWeeks, true)
  assert.deepEqual(denseMonth.weeks.map(week => week.label), ['第5周', '第4周', '第3周', '第2周', '第1周'])
  assert.deepEqual(denseMonth.weeks.map(week => week.diaryCount), [1, 1, 1, 2, 3])
  assert.equal(sparseMonth.usesWeeks, false)
  assert.equal(sparseMonth.diaries.length, 2)
})

test('initialExpandedTimelineKeys opens every year month and week by default', () => {
  const tree = buildTimelineTree([
    {
      month: '2026-07',
      diaries: Array.from({ length: 8 }, (_, index) => diary(index + 1, `2026-07-${String(index + 1).padStart(2, '0')}`))
    },
    { month: '2026-06', diaries: [diary(10, '2026-06-01')] },
    { month: '2025-12', diaries: [diary(20, '2025-12-01')] }
  ])

  const keys = initialExpandedTimelineKeys(tree)

  assert.deepEqual(keys, [
    timelineKey('year', '2026'),
    timelineKey('month', '2026-07'),
    timelineKey('week', '2026-07', '第2周'),
    timelineKey('week', '2026-07', '第1周'),
    timelineKey('month', '2026-06'),
    timelineKey('year', '2025'),
    timelineKey('month', '2025-12')
  ])
})
