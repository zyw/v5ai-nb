<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { NButton, NIcon, NInput, NSwitch, NTooltip, useMessage, useThemeVars } from 'naive-ui'
import { Paperclip, Send, X } from 'lucide-vue-next'
import {
  ACCEPTED_ATTACHMENT_TYPES,
  MAX_ATTACHMENT_BYTES,
  MAX_ATTACHMENTS_PER_MESSAGE
} from '../api/client'

/**
 * 输入区：文本 + 图片附件 + 联网开关 + 发送。
 *
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
  (e: 'update:webSearch', value: boolean): void
}>()

const themeVars = useThemeVars()
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
  <div class="composer" :style="{ borderColor: themeVars.borderColor, background: themeVars.cardColor }">
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

      <n-button type="primary" size="small" :loading="loading" :disabled="!canSend" aria-label="发送" @click="send">
        <template #icon><n-icon :component="Send" :size="16" /></template>
      </n-button>
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
  border: 1px solid;
  border-radius: 12px;
  padding: 8px 8px 6px;
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
  border: 1px solid rgba(127, 127, 127, 0.25);
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
</style>
