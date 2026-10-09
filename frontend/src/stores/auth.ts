import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { http, TOKEN_KEY, VIEW_CONTRACTOR_KEY } from '@/api/http'
import type { Me } from '@/api/types'

interface Viewing {
  id: number
  name: string
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}
function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    /* storage unavailable */
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(read(TOKEN_KEY))
  const user = ref<Me | null>(null)
  const viewing = ref<Viewing | null>(parseViewing())

  function parseViewing(): Viewing | null {
    try {
      const v = read(VIEW_CONTRACTOR_KEY)
      return v ? (JSON.parse(v) as Viewing) : null
    } catch {
      return null
    }
  }

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  /** Admin looking at a contractor's business: everything is read-only. */
  const readOnly = computed(() => isAdmin.value && !!viewing.value)
  const businessName = computed(() => (isAdmin.value ? viewing.value?.name : user.value?.contractorName) ?? '')

  async function login(username: string, password: string) {
    const { data } = await http.post('/auth/login', { username, password })
    token.value = data.token
    write(TOKEN_KEY, data.token)
    user.value = data.user
    stopViewing()
    return data.user as Me
  }

  async function fetchMe() {
    const { data } = await http.get<Me>('/auth/me')
    user.value = data
    return data
  }

  function logout() {
    token.value = null
    user.value = null
    write(TOKEN_KEY, null)
    stopViewing()
  }

  function startViewing(id: number, name: string) {
    viewing.value = { id, name }
    write(VIEW_CONTRACTOR_KEY, JSON.stringify(viewing.value))
  }

  function stopViewing() {
    viewing.value = null
    write(VIEW_CONTRACTOR_KEY, null)
  }

  return { token, user, viewing, isLoggedIn, isAdmin, readOnly, businessName, login, fetchMe, logout, startViewing, stopViewing }
})
