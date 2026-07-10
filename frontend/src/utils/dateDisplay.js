const pad2 = (value) => String(value).padStart(2, '0')

const parseDateLike = (value) => {
  if (!value) return null

  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return {
      year: value.getFullYear(),
      month: value.getMonth() + 1,
      day: value.getDate(),
      hour: value.getHours(),
      minute: value.getMinutes()
    }
  }

  const text = String(value)
  const match = text.match(/^(\d{4})-(\d{1,2})(?:-(\d{1,2}))?(?:[T\s](\d{1,2}):(\d{1,2}))?/)
  if (match) {
    return {
      year: Number(match[1]),
      month: Number(match[2]),
      day: match[3] ? Number(match[3]) : null,
      hour: match[4] ? Number(match[4]) : null,
      minute: match[5] ? Number(match[5]) : null
    }
  }

  const date = new Date(value)
  if (!Number.isNaN(date.getTime())) {
    return {
      year: date.getFullYear(),
      month: date.getMonth() + 1,
      day: date.getDate(),
      hour: date.getHours(),
      minute: date.getMinutes()
    }
  }

  return null
}

export function formatChineseDate(value) {
  const parsed = parseDateLike(value)
  if (!parsed?.day) return value || ''
  return `${parsed.year}年${parsed.month}月${parsed.day}日`
}

export function formatChineseMonth(value) {
  const parsed = parseDateLike(value)
  if (!parsed) return value || ''
  return `${parsed.year}年${parsed.month}月`
}

export function formatChineseMonthDay(value) {
  const parsed = parseDateLike(value)
  if (!parsed?.day) return value || ''
  return `${parsed.month}月${parsed.day}日`
}

export function formatChineseDateTime(value) {
  const parsed = parseDateLike(value)
  if (!parsed?.day) return value || ''
  if (parsed.hour === null || parsed.minute === null) {
    return formatChineseDate(value)
  }
  return `${parsed.year}年${parsed.month}月${parsed.day}日 ${pad2(parsed.hour)}:${pad2(parsed.minute)}`
}

export function formatChineseDateRange(start, end) {
  if (!start && !end) return ''
  if (!start) return formatChineseDate(end)
  if (!end) return formatChineseDate(start)
  return `${formatChineseDate(start)} 至 ${formatChineseDate(end)}`
}

export function formatChineseReportPeriod(type, period) {
  if (type === 'MONTHLY') return formatChineseMonth(period)
  const match = String(period || '').match(/^(\d{4})-W0?(\d{1,2})$/)
  if (match) return `${match[1]}年第${Number(match[2])}周`
  return period || ''
}
