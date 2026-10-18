import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { apiGet, apiPut, auth, route, routerPush } = vi.hoisted(() => ({
  apiGet: vi.fn(), apiPut: vi.fn(), auth: { user: { id: 1 }, ready: true },
  route: { params: { id: '12' }, fullPath: '/items/12/edit' }, routerPush: vi.fn(),
}))

vi.mock('../../src/api', () => ({
  api: { get: apiGet, put: apiPut },
  messageOf: (error) => error.response?.data?.message || '保存失败',
}))
vi.mock('../../src/auth', () => ({ auth, initializeAuth: () => Promise.resolve() }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: routerPush }),
}))

import EditItemView from '../../src/views/EditItemView.vue'

describe('EditItemView', () => {
  beforeEach(() => {
    apiGet.mockReset()
    apiPut.mockReset()
    routerPush.mockReset()
    auth.user = { id: 1 }
    apiGet.mockImplementation((url) => url.includes('/detail/')
      ? Promise.resolve({ data: {
        id: 12, title: '台灯', description: '正常使用', category: '日用', tradeType: 'FREE',
        price: 0, pickupLocation: '社区门口', status: 'AVAILABLE', version: 4,
        publisher: { id: 1 },
      } })
      : Promise.resolve({ data: [{
        id: 8, startAt: '2026-10-20T10:00:00', endAt: '2026-10-20T11:00:00',
      }] }))
  })

  it('sends the loaded version and prevents duplicate saves', async () => {
    let resolveSave
    apiPut.mockReturnValue(new Promise((resolve) => { resolveSave = resolve }))
    const wrapper = mount(EditItemView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(apiPut).toHaveBeenCalledTimes(1)
    expect(apiPut.mock.calls[0][1].expectedVersion).toBe(4)

    resolveSave({ data: { version: 5 } })
    await flushPromises()
    expect(wrapper.text()).toContain('修改已保存')
  })

  it('shows a server conflict without discarding the form', async () => {
    apiPut.mockRejectedValue({ response: { data: { message: '物品已被修改，请刷新后重试' } } })
    const wrapper = mount(EditItemView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('请刷新后重试')
    expect(wrapper.get('input').element.value).toBe('台灯')
  })
})
