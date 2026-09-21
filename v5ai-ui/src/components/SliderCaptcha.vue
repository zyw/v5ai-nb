<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getSliderCaptcha, verifySliderCaptcha, type SliderCaptcha } from '../api/client'

const emit = defineEmits<{ success: [uuid: string] }>()

/**
 * 与后端 SliderCaptchaService 约定的图片尺寸/拼图块尺寸（自然像素）。
 * 这两个常量是**逻辑坐标系**，与渲染尺寸解耦：容器宽度变化时靠百分比定位 +
 * 拖拽时量出的 dragScale 换算，逻辑坐标始终保持 0..WIDTH。
 */
const WIDTH = 320
const HEIGHT = 160
const PIECE = 40
const MAX_PIECE_X = WIDTH - PIECE

const loading = ref(false)
const verifying = ref(false)
const error = ref('')
const uuid = ref('')
const backgroundUrl = ref('')
const puzzleUrl = ref('')
const gapY = ref(0)
const pieceX = ref(0)
const dragging = ref(false)
const verified = ref(false)
const shake = ref(false)

/** 逻辑像素 → 容器百分比（容器保持 2:1，与自然尺寸同比例）。 */
const toPercentX = (logicalX: number) => (logicalX / WIDTH) * 100
const toPercentY = (logicalY: number) => (logicalY / HEIGHT) * 100
const sliderPercent = () => (pieceX.value / MAX_PIECE_X) * 100

const dragScale = ref(1)

let startClientX = 0
let startPieceX = 0

async function loadCaptcha() {
  loading.value = true
  error.value = ''
  verified.value = false
  pieceX.value = 0
  try {
    const captcha: SliderCaptcha = await getSliderCaptcha()
    uuid.value = captcha.uuid
    backgroundUrl.value = `data:image/png;base64,${captcha.background}`
    puzzleUrl.value = `data:image/png;base64,${captcha.puzzle}`
    gapY.value = captcha.y
  } catch (e) {
    error.value = e instanceof Error ? e.message : '验证码加载失败'
  } finally {
    loading.value = false
  }
}

/** 提交当前滑块位置校验；拖拽与键盘两条路径共用，行为完全一致。 */
async function submit() {
  if (verifying.value || verified.value) return
  verifying.value = true
  error.value = ''
  try {
    await verifySliderCaptcha(uuid.value, pieceX.value)
    verified.value = true
    emit('success', uuid.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '滑块验证失败'
    shake.value = true
    window.setTimeout(() => (shake.value = false), 400)
    await loadCaptcha()
  } finally {
    verifying.value = false
  }
}

function onPointerDown(e: PointerEvent) {
  if (loading.value || verifying.value || verified.value) return
  dragging.value = true
  startClientX = e.clientX
  startPieceX = pieceX.value
  // 容器可能被拉宽/收窄，量一次换算系数：屏幕像素 ÷ 逻辑像素。
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  dragScale.value = rect.width > 0 ? rect.width / WIDTH : 1
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const delta = (e.clientX - startClientX) / dragScale.value
  pieceX.value = Math.min(MAX_PIECE_X, Math.max(0, startPieceX + delta))
}

async function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
  await submit()
}

/**
 * 键盘替代路径（WCAG 2.2 dragging-alternative）：滑块不能只有拖拽一种操作方式。
 * ←/→ 移动 5 逻辑像素（按住 Shift 为 20），Enter/空格提交。
 */
function onKeydown(e: KeyboardEvent) {
  if (loading.value || verifying.value || verified.value) return
  const step = e.shiftKey ? 20 : 5
  if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
    pieceX.value = Math.max(0, pieceX.value - step)
  } else if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
    pieceX.value = Math.min(MAX_PIECE_X, pieceX.value + step)
  } else if (e.key === 'Enter' || e.key === ' ') {
    void submit()
  } else {
    return
  }
  e.preventDefault()
}

onMounted(loadCaptcha)
</script>

