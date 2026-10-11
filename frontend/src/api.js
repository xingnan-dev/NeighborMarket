import axios from 'axios'
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { 'Content-Type': 'application/json' },
})

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.skipAuthRedirect) unauthorizedHandler?.()
    return Promise.reject(error)
  },
)

export function messageOf(error) {
  return error.response?.data?.message || error.response?.data?.detail || (error.response ? '请求失败，请稍后重试' : '连接失败，请稍后重试')
}
