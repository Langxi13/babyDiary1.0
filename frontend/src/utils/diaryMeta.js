export const MOODS = [
  { key: 'happy', label: '开心', emoji: '😊', color: '#9f304d' },
  { key: 'calm', label: '平静', emoji: '😌', color: '#287367' },
  { key: 'miss', label: '想念', emoji: '💭', color: '#5945a8' },
  { key: 'date', label: '约会', emoji: '💕', color: '#935608' },
  { key: 'tired', label: '疲惫', emoji: '😴', color: '#536477' },
  { key: 'special', label: '特别', emoji: '✨', color: '#9d2f68' }
]

export const moodLabel = (key) => {
  const mood = MOODS.find(item => item.key === key)
  return mood ? `${mood.emoji} ${mood.label}` : ''
}
export const moodColor = (key) => MOODS.find(mood => mood.key === key)?.color || '#5f6368'

export const stripHtml = (html = '') => {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}
