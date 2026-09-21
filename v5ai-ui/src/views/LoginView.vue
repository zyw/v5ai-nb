<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAlert, NButton, NIcon, NInput } from 'naive-ui'
import { Lock, Moon, Sun, User } from 'lucide-vue-next'
import { ApiError, login } from '../api/client'
import { ensureSession, setAdminToken } from '../stores/session'
import { isDark, toggleTheme } from '../stores/theme'
import SliderCaptcha from '../components/SliderCaptcha.vue'

/** 与后端 SliderCaptchaService.CAPTCHA_REQUIRED_CODE 对应。 */
const CAPTCHA_REQUIRED_CODE = 1006

/**
 * 右侧能力清单。文案是「管理员能配置什么」而非门户那种终端用户收益，
 * 与门户 KeyView 的宣传语刻意区分开。
 */
const CAPABILITIES = [
  { no: '01', title: '多智能体编排', desc: 'Agent 配置、能力开关与发布快照' },
  { no: '02', title: '知识库与引用治理', desc: '文档切片、检索参数与引用载荷' },
  { no: '03', title: '联网检索配置', desc: '按 Agent 开关联网，Key 由环境变量提供' },
  { no: '04', title: '模型与配额管理', desc: 'Provider 接入、用量明细与限流配额' }
]

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const captchaRequired = ref(false)
const sliderUuid = ref('')
const sliderVerified = ref(false)
const errorRef = ref<HTMLElement | null>(null)

/**
 * 错误既有视觉提示也有 role="alert" 播报，再把焦点移过去，
 * 否则读屏用户提交失败后仍停在按钮上，不知道发生了什么。
 */
async function showError(message: string) {
  error.value = message
  await nextTick()
  errorRef.value?.focus()
}

