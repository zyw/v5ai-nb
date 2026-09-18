import { createRouter, createWebHistory } from 'vue-router'
import KeyView from '../views/KeyView.vue'
import ChatView from '../views/ChatView.vue'
import { apiKey, bootstrapData } from '../stores/portal'

/**
 * 独立对话门户路由。
 *
 * - `/key`：API Key 输入页（本地没有 Key 时的入口）；
 * - `/chat`：对话页（顶栏 + 会话栏 + 对话区）。
 *
 * 守卫只看本地是否存过 API Key：没有 Key 一律回 `/key`；有 Key 时进对话页
 * （Key 是否仍有效由 `/api/v1/agents/auth/bootstrap` 判定，失效会自动清 Key 并回 Key 页）。
 * history base 走 `import.meta.env.BASE_URL`，支持部署到子路径。
 */
export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/key', name: 'key', component: KeyView },
    { path: '/chat', name: 'chat', component: ChatView },
    { path: '/:pathMatch(.*)*', redirect: '/chat' }
  ]
})

router.beforeEach((to) => {
  if (!apiKey.value && to.name !== 'key') {
    return { name: 'key' }
  }
  // 已校验过（bootstrap 成功）时，Key 页不再是有效入口
  if (apiKey.value && bootstrapData.value && to.name === 'key') {
    return { name: 'chat' }
  }
  return true
})
