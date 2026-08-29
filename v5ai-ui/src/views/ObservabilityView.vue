<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NGi,
  NGrid,
  NInputNumber,
  NPagination,
  NSelect,
  NSpace,
  NStatistic,
  NTag,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  getAppDailyUsage,
  getAppQuota,
  getStatsOverview,
  listAgents,
  listUsage,
  updateAppQuota,
  type OverviewResponse,
  type UsageRecord
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const overview = ref<OverviewResponse | null>(null)
const usage = ref<UsageRecord[]>([])
const apps = ref<{ agentKey: string }[]>([])
const filterAppKey = ref('')
const savingQuota = ref(false)
const dailyUsage = ref('')
const usagePagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const quotaForm = reactive({
  agentKey: '',
  dailyModelCalls: 0,
  dailyTokens: 0,
  ratePerMinute: 0
})

const usageColumns: DataTableColumns<UsageRecord> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: 'App', key: 'agentKey' },
  { title: '模型', key: 'modelKey' },
  { title: '输入 Token', key: 'promptTokens' },
  { title: '输出 Token', key: 'completionTokens' },
  { title: '合计', key: 'totalTokens' },
  { title: '耗时(ms)', key: 'durationMs', render: (row) => row.durationMs ?? '-' },
  { title: '状态', key: 'status', width: 100, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '时间', key: 'createdAt', width: 180, render: (row) => (row.createdAt ? new Date(row.createdAt).toLocaleString() : '-') }
]

async function reloadOverview() {
  try {
    const [overviewData, appRows] = await Promise.all([
      getStatsOverview(adminToken.value),
      listAgents(adminToken.value)
    ])
    overview.value = overviewData
    apps.value = appRows.rows
    if (!quotaForm.agentKey && appRows.rows.length > 0) {
      quotaForm.agentKey = appRows.rows[0].agentKey
      await reloadQuota()
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载统计失败')
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

async function reloadQuota() {
  if (!quotaForm.agentKey) return
  try {
    const [quota, usageToday] = await Promise.all([
      getAppQuota(adminToken.value, quotaForm.agentKey),
      getAppDailyUsage(adminToken.value, quotaForm.agentKey)
    ])
    quotaForm.dailyModelCalls = quota.dailyModelCalls
    quotaForm.dailyTokens = quota.dailyTokens
    quotaForm.ratePerMinute = quota.ratePerMinute
    dailyUsage.value = `今日调用 ${usageToday.modelCalls} 次 / ${usageToday.tokens} tokens`
  } catch {
    quotaForm.dailyModelCalls = 0
    quotaForm.dailyTokens = 0
    quotaForm.ratePerMinute = 0
    dailyUsage.value = '今日暂无用量'
  }
}

async function handleSaveQuota() {
  savingQuota.value = true
  try {
    await updateAppQuota(adminToken.value, quotaForm.agentKey, {
      dailyModelCalls: quotaForm.dailyModelCalls,
      dailyTokens: quotaForm.dailyTokens,
      ratePerMinute: quotaForm.ratePerMinute
    })
    message.success('配额已更新（0 = 不限）')
    await reloadQuota()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新配额失败')
  } finally {
    savingQuota.value = false
  }
}

onMounted(async () => {
  await reloadOverview()
  await reloadUsage()
})
</script>

<template>
  <div class="page">
    <PageHeader title="可观测性" description="查看平台运行统计、用量明细，并配置应用配额。（审计日志已迁移至 系统管理 → 日志管理）" />

    <n-card title="平台总览" :bordered="true">
      <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="Agent" :value="overview?.agents ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="模型" :value="overview?.models ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="知识库" :value="overview?.knowledgeBases ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="Skill" :value="overview?.skills ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="运行总数" :value="overview?.totalRuns ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="今日运行" :value="overview?.todayRuns ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="今日模型调用" :value="overview?.todayModelCalls ?? 0" />
        </n-gi>
        <n-gi span="4 m:2 l:1">
          <n-statistic label="今日 Token" :value="overview?.todayTokens ?? 0" />
        </n-gi>
      </n-grid>
    </n-card>

    <n-card title="应用配额（0 = 不限）" :bordered="true">
      <div class="quota-form">
        <n-select
          v-model:value="quotaForm.agentKey"
          filterable
          style="min-width: 220px"
          :options="apps.map((a) => ({ label: a.agentKey, value: a.agentKey }))"
          @update:value="reloadQuota"
        />
        <div class="quota-numbers">
          <label>每日调用次数</label>
          <n-input-number v-model:value="quotaForm.dailyModelCalls" :min="0" />
        </div>
        <div class="quota-numbers">
          <label>每日 Token</label>
          <n-input-number v-model:value="quotaForm.dailyTokens" :min="0" />
        </div>
        <div class="quota-numbers">
          <label>每分钟限流</label>
          <n-input-number v-model:value="quotaForm.ratePerMinute" :min="0" />
        </div>
        <n-button type="primary" :loading="savingQuota" @click="handleSaveQuota">保存配额</n-button>
      </div>
      <n-tag v-if="dailyUsage" type="info" size="small" :bordered="false" style="margin-top: 12px">{{ dailyUsage }}</n-tag>
    </n-card>

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

<style scoped>
.quota-form {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.quota-numbers {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quota-numbers label {
  font-size: 12px;
  opacity: 0.6;
}
</style>
