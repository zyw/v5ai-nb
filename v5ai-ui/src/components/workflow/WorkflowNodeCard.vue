<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{ data: any; selected: boolean }>()

const TYPE_LABELS: Record<string, string> = {
  START: '开始',
  AGENT: 'Agent',
  CONDITION: '条件',
  END: '结束'
}
</script>

<template>
  <div
    class="wf-card"
    :class="[`type-${String(data.nodeType).toLowerCase()}`, { selected }]"
  >
    <Handle v-if="data.nodeType !== 'START'" type="target" :position="Position.Left" id="in" />
    <div class="wf-card-head">
      <span class="wf-card-type">{{ TYPE_LABELS[data.nodeType] ?? data.nodeType }}</span>
      <span class="wf-card-name">{{ data.name }}</span>
    </div>
    <div v-if="data.nodeType === 'AGENT'" class="wf-card-sub">
      {{ data.config?.agentKey || '未选择 Agent' }}
    </div>
    <div v-else-if="data.nodeType === 'CONDITION'" class="wf-card-sub">
      {{ data.config?.left || '…' }} {{ data.config?.operator || '==' }} {{ data.config?.right || '…' }}
    </div>

    <template v-if="data.nodeType === 'CONDITION'">
      <Handle type="source" :position="Position.Right" id="true" class="wf-handle wf-handle-true" />
      <Handle type="source" :position="Position.Right" id="false" class="wf-handle wf-handle-false" />
      <span class="wf-handle-label wf-handle-label-true">真</span>
      <span class="wf-handle-label wf-handle-label-false">假</span>
    </template>
    <Handle v-else-if="data.nodeType !== 'END'" type="source" :position="Position.Right" id="out" />
  </div>
</template>

<style scoped>
.wf-card {
  position: relative;
  min-width: 170px;
  max-width: 220px;
  padding: 10px 14px;
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.24));
  border-radius: 10px;
  background: var(--n-color, #fff);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  font-size: 13px;
  cursor: pointer;
}

.wf-card.selected {
  border-color: #7c3aed;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.25);
}

.wf-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wf-card-type {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: #64748b;
}

.type-start .wf-card-type { background: #0891b2; }
.type-agent .wf-card-type { background: #7c3aed; }
.type-condition .wf-card-type { background: #d97706; }
.type-end .wf-card-type { background: #16a34a; }

.wf-card-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-card-sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-handle-true { top: 30% !important; }
.wf-handle-false { top: 70% !important; }

.wf-handle-label {
  position: absolute;
  right: 6px;
  font-size: 10px;
  color: #d97706;
  transform: translateY(-50%);
  pointer-events: none;
}

.wf-handle-label-true { top: 30%; }
.wf-handle-label-false { top: 70%; }
</style>