async function handleSubmit() {
  if (!username.value.trim() || !password.value) {
    await showError('请输入用户名和密码')
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await login(
      username.value.trim(),
      password.value,
      sliderVerified.value ? sliderUuid.value : undefined
    )
    setAdminToken(res.access_token, res.refresh_token)
    // 登录成功写入 token 后：getInfo 拉取用户/角色/权限，getRouters 拉取权限内菜单
    await ensureSession()
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (e) {
    if (e instanceof ApiError && e.code === CAPTCHA_REQUIRED_CODE) {
      // 错误次数已达阈值：弹出滑块，验证通过后自动重试登录
      captchaRequired.value = true
      sliderVerified.value = false
      await showError(e.message)
    } else {
      await showError(e instanceof Error ? e.message : '登录失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

function onSliderSuccess(uuid: string) {
  sliderUuid.value = uuid
  sliderVerified.value = true
  error.value = ''
  handleSubmit()
}
</script>

<template>
  <div class="login-page">
    <!--
      装饰层：正交标尺网格 + 左缘品牌竖线 + 图纸角标。
      全景静态——无动画、无模糊、无透视，与门户 KeyView 的 HUD 语言（透视地面、扫描线、
      视差、玻璃）刻意对立，两端一望即知不是同一个页面。
    -->
    <div class="login-fx" aria-hidden="true">
      <div class="login-fx-grid"></div>
      <div class="login-fx-major"></div>
      <div class="login-fx-rule"></div>
    </div>

    <button
      class="theme-fab"
      type="button"
      :aria-label="isDark ? '切换为亮色' : '切换为暗色'"
      @click="toggleTheme"
    >
      <n-icon :component="isDark ? Sun : Moon" :size="18" />
    </button>

    <!--
      卡片在前、上下文区在后：DOM 顺序即视觉顺序，
      这样 <1024px 折成单列时表单自然排在最上方，不需要用 CSS order 把 Tab 顺序拧乱。
    -->
    <div class="login-shell">
      <div class="login-marks" aria-hidden="true">
        <span class="login-mark login-mark--tl"></span>
        <span class="login-mark login-mark--tr"></span>
        <span class="login-mark login-mark--bl"></span>
        <span class="login-mark login-mark--br"></span>
      </div>

      <section class="login-card">
        <header class="login-card-head">
          <div class="brand-mark brand-mark-lg" aria-hidden="true"></div>
          <div>
            <h1 class="login-title">v5ai-nb 管理系统</h1>
            <p class="login-subtitle">AgentScope 中心化 AI 平台控制台</p>
          </div>
        </header>

        <!-- n-alert 自身已带 role="alert"，这里只补一个可编程聚焦的落点，不再套一层 live region。 -->
        <div v-if="error" ref="errorRef" class="login-error" tabindex="-1">
          <n-alert type="error" :bordered="false">{{ error }}</n-alert>
        </div>

        <form class="login-form" @submit.prevent="handleSubmit">
          <!--
            id 必须走 :input-props：n-input 不声明 id prop，它的内层 <input> 只展开
            inputProps，直接写 id="..." 会被透传到外层 wrapper 上，label 的 for 就落空了。
          -->
          <label class="field-label" for="login-username">用户名</label>
          <n-input
            v-model:value="username"
            size="large"
            placeholder="请输入用户名"
            autocomplete="username"
            :input-props="{ id: 'login-username', autocapitalize: 'none', autocorrect: 'off' }"
          >
            <template #prefix><n-icon :component="User" /></template>
          </n-input>

          <label class="field-label" for="login-password">密码</label>
          <n-input
            v-model:value="password"
            type="password"
            size="large"
            show-password-on="click"
            placeholder="请输入密码"
            autocomplete="current-password"
            :input-props="{ id: 'login-password' }"
            @keyup.enter="handleSubmit"
          >
            <template #prefix><n-icon :component="Lock" /></template>
          </n-input>

          <SliderCaptcha
            v-if="captchaRequired && !sliderVerified"
            class="login-slider"
            @success="onSliderSuccess"
          />

          <n-button
            type="primary"
            size="large"
            block
            attr-type="submit"
            :loading="loading"
            class="login-submit"
          >
            登 录
          </n-button>
        </form>
      </section>

      <aside class="login-context" aria-label="管理端能力">
        <p class="login-kicker">v5ai-nb · 管理控制台</p>
        <h2 class="login-headline">统一编排 Agent、知识与模型</h2>
        <p class="login-lede">
          Agent、知识库、模型、工具与权限都在这里配置；改动经发布后对线上生效。
        </p>

        <ol class="login-caps" role="list">
          <li v-for="cap in CAPABILITIES" :key="cap.no">
            <span class="login-cap-no">{{ cap.no }}</span>
            <span class="login-cap-title">{{ cap.title }}</span>
            <span class="login-cap-desc">{{ cap.desc }}</span>
          </li>
        </ol>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/*
 * 页面自成滚动容器：全局 body 是 overflow: hidden，登录页在矮屏（横屏平板、小手机）
 * 必须能自己滚，否则内容会被直接裁掉。
 * .login-shell 用 margin: auto 居中——内容超出时它会退化成从顶部开始并正常滚动，
 * 不像 place-items: center 那样把顶部切掉且滚不到。
 */
.login-page {
  position: relative;
  isolation: isolate;
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  max-height: 100vh;
  max-height: 100dvh;
  overflow-x: hidden;
  overflow-y: auto;
  padding: clamp(20px, 5vh, 56px) clamp(12px, 4vw, 48px);
  background-color: var(--login-canvas);
  color: var(--login-ink);
}

/* ---------- 装饰层（工程图纸） ---------- */

.login-fx {
  position: fixed;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
}

/* 32px 细网格 / 160px 主网格（160 = 5×32，主网格线正好压在第 5 条细线上）。 */
.login-fx-grid,
.login-fx-major {
  position: absolute;
  inset: 0;
}

.login-fx-grid {
  background-image:
    linear-gradient(to right, var(--login-grid-minor) 1px, transparent 1px),
    linear-gradient(to bottom, var(--login-grid-minor) 1px, transparent 1px);
  background-size: 32px 32px;
}

.login-fx-major {
  background-image:
    linear-gradient(to right, var(--login-grid-major) 1px, transparent 1px),
    linear-gradient(to bottom, var(--login-grid-major) 1px, transparent 1px);
  background-size: 160px 160px;
}

/* 左缘品牌竖线：预演登录后左侧那条 248px 侧边栏的位置。 */
.login-fx-rule {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: var(--brand);
}

/* ---------- 图纸角标（锚在内容区四角，不锚视口） ---------- */

.login-shell {
  position: relative;
  display: grid;
  grid-template-columns: minmax(392px, 420px) minmax(0, 1fr);
  gap: clamp(32px, 5vw, 72px);
  align-items: center;
  width: 100%;
  max-width: 1180px;
  margin: auto;
}

.login-marks {
  position: absolute;
  inset: -16px;
  pointer-events: none;
}

.login-mark {
  position: absolute;
  width: 18px;
  height: 18px;
  border: 0 solid var(--login-tick);
}

.login-mark--tl {
  top: 0;
  left: 0;
  border-width: 1px 0 0 1px;
}
.login-mark--tr {
  top: 0;
  right: 0;
  border-width: 1px 1px 0 0;
}
.login-mark--bl {
  bottom: 0;
  left: 0;
  border-width: 0 0 1px 1px;
}
.login-mark--br {
  bottom: 0;
  right: 0;
  border-width: 0 1px 1px 0;
}

/* ---------- 主题切换 ---------- */

.theme-fab {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--login-border);
  border-radius: 8px;
  background: var(--login-surface);
  color: var(--login-ink);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease;
}

.theme-fab:hover {
  border-color: var(--brand);
  color: var(--brand);
}

/* ---------- 登录卡片 ---------- */

.login-card {
  position: relative;
  padding: 36px 32px 32px;
  border: 1px solid var(--login-border);
  border-radius: 10px;
  background: var(--login-surface);
  box-shadow: var(--login-shadow);
}

/* 卡片左缘品牌线：与页面左缘竖线同一条视觉语言的延续。 */
.login-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--brand);
}

/* 只保留侧边栏那圈 ring，去掉蓝色外发光与内发光——本页不用辉光。 */
.brand-mark-lg {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  box-shadow: 0 0 0 3px var(--brand-icon-ring);
}

.login-card-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.login-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.login-subtitle {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--login-ink-muted);
}

.login-error {
  margin-bottom: 18px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field-label {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 500;
}

.login-slider {
  margin-top: 6px;
}

.login-submit {
  margin-top: 10px;
  font-weight: 600;
}

/* ---------- 上下文区 ---------- */

.login-context {
  max-width: 520px;
}

.login-kicker {
  margin: 0 0 14px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--brand);
}

.login-headline {
  margin: 0;
  font-size: clamp(28px, 3.2vw, 40px);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.login-lede {
  max-width: 46ch;
  margin: 16px 0 0;
  font-size: 14.5px;
  line-height: 1.7;
  color: var(--login-ink-muted);
}

.login-caps {
  display: grid;
  margin: 32px 0 0;
  padding: 0;
  border-top: 1px solid var(--login-hairline);
  list-style: none;
}

.login-caps > li {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  /* 两列布局下同一行的两个 li 会被拉伸到等高，align-content:start 让行保持自然高度、
     文字都从顶部对齐，否则文案短的会整块被推下去。 */
  align-content: start;
  padding: 15px 0;
  border-bottom: 1px solid var(--login-hairline);
}

.login-cap-no {
  grid-row: 1 / 3;
  padding-top: 2px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--brand);
}

.login-cap-title {
  font-size: 14px;
  font-weight: 600;
}

.login-cap-desc {
  margin-top: 3px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--login-ink-muted);
}

/* ---------- 响应式 ---------- */

/* 单列：卡片自带品牌头已承担识别，kicker/标题/导语收起，只留能力清单垫在卡片下方。 */
@media (max-width: 1023px) {
  .login-shell {
    grid-template-columns: minmax(0, 1fr);
    max-width: 34rem;
    gap: 28px;
  }

  .login-kicker,
  .login-headline,
  .login-lede {
    display: none;
  }

  .login-context {
    max-width: none;
  }

  .login-caps {
    grid-template-columns: 1fr 1fr;
    column-gap: 28px;
    margin-top: 0;
  }
}

/* 手机上登录是唯一任务：清单与角标都让位。 */
@media (max-width: 767px) {
  .login-context,
  .login-marks {
    display: none;
  }

  .login-card {
    padding: 28px 16px 24px;
  }
}
</style>
