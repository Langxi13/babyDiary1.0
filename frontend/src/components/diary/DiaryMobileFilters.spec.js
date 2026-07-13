import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DiaryMobileFilters from './DiaryMobileFilters.vue'

const mountFilters = (props = {}) => shallowMount(DiaryMobileFilters, {
  props: {
    keyword: '',
    startDate: '',
    endDate: '',
    tagId: null,
    moodKey: '',
    tags: [{ tagId: 7, name: '旅行', color: '#2f8f83' }],
    moods: [{ key: 'happy', emoji: '😊', label: '开心' }],
    ...props
  },
  global: {
    stubs: {
      ElButton: { template: '<button type="button"><slot /></button>' },
      ElDatePicker: true,
      ElIcon: { template: '<span><slot /></span>' },
      ElInput: true
    }
  }
})

describe('DiaryMobileFilters', () => {
  it('keeps advanced filters collapsed until their disclosure is pressed', async () => {
    const wrapper = mountFilters()
    expect(wrapper.find('.mobile-filter-panel').exists()).toBe(false)

    const tagTrigger = wrapper.findAll('.mobile-filter-trigger')[1]
    await tagTrigger.trigger('click')

    expect(tagTrigger.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.mobile-filter-panel').text()).toContain('按标签筛选')
  })

  it('applies a tag immediately and closes the filter panel', async () => {
    const wrapper = mountFilters()
    await wrapper.findAll('.mobile-filter-trigger')[1].trigger('click')
    await wrapper.findAll('.tag-choices button')[1].trigger('click')

    expect(wrapper.emitted('update:tagId')).toEqual([[7]])
    expect(wrapper.emitted('filter')).toHaveLength(1)
    expect(wrapper.find('.mobile-filter-panel').exists()).toBe(false)
  })
})
