<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NIcon, NInput, NSwitch, NTooltip, useThemeVars } from 'naive-ui'
import { Paperclip, Send } from 'lucide-vue-next'

/**
 * 输入区：文本 + 附件按钮 + 联网开关 + 发送。
 *
 * - 联网开关：Agent 关闭联网能力时置灰禁用；开启时按「本次是否联网」提交给后端。
 * - 附件：当前运行时没有附件/多模态接收链路（ChatBo 只有 query），故一律禁用并说明原因，
 *   避免出现"能选文件但发不出去"的假交互。
 */
const props = defineProps<{
  loading: boolean
  /** Agent 是否允许联网（false 时开关禁用并说明） */
  webSearchEnabled: boolean
  webSearch: boolean
}>()

const emit = defineEmits<{
  (e: 'send', query: string): void
  (e: 'update:webSearch', value: boolean): void
}>()

const themeVars = useThemeVars()
const query = ref('')

const attachDisabledReason = '当前版本运行时不支持上传附件（模型多模态链路尚未开放）'
const canSend = computed(() => !props.loading && query.value.trim().length > 0)
const placeholder = '输入你的问题，Enter 发送，Shift + Enter 换行'

function send() {
  if (!canSend.value) return
  const text = query.value.trim()
  query.value = ''
  emit('send', text)
}
</script>

<template>
  <div class="composer" :style="{ borderColor: themeVars.borderColor, background: themeVars.cardColor }">
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
            <n-button quaternary size="small" disabled :aria-label="'添加附件（暂不可用）'">
              <template #icon><n-icon :component="Paperclip" :size="16" /></template>
            </n-button>
          </template>
          {{ attachDisabledReason }}
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
</style>
