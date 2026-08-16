import { computed, ref } from 'vue'
import { fetchMe, fetchMyMenus, type LoginResponseFull, type Menu } from '../api/client'

const tokenKey = 'v5ai.adminToken'

export const adminToken = ref(localStorage.getItem(tokenKey) ?? '')
export const currentUser = ref<LoginResponseFull | null>(null)
export const currentMenus = ref<Menu[]>([])

export const isLoggedIn = computed(() => adminToken.value.length > 0)
export const isAdmin = computed(() => currentUser.value?.roles?.includes('ADMIN') ?? false)
export const displayName = computed(
  () => currentUser.value?.displayName || currentUser.value?.username || '管理员'
)

/** 当前用户可访问的 vue-router 路由名集合（由菜单 routeName 推导）。 */
export const allowedRouteNames = computed<Set<string>>(
  () => new Set(currentMenus.value.filter((m) => m.type === 'MENU' && m.routeName).map((m) => m.routeName as string))
)

/** 缺省落地页：排序靠前的第一个可访问页面。 */
export const defaultRouteName = computed<string>(() => {
  const first = currentMenus.value.find((m) => m.type === 'MENU' && m.routeName)
  return first?.routeName ?? 'dashboard'
})

export function setAdminToken(token: string): void {
  adminToken.value = token
  localStorage.setItem(tokenKey, token)
  sessionLoaded = false
}

export function clearAdminToken(): void {
  adminToken.value = ''
  currentUser.value = null
  currentMenus.value = []
  sessionLoaded = false
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

/** 拉取当前用户可见菜单（用于动态导航与页面权限）。 */
export async function loadCurrentMenus(): Promise<void> {
  if (!adminToken.value) {
    currentMenus.value = []
    return
  }
  try {
    currentMenus.value = await fetchMyMenus(adminToken.value)
  } catch {
    currentMenus.value = []
  }
}

let sessionLoaded = false

/** 首次登录后加载用户信息 + 菜单（幂等）。 */
export async function ensureSession(): Promise<void> {
  if (!adminToken.value) {
    return
  }
  if (sessionLoaded) {
    return
  }
  await Promise.all([loadCurrentUser(), loadCurrentMenus()])
  sessionLoaded = true
}
