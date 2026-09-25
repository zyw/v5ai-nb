<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NIcon,
  NInput,
  NPagination,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  getApiKeyUsageStats,
  listAgents,
  listUsage,
  type ApiKeyUsageSummary,
  type UsageRecord
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const activeTab = ref('agent')
const usage = ref<UsageRecord[]>([])
const apps = ref<{ agentKey: string }[]>([])
const filterAppKey = ref('')
const agentUsageDateRange = ref<[number, number] | null>(null)
const usageLoading = ref(false)
const usagePagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const apiKeyUsage = ref<ApiKeyUsageSummary[]>([])
const apiKeyUsageLoading = ref(false)
const apiKeySearch = ref('')
const apiKeySearchInput = ref('')

function localDateString(timestamp: number): string {
  const date = new Date(timestamp)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function localDayIso(timestamp: number, endOfDay = false): string {
  const date = new Date(timestamp)
  if (endOfDay) date.setHours(23, 59, 59, 999)
  else date.setHours(0, 0, 0, 0)
  return date.toISOString()
}

function defaultDateRange(): [number, number] {
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  const start = new Date()
  start.setDate(start.getDate() - 13)
  start.setHours(0, 0, 0, 0)
  return [start.getTime(), end.getTime()]
}

const apiKeyDateRange = ref<[number, number]>(defaultDateRange())
const filteredApiKeyUsage = computed(() => {
  const keyword = apiKeySearch.value.trim().toLocaleLowerCase()
  if (!keyword) return apiKeyUsage.value
  return apiKeyUsage.value.filter((row) =>
    `${row.apiKeyName} ${row.trackingId ?? ''}`.toLocaleLowerCase().includes(keyword)
  )
})

const usageColumns: DataTableColumns<UsageRecord> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: 'Agent Key', key: 'agentKey', width: 180, ellipsis: { tooltip: true } },
  { title: '运行 ID', key: 'runId', width: 220, render: (row) => row.runId ?? '—' },
  { title: '模型 ID', key: 'modelId', width: 90, render: (row) => row.modelId ?? '—' },
  { title: '模型 Key', key: 'modelKey', width: 180, ellipsis: { tooltip: true } },
  { title: '输入 Token', key: 'promptTokens', width: 110 },
  { title: '输出 Token', key: 'completionTokens', width: 110 },
  { title: '合计 Token', key: 'totalTokens', width: 110 },
  { title: '耗时', key: 'durationMs', width: 100, render: (row) => row.durationMs == null ? '—' : `${row.durationMs} ms` },
  { title: '状态', key: 'status', width: 100, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '调用时间', key: 'createdAt', width: 180, render: (row) => row.createdAt ? (formatDateTime(row.createdAt) ?? '—') : '—' }
]

const apiKeyUsageColumns: DataTableColumns<ApiKeyUsageSummary> = [
  {
    title: 'API Key',
    key: 'apiKeyName',
    width: 210,
    render: (row) => h('span', { title: row.apiKeyName }, row.apiKeyName)
  },
  { title: '跟踪 ID', key: 'trackingId', width: 145, render: (row) => row.trackingId ?? '—' },
  { title: '模型调用', key: 'modelCalls', width: 100 },
  { title: '成功', key: 'successCalls', width: 80 },
  { title: '失败', key: 'failedCalls', width: 80 },
  { title: '输入 Token', key: 'promptTokens', width: 110 },
  { title: '输出 Token', key: 'completionTokens', width: 110 },
  { title: '合计 Token', key: 'totalTokens', width: 110 },
  { title: '运行数', key: 'runCount', width: 90 },
  { title: '会话数', key: 'conversationCount', width: 90 },
  { title: '平均耗时', key: 'averageDurationMs', width: 110, render: (row) => `${row.averageDurationMs} ms` }
]

const usageScrollX = usageColumns.reduce((sum, column) => sum + (typeof column.width === 'number' ? column.width : 160), 0)
const apiKeyUsageScrollX = apiKeyUsageColumns.reduce((sum, column) => sum + (typeof column.width === 'number' ? column.width : 160), 0)

async function reloadApps() {
  try {
    const appRows = await listAgents(adminToken.value)
    apps.value = appRows.rows
    filterAppKey.value = appRows.rows[0]?.agentKey ?? ''
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Agent 列表失败')
  }
}

async function reloadUsage() {
  usageLoading.value = true
  try {
    const rows = await listUsage(adminToken.value, {
      agentKey: filterAppKey.value || undefined,
      from: agentUsageDateRange.value ? localDayIso(agentUsageDateRange.value[0]) : undefined,
      to: agentUsageDateRange.value ? localDayIso(agentUsageDateRange.value[1], true) : undefined,
      pageNum: usagePagination.page,
      pageSize: usagePagination.pageSize
    })
    usage.value = rows.rows
    usagePagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Agent 用量失败')
  } finally {
    usageLoading.value = false
  }
}

