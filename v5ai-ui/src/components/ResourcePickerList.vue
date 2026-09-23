<script setup lang="ts">
/**
 * 列表式资源选择器（替代下拉框）：搜索 + 逐行「图标/名称/描述」选择。
 * 单选（模型）：点行即选中并关闭；多选（Skill/MCP/知识库）：勾选 + 底部「已选 N 项 / 确定」。
 */
import { computed, ref, watch } from 'vue'
import { NButton, NEmpty, NIcon, NInput, NModal, NSpace, NTag } from 'naive-ui'
import { Check, Search } from 'lucide-vue-next'
import ResourceIcon from './ResourceIcon.vue'
import type { ResourceOption } from '../api/client'

const props = defineProps<{
  show: boolean
  title: string
  kind: 'model' | 'skill' | 'mcp' | 'rag'
  multiple: boolean
  items: ResourceOption[]
  selectedIds: number[]
}>()

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'select', id: number): void
  (e: 'confirm', ids: number[]): void
}>()

const keyword = ref('')
const draft = ref<number[]>([])

watch(
  () => props.show,
  (open) => {
    if (open) {
      keyword.value = ''
      draft.value = [...props.selectedIds]
    }
  }
)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return props.items
  return props.items.filter((it) => it.name.toLowerCase().includes(k) || (it.description ?? '').toLowerCase().includes(k))
})

function isSelected(id: number) {
  return draft.value.includes(id)
}

function toggle(id: number) {
  if (props.multiple) {
    const i = draft.value.indexOf(id)
    if (i >= 0) draft.value.splice(i, 1)
    else draft.value.push(id)
    return
  }
  emit('select', id)
  emit('update:show', false)
}

function confirm() {
  emit('confirm', [...draft.value])
  emit('update:show', false)
}
</script>

<template>
  <n-modal :show="show" preset="card" :title="title" style="width: 560px" :bordered="false" @update:show="(v) => emit('update:show', v)">
    <n-input v-model:value="keyword" placeholder="搜索名称或描述" clearable style="margin-bottom: 12px">
      <template #prefix><n-icon :component="Search" /></template>
    </n-input>

    <div class="picker-list">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="picker-item"
        :class="{ 'is-selected': isSelected(item.id) }"
        @click="toggle(item.id)"
      >
        <ResourceIcon :kind="kind" :icon="item.icon" :size="36" />
        <div class="picker-item-main">
          <div class="picker-item-name">{{ item.name }}</div>
          <div v-if="item.description" class="picker-item-desc">{{ item.description }}</div>
        </div>
        <n-tag v-if="item.meta" size="small" :bordered="false" type="info">{{ item.meta }}</n-tag>
        <span v-if="isSelected(item.id)" class="picker-check"><n-icon :component="Check" size="14" /></span>
      </div>
      <n-empty v-if="!filtered.length" description="没有匹配的条目" style="padding: 24px 0" />
    </div>

    <template #footer>
      <n-space justify="end" align="center">
        <span v-if="multiple" style="margin-right: auto; font-size: 13px; color: #888">已选 {{ draft.length }} 项</span>
        <n-button @click="emit('update:show', false)">取消</n-button>
        <n-button v-if="multiple" type="primary" @click="confirm">确定</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<style scoped>
.picker-list {
  max-height: 380px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.picker-item:hover {
  background: rgba(128, 128, 128, 0.08);
}
.picker-item.is-selected {
  border-color: transparent;
  background: rgba(24, 160, 88, 0.08);
}
.picker-item-main {
  flex: 1;
  min-width: 0;
}
.picker-item-name {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.picker-item-desc {
  font-size: 12px;
  color: rgba(120, 120, 120, 0.95);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.picker-check {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #18a058;
}
</style>