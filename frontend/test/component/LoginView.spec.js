import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const { apiPost, routerPush, route, auth } = vi.hoisted(() => ({
  apiPost: vi.fn(),
  routerPush: vi.fn(),
  route: { query: {} },
  auth: { user: null, ready: true }
}))

vi.mock('../../src/api', () => ({
  api: { post: apiPost },
  messageOf: (error) => error?.response?.data?.message || '连接失败，请稍后重试'
}))

vi.mock('../../src/auth', () => ({ auth }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => route
}))

import LoginView from '../../src/views/LoginView.vue'

describe('LoginView', () => {
  beforeEach(() => {
    apiPost.mockReset()
    routerPush.mockReset()
    auth.user = null
    route.query = {}
  })

  it('validates missing credentials without sending a request', async () => {
    const wrapper = mount(LoginView)

    await wrapper.find('form').trigger('submit')

    expect(wrapper.get('[role="alert"], .error').text()).toBe('请输入账号和密码')
    expect(apiPost).not.toHaveBeenCalled()
  })

  it('disables duplicate submission and follows a safe redirect after login', async () => {
    let resolveLogin
    apiPost.mockReturnValue(new Promise((resolve) => { resolveLogin = resolve }))
    route.query = { redirect: '/mine' }
    const wrapper = mount(LoginView)
    await wrapper.get('input[type="password"]').setValue('correct-password')

    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')

    expect(apiPost).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').text()).toBe('登录中…')

    resolveLogin({ data: { id: 7, username: 'neighbor_a' } })
    await flushPromises()

    expect(auth.user.id).toBe(7)
    expect(routerPush).toHaveBeenCalledWith('/mine')
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
  })

  it('shows the API error and rejects an external redirect target', async () => {
    apiPost.mockRejectedValueOnce({ response: { data: { message: '账号或密码错误' } } })
    const wrapper = mount(LoginView)
    await wrapper.get('input[type="password"]').setValue('wrong-password')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.error').text()).toBe('账号或密码错误')
    expect(routerPush).not.toHaveBeenCalled()

    route.query = { redirect: 'https://evil.example' }
    apiPost.mockResolvedValueOnce({ data: { id: 7 } })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(routerPush).toHaveBeenLastCalledWith('/')
  })
})
