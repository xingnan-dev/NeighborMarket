import { reactive } from 'vue'
import { api } from './api'

export const auth = reactive({ user: null, ready: false })

let csrfPromise = null
let initializationPromise = null

export function ensureCsrf() {
  if (!csrfPromise) {
    csrfPromise = api.get('/auth/csrf', { skipAuthRedirect: true })
      .catch((error) => {
        csrfPromise = null
        throw error
      })
  }
  return csrfPromise
}

api.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method)) await ensureCsrf()
  return config
})

export async function loadUser() {
  try {
    auth.user = (await api.get('/auth/me', { skipAuthRedirect: true })).data
  } catch {
    auth.user = null
  } finally {
    auth.ready = true
  }
}

export function initializeAuth() {
  if (!initializationPromise) {
    initializationPromise = (async () => {
      try {
        await ensureCsrf()
      } catch {
        // Public pages remain usable; unsafe requests retry CSRF initialization.
      }
      await loadUser()
    })()
  }
  return initializationPromise
}

export async function logout() {
  await api.post('/auth/logout')
  auth.user = null
  csrfPromise = null
}
