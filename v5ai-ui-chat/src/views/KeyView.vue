<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NFormItem, NIcon, NInput, NSpin, useMessage } from 'naive-ui'
import { ArrowRight, KeyRound, ShieldCheck } from 'lucide-vue-next'
import { appTitle, ApiError } from '../api/client'
import { loadBootstrap, setApiKey } from '../stores/portal'

/**
 * API Key 输入页：门户的"登录页"。
 * 校验方式就是调 `/api/v1/agents/auth/bootstrap`——通过即证明 Key 有效且至少有一个可用 Agent。
 *
 * 视觉：整页是一块深色舞台（环境光斑 + 极淡网格，见 styles/base.css 的 .portal-stage），
 * 卡片走玻璃层，品牌色只用一处渐变（logo 与主按钮），避免大面积高饱和。
 */
const router = useRouter()
const message = useMessage()

const key = ref('')
const loading = ref(false)
const error = ref('')

const canSubmit = computed(() => key.value.trim().length > 0 && !loading.value)

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
  <div class="key-page portal-stage">
    <n-spin :show="loading" class="key-spin">
      <section class="key-card portal-glass">
        <header class="key-brand">
          <span class="key-logo" aria-hidden="true">v5</span>
          <h1 class="key-title">{{ appTitle }}</h1>
          <p class="key-sub">企业级智能体对话门户</p>
        </header>

        <n-alert v-if="error" type="error" :bordered="false" class="key-alert" role="alert">
          {{ error }}
        </n-alert>

        <n-form-item label="API Key" :show-feedback="false" class="key-field">
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

        <n-button
          class="key-submit"
          size="large"
          block
          :disabled="!canSubmit"
          :loading="loading"
          @click="submit"
        >
          进入对话
          <template #icon><n-icon :component="ArrowRight" :size="16" /></template>
        </n-button>

        <p class="key-hint">
          <n-icon :component="ShieldCheck" :size="14" class="key-hint-icon" />
          <span>
            Key 由管理员在「API Key 管理」页签发，仅在创建时展示一次；只保存在本机浏览器，
            请勿在公共设备上使用。
          </span>
        </p>
      </section>
    </n-spin>

    <p class="key-foot">{{ appTitle }} · 对话门户</p>
  </div>
</template>

<style scoped>
.key-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 24px;
}

.key-spin {
  width: 100%;
  max-width: 440px;
}

/*
 * 玻璃卡片：半透明底 + 发丝描边 + 顶边高光，外面再罩一层冷色辉光。
 * 顶边高光用 ::before 做，模拟光源从上方打下来的边界反光。
 */
.key-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 32px 30px 26px;
  border-radius: var(--portal-radius-lg);
  box-shadow: var(--portal-shadow-lg), 0 0 0 1px var(--portal-border) inset;
  overflow: hidden;
}

.key-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 12%;
  right: 12%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--portal-shine), transparent);
  pointer-events: none;
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
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--portal-grad);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.02em;
  box-shadow: 0 10px 30px var(--portal-accent-glow);
}

.key-title {
  margin: 10px 0 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.key-sub {
  margin: 0;
  font-size: 13px;
  color: var(--portal-fg-muted);
}

.key-alert {
  border-radius: 10px;
}

.key-field :deep(.n-form-item-label) {
  font-size: 12.5px;
  color: var(--portal-fg-muted);
}

/* 主按钮：整页唯一的大面积渐变，hover 提亮、按下轻微缩放（150ms 内给反馈） */
.key-submit {
  background: var(--portal-grad);
  border: 0;
  color: #fff;
  font-weight: 600;
  box-shadow: 0 10px 28px var(--portal-accent-glow);
  transition: filter 0.18s var(--portal-ease), transform 0.18s var(--portal-ease),
    box-shadow 0.18s var(--portal-ease);
}

.key-submit:not(.n-button--disabled):hover {
  filter: brightness(1.08);
  box-shadow: 0 14px 34px var(--portal-accent-glow);
}

.key-submit:not(.n-button--disabled):active {
  transform: scale(0.99);
}

.key-hint {
  display: flex;
  gap: 8px;
  margin: 0;
  padding-top: 16px;
  border-top: 1px solid var(--portal-border);
  font-size: 12px;
  line-height: 1.7;
  color: var(--portal-fg-muted);
}

.key-hint-icon {
  flex: none;
  margin-top: 3px;
}

.key-foot {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--portal-fg-muted);
  opacity: 0.7;
}
</style>
