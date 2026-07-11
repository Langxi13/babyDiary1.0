import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NavBar from './NavBar.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn(),
  preload: vi.fn(),
  route: { path: '/timeline' }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    username: '一位名字很长的测试用户',
    userInfo: { avatarPath: '' },
    logout: mocks.logout
  })
}))

vi.mock('@/router', () => ({ preloadRouteComponent: mocks.preload }))

const SlotStub = { template: '<div><slot /></div>' }
const MenuItemStub = {
  props: ['index'],
  template: '<button type="button" :data-index="index"><slot /></button>'
}
const SubMenuStub = {
  props: ['index'],
  template: '<section :data-index="index"><header><slot name="title" /></header><slot /></section>'
}

const mountNav = () => shallowMount(NavBar, {
  global: {
    stubs: {
      ElAvatar: SlotStub,
      ElButton: MenuItemStub,
      ElDropdown: SlotStub,
      ElDropdownItem: MenuItemStub,
      ElDropdownMenu: SlotStub,
      ElIcon: { template: '<span><slot /></span>' },
      ElMenu: SlotStub,
      ElMenuItem: MenuItemStub,
      ElSubMenu: SubMenuStub,
      RouterLink: { template: '<a><slot /></a>' },
      SpaceSwitcher: true
    }
  }
})

describe('desktop navigation', () => {
  beforeEach(() => {
    mocks.push.mockReset()
    mocks.logout.mockReset()
    mocks.preload.mockReset()
    mocks.route.path = '/timeline'
  })

  it('renders five primary destinations and keeps secondary destinations under more', () => {
    const wrapper = mountNav()
    expect(wrapper.findAll('.desktop-primary-item')).toHaveLength(5)
    expect(wrapper.findAll('.desktop-primary-item').map(item => item.text())).toEqual([
      '首页', '日记', '空间', '相册', '写日记'
    ])
    expect(wrapper.find('.desktop-more-menu').text()).toContain('更多')
    expect(wrapper.findAll('.desktop-more-item').map(item => item.text())).toEqual([
      '时间轴', '日历', '纪念日', 'AI 报告', '草稿'
    ])
  })

  it('marks the more menu active for a secondary route', () => {
    const wrapper = mountNav()
    expect(wrapper.find('.desktop-more-menu').classes()).toContain('is-route-active')
  })
})
