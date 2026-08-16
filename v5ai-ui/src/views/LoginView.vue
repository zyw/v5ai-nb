<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAlert, NButton, NIcon, NInput } from 'naive-ui'
import { Bot, Lock, Moon, Sun, User } from 'lucide-vue-next'
import { login } from '../api/client'
import { loadCurrentUser, setAdminToken } from '../stores/session'
import { isDark, toggleTheme } from '../stores/theme'

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await login(username.value.trim(), password.value)
    setAdminToken(res.token)
    await loadCurrentUser()
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-bg login-page">
    <button
      class="theme-fab"
      type="button"
      :aria-label="isDark ? '切换为亮色' : '切换为暗色'"
      @click="toggleTheme"
    >
      <n-icon :component="isDark ? Sun : Moon" :size="18" />
    </button>

    <div class="login-card">
      <div class="brand">
        <div class="brand-mark brand-mark-lg"><n-icon :component="Bot" :size="26" /></div>
        <div class="brand-title">v5ai-nb</div>
        <div class="brand-subtitle">AgentScope 中心化 AI 平台控制台</div>
      </div>

      <n-alert v-if="error" type="error" :bordered="false" class="login-error">
        {{ error }}
      </n-alert>

      <form class="login-form" @submit.prevent="handleSubmit">
        <label class="field-label" for="login-username">用户名</label>
        <n-input
          id="login-username"
          v-model:value="username"
          size="large"
          placeholder="请输入用户名"
          autocomplete="username"
          :input-props="{ autocapitalize: 'none', autocorrect: 'off' }"
        >
          <template #prefix><n-icon :component="User" /></template>
        </n-input>

        <label class="field-label" for="login-password">密码</label>
        <n-input
          id="login-password"
          v-model:value="password"
          type="password"
          size="large"
          show-password-on="click"
          placeholder="请输入密码"
          autocomplete="current-password"
          @keyup.enter="handleSubmit"
        >
          <template #prefix><n-icon :component="Lock" /></template>
        </n-input>

        <n-button type="primary" size="large" block attr-type="submit" :loading="loading">
          登 录
        </n-button>
      </form>

      <p class="login-hint">默认账号：admin / admin</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
}

.theme-fab {
  position: fixed;
  top: 20px;
  right: 20px;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid rgba(128, 128, 128, 0.2);
  border-radius: 10px;
  background: var(--n-color, rgba(128, 128, 128, 0.08));
  color: var(--n-text-color, inherit);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}

.theme-fab:hover {
  background: rgba(128, 128, 128, 0.16);
  transform: translateY(-1px);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 40px 36px 32px;
  border-radius: 16px;
  background: var(--n-color, #fff);
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.14));
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.18);
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
}

.brand-mark-lg {
  width: 52px;
  height: 52px;
  border-radius: 14px;
}

.brand-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.brand-subtitle {
  font-size: 13px;
  opacity: 0.6;
}

.login-error {
  margin-bottom: 16px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  margin-top: 6px;
}

.login-hint {
  margin: 20px 0 0;
  text-align: center;
  font-size: 12px;
  opacity: 0.45;
}
</style>
