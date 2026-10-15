import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('../../src/api', () => ({
  api: { post: vi.fn() },
  messageOf: () => '发布失败'
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import PublishView from '../../src/views/PublishView.vue'

describe('PublishView validation', () => {
  it('blocks an incomplete publish before sending an API request', async () => {
    const wrapper = mount(PublishView)

    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toBe('请完整填写物品与公共交接地点')
  })
})
