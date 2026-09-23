<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NFormItem, NIcon, NInput, NSpin, useMessage } from 'naive-ui'
import { ArrowRight, BookMarked, Globe, Images, KeyRound, Network, ShieldCheck } from 'lucide-vue-next'
import { appTitle, ApiError } from '../api/client'
import { loadBootstrap, setApiKey } from '../stores/portal'

/**
 * API Key 输入页：门户的"登录页"。
 * 校验方式就是调 `/api/v1/agents/auth/bootstrap`——通过即证明 Key 有效且至少有一个可用 Agent。
 *
 * 布局：左宣传、右登录。宽屏两栏（≥1024px），窄屏收成单栏——
 * 宣传区塌缩成「标识 + 标语」的紧凑头部仍排在登录卡**前面**，于是不必用 CSS order 调换位置，
 * DOM 顺序和视觉顺序、Tab 顺序始终一致（order 会让读屏和键盘焦点对不上）。
 *
 * 视觉：科技感底全部由 CSS/SVG 画出（地平线网格 / 电路走线 / 扫描线 / 收边），没有图片素材——
 * 免去明暗两套图、CLS 与外部依赖。装饰层都在 .key-fx-* 上，不进无障碍树、不接指针事件。
 */
const router = useRouter()
const message = useMessage()

const key = ref('')
const loading = ref(false)
const error = ref('')

const canSubmit = computed(() => key.value.trim().length > 0 && !loading.value)

/**
 * 左侧宣传区的四条能力：每条都由前端真实接线的能力支撑，不是营销词——
 * 分别对应多 Agent 切换（PortalAgent）、引用切片（KbHitResponse）、联网开关（webSearchEnabled）、
 * 图片输入与流式 / 停止（imageSupported、chat/stream、runs/{id}/stop）。
 */
const features = [
  { icon: Network, title: '多智能体协同', desc: '按业务切换专属助手，各自独立的知识域' },
  { icon: BookMarked, title: '知识库引用溯源', desc: '回答标注引用切片，可回查原文' },
  { icon: Globe, title: '联网检索增强', desc: '按需联网检索，突破语料时效' },
  { icon: Images, title: '图文多模态', desc: '直接读图提问，流式回答可随时停止' }
]

/* ---- 背景视差：装饰层随指针轻微错动，做出前后景深 ---- */

const fxEl = ref<HTMLElement | null>(null)
let frame = 0
let pointerX = 0
let pointerY = 0

/**
 * 只有「有真指针」且「未开启减少动态效果」时才做视差：
 * 触屏没有 hover，跟着手指位移纯属浪费；开了减少动态效果的用户明确表示不要动。
 */
function parallaxAllowed(): boolean {
  return (
    window.matchMedia('(hover: hover) and (pointer: fine)').matches &&
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * 每帧最多写一次，且只把偏移写成装饰容器上的两个自定义属性，位移全部由 CSS 的 transform 消费。
 * 写样式放在 rAF 里而不是 pointermove 里：指针事件比帧率密得多，直接写会让每帧重算多次。
 * 位移夹在 ±14px / ±10px，再多就晃眼且会把网格推出裁切边界。
 */
function onPointerMove(event: PointerEvent) {
  if (!parallaxAllowed()) return
  pointerX = (event.clientX / window.innerWidth - 0.5) * 2
  pointerY = (event.clientY / window.innerHeight - 0.5) * 2
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const el = fxEl.value
    if (!el) return
    el.style.setProperty('--fx-x', `${(pointerX * 14).toFixed(2)}px`)
    el.style.setProperty('--fx-y', `${(pointerY * 10).toFixed(2)}px`)
  })
}

onMounted(() => window.addEventListener('pointermove', onPointerMove, { passive: true }))

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  // 卸载时把没跑完的那一帧取消掉，否则回调里会去写已经摘掉的节点
  if (frame) cancelAnimationFrame(frame)
})

