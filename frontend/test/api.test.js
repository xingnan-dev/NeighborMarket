import test from 'node:test'
import assert from 'node:assert/strict'
import { api, messageOf, setUnauthorizedHandler } from '../src/api.js'

test('prefers the stable API message and supports problem detail fallback', () => {
  assert.equal(messageOf({ response: { data: { message: '业务冲突' } } }), '业务冲突')
  assert.equal(messageOf({ response: { data: { detail: '旧格式错误' } } }), '旧格式错误')
  assert.equal(messageOf({ response: { data: {} } }), '请求失败，请稍后重试')
  assert.equal(messageOf({}), '连接失败，请稍后重试')
})

test('401 interceptor invokes redirect handler unless explicitly suppressed', async () => {
  let redirects = 0
  setUnauthorizedHandler(() => { redirects += 1 })
  const rejected401 = (config) => Promise.reject({ response: { status: 401 }, config })

  await assert.rejects(api.get('/protected', { adapter: rejected401 }))
  await assert.rejects(api.get('/auth/me', { adapter: rejected401, skipAuthRedirect: true }))

  assert.equal(redirects, 1)
})
