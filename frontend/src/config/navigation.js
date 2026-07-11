export const APP_NAVIGATION_ITEMS = Object.freeze([
  { id: 'home', label: '首页', path: '/', icon: 'HomeFilled' },
  { id: 'diaries', label: '日记', path: '/diaries', icon: 'Document' },
  { id: 'spaces', label: '空间', path: '/spaces', icon: 'Connection' },
  { id: 'timeline', label: '时间轴', path: '/timeline', icon: 'Clock' },
  { id: 'calendar', label: '日历', path: '/calendar', icon: 'Calendar' },
  { id: 'anniversaries', label: '纪念日', path: '/anniversaries', icon: 'Star' },
  { id: 'album', label: '相册', path: '/album', icon: 'Picture' },
  { id: 'aiReports', label: 'AI 报告', path: '/ai-reports', icon: 'MagicStick' },
  { id: 'drafts', label: '草稿', path: '/drafts', icon: 'Tickets' },
  { id: 'write', label: '写日记', path: '/diaries/create', icon: 'Edit' },
  { id: 'profile', label: '个人信息', path: '/profile', icon: 'User' },
  { id: 'notifications', label: '通知', path: '/notifications', icon: 'Bell' }
])

const navigationById = new Map(APP_NAVIGATION_ITEMS.map(item => [item.id, item]))

export const navigationItems = (...ids) => ids.map(id => navigationById.get(id)).filter(Boolean)

export const DESKTOP_PRIMARY_NAVIGATION = navigationItems('home', 'diaries', 'spaces', 'album', 'write')
export const DESKTOP_MORE_NAVIGATION = navigationItems('timeline', 'calendar', 'anniversaries', 'aiReports', 'drafts')
export const DESKTOP_COMPACT_NAVIGATION = [...DESKTOP_PRIMARY_NAVIGATION, ...DESKTOP_MORE_NAVIGATION]