/* ---- 提交 ---- */

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
  <div ref="fxEl" class="key-page portal-stage">
    <!-- 装饰层：纯 CSS/SVG 画的科技感底。aria-hidden + pointer-events:none，纯装饰 -->
    <div class="key-fx-vignette" aria-hidden="true"></div>
    <div class="key-fx-floor" aria-hidden="true"></div>
    <svg
      class="key-fx-traces"
      viewBox="0 0 1200 800"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
      focusable="false"
    >
      <path class="trace" d="M-24 168H196v152h232" />
      <path class="trace" d="M1224 96h-236v148h-186" />
      <path class="trace" d="M-24 636h268V472h228v148" />
      <path class="trace" d="M1224 712h-206V572" />
      <path class="trace" d="M336-24v148h228V-24" />
      <path class="trace" d="M892 824V668H688" />
      <circle class="node" cx="196" cy="168" r="2.5" />
      <circle class="node" cx="428" cy="320" r="2.5" />
      <circle class="node" cx="988" cy="96" r="2.5" />
      <circle class="node" cx="802" cy="244" r="2.5" />
      <circle class="node" cx="244" cy="636" r="2.5" />
      <circle class="node" cx="472" cy="472" r="2.5" />
      <circle class="node" cx="1018" cy="712" r="2.5" />
      <circle class="node" cx="564" cy="124" r="2.5" />
      <circle class="node" cx="688" cy="668" r="2.5" />
    </svg>
    <div class="key-fx-scan" aria-hidden="true"></div>

    <div class="key-shell">
      <div class="key-main">
        <section class="key-promo">
          <div class="key-promo-brand">
            <span class="key-logo" aria-hidden="true"></span>
            <span class="key-promo-name">{{ appTitle }}</span>
          </div>
          <h1 class="key-promo-title">让企业知识<br />随问随答</h1>
          <p class="key-promo-sub">面向企业场景的智能体对话门户</p>

          <ul class="key-feats portal-glass">
            <li v-for="feat in features" :key="feat.title" class="key-feat">
              <span class="key-feat-icon" aria-hidden="true">
                <n-icon :component="feat.icon" :size="17" />
              </span>
              <span class="key-feat-title">{{ feat.title }}</span>
              <span class="key-feat-desc">{{ feat.desc }}</span>
            </li>
          </ul>
        </section>

        <n-spin :show="loading" class="key-spin">
          <section class="key-card portal-glass">
            <header class="key-card-head">
              <h2 class="key-card-title">身份验证</h2>
              <p class="key-card-sub">使用管理员签发的 API Key 进入</p>
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
                API Key 仅在创建时展示一次，只保存在本机浏览器，请勿在公共设备上使用。
              </span>
            </p>
          </section>
        </n-spin>
      </div>
    </div>
  </div>
</template>

<style scoped>
.key-page {
  /* dvh 跟随移动端地址栏收放；先声明 100vh 给不认 dvh 的旧浏览器兜底 */
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  /* overflow: clip 来自 .portal-stage：装饰层都比盒子大，不裁会给页面多出滚动条 */
}

/* 内容整体压在装饰层之上（装饰层最高 z-index: 0，各自内部再分层） */
.key-shell {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 28px clamp(20px, 5vw, 64px);
}

.key-main {
  flex: 1;
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 424px);
  align-items: center;
  gap: clamp(32px, 6vw, 88px);
}

/* ---------- 装饰层 ----------
   分层（从下到上）：收边 -4 → 地平线网格 -3 → 环境光斑 -2 → 图纸网格 -1
   → 电路走线 0 → 扫描线 0 → 内容 1。光斑与图纸网格来自 .portal-stage 的两个伪元素。 */
.key-fx-vignette,
.key-fx-floor,
.key-fx-traces,
.key-fx-scan {
  position: absolute;
  pointer-events: none;
}

/* 收边：四周向「更远的一端」压，同时把压在边缘的次要文字衬托得更清楚 */
.key-fx-vignette {
  inset: 0;
  z-index: -4;
  background: radial-gradient(125% 95% at 50% 38%, transparent 42%, var(--portal-fx-vignette) 100%);
}

