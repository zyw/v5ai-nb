import { computed, ref } from 'vue'
import { getInfo, getRouters, type RouterVo, type UserInfo } from '../api/client'

const tokenKey = 'v5ai.adminToken'

export const adminToken = ref(localStorage.getItem(tokenKey) ?? '')
/** 登录后 getInfo 结果：用户信息 + 角色 + 权限集合。 */
export const currentUser = ref<UserInfo | null>(null)
/** 登录后 getRouters 结果：当前用户权限允许的路由菜单树。 */
export const currentMenus = ref<RouterVo[]>([])

export const isLoggedIn = computed(() => adminToken.value.length > 0)
export const isAdmin = computed(() => currentUser.value?.roles?.includes('ADMIN') ?? false)
export const displayName = computed(
  () => currentUser.value?.user?.nickName || currentUser.value?.user?.userName || '管理员'
)

/** 收集菜单树叶子节点的 path（统一带 '/' 前缀，与 vue-router 的 to.path 对齐）。 */
function collectLeafPaths(menus: RouterVo[]): Set<string> {
  const set = new Set<string>()
  const walk = (list: RouterVo[]) => {
    for (const m of list) {
      if (m.children && m.children.length > 0) {
        walk(m.children)
      } else if (m.path) {
        set.add(m.path.startsWith('/') ? m.path : `/${m.path}`)
      }
    }
  }
  walk(menus)
  return set
}

/** 当前用户可访问的路由 path 集合（由 getRouters 菜单树叶子推导）。 */
export const allowedPaths = computed<Set<string>>(() => collectLeafPaths(currentMenus.value))

/** 缺省落地页：菜单树第一个叶子 path。 */
export const defaultPath = computed<string>(() => {
  const first = collectLeafPaths(currentMenus.value).values().next().value
  return first ?? '/dashboard'
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

/** 登录后调用 getInfo：拉取用户信息、角色与权限并存储到本地。 */
export async function loadCurrentUser(): Promise<void> {
  if (!adminToken.value) {
    currentUser.value = null
    return
  }
  try {
    currentUser.value = await getInfo(adminToken.value)
  } catch {
    currentUser.value = null
  }
}

/** 登录后调用 getRouters：拉取权限允许的菜单树并存储到本地。 */
export async function loadCurrentMenus(): Promise<void> {
  if (!adminToken.value) {
    currentMenus.value = []
    return
  }
  try {
    currentMenus.value = await getRouters(adminToken.value)
  } catch {
    currentMenus.value = []
  }
}

let sessionLoaded = false

/** 登录后加载用户信息 + 菜单（幂等）：getInfo → getRouters。 */
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
