import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'dashboard' } },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/models', name: 'models', component: () => import('../views/ModelsView.vue') },
    { path: '/applications', name: 'applications', component: () => import('../views/ApplicationsView.vue') },
    { path: '/knowledge-bases', name: 'knowledge-bases', component: () => import('../views/KnowledgeBasesView.vue') },
    { path: '/mcp-servers', name: 'mcp-servers', component: () => import('../views/McpServersView.vue') },
    { path: '/skills', name: 'skills', component: () => import('../views/SkillsView.vue') },
    { path: '/chat', name: 'chat', component: () => import('../views/ChatDebugView.vue') },
    { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue') }
  ]
})
