<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NFormItem, NIcon, NInput, NSpin, useMessage, useThemeVars } from 'naive-ui'
import { ArrowRight, KeyRound } from 'lucide-vue-next'
import { appTitle, ApiError } from '../api/client'
import { loadBootstrap, setApiKey } from '../stores/portal'

/**
 * API Key 输入页：门户的"登录页"。
 * 校验方式就是调 `/api/v1/agents/auth/bootstrap`——通过即证明 Key 有效且至少有一个可用 Agent。
 */
const router = useRouter()
const message = useMessage()

const key = ref('')
const loading = ref(false)
const error = ref('')

const canSubmit = computed(() => key.value.trim().length > 0 && !loading.value)

const themeVars = useThemeVars()
/** 页面级语义令牌（跟随 Naive 主题，不硬编码颜色） */
const pageVars = computed<Record<string, string>>(() => ({
  '--key-bg': themeVars.value.bodyColor,
  '--key-card': themeVars.value.cardColor,
  '--key-border': themeVars.value.borderColor,
  '--key-muted': themeVars.value.textColor3,
  '--key-accent': themeVars.value.primaryColor,
  '--key-accent-soft': themeVars.value.primaryColorSuppl
}))

async function submit() {
  const value = key.value.trim()
  if (!value) {
    error.value = '请输入 API Key'
    return
  }
  loading.value = true
  error.value = ''
  try {
    setApiKey(value)
    const data = await loadBootstrap()
    if (data.agents.length === 0) {
      error.value = '该 API Key 还没有绑定任何已发布的 Agent，请联系管理员在「API Key 管理」中绑定'
      return
    }
    message.success(`欢迎，${data.ownerName || data.keyName}`)
    await router.push({ name: 'chat' })
  } catch (e) {
    error.value =
      e instanceof ApiError && e.status === 401
        ? 'API Key 无效或已停用，请确认后重试'
        : e instanceof Error
          ? e.message
          : '校验失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="key-page" :style="pageVars">
    <n-spin :show="loading">
      <div class="key-card">
        <div class="key-brand">
          <span class="key-logo" aria-hidden="true"><n-icon :component="KeyRound" :size="22" /></span>
          <h1 class="key-title">{{ appTitle }}</h1>
          <p class="key-sub">输入你的 API Key 开始对话</p>
        </div>

        <n-alert v-if="error" type="error" :bordered="false" class="key-alert">{{ error }}</n-alert>

        <n-form-item label="API Key" :show-feedback="false">
          <n-input
            v-model:value="key"
            type="password"
            show-password-on="click"
            placeholder="v5ai-…"
            size="large"
            :input-props="{ autocomplete: 'off', 'aria-label': 'API Key' }"
            @keyup.enter="submit"
            @update:value="error = ''"
          >
            <template #prefix><n-icon :component="KeyRound" :size="16" /></template>
          </n-input>
        </n-form-item>

        <n-button type="primary" size="large" block :disabled="!canSubmit" :loading="loading" @click="submit">
          进入对话
          <template #icon><n-icon :component="ArrowRight" :size="16" /></template>
        </n-button>

        <p class="key-hint">
          Key 由管理员在「API Key 管理」页签发，仅在创建时展示一次。Key 只保存在本机浏览器，请勿在公共设备上使用。
        </p>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.key-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--key-bg);
}

.key-card {
  width: 100%;
  max-width: 420px;
  padding: 32px 28px 24px;
  border-radius: 12px;
  background: var(--key-card);
  border: 1px solid var(--key-border);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.key-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.key-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  color: var(--key-accent);
  background: var(--key-accent-soft);
}

.key-title {
  margin: 6px 0 0;
  font-size: 20px;
  font-weight: 700;
}

.key-sub {
  margin: 0;
  font-size: 13px;
  color: var(--key-muted);
}

.key-alert {
  border-radius: 8px;
}

.key-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--key-muted);
}
</style>
