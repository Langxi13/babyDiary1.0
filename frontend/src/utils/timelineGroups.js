const WEEKLY_THRESHOLD = 8

export const timelineKey = (type, primary, secondary = '') => {
  return secondary ? `${type}:${primary}:${secondary}` : `${type}:${primary}`
}

const photoCount = diary => diary.media?.filter(item => item.mediaType === 'IMAGE').length || 0
const countPhotos = (diaries = []) => diaries.reduce((total, diary) => total + photoCount(diary), 0)

const monthOf = (date = '') => date.slice(0, 7)

const weekIndexOfMonth = (date = '') => {
  const [, , dayText] = date.split('-')
  const day = Number(dayText || 1)
  return Math.max(1, Math.ceil(day / 7))
}

const buildWeeks = (month, diaries) => {
  const weeksByIndex = new Map()
  for (const diary of diaries) {
    const weekIndex = weekIndexOfMonth(diary.diaryDate)
    const label = `第${weekIndex}周`
    if (!weeksByIndex.has(weekIndex)) {
      weeksByIndex.set(weekIndex, {
        key: timelineKey('week', month, label),
        label,
        diaries: [],
        diaryCount: 0,
        photoCount: 0
      })
    }
    const week = weeksByIndex.get(weekIndex)
    week.diaries.push(diary)
    week.diaryCount += 1
    week.photoCount += photoCount(diary)
  }
  return [...weeksByIndex.entries()]
    .sort(([left], [right]) => right - left)
    .map(([, week]) => week)
}

export const buildTimelineTree = (groups = [], options = {}) => {
  const weeklyThreshold = options.weeklyThreshold || WEEKLY_THRESHOLD
  const years = new Map()

  for (const group of groups) {
    const month = group.month || monthOf(group.diaries?.[0]?.diaryDate)
    if (!month) continue

    const year = month.slice(0, 4)
    const diaries = group.diaries || []
    if (!years.has(year)) {
      years.set(year, {
        key: timelineKey('year', year),
        year,
        months: [],
        diaryCount: 0,
        photoCount: 0
      })
    }

    const yearNode = years.get(year)
    const diaryCount = Number(group.diaryCount ?? diaries.length)
    const mediaCount = Number(group.mediaCount ?? countPhotos(diaries))
    const usesWeeks = diaryCount >= weeklyThreshold && diaries.length > 0
    const monthNode = {
      key: timelineKey('month', month),
      month,
      year,
      diaries,
      weeks: usesWeeks ? buildWeeks(month, diaries) : [],
      usesWeeks,
      diaryCount,
      photoCount: mediaCount,
      loaded: !!group.loaded,
      nextCursor: group.nextCursor || null
    }

    yearNode.months.push(monthNode)
    yearNode.diaryCount += monthNode.diaryCount
    yearNode.photoCount += monthNode.photoCount
  }

  return [...years.values()]
    .sort((left, right) => right.year.localeCompare(left.year))
    .map(year => ({
      ...year,
      months: year.months.sort((left, right) => right.month.localeCompare(left.month))
    }))
}

export const initialExpandedTimelineKeys = (tree = []) => {
  const year = tree[0]
  const month = year?.months?.find(item => item.loaded) || year?.months?.[0]
  const keys = year ? [year.key] : []
  if (month) keys.push(month.key, ...month.weeks.map(week => week.key))
  return keys
}
