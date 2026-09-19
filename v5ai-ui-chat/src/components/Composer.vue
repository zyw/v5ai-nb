<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { NButton, NIcon, NInput, NSwitch, NTooltip, useMessage } from 'naive-ui'
import { Paperclip, Send, Square, X } from 'lucide-vue-next'
import {
  ACCEPTED_ATTACHMENT_TYPES,
  MAX_ATTACHMENT_BYTES,
  MAX_ATTACHMENTS_PER_MESSAGE
} from '../api/client'

/**
 * 输入区：文本 + 图片附件 + 联网开关 + 发送/停止。
 *
 * - 发送与停止共用右侧那一个按钮：空闲时是发送（纸飞机），回答生成中变成停止（方块），
 *   点停止或本轮输出结束都会变回发送。
 * - 附件：Agent 所绑模型未声明 image 能力时入口禁用（后端也会 400）；
 *   选中即做客户端预校验（类型 / 单张体积 / 数量）并给出缩略图，上传发生在发送时。
 * - 联网开关：Agent 关闭联网能力时置灰禁用；开启时按「本次是否联网」提交给后端。
 */
const props = defineProps<{
  loading: boolean
  /** Agent 是否允许联网（false 时开关禁用并说明） */
  webSearchEnabled: boolean
  webSearch: boolean
  /** 所绑模型是否支持图片输入（false 时附件入口禁用） */
  imageSupported: boolean
}>()

const emit = defineEmits<{
  (e: 'send', query: string, files: File[]): void
  (e: 'stop'): void
  (e: 'update:webSearch', value: boolean): void
}>()

const message = useMessage()
const query = ref('')
const pending = ref<{ file: File; url: string }[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

const attachDisabledReason = '当前 Agent 绑定的模型不支持图片输入'
const canSend = computed(() => !props.loading && (query.value.trim().length > 0 || pending.value.length > 0))
const placeholder = '输入你的问题，Enter 发送，Shift + Enter 换行'

function send() {
  if (!canSend.value) return
  const text = query.value.trim()
  const files = pending.value.map((item) => item.file)
  query.value = ''
  clearPending()
  emit('send', text, files)
}

function pickFiles() {
  if (!props.imageSupported || props.loading) return
  fileInput.value?.click()
}

function onFilesChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const chosen = Array.from(input.files ?? [])
  input.value = ''
  for (const file of chosen) {
    if (pending.value.length >= MAX_ATTACHMENTS_PER_MESSAGE) {
      message.warning(`每条消息最多 ${MAX_ATTACHMENTS_PER_MESSAGE} 张图片`)
      break
    }
    if (!ACCEPTED_ATTACHMENT_TYPES.includes(file.type)) {
      message.warning('只支持 PNG / JPEG / WebP 图片')
      continue
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      message.warning(`单张图片不能超过 ${Math.round(MAX_ATTACHMENT_BYTES / 1024 / 1024)}MB`)
      continue
    }
    pending.value.push({ file, url: URL.createObjectURL(file) })
  }
}

function removePending(index: number) {
  const [removed] = pending.value.splice(index, 1)
  if (removed) URL.revokeObjectURL(removed.url)
}

function clearPending() {
  for (const item of pending.value) URL.revokeObjectURL(item.url)
  pending.value = []
}

onBeforeUnmount(clearPending)
</script>

