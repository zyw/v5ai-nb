import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境后端地址（vite dev 代理目标）
const DEV_PROXY_TARGET = 'http://localhost:8080'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const baseApi = env.VITE_APP_BASE_API || '/dev-api'
  return {
    plugins: [vue()],
    server: {
      port: Number(env.VITE_APP_PORT) || 5173,
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
