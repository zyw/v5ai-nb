<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = withDefaults(
  defineProps<{ status?: string | null; label?: string }>(),
  { status: undefined, label: '' }
)

type TagType = 'default' | 'success' | 'info' | 'warning' | 'error'

const MAP: Record<string, { type: TagType; label: string }> = {
  ACTIVE: { type: 'success', label: '启用' },
  ENABLED: { type: 'success', label: '启用' },
  PUBLISHED: { type: 'success', label: '已发布' },
  COMPLETED: { type: 'success', label: '已完成' },
  SUCCESS: { type: 'success', label: '成功' },
  DISABLED: { type: 'default', label: '停用' },
  INACTIVE: { type: 'default', label: '停用' },
  SUSPENDED: { type: 'default', label: '停用' },
  DRAFT: { type: 'info', label: '草稿' },
  OFFLINE: { type: 'default', label: '已下线' },
  PENDING: { type: 'warning', label: '待处理' },
  PROCESSING: { type: 'info', label: '处理中' },
  RUNNING: { type: 'info', label: '运行中' },
  SUCCEEDED: { type: 'success', label: '成功' },
  SKIPPED: { type: 'default', label: '跳过' },
  FAILED: { type: 'error', label: '失败' },
  ERROR: { type: 'error', label: '错误' }
}

const normalized = computed(() => (props.status ?? '').trim().toUpperCase())
const entry = computed(() => MAP[normalized.value])
const type = computed<TagType>(() => entry.value?.type ?? 'default')
const text = computed(() => props.label || entry.value?.label || props.status || '—')
</script>

<template>
  <n-tag v-if="normalized" :type="type" size="small" :bordered="false" round>
    {{ text }}
  </n-tag>
  <span v-else class="muted">—</span>
</template>
