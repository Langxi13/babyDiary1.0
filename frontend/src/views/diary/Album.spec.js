import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Album from './Album.vue'

const mocks = vi.hoisted(() => ({
  getGroups: vi.fn(),
  push: vi.fn()
}))

vi.mock('@/api/album', () => ({
  albumApi: {
    getGroups: mocks.getGroups,
    createGroup: vi.fn(),
    updateGroup: vi.fn(),
    deleteGroup: vi.fn(),
    createAlbum: vi.fn(),
    updateAlbum: vi.fn(),
    deleteAlbum: vi.fn(),
    generateProposal: vi.fn(),
    updateProposal: vi.fn(),
    confirmProposal: vi.fn(),
    discardProposal: vi.fn()
  }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push })
}))

const ButtonStub = {
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>'
}

const mountAlbum = () => shallowMount(Album, {
  global: {
    directives: { loading: () => {} },
    stubs: {
      ElButton: ButtonStub,
      ElIcon: { template: '<span><slot /></span>' },
      ElDatePicker: true,
      ElDialog: true,
      ElEmpty: true,
      ElForm: true,
      ElFormItem: true,
      ElInput: true,
      ElTag: true
    }
  }
})

describe('Album loading states', () => {
  beforeEach(() => {
    mocks.getGroups.mockReset()
    mocks.push.mockReset()
  })

  it('shows an inline error and retries without an unhandled rejection', async () => {
    mocks.getGroups.mockRejectedValueOnce(new Error('HTTP 500'))
    const wrapper = mountAlbum()
    await flushPromises()

    expect(wrapper.find('.album-load-error').exists()).toBe(true)
    expect(wrapper.text()).toContain('相册暂时无法加载')

    mocks.getGroups.mockResolvedValueOnce({ data: [] })
    await wrapper.find('.album-load-error button').trigger('click')
    await flushPromises()

    expect(mocks.getGroups).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.album-load-error').exists()).toBe(false)
  })

  it('selects the first group returned by the API', async () => {
    mocks.getGroups.mockResolvedValueOnce({
      data: [{ groupId: 1, type: 'SYSTEM', name: '默认相册', editable: false, albums: [] }]
    })
    const wrapper = mountAlbum()
    await flushPromises()

    expect(wrapper.text()).toContain('默认相册')
    expect(wrapper.find('.album-load-error').exists()).toBe(false)
  })
})
