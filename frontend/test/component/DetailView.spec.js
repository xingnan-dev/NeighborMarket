import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const { apiGet, auth, route } = vi.hoisted(() => ({
  apiGet: vi.fn(),
  auth: { user: null, ready: true },
  route: { params: { id: '12' } }
}))

vi.mock('../../src/api', () => ({
  api: { get: apiGet, post: vi.fn(), delete: vi.fn() },
  messageOf: () => '加载失败'
}))
vi.mock('../../src/auth', () => ({ auth, initializeAuth: () => Promise.resolve() }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn() })
}))

import DetailView from '../../src/views/DetailView.vue'

const item = {
  id: 12,
  title: '闲置台灯',
  description: '正常使用',
  category: '日用',
  tradeType: 'FREE',
  status: 'AVAILABLE',
  imageUrl: '/demo/lamp.jpg',
  publishedAt: '2026-10-14T10:00:00',
  lastConfirmedAt: '2026-10-14T10:00:00',
  pickupLocation: '社区服务中心',
  publisher: { id: 1, nickname: '邻居甲' }
}

function responseFor(url) {
  if (url.includes('/detail/')) return Promise.resolve({ data: item })
  if (url.endsWith('/slots')) return Promise.resolve({ data: [{ id: 8, startAt: '2026-10-20T10:00:00', endAt: '2026-10-20T11:00:00' }] })
  if (url.endsWith('/comments')) return Promise.resolve({ data: [] })
  if (url.endsWith('/interests')) return Promise.resolve({ data: [] })
  return Promise.resolve({ data: null })
}

describe('DetailView permissions', () => {
  beforeEach(() => {
    auth.user = null
    apiGet.mockReset()
    apiGet.mockImplementation(responseFor)
  })

  it('shows a login path instead of recipient actions to an anonymous visitor', async () => {
    const wrapper = mount(DetailView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()

    expect(wrapper.text()).toContain('登录后选择时段并表达想要')
    expect(wrapper.text()).not.toContain('我想要')
    expect(apiGet.mock.calls.some(([url]) => url.endsWith('/interests/mine'))).toBe(false)
  })

  it('loads the interest list only for the publisher', async () => {
    auth.user = { id: 1, username: 'neighbor_a' }
    const wrapper = mount(DetailView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()

    expect(wrapper.text()).toContain('意向名单')
    expect(wrapper.text()).toContain('确认还在')
    expect(apiGet.mock.calls.some(([url]) => url.endsWith('/interests'))).toBe(true)
    expect(apiGet.mock.calls.some(([url]) => url.endsWith('/interests/mine'))).toBe(false)
  })
})
