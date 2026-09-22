<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NCard,
  NDataTable,
  NPagination,
  NSelect,
  NSpace,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  listAgents,
  listUsage,
  type UsageRecord
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const usage = ref<UsageRecord[]>([])
const apps = ref<{ agentKey: string }[]>([])
const filterAppKey = ref('')
const usagePagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const usageColumns: DataTableColumns<UsageRecord> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: 'App', key: 'agentKey' },
  { title: '模型 ID', key: 'modelId', width: 90, render: (row) => row.modelId ?? '-' },
  { title: '模型 Key', key: 'modelKey' },
  { title: '输入 Token', key: 'promptTokens' },
  { title: '输出 Token', key: 'completionTokens' },
  { title: '合计', key: 'totalTokens' },
  { title: '耗时(ms)', key: 'durationMs', render: (row) => row.durationMs ?? '-' },
  { title: '状态', key: 'status', width: 100, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '时间', key: 'createdAt', width: 180, render: (row) => (row.createdAt ? (formatDateTime(row.createdAt) ?? '-') : '-') }
]

async function reloadApps() {
  try {
    const appRows = await listAgents(adminToken.value)
    apps.value = appRows.rows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Agent 列表失败')
  }
}

async function reloadUsage() {
  try {
    const rows = await listUsage(adminToken.value, {
      agentKey: filterAppKey.value || undefined,
      pageNum: usagePagination.page,
      pageSize: usagePagination.pageSize
    })
    usage.value = rows.rows
    usagePagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载用量失败')
  }
}

function handleUsageFilterChange() {
  usagePagination.page = 1
  void reloadUsage()
}

function handleUsagePageChange(page: number) {
  usagePagination.page = page
  void reloadUsage()
}

function handleUsagePageSizeChange(pageSize: number) {
  usagePagination.pageSize = pageSize
  usagePagination.page = 1
  void reloadUsage()
}

onMounted(async () => {
  await Promise.all([reloadApps(), reloadUsage()])
})
</script>

<template>
  <div class="page">
    <PageHeader title="可观测性" description="查看 Agent 用量明细。（审计日志已迁移至 系统管理 → 日志管理）" />

    <n-card title="用量明细" :bordered="true">
      <n-space style="margin-bottom: 12px" align="center">
        <n-select
          v-model:value="filterAppKey"
          filterable
          clearable
          placeholder="按 App 筛选"
          style="width: 220px"
          :options="apps.map((a) => ({ label: a.agentKey, value: a.agentKey }))"
          @update:value="handleUsageFilterChange"
        />
      </n-space>
      <n-data-table :columns="usageColumns" :data="usage" :bordered="false" />
      <n-pagination
        :page="usagePagination.page"
        :page-size="usagePagination.pageSize"
        :item-count="usagePagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="handleUsagePageChange"
        @update:page-size="handleUsagePageSizeChange"
      />
    </n-card>
  </div>
</template>
