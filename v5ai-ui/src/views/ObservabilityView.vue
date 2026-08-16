<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { NAlert, NCard, NDataTable, NInput, NSelect, NStatistic, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import {
  getStatsOverview,
  listAgents,
  listAuditLogs,
  listUsage,
  type AuditLog,
  type OverviewResponse,
  type UsageRecord
} from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const overview = ref<OverviewResponse | null>(null)
const usage = ref<UsageRecord[]>([])
const audits = ref<AuditLog[]>([])
const apps = ref<{ agentKey: string }[]>([])
const filterAppKey = ref('')
const filterAction = ref('')

const usageColumns: DataTableColumns<UsageRecord> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: 'App', key: 'agentKey' },
  { title: '模型', key: 'modelKey' },
  { title: '输入 Token', key: 'promptTokens' },
  { title: '输出 Token', key: 'completionTokens' },
  { title: '合计', key: 'totalTokens' },
  { title: '耗时(ms)', key: 'durationMs', render: row => row.durationMs ?? '-' },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, { size: 'small', type: row.status === 'SUCCESS' ? 'success' : 'error' }, { default: () => row.status })
  },
  { title: '时间', key: 'createdAt', render: row => row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }
]

const auditColumns: DataTableColumns<AuditLog> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '时间', key: 'createdAt', render: row => row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' },
  { title: '操作者', key: 'actor', render: row => row.actor ?? '-' },
  { title: '动作', key: 'action', render: row => h(NTag, { size: 'small', type: row.action === 'LOGIN' || row.action === 'CREATE' ? 'info' : 'warning' }, { default: () => row.action }) },
  { title: '目标', key: 'targetType', render: row => row.targetType ? `${row.targetType}${row.targetId ? `#${row.targetId}` : ''}` : '-' },
  { title: '详情', key: 'detail', ellipsis: { tooltip: true } }
]

async function reloadUsage() {
  try {
    usage.value = await listUsage(adminToken.value, { agentKey: filterAppKey.value || undefined, limit: 100 })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载用量失败')
  }
}

async function reloadAudits() {
  try {
    audits.value = await listAuditLogs(adminToken.value, { action: filterAction.value || undefined, limit: 100 })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载审计失败')
  }
}

onMounted(async () => {
  if (!isLoggedIn.value) return
  try {
    const [overviewData, appRows] = await Promise.all([
      getStatsOverview(adminToken.value),
      listAgents(adminToken.value)
    ])
    overview.value = overviewData
    apps.value = appRows
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载统计失败')
  }
  await reloadUsage()
  await reloadAudits()
})
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再查看用量、审计与统计。
    </n-alert>

    <n-card title="平台总览" class="panel wide">
      <n-space size="large" wrap>
        <n-statistic label="Agent" :value="overview?.agents ?? 0" />
        <n-statistic label="模型" :value="overview?.models ?? 0" />
        <n-statistic label="知识库" :value="overview?.knowledgeBases ?? 0" />
        <n-statistic label="Skill" :value="overview?.skills ?? 0" />
        <n-statistic label="运行总数" :value="overview?.totalRuns ?? 0" />
        <n-statistic label="今日运行" :value="overview?.todayRuns ?? 0" />
        <n-statistic label="今日模型调用" :value="overview?.todayModelCalls ?? 0" />
        <n-statistic label="今日 Token" :value="overview?.todayTokens ?? 0" />
      </n-space>
    </n-card>

    <n-card title="用量明细" class="panel wide">
      <n-space style="margin-bottom: 12px" align="center">
        <n-select
          v-model:value="filterAppKey"
          filterable
          clearable
          placeholder="按 App 筛选"
          style="width: 220px"
          :options="apps.map(a => ({ label: a.agentKey, value: a.agentKey }))"
          @update:value="reloadUsage"
        />
      </n-space>
      <n-data-table :columns="usageColumns" :data="usage" />
    </n-card>

    <n-card title="审计日志" class="panel wide">
      <n-space style="margin-bottom: 12px" align="center">
        <n-input v-model:value="filterAction" placeholder="按动作筛选（如 CREATE / LOGIN）" style="width: 220px" clearable @update:value="reloadAudits" />
      </n-space>
      <n-data-table :columns="auditColumns" :data="audits" />
    </n-card>
  </div>
</template>
