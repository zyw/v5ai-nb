<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getSliderCaptcha, verifySliderCaptcha, type SliderCaptcha } from '../api/client'

const emit = defineEmits<{ success: [uuid: string] }>()

/** 与后端 SliderCaptchaService 约定的图片尺寸/拼图块尺寸（自然像素）。 */
const WIDTH = 320
const PIECE = 40

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

const sliderPercent = () => (pieceX.value / (WIDTH - PIECE)) * 100

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

function onPointerDown(e: PointerEvent) {
  if (loading.value || verifying.value || verified.value) return
  dragging.value = true
  startClientX = e.clientX
  startPieceX = pieceX.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const delta = e.clientX - startClientX
  pieceX.value = Math.min(WIDTH - PIECE, Math.max(0, startPieceX + delta))
}

async function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
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

onMounted(loadCaptcha)
</script>

<template>
  <div class="captcha" :class="{ shake }">
    <div class="captcha-image">
      <img v-if="backgroundUrl" :src="backgroundUrl" width="320" height="160" alt="滑块背景" />
      <div v-else class="captcha-image-loading">拼图加载中…</div>
      <div
        v-if="puzzleUrl && !verified"
        class="captcha-piece"
        :style="{ left: pieceX + 'px', top: gapY + 'px', backgroundImage: `url(${puzzleUrl})` }"
      />
    </div>
    <div
      class="captcha-track"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
    >
      <div class="captcha-progress" :style="{ width: sliderPercent() + '%' }" />
      <div class="captcha-handle" :style="{ left: pieceX + 'px' }">
        <span>{{ verified ? '✓' : '→' }}</span>
      </div>
    </div>
    <p class="captcha-tip" :class="{ ok: verified }">
      {{ error || (verified ? '验证通过' : loading ? '拼图加载中…' : '拖动滑块完成拼图') }}
    </p>
  </div>
</template>

<style scoped>
.captcha {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
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

.captcha-image {
  position: relative;
  width: 320px;
  height: 160px;
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid rgba(128, 128, 128, 0.25);
}

.captcha-image-loading {
  display: grid;
  place-items: center;
  height: 100%;
  font-size: 12px;
  opacity: 0.5;
}

.captcha-piece {
  position: absolute;
  width: 40px;
  height: 40px;
  background-size: 40px 40px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
  pointer-events: none;
}

.captcha-track {
  position: relative;
  width: 320px;
  height: 40px;
  border-radius: 20px;
  background: rgba(128, 128, 128, 0.12);
  border: 1px solid rgba(128, 128, 128, 0.22);
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
  border-radius: 20px;
  background: rgba(24, 160, 88, 0.25);
  transition: width 0.1s linear;
}

.captcha-handle {
  position: absolute;
  top: 0;
  left: 0;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--n-color, #fff);
  color: var(--n-text-color, #333);
  border: 1px solid rgba(128, 128, 128, 0.35);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
  font-size: 15px;
}

.captcha-tip {
  margin: 0;
  font-size: 12px;
  text-align: center;
  opacity: 0.7;
}

.captcha-tip.ok {
  opacity: 1;
  color: #18a058;
}
</style>
