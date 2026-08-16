import { createRouter, createWebHistory } from 'vue-router'
import {
  allowedRouteNames,
  clearAdminToken,
  defaultRouteName,
  ensureSession,
  isAdmin,
  isLoggedIn
} from '../stores/session'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      redirect: { name: 'dashboard' },
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '总览' } },
        { path: 'models', name: 'models', component: () => import('../views/ModelsView.vue'), meta: { title: '模型管理' } },
        { path: 'agents', name: 'agents', component: () => import('../views/AgentsView.vue'), meta: { title: 'Agent 管理' } },
        { path: 'knowledge-bases', name: 'knowledge-bases', component: () => import('../views/KnowledgeBasesView.vue'), meta: { title: '知识库管理' } },
        { path: 'mcp-servers', name: 'mcp-servers', component: () => import('../views/McpServersView.vue'), meta: { title: 'MCP 管理' } },
        { path: 'skills', name: 'skills', component: () => import('../views/SkillsView.vue'), meta: { title: 'Skill 管理' } },
        { path: 'system/users', name: 'system-users', component: () => import('../views/UserManagementView.vue'), meta: { title: '用户管理' } },
        { path: 'system/roles', name: 'system-roles', component: () => import('../views/RoleManagementView.vue'), meta: { title: '角色管理' } },
        { path: 'system/menus', name: 'system-menus', component: () => import('../views/MenuManagementView.vue'), meta: { title: '菜单管理' } },
        { path: 'system/tenants', name: 'system-tenants', component: () => import('../views/TenantManagementView.vue'), meta: { title: '租户管理' } },
        { path: 'observability', name: 'observability', component: () => import('../views/ObservabilityView.vue'), meta: { title: '可观测性' } },
        { path: 'chat', name: 'chat', component: () => import('../views/ChatDebugView.vue'), meta: { title: '调试工具' } },
        { path: 'settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '系统信息' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } }
  ]
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    if (isLoggedIn.value && to.name === 'login') {
      return { name: 'dashboard' }
    }
    return true
  }
  if (!isLoggedIn.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  await ensureSession()

  // 管理员拥有全部页面权限，直接放行（与后端「ADMIN 角色分配全部菜单」一致）。
  if (isAdmin.value) {
    return true
  }

  const allowed = allowedRouteNames.value
  const name = to.name ? String(to.name) : ''
  if (name && !allowed.has(name)) {
    if (allowed.size === 0) {
      clearAdminToken()
      return { name: 'login' }
    }
    return { name: defaultRouteName.value }
  }
  return true
})
