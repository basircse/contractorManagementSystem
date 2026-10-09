import axios, { AxiosError } from 'axios'
import type { ApiError } from './types'

export const TOKEN_KEY = 'ccms.token'
export const VIEW_CONTRACTOR_KEY = 'ccms.viewContractor'

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export const http = axios.create({ baseURL: '/api' })

http.interceptors.request.use((config) => {
  const token = readStorage(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  // Admin viewing a contractor's business (read-only): the server scopes /api/app/** to it.
  const viewing = readStorage(VIEW_CONTRACTOR_KEY)
  if (viewing) {
    try {
      config.headers['X-Contractor-Id'] = String(JSON.parse(viewing).id)
    } catch {
      /* ignore malformed value */
    }
  }
  return config
})

type UnauthorizedHandler = (code: string) => void
let onUnauthorized: UnauthorizedHandler = () => {}
export function setUnauthorizedHandler(fn: UnauthorizedHandler) {
  onUnauthorized = fn
}

http.interceptors.response.use(
  (r) => r,
  (err: AxiosError<ApiError>) => {
    const isLogin = err.config?.url?.includes('/auth/login')
    if (err.response?.status === 401 && !isLogin) {
      onUnauthorized(err.response.data?.code ?? 'UNAUTHENTICATED')
    }
    return Promise.reject(err)
  },
)

export function errorOf(e: unknown): ApiError {
  const ax = e as AxiosError<ApiError>
  return ax.response?.data && typeof ax.response.data === 'object'
    ? ax.response.data
    : { code: 'NETWORK', message: String((e as Error)?.message ?? e) }
}