async function reloadApiKeyUsage() {
  apiKeyUsageLoading.value = true
  try {
    const [from, to] = apiKeyDateRange.value
    const result = await getApiKeyUsageStats(adminToken.value, {
      from: localDateString(from),
      to: localDateString(to)
    })
    apiKeyUsage.value = result.summaries
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 API Key 用量失败')
  } finally {
    apiKeyUsageLoading.value = false
  }
}

function handleAgentUsageRangeChange(value: [number, number] | null) {
  agentUsageDateRange.value = value
}

function handleAgentUsageSearch() {
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

function handleTabChange(tab: string) {
  activeTab.value = tab
  if (tab === 'api-key') void reloadApiKeyUsage()
}

function handleApiKeyDateRangeChange(value: [number, number] | null) {
  if (!value) return
  apiKeyDateRange.value = value
}

function handleApiKeyUsageSearch() {
  apiKeySearch.value = apiKeySearchInput.value.trim()
  void reloadApiKeyUsage()
}

onMounted(async () => {
  await reloadApps()
  await reloadUsage()
})
</script>

<template>
  <div class="page">
    <PageHeader title="可观测性" description="查看 Agent 模型用量明细与 API Key 维度用量汇总。审计日志请前往系统管理 → 日志管理。" />

    <n-tabs v-model:value="activeTab" type="line" animated @update:value="handleTabChange">
      <n-tab-pane name="agent" tab="Agent 用量">
        <n-card title="Agent 模型用量明细" :bordered="true">
          <n-space style="margin-bottom: 12px" align="center" justify="space-between" :wrap="true">
            <n-space align="center" :wrap="true">
              <n-select
                v-model:value="filterAppKey"
                filterable
                clearable
                placeholder="按 Agent 筛选"
                style="width: 240px; max-width: 100%"
                :options="apps.map((app) => ({ label: app.agentKey, value: app.agentKey }))"
              />
              <n-date-picker
                :value="agentUsageDateRange"
                type="daterange"
                clearable
                format="yyyy-MM-dd"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 280px; max-width: 100%"
                @update:value="handleAgentUsageRangeChange"
              />
              <n-button size="small" type="primary" secondary :loading="usageLoading" @click="handleAgentUsageSearch">
                <template #icon><n-icon :component="Search" /></template>
                查询
              </n-button>
              <n-button size="small" quaternary :loading="usageLoading" @click="reloadUsage">
                <template #icon><n-icon :component="RefreshCw" /></template>
                刷新
              </n-button>
            </n-space>
          </n-space>
          <n-data-table
            :columns="usageColumns"
            :data="usage"
            :loading="usageLoading"
            :scroll-x="usageScrollX"
            :bordered="false"
          />
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
      </n-tab-pane>

      <n-tab-pane name="api-key" tab="API Key 用量">
        <n-card title="API Key 用量汇总" :bordered="true">
          <n-space style="margin-bottom: 12px" align="center" justify="space-between" :wrap="true">
            <n-space align="center" :wrap="true">
              <n-date-picker
                :value="apiKeyDateRange"
                type="daterange"
                :clearable="false"
                format="yyyy-MM-dd"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 280px; max-width: 100%"
                @update:value="handleApiKeyDateRangeChange"
              />
              <n-input
                v-model:value="apiKeySearchInput"
                clearable
                placeholder="搜索 API Key / 跟踪 ID"
                style="width: 240px; max-width: 100%"
                @keyup.enter="handleApiKeyUsageSearch"
              />
              <n-button size="small" type="primary" secondary :loading="apiKeyUsageLoading" @click="handleApiKeyUsageSearch">
                <template #icon><n-icon :component="Search" /></template>
                查询
              </n-button>
              <n-button size="small" quaternary :loading="apiKeyUsageLoading" @click="reloadApiKeyUsage">
                <template #icon><n-icon :component="RefreshCw" /></template>
                刷新
              </n-button>
            </n-space>
          </n-space>
          <n-data-table
            :columns="apiKeyUsageColumns"
            :data="filteredApiKeyUsage"
            :loading="apiKeyUsageLoading"
            :scroll-x="apiKeyUsageScrollX"
            :bordered="false"
          >
            <template #empty>
              <div class="empty-state">所选日期范围内暂无 API Key 用量</div>
            </template>
          </n-data-table>
          <p class="table-note">统计范围为所选日期（含首尾）；未关联到 API Key 的管理端调试及历史调用会单独归入“未关联 API Key”。</p>
        </n-card>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 28px 12px;
  color: var(--n-text-color-3);
  text-align: center;
}

.table-note {
  margin: 10px 0 0;
  color: var(--n-text-color-3);
  font-size: 12px;
  line-height: 1.5;
}
</style>