<template>
  <!-- 该组件全站只在登录页渲染一处，故提示文案用固定 id 供 aria-describedby 引用。 -->
  <div class="captcha" :class="{ shake }">
    <div class="captcha-image">
      <!-- 拼图背景没有文字等价物（这是纯视觉谜题），语义由下方滑块与状态文案承担。 -->
      <img v-if="backgroundUrl" :src="backgroundUrl" width="320" height="160" alt="" />
      <div v-else class="captcha-image-loading">拼图加载中…</div>
      <div
        v-if="puzzleUrl && !verified"
        class="captcha-piece"
        :style="{
          left: toPercentX(pieceX) + '%',
          top: toPercentY(gapY) + '%',
          backgroundImage: `url(${puzzleUrl})`
        }"
      />
    </div>
    <div
      class="captcha-track"
      role="slider"
      tabindex="0"
      aria-label="滑块验证"
      aria-describedby="captcha-tip"
      :aria-valuemin="0"
      :aria-valuemax="MAX_PIECE_X"
      :aria-valuenow="Math.round(pieceX)"
      :aria-disabled="verified"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @keydown="onKeydown"
    >
      <div class="captcha-progress" :style="{ width: sliderPercent() + '%' }" />
      <div class="captcha-handle" :style="{ left: toPercentX(pieceX) + '%' }">
        <span>{{ verified ? '✓' : '→' }}</span>
      </div>
    </div>
    <p id="captcha-tip" class="captcha-tip" :class="{ ok: verified }" role="status">
      {{ error || (verified ? '验证通过' : loading ? '拼图加载中…' : '拖动滑块完成拼图') }}
    </p>
  </div>
</template>

<style scoped>
/*
 * 自带调色板：不使用 Naive 的 --n-* 变量——那些变量只挂在带 Naive 组件 class 的元素上，
 * NConfigProvider 并不向 :root 注入，所以 .captcha-handle 里的 var(--n-color, #fff)
 * 实际永远取 fallback（曾因此既不适配暗色、也无法跟随主题）。
 */
.captcha {
  --captcha-line: rgba(15, 23, 42, 0.16);
  --captcha-track-bg: rgba(15, 23, 42, 0.05);
  --captcha-handle-bg: #ffffff;
  --captcha-handle-ink: #334155;
  --captcha-ok: #10b981;
  --captcha-ok-soft: rgba(16, 185, 129, 0.18);

  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  margin-top: 4px;
}

:root[data-theme='dark'] .captcha {
  --captcha-line: rgba(255, 255, 255, 0.16);
  --captcha-track-bg: rgba(255, 255, 255, 0.06);
  --captcha-handle-bg: #e8eaf0;
  --captcha-handle-ink: #1f2430;
}

.captcha.shake {
  animation: captcha-shake 0.4s ease;
}

@keyframes captcha-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-6px);
  }
  75% {
    transform: translateX(6px);
  }
}

/* 流体尺寸：容器定 2:1，图片与拼图块按百分比缩放，逻辑坐标不受影响。 */
.captcha-image {
  position: relative;
  width: 100%;
  aspect-ratio: 320 / 160;
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid var(--captcha-line);
}

.captcha-image img {
  display: block;
  width: 100%;
  height: 100%;
}

.captcha-image-loading {
  display: grid;
  place-items: center;
  height: 100%;
  font-size: 12px;
  color: var(--login-ink-muted, inherit);
}

.captcha-piece {
  position: absolute;
  width: 12.5%; /* 40 / 320 */
  height: 25%; /* 40 / 160 */
  background-size: 100% 100%;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
  pointer-events: none;
}

/* 轨道同样按 8:1 定比例（320:40），于是滑块宽度 = 轨道高度，恒为正方形不会溢出轨道。 */
.captcha-track {
  position: relative;
  width: 100%;
  aspect-ratio: 8 / 1;
  border-radius: 999px;
  background: var(--captcha-track-bg);
  border: 1px solid var(--captcha-line);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.captcha-track:active {
  cursor: grabbing;
}

.captcha-progress {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  border-radius: 999px;
  background: var(--captcha-ok-soft);
  transition: width 0.1s linear;
}

.captcha-handle {
  position: absolute;
  top: 0;
  left: 0;
  width: 12.5%; /* 40 / 320，与拼图块同尺寸 */
  height: 100%;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--captcha-handle-bg);
  color: var(--captcha-handle-ink);
  border: 1px solid var(--captcha-line);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.22);
  font-size: 15px;
}

.captcha-tip {
  margin: 0;
  font-size: 12px;
  text-align: center;
  color: var(--login-ink-muted, inherit);
}

.captcha-tip.ok {
  color: var(--captcha-ok);
}
</style>
