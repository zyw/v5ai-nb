import { createApp } from 'vue'
import naive from 'naive-ui'
import App from './App.vue'
import { router } from './router'
import './styles/base.css'

const app = createApp(App)

// 组件渲染/生命周期异常默认只在控制台静默一次，DOM 会停在旧状态；显式打日志便于排查。
app.config.errorHandler = (err, _instance, info) => {
  console.error('[v5ai-chat] 组件错误', info, err)
}

app.use(router).use(naive).mount('#app')