/* 地平线网格：透视后向远处收束，遮罩只留近处那一段，免得和图纸网格打架 */
.key-fx-floor {
  inset: 0;
  z-index: -3;
  background-image:
    linear-gradient(var(--portal-fx-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--portal-fx-grid) 1px, transparent 1px);
  background-size: 72px 72px;
  transform-origin: 50% 100%;
  /* 视差平移放在最外层（最左），作用于已经投影完的网格；远近层次靠位移量的差 */
  transform: translate3d(var(--fx-x, 0px), var(--fx-y, 0px), 0) perspective(560px) rotateX(58deg)
    scale(1.7);
  -webkit-mask-image: linear-gradient(to top, #000 2%, transparent 46%);
  mask-image: linear-gradient(to top, #000 2%, transparent 46%);
}

/* 电路走线：直角折线 + 节点。放大后仍要保持 1px，所以走 non-scaling-stroke */
.key-fx-traces {
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  /* 视差量取地平线网格的一半，于是走线看起来比网格更"远" */
  transform: translate3d(calc(var(--fx-x, 0px) * 0.5), calc(var(--fx-y, 0px) * 0.5), 0);
}

.key-fx-traces .trace {
  fill: none;
  stroke: var(--portal-fx-trace);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.key-fx-traces .node {
  fill: var(--portal-fx-trace);
  stroke: none;
}

/* 扫描线：整页唯一的持续动效，只动 transform（合成层上跑，不触发重排重绘） */
.key-fx-scan {
  top: 0;
  left: 0;
  right: 0;
  height: 220px;
  z-index: 0;
  background: linear-gradient(to bottom, transparent, var(--portal-fx-scan), transparent);
  animation: key-scan 11s linear infinite;
}

@keyframes key-scan {
  from {
    transform: translate3d(0, -220px, 0);
  }
  to {
    /* 用 vh 而不是 dvh：这里只是装饰的行程距离，不参与布局，没必要跟着地址栏抖 */
    transform: translate3d(0, 110vh, 0);
  }
}

/* ---------- 左：宣传区 ---------- */
.key-promo {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.key-promo-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.key-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border: 1px solid var(--portal-brand-icon-border);
  border-radius: 15px;
  background-color: transparent;
  background-image: var(--portal-brand-icon);
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  box-shadow:
    0 0 0 3px var(--portal-brand-icon-ring),
    0 12px 30px rgba(37, 99, 235, 0.16),
    inset 0 0 14px rgba(37, 99, 235, 0.08);
}

.key-promo-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.key-promo-title {
  margin: 6px 0 0;
  font-size: clamp(30px, 3.4vw, 46px);
  line-height: 1.18;
  font-weight: 700;
  /* 大字号收紧字距是排版惯例；正文不要这么做 */
  letter-spacing: -0.02em;
}

.key-promo-sub {
  margin: 0;
  font-size: clamp(13px, 1.1vw, 15px);
  line-height: 1.7;
  color: var(--portal-fg-muted);
}

/* 能力清单做成一块"规格面板"：整块玻璃 + 行间发丝分隔，比四张散卡更整、更像 HUD */
.key-feats {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  border-radius: var(--portal-radius);
  overflow: hidden;
}

.key-feat {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  column-gap: 12px;
  row-gap: 2px;
  padding: 11px 14px;
}

.key-feat + .key-feat {
  border-top: 1px solid var(--portal-border);
}

.key-feat-icon {
  grid-row: 1 / span 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--portal-accent-soft);
  color: var(--portal-accent);
}

.key-feat-title {
  font-size: 13.5px;
  font-weight: 600;
}

.key-feat-desc {
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--portal-fg-muted);
}

/* ---------- 右：登录卡 ---------- */
.key-spin {
  width: 100%;
  max-width: 440px;
  margin-inline: auto;
}

/*
 * 玻璃卡片：半透明底 + 发丝描边 + 顶边高光（::before），
 * 四角再补两段短线（::after）呼应左侧的 HUD 语言。
 */
.key-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 28px 30px 24px;
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

/* 左上 / 右下两段直角短线。内缩 8px，免得贴着圆角被切掉 */
.key-card::after {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 10px;
  pointer-events: none;
  background:
    linear-gradient(var(--portal-fx-corner), var(--portal-fx-corner)) left top / 16px 1px no-repeat,
    linear-gradient(var(--portal-fx-corner), var(--portal-fx-corner)) left top / 1px 16px no-repeat,
    linear-gradient(var(--portal-fx-corner), var(--portal-fx-corner)) right bottom / 16px 1px no-repeat,
    linear-gradient(var(--portal-fx-corner), var(--portal-fx-corner)) right bottom / 1px 16px no-repeat;
}

.key-card-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.key-card-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.key-card-sub {
  margin: 0;
  font-size: 12.5px;
  color: var(--portal-fg-muted);
}

.key-alert {
  border-radius: 10px;
}

.key-field :deep(.n-form-item-label) {
  font-size: 12.5px;
  color: var(--portal-fg-muted);
}

/* 全页唯一的大面积渐变，hover 提亮、按下轻微缩放 */
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
  padding-top: 14px;
  border-top: 1px solid var(--portal-border);
  font-size: 12px;
  line-height: 1.7;
  color: var(--portal-fg-muted);
}

.key-hint-icon {
  flex: none;
  margin-top: 3px;
}

/* 原先这里有一条 "{{ appTitle }} · 对话门户" 页脚，已删：
   它与宣传区的品牌行、副标题完全重复，而且正好落在整体最底部——
   收边最重、地平线网格满遮罩的那一带，是 muted 文字最不该待的位置。 */

/* ---------- 单栏：宣传区塌缩成紧凑头部，仍排在登录卡前面 ---------- */
@media (max-width: 1023px) {
  .key-main {
    grid-template-columns: minmax(0, 1fr);
    justify-items: center;
    /* 单列时 align-items:center 会配合默认的 stretch 把两行各自撑开、
       于是宣传区被顶到上半区中央；改成整组居中，两块内容才挨在一起 */
    align-content: center;
    gap: 28px;
  }

  .key-promo {
    max-width: 34rem;
    align-items: center;
    text-align: center;
  }

  .key-promo-title {
    font-size: clamp(26px, 7vw, 34px);
  }

  /* 窄屏只留「标识 + 标语」：登录才是这一页的主任务，能力清单是次要内容，折起来 */
  .key-feats {
    display: none;
  }
}

/* 减少动态效果：扫描线与视差一律停用（视差在 JS 侧也已挡掉） */
@media (prefers-reduced-motion: reduce) {
  .key-fx-scan {
    animation: none;
    /* 停在视口外，而不是停在顶部当一条亮边 */
    transform: translate3d(0, -220px, 0);
  }

  /* 视差不写了（--fx-* 也不会被 JS 更新），但地平线网格本身的透视要留着，否则会摊平 */
  .key-fx-traces {
    transform: none;
  }

  .key-fx-floor {
    transform: perspective(560px) rotateX(58deg) scale(1.7);
  }
}
</style>
