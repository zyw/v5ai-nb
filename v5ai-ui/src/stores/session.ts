import { computed, ref } from 'vue'
import { fetchMe, type LoginResponseFull } from '../api/client'

const tokenKey = 'v5ai.adminToken'

export const adminToken = ref(localStorage.getItem(tokenKey) ?? '')
export const currentUser = ref<LoginResponseFull | null>(null)

export const isLoggedIn = computed(() => adminToken.value.length > 0)
export const isAdmin = computed(() => currentUser.value?.roles?.includes('ADMIN') ?? false)
export const displayName = computed(
  () => currentUser.value?.displayName || currentUser.value?.username || '管理员'
)

export function setAdminToken(token: string): void {
  adminToken.value = token
  localStorage.setItem(tokenKey, token)
}

export function clearAdminToken(): void {
  adminToken.value = ''
  currentUser.value = null
  localStorage.removeItem(tokenKey)
}

/** 登录后拉取当前用户信息（用于用户名/角色展示）。 */
export async function loadCurrentUser(): Promise<void> {
  if (!adminToken.value) {
    currentUser.value = null
    return
  }
  try {
    currentUser.value = await fetchMe(adminToken.value)
  } catch {
    currentUser.value = null
  }
}