<template>
  <div class="composer">
    <div v-if="pending.length" class="composer-attachments">
      <div v-for="(item, index) in pending" :key="item.url" class="attachment">
        <img :src="item.url" :alt="item.file.name" />
        <n-button
          text
          size="tiny"
          class="attachment-remove"
          :aria-label="`移除附件 ${item.file.name}`"
          @click="removePending(index)"
        >
          <template #icon><n-icon :component="X" :size="12" /></template>
        </n-button>
      </div>
    </div>

    <n-input
      v-model:value="query"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 6 }"
      :placeholder="placeholder"
      :disabled="loading"
      class="composer-input"
      :input-props="{ 'aria-label': '输入你的问题' }"
      @keydown.enter.exact.prevent="send"
    />

    <div class="composer-actions">
      <div class="composer-tools">
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button
              quaternary
              size="small"
              :disabled="!imageSupported || loading"
              :aria-label="imageSupported ? '添加图片' : '添加图片（模型不支持）'"
              @click="pickFiles"
            >
              <template #icon><n-icon :component="Paperclip" :size="16" /></template>
            </n-button>
          </template>
          {{
            imageSupported
              ? `添加图片（最多 ${MAX_ATTACHMENTS_PER_MESSAGE} 张，单张不超过 ${Math.round(MAX_ATTACHMENT_BYTES / 1024 / 1024)}MB）`
              : attachDisabledReason
          }}
        </n-tooltip>

        <n-tooltip trigger="hover">
          <template #trigger>
            <span class="composer-switch">
              <n-switch
                size="small"
                :value="webSearch"
                :disabled="!webSearchEnabled"
                :aria-label="'本次是否联网搜索'"
                @update:value="(value: boolean) => emit('update:webSearch', value)"
              />
              <span class="composer-switch-label" :class="{ 'is-disabled': !webSearchEnabled }">联网搜索</span>
            </span>
          </template>
          {{
            webSearchEnabled
              ? '打开后本次提问会检索网页（可能较慢）'
              : '当前 Agent 未开启联网搜索能力，无法使用'
          }}
        </n-tooltip>
      </div>

      <n-tooltip trigger="hover">
        <template #trigger>
          <!-- 包一层 span：naive-ui 的禁用按钮是原生 disabled，收不到 hover，提示就出不来 -->
          <span class="composer-send">
            <n-button
              v-if="loading"
              circle
              type="primary"
              size="medium"
              aria-label="停止生成"
              @click="emit('stop')"
            >
              <template #icon><n-icon :component="Square" :size="16" class="stop-icon" /></template>
            </n-button>
            <n-button
              v-else
              circle
              type="primary"
              size="medium"
              :disabled="!canSend"
              :aria-label="canSend ? '发送' : '请输入你的问题'"
              @click="send"
            >
              <template #icon><n-icon :component="Send" :size="18" /></template>
            </n-button>
          </span>
        </template>
        {{ loading ? '停止生成' : canSend ? '发送' : '请输入你的问题' }}
      </n-tooltip>
    </div>

    <input
      ref="fileInput"
      class="composer-file"
      type="file"
      accept="image/png,image/jpeg,image/webp"
      multiple
      tabindex="-1"
      aria-hidden="true"
      @change="onFilesChosen"
    />
  </div>
</template>

<style scoped>
.composer {
  border: 1px solid var(--portal-border);
  border-radius: var(--portal-radius-lg);
  padding: 8px 8px 6px;
  background: var(--portal-surface);
  backdrop-filter: blur(18px) saturate(140%);
  -webkit-backdrop-filter: blur(18px) saturate(140%);
  transition: border-color 0.18s var(--portal-ease), box-shadow 0.18s var(--portal-ease);
}

/* 聚焦时给输入区一圈强调色光环，提示「现在可以打字」 */
.composer:focus-within {
  border-color: var(--portal-accent);
  box-shadow: 0 0 0 3px var(--portal-accent-soft);
}

.composer-input :deep(.n-input__border),
.composer-input :deep(.n-input__state-border) {
  display: none;
}

.composer-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 2px 2px 6px;
}

.attachment {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--portal-border);
}

.attachment img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.attachment-remove {
  position: absolute;
  top: 0;
  right: 0;
  padding: 2px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  border-bottom-left-radius: 6px;
}

.composer-send {
  display: inline-flex;
}

/*
 * 禁用态（输入框空着）别用 naive 默认的「整块 50% 透明主色」：浅色主题下白图标会糊在淡底上，
 * 远看就是「图标没了」。改成中性底 + 次级前景色：既说明「现在还不能发」，又保证图标可辨。
 */
.composer-send :deep(.n-button--disabled) {
  opacity: 1;
  background-color: var(--portal-surface-2);
  color: var(--portal-fg-muted);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.composer-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.composer-switch-label {
  font-size: 12px;
}

.composer-switch-label.is-disabled {
  opacity: 0.45;
}

.composer-file {
  display: none;
}

/*
 * 停止按钮用实心方块：lucide 的 Square 只有描边（fill="none"），
 * 而 n-icon 的内部属性不向图标透传（naive-ui 把 $attrs 留在外层 <i> 上），
 * 所以在这里直接把 svg 填充成图标自身的颜色。
 */
.stop-icon :deep(svg) {
  fill: currentColor;
}
</style>
