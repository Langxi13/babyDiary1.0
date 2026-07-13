import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasServerOrigin, isNativeApp } from '@/platform/runtimeConfig'

const routeComponents = {
  Login: () => import('@/views/auth/Login.vue'),
  Register: () => import('@/views/auth/Register.vue'),
  Home: () => import('@/views/home/Home.vue'),
  Profile: () => import('@/views/auth/Profile.vue'),
  DraftList: () => import('@/views/diary/DraftList.vue'),
  DiaryList: () => import('@/views/diary/DiaryList.vue'),
  Timeline: () => import('@/views/diary/Timeline.vue'),
  DiaryCalendar: () => import('@/views/diary/Calendar.vue'),
  Anniversaries: () => import('@/views/diary/Anniversaries.vue'),
  Album: () => import('@/views/diary/Album.vue'),
  AlbumDetail: () => import('@/views/diary/AlbumDetail.vue'),
  AiReports: () => import('@/views/diary/AiReports.vue'),
  DiaryForm: () => import('@/views/diary/DiaryForm.vue'),
  DiaryDetail: () => import('@/views/diary/DiaryDetail.vue'),
  Workspace: () => import('@/views/workspace/Workspace.vue'),
  SpaceSettings: () => import('@/views/workspace/SpaceSettings.vue'),
  SpaceDiaryDetail: () => import('@/views/workspace/SpaceDiaryDetail.vue'),
  Notifications: () => import('@/views/workspace/Notifications.vue'),
  SharedDiary: () => import('@/views/workspace/SharedDiary.vue'),
  AcceptInvitation: () => import('@/views/workspace/AcceptInvitation.vue'),
  ServerSetup: () => import('@/views/auth/ServerSetup.vue')
}

const routes = [
  {
    path: '/connect-server',
    name: 'ServerSetup',
    component: routeComponents.ServerSetup,
    meta: { requiresAuth: false, nativeOnly: true }
  },
  {
    path: '/shared/:token',
    name: 'SharedDiary',
    component: routeComponents.SharedDiary,
    meta: { requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: routeComponents.Login,
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: routeComponents.Register,
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'Home',
    component: routeComponents.Home,
    meta: { requiresAuth: true, mobileTitle: '首页', mobileTab: 'home' }
  },
  {
    path: '/spaces',
    name: 'Workspace',
    component: routeComponents.Workspace,
    meta: { requiresAuth: true, mobileTitle: '共同空间', mobileTab: 'memory' }
  },
  {
    path: '/spaces/settings',
    name: 'SpaceSettings',
    component: routeComponents.SpaceSettings,
    meta: { requiresAuth: true, mobileTitle: '空间设置', mobileTab: 'account' }
  },
  {
    path: '/spaces/invitations/:token',
    name: 'AcceptInvitation',
    component: routeComponents.AcceptInvitation,
    meta: { requiresAuth: true, mobileTitle: '加入空间', mobileTab: 'memory' }
  },
  {
    path: '/spaces/:spaceId/diaries/:diaryId',
    name: 'SpaceDiaryDetail',
    component: routeComponents.SpaceDiaryDetail,
    meta: { requiresAuth: true, mobileTitle: '共同日记', mobileTab: 'memory' }
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: routeComponents.Notifications,
    meta: { requiresAuth: true, mobileTitle: '通知', mobileTab: 'account' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: routeComponents.Profile,
    meta: { requiresAuth: true, mobileTitle: '我的', mobileTab: 'account' }
  },
  {
    path: '/drafts',
    name: 'DraftList',
    component: routeComponents.DraftList,
    meta: { requiresAuth: true, mobileTitle: '草稿', mobileTab: 'account' }
  },
  {
    path: '/diaries',
    name: 'DiaryList',
    component: routeComponents.DiaryList,
    meta: { requiresAuth: true, mobileTitle: '日记', mobileTab: 'diaries' }
  },
  {
    path: '/timeline',
    name: 'Timeline',
    component: routeComponents.Timeline,
    meta: { requiresAuth: true, mobileTitle: '时间轴', mobileTab: 'memory' }
  },
  {
    path: '/calendar',
    name: 'DiaryCalendar',
    component: routeComponents.DiaryCalendar,
    meta: { requiresAuth: true, mobileTitle: '日历', mobileTab: 'memory' }
  },
  {
    path: '/anniversaries',
    name: 'Anniversaries',
    component: routeComponents.Anniversaries,
    meta: { requiresAuth: true, mobileTitle: '纪念日', mobileTab: 'memory' }
  },
  {
    path: '/album',
    name: 'Album',
    component: routeComponents.Album,
    meta: { requiresAuth: true, mobileTitle: '相册', mobileTab: 'memory' }
  },
  {
    path: '/album/system/:systemKey',
    name: 'AlbumSystemDetail',
    component: routeComponents.AlbumDetail,
    meta: { requiresAuth: true, mobileTitle: '相册详情', mobileTab: 'memory' }
  },
  {
    path: '/album/item/:albumId',
    name: 'AlbumItemDetail',
    component: routeComponents.AlbumDetail,
    meta: { requiresAuth: true, mobileTitle: '相册详情', mobileTab: 'memory' }
  },
  {
    path: '/ai-reports',
    name: 'AiReports',
    component: routeComponents.AiReports,
    meta: { requiresAuth: true, mobileTitle: 'AI 报告', mobileTab: 'account' }
  },
  {
    path: '/diaries/create',
    name: 'DiaryCreate',
    component: routeComponents.DiaryForm,
    meta: { requiresAuth: true, mobileTitle: '写日记', mobileTab: 'write' }
  },
  {
    path: '/diaries/:id',
    name: 'DiaryDetail',
    component: routeComponents.DiaryDetail,
    meta: { requiresAuth: true, mobileTitle: '日记详情', mobileTab: 'diaries' }
  },
  {
    path: '/diaries/:id/edit',
    name: 'DiaryEdit',
    component: routeComponents.DiaryForm,
    meta: { requiresAuth: true, mobileTitle: '编辑日记', mobileTab: 'write' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: isNativeApp() ? createWebHashHistory() : createWebHistory(),
  routes
})

export function preloadRouteComponent(path) {
  const route = router.resolve(path)
  const component = route.matched[route.matched.length - 1]?.components?.default
  if (typeof component === 'function') {
    component()
  }
}

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const hasToken = !!localStorage.getItem('token')
  const hashParams = new URLSearchParams((to.hash || '').replace(/^#/, ''))
  const isPasswordReset = to.path === '/login' && (!!to.query.resetToken || hashParams.has('resetToken'))

  if (isNativeApp() && !hasServerOrigin() && to.path !== '/connect-server') {
    next('/connect-server')
    return
  }
  if ((!isNativeApp() || hasServerOrigin()) && to.path === '/connect-server') {
    next(hasToken ? '/' : '/login')
    return
  }

  if (authStore.isLoggedIn && !hasToken) {
    authStore.clearAuth()
  }
  
  if (to.meta.requiresAuth && !hasToken) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if ((to.path === '/login' || to.path === '/register') && hasToken && !isPasswordReset) {
    next('/')
  } else {
    next()
  }
})

export default router
