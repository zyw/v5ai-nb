<script lang="ts">
export interface RowAction {
  key: string
  label: string
  type?: 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'
  secondary?: boolean
  quaternary?: boolean
  disabled?: boolean
  loading?: boolean
  /** 二次确认文案；设置后点击前需确认（内联按钮用 Popconfirm，更多菜单用 Dialog） */
  confirm?: string
  onClick?: () => void
}
</script>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NButton, NDropdown, NIcon, NPopconfirm, NSpace, useDialog, useThemeVars, type DropdownOption } from 'naive-ui'
import { ChevronDown } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    actions: RowAction[]
    /** 直接展示的最大按钮数，超出部分收进「更多」下拉，默认 2 */
    maxInline?: number
  }>(),
  { maxInline: 2 }
)

const dialog = useDialog()
const themeVars = useThemeVars()

/** NButton type -> naive 主题变量名：下拉项按 type 渲染同色文本 */
const TYPE_THEME_KEYS: Partial<Record<NonNullable<RowAction['type']>, string>> = {
  primary: 'primaryColor',
  info: 'infoColor',
  success: 'successColor',
  warning: 'warningColor',
  error: 'errorColor'
}

const inlineActions = computed(() => props.actions.slice(0, props.maxInline))
const moreActions = computed(() => props.actions.slice(props.maxInline))

const moreOptions = computed<DropdownOption[]>(() =>
  moreActions.value.map((action) => {
    const themeKey = action.type ? TYPE_THEME_KEYS[action.type] : undefined
    const color = themeKey ? (themeVars.value as unknown as Record<string, string>)[themeKey] : undefined
    return {
      key: action.key,
      label: color ? () => h('span', { style: { color } }, action.label) : action.label,
      disabled: action.disabled
    }
  })
)

function runAction(action: RowAction) {
  if (!action.onClick) return
  if (action.confirm) {
    dialog.warning({
      title: '确认操作',
      content: action.confirm,
      positiveText: '确认',
      negativeText: '取消',
      onPositiveClick: action.onClick
    })
    return
  }
  action.onClick()
}

function handleMoreSelect(key: string) {
  const action = moreActions.value.find((item) => item.key === key)
  if (action && !action.disabled) runAction(action)
}
</script>

<template>
  <n-space :size="4" :wrap="false">
    <template v-for="action in inlineActions" :key="action.key">
      <n-popconfirm v-if="action.confirm" :disabled="action.disabled" @positive-click="action.onClick">
        <template #trigger>
          <n-button
            size="small"
            :type="action.type"
            :secondary="action.secondary"
            :quaternary="action.quaternary"
            :disabled="action.disabled"
            :loading="action.loading"
          >
            {{ action.label }}
          </n-button>
        </template>
        {{ action.confirm }}
      </n-popconfirm>
      <n-button
        v-else
        size="small"
        :type="action.type"
        :secondary="action.secondary"
        :quaternary="action.quaternary"
        :disabled="action.disabled"
        :loading="action.loading"
        @click="action.onClick"
      >
        {{ action.label }}
      </n-button>
    </template>

    <n-dropdown v-if="moreActions.length > 0" :options="moreOptions" @select="handleMoreSelect">
      <n-button size="small" quaternary>
        更多
        <template #icon>
          <n-icon :component="ChevronDown" :size="14" />
        </template>
      </n-button>
    </n-dropdown>
  </n-space>
</template>
