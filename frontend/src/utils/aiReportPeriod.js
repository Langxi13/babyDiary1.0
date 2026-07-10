const pad2 = (value) => String(value).padStart(2, '0')

const startOfDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate())

export function formatMonthlyPeriod(date = new Date()) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
}

export function formatLocalDate(date = new Date()) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function formatWeeklyPeriod(date = new Date()) {
  const current = startOfDay(date)
  const day = current.getDay() || 7
  current.setDate(current.getDate() + 4 - day)
  const yearStart = new Date(current.getFullYear(), 0, 1)
  const week = Math.ceil((((current - yearStart) / 86400000) + 1) / 7)
  return `${current.getFullYear()}-W${pad2(week)}`
}
