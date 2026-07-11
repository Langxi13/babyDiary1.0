import { navigationItems } from '../../config/navigation.js'

export const MOBILE_BREAKPOINT = 768

export const MOBILE_PRIMARY_TABS = [
  { label: '首页', path: '/', match: '/', icon: 'HomeFilled' },
  { label: '日记', path: '/diaries', match: '/diaries', icon: 'Document' },
  { label: '写日记', path: '/diaries/create', match: '/diaries/create', icon: 'Edit', primary: true },
  { label: '回忆', path: '/album', match: 'memory', icon: 'Picture' },
  { label: '我的', path: '/profile', match: 'account', icon: 'User' }
]

const memoryLinks = navigationItems('spaces', 'album', 'timeline', 'calendar', 'anniversaries')
const accountLinks = navigationItems('aiReports', 'drafts', 'profile', 'notifications')

export const MOBILE_SECONDARY_LINKS = [
  ...memoryLinks.map(item => ({ ...item, label: item.id === 'spaces' ? '共同空间' : item.label, group: 'memory', icon: item.id === 'anniversaries' ? 'Trophy' : item.icon })),
  ...accountLinks.map(item => ({ ...item, group: 'account' }))
]

const routeTitles = new Map([
  ['/', '首页'],
  ['/profile', '我的'],
  ['/drafts', '草稿'],
  ['/diaries', '日记'],
  ['/timeline', '时间轴'],
  ['/calendar', '日历'],
  ['/anniversaries', '纪念日'],
  ['/album', '相册'],
  ['/ai-reports', 'AI 报告'],
  ['/diaries/create', '写日记'],
  ['/spaces', '共同空间'],
  ['/spaces/settings', '空间设置'],
  ['/notifications', '通知']
])

export const getMobileRouteTitle = (path) => {
  if (/^\/diaries\/[^/]+\/edit$/.test(path)) return '编辑日记'
  if (/^\/diaries\/[^/]+$/.test(path)) return '日记详情'
  if (/^\/album\/.+/.test(path)) return '相册详情'
  return routeTitles.get(path) || 'Baby Diary'
}

export const mobileRouteGroup = (path) => {
  if (path === '/album' || path.startsWith('/album/') || ['/timeline', '/calendar', '/anniversaries'].includes(path)) return 'memory'
  if (['/ai-reports', '/drafts', '/profile', '/notifications', '/spaces/settings'].includes(path)) return 'account'
  if (path === '/spaces' || path.startsWith('/spaces/')) return 'memory'
  return ''
}

export const isPrimaryMobileTab = (path) => MOBILE_PRIMARY_TABS.some(tab => tab.path === path)

export const isMobileTabActive = (tab, path) => {
  if (tab.match === '/') return path === '/'
  if (tab.match === 'memory') return mobileRouteGroup(path) === 'memory'
  if (tab.match === 'account') return mobileRouteGroup(path) === 'account'
  if (tab.path === '/diaries/create') return path === '/diaries/create' || /^\/diaries\/[^/]+\/edit$/.test(path)
  if (tab.path === '/diaries') return path === '/diaries' || /^\/diaries\/[^/]+$/.test(path)
  return path === tab.path
}
