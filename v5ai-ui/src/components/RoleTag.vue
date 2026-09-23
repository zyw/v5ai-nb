<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps<{ role: string }>()

const type = computed<'warning' | 'default' | 'info'>(() => {
  if (props.role === 'ADMIN') return 'warning'
  if (props.role === 'USER') return 'default'
  return 'info'
})

const label = computed(() => {
  switch (props.role) {
    case 'ADMIN':
      return '管理员'
    case 'USER':
      return '成员'
    default:
      return props.role
  }
})
</script>

<template>
  <n-tag :type="type" size="small" :bordered="false" round>{{ label }}</n-tag>
</template>
