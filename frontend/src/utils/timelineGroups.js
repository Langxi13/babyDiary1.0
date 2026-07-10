const WEEKLY_THRESHOLD = 8

export const timelineKey = (type, primary, secondary = '') => {
  return secondary ? `${type}:${primary}:${secondary}` : `${type}:${primary}`
}

const countPhotos = (diaries = []) => diaries.reduce((total, diary) => total + (diary.imagePathList?.length || 0), 0)

const monthOf = (date = '') => date.slice(0, 7)

const weekIndexOfMonth = (date = '') => {
  const [, , dayText] = date.split('-')
  const day = Number(dayText || 1)
  return Math.max(1, Math.ceil(day / 7))
}

const buildWeeks = (month, diaries) => {
  const weeksByIndex = new Map()
  for (const diary of diaries) {
    const weekIndex = weekIndexOfMonth(diary.date)
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
    week.photoCount += diary.imagePathList?.length || 0
  }
  return [...weeksByIndex.entries()]
    .sort(([left], [right]) => right - left)
    .map(([, week]) => week)
}

export const buildTimelineTree = (groups = [], options = {}) => {
  const weeklyThreshold = options.weeklyThreshold || WEEKLY_THRESHOLD
  const years = new Map()

  for (const group of groups) {
    const month = group.month || monthOf(group.diaries?.[0]?.date)
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
    const usesWeeks = diaries.length >= weeklyThreshold
    const monthNode = {
      key: timelineKey('month', month),
      month,
      year,
      diaries,
      weeks: usesWeeks ? buildWeeks(month, diaries) : [],
      usesWeeks,
      diaryCount: diaries.length,
      photoCount: countPhotos(diaries)
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
  const keys = []
  for (const year of tree) {
    keys.push(year.key)
    for (const month of year.months) {
      keys.push(month.key)
      for (const week of month.weeks) {
        keys.push(week.key)
      }
    }
  }
  return keys
}
