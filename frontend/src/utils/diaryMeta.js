export const MOODS = [
  { key: 'happy', label: '开心', emoji: '😊', color: '#ff7a90' },
  { key: 'calm', label: '平静', emoji: '😌', color: '#40b3a2' },
  { key: 'miss', label: '想念', emoji: '💭', color: '#8f7cf6' },
  { key: 'date', label: '约会', emoji: '💕', color: '#f59f35' },
  { key: 'tired', label: '疲惫', emoji: '😴', color: '#7a8aa0' },
  { key: 'special', label: '特别', emoji: '✨', color: '#d14f8f' }
]

export const moodLabel = (key) => {
  const mood = MOODS.find(item => item.key === key)
  return mood ? `${mood.emoji} ${mood.label}` : ''
}
export const moodColor = (key) => MOODS.find(mood => mood.key === key)?.color || '#909399'

export const stripHtml = (html = '') => {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}
