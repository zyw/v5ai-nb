import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境后端地址（vite dev 代理目标），与管理端 v5ai-ui 保持一致
const DEV_PROXY_TARGET = 'http://localhost:8080'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const baseApi = env.VITE_APP_BASE_API || '/dev-api'
  return {
    // 独立部署到子路径时用 VITE_APP_BASE（如 /chat/），默认部署在根
    base: env.VITE_APP_BASE || '/',
    plugins: [vue()],
    server: {
      // 与管理端错开端口，两个前端可同时跑
      port: Number(env.VITE_APP_PORT) || 5174,
      proxy: {
        [baseApi]: {
          target: DEV_PROXY_TARGET,
          changeOrigin: true,
          rewrite: (path) => path.replace(baseApi, '')
        }
      }
    }
  }
})
