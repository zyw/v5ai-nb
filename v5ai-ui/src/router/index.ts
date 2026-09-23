import { createRouter, createWebHistory } from 'vue-router'
import {
  allowedPaths,
  clearAdminToken,
  defaultPath,
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
        { path: 'agents/:agentKey', name: 'agent-edit', component: () => import('../views/agent/AgentEditView.vue'), meta: { title: '智能体编辑' } },
        { path: 'api-keys', name: 'api-keys', component: () => import('../views/ApiKeysView.vue'), meta: { title: 'API Keys 管理' } },
        { path: 'knowledge-bases', name: 'knowledge-bases', component: () => import('../views/KnowledgeBasesView.vue'), meta: { title: '知识库管理' } },
        { path: 'knowledge-bases/:id', name: 'knowledge-base-detail', component: () => import('../views/knowledge-base/KnowledgeBaseDetailView.vue'), meta: { title: '知识库详情' } },
        { path: 'store-instances', name: 'store-instances', component: () => import('../views/StoreInstancesView.vue'), meta: { title: '存储实例' } },
        { path: 'resources', name: 'resources', component: () => import('../views/ResourcesView.vue'), meta: { title: '资源存储' } },
        { path: 'mcp-servers', name: 'mcp-servers', component: () => import('../views/McpServersView.vue'), meta: { title: 'MCP 管理' } },
        { path: 'skills', name: 'skills', component: () => import('../views/SkillsView.vue'), meta: { title: 'Skill 管理' } },
        { path: 'skills/:id/editor', name: 'skill-editor', component: () => import('../views/SkillEditorView.vue'), meta: { title: 'Skill 文件编辑' } },
        { path: 'workflows', name: 'workflows', component: () => import('../views/WorkflowsView.vue'), meta: { title: '工作流' } },
        { path: 'workflows/:key/editor', name: 'workflow-editor', component: () => import('../views/WorkflowEditorView.vue'), meta: { title: '工作流编排' } },
        { path: 'system/users', name: 'system-users', component: () => import('../views/UserManagementView.vue'), meta: { title: '用户管理' } },
        { path: 'system/roles', name: 'system-roles', component: () => import('../views/RoleManagementView.vue'), meta: { title: '角色管理' } },
        { path: 'system/menus', name: 'system-menus', component: () => import('../views/MenuManagementView.vue'), meta: { title: '菜单管理' } },
        { path: 'system/clients', name: 'system-clients', component: () => import('../views/ClientManagementView.vue'), meta: { title: '客户端管理' } },
        { path: 'system/logs', name: 'system-logs', component: () => import('../views/LogManagementView.vue'), meta: { title: '日志管理' } },
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

  const allowed = allowedPaths.value
  if (allowed.size === 0) {
    clearAdminToken()
    return { name: 'login' }
  }
  // getRouters 返回的菜单树决定可访问页面（按 path 匹配）。
  // 动态子页面（如 /workflows/{key}/editor）不在菜单树中，按其父级路径前缀放行。
  const isReachable = [...allowed].some(
    (path) => to.path === path || to.path.startsWith(path.endsWith('/') ? path : `${path}/`)
  )
  if (!isReachable) {
    return { path: defaultPath.value }
  }
  return true
})
