<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NIcon, NSelect, NTooltip, useMessage } from 'naive-ui'
import { Activity, Bot, Boxes, Database, KeyRound, MessageSquare, Server, Sparkles, Workflow } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  getApiKeyUsageStats,
  getStatsOverview,
  listAgents,
  listAppUsage,
  type AgentResponse,
  type ApiKeyUsageStats,
  type AppUsage,
  type OverviewResponse
} from '../api/client'
import { adminToken, displayName } from '../stores/session'

const router = useRouter()
const message = useMessage()
const loading = ref(true)
const overview = ref<OverviewResponse | null>(null)
const agents = ref<AgentResponse[]>([])
const selectedAgentKey = ref('')
const usageTrend = ref<AppUsage[]>([])
const usageLoading = ref(false)
const apiKeyStats = ref<ApiKeyUsageStats | null>(null)
const selectedApiKeyId = ref<number | null>(null)
const apiKeyUsageLoading = ref(false)

interface StatDef {
  key: keyof OverviewResponse
  label: string
  icon: Component
  color: string
}

const stats: StatDef[] = [
  { key: 'agents', label: 'Agent', icon: Bot, color: '#7c3aed' },
  { key: 'models', label: '模型', icon: Boxes, color: '#0891b2' },
  { key: 'knowledgeBases', label: '知识库', icon: Database, color: '#10b981' },
  { key: 'skills', label: 'Skill', icon: Sparkles, color: '#f59e0b' },
  { key: 'totalRuns', label: '运行总数', icon: Activity, color: '#ef4444' },
  { key: 'todayRuns', label: '今日运行', icon: Activity, color: '#8b5cf6' },
  { key: 'todayModelCalls', label: '今日调用', icon: MessageSquare, color: '#0ea5e9' },
  { key: 'todayTokens', label: '今日 Token', icon: Boxes, color: '#f472b6' }
]

const quickLinks = [
  { name: 'agents', label: '创建 Agent', fullLabel: '创建 Agent', icon: Bot },
  { name: 'models', label: '配置模型', fullLabel: '配置 Provider 与模型', icon: Boxes },
  { name: 'chat', label: '调试 Agent', fullLabel: '调试 Agent 对话', icon: MessageSquare },
  { name: 'api-keys', label: 'API Key', fullLabel: '管理 Agent API Key', icon: KeyRound },
  { name: 'knowledge-bases', label: '知识库', fullLabel: '管理知识库与文档', icon: Database },
  { name: 'mcp-servers', label: 'MCP', fullLabel: '管理 MCP Server', icon: Server },
  { name: 'skills', label: 'Skill', fullLabel: '管理 Skill', icon: Sparkles },
  { name: 'workflows', label: '工作流', fullLabel: '工作流编排与管理', icon: Workflow }
]

interface QuickStartStep {
  label: string
  description: string
  route: string
  action: string
  done: boolean
}

async function reload() {
  loading.value = true
  try {
    const [overviewData, agentPage, apiKeyData] = await Promise.all([
      getStatsOverview(adminToken.value),
      listAgents(adminToken.value, { pageNum: 1, pageSize: 100 }),
      getApiKeyUsageStats(adminToken.value)
    ])
    overview.value = overviewData
    agents.value = agentPage.rows
    apiKeyStats.value = apiKeyData
    if (!apiKeyData.summaries.some((summary) => summary.apiKeyId === selectedApiKeyId.value)) {
      selectedApiKeyId.value = apiKeyData.summaries.find((summary) => summary.apiKeyId != null)?.apiKeyId ?? null
    }
    if (!agents.value.some((agent) => agent.agentKey === selectedAgentKey.value)) {
      selectedAgentKey.value = agents.value[0]?.agentKey ?? ''
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载统计失败')
  } finally {
    loading.value = false
  }
}

function dateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const trendTo = computed(() => new Date())
const trendFrom = computed(() => {
  const date = new Date(trendTo.value)
  date.setDate(date.getDate() - 13)
  return date
})

const trendDays = computed(() => {
  const days: string[] = []
  const date = new Date(trendFrom.value)
  while (date <= trendTo.value) {
    days.push(dateKey(date))
    date.setDate(date.getDate() + 1)
  }
  return days
})

const trendData = computed(() => {
  const values = new Map(usageTrend.value.map((item) => [item.usageDate, item]))
  return trendDays.value.map((usageDate) => ({
    usageDate,
    modelCalls: values.get(usageDate)?.modelCalls ?? 0,
    tokens: values.get(usageDate)?.tokens ?? 0
  }))
})

const agentOptions = computed(() => agents.value.map((agent) => ({
  label: `${agent.name} (${agent.agentKey})`,
  value: agent.agentKey
})))

const apiKeyOptions = computed(() => (apiKeyStats.value?.summaries ?? [])
  .filter((summary) => summary.apiKeyId != null)
  .map((summary) => ({
    label: summary.trackingId ? `${summary.apiKeyName} (${summary.trackingId})` : summary.apiKeyName,
    value: summary.apiKeyId as number
  })))

const selectedApiKeySummary = computed(() =>
  apiKeyStats.value?.summaries.find((summary) => summary.apiKeyId === selectedApiKeyId.value) ?? null
)

const apiKeyRanking = computed(() => (apiKeyStats.value?.summaries ?? [])
  .filter((summary) => summary.modelCalls > 0)
  .slice(0, 6))

const apiKeyRankingMaxTokens = computed(() => Math.max(...apiKeyRanking.value.map((summary) => summary.totalTokens), 1))

const quickStartSteps = computed<QuickStartStep[]>(() => {
  const hasModel = (overview.value?.models ?? 0) > 0
  const hasAgent = (overview.value?.agents ?? 0) > 0
  const hasPublishedAgent = agents.value.some((agent) => agent.status.toUpperCase() === 'PUBLISHED')
  const hasRun = (overview.value?.totalRuns ?? 0) > 0
  const hasApiKey = (apiKeyStats.value?.summaries ?? []).some((summary) => summary.apiKeyId != null)

  return [
    { label: '配置模型', description: '先配置 Provider 和可用模型。', route: 'models', action: '去配置', done: hasModel },
    { label: '创建 Agent', description: '填写提示词并绑定对话模型。', route: 'agents', action: '去创建', done: hasAgent },
    { label: '发布并调试', description: '发布 Agent 后运行一轮对话验证。', route: hasPublishedAgent ? 'chat' : 'agents', action: hasPublishedAgent ? '去调试' : '去发布', done: hasRun },
    { label: '创建 API Key', description: '为已发布 Agent 创建调用凭证。', route: 'api-keys', action: '去创建', done: hasApiKey }
  ]
})

function apiKeyRankingWidth(summary: ApiKeyUsageStats['summaries'][number]): string {
  return `${Math.max((summary.totalTokens / apiKeyRankingMaxTokens.value) * 100, 3)}%`
}

const apiKeyTrendData = computed(() => {
  const values = new Map((apiKeyStats.value?.daily ?? []).map((item) => [item.usageDate, item]))
  return trendDays.value.map((usageDate) => {
    const item = values.get(usageDate)
    return {
      usageDate,
      modelCalls: item?.modelCalls ?? 0,
      tokens: item?.totalTokens ?? 0
    }
  })
})

async function reloadUsageTrend() {
  if (!selectedAgentKey.value) {
    usageTrend.value = []
    return
  }
  usageLoading.value = true
  try {
    usageTrend.value = await listAppUsage(adminToken.value, selectedAgentKey.value, dateKey(trendFrom.value), dateKey(trendTo.value))
  } catch (e) {
    usageTrend.value = []
    message.error(e instanceof Error ? e.message : '加载 Agent 用量失败')
  } finally {
    usageLoading.value = false
  }
}

async function reloadApiKeyUsage() {
  if (selectedApiKeyId.value == null) {
    return
  }
  apiKeyUsageLoading.value = true
  try {
    apiKeyStats.value = await getApiKeyUsageStats(adminToken.value, {
      from: dateKey(trendFrom.value),
      to: dateKey(trendTo.value),
      apiKeyId: selectedApiKeyId.value
    })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 API Key 用量失败')
  } finally {
    apiKeyUsageLoading.value = false
  }
}

type TrendMetric = 'modelCalls' | 'tokens'

type TrendDataPoint = { usageDate: string; modelCalls: number; tokens: number }

function metricMax(metric: TrendMetric, data: TrendDataPoint[] = trendData.value): number {
  return Math.max(...data.map((item) => item[metric]), 1)
}

function metricPath(metric: TrendMetric, data: TrendDataPoint[] = trendData.value): string {
  const max = metricMax(metric, data)
  const width = 560
  const height = 190
  const left = 14
  const top = 16
  const innerWidth = width - left * 2
  const innerHeight = height - top * 2
  return data.map((item, index) => {
    const x = left + (data.length === 1 ? innerWidth / 2 : (index / (data.length - 1)) * innerWidth)
    const y = top + innerHeight - (item[metric] / max) * innerHeight
    return `${index === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`
  }).join(' ')
}

function metricPoint(metric: TrendMetric, index: number, data: TrendDataPoint[] = trendData.value): { x: number; y: number } {
  const max = metricMax(metric, data)
  const width = 560
  const height = 190
  const left = 14
  const top = 16
  const innerWidth = width - left * 2
  const innerHeight = height - top * 2
  const value = trendData.value[index]?.[metric] ?? 0
  return {
    x: left + (data.length === 1 ? innerWidth / 2 : (index / (data.length - 1)) * innerWidth),
    y: top + innerHeight - (value / max) * innerHeight
  }
}

function formatTrendLabel(value: number): string {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

watch(selectedAgentKey, () => void reloadUsageTrend())
watch(selectedApiKeyId, () => void reloadApiKeyUsage())

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader
      title="总览"
      :description="`欢迎回来，${displayName}。这里是平台的运行概览与快捷入口。`"
    >
      <template #actions>
        <n-button size="small" secondary @click="reload">刷新</n-button>
      </template>
    </PageHeader>

    <div class="stats-grid">
      <n-card v-for="s in stats" :key="s.key" class="stat-card" :bordered="true">
        <div class="stat-body">
          <div class="stat-icon" :style="{ color: s.color, background: `${s.color}1f` }">
            <n-icon :component="s.icon" :size="20" />
          </div>
          <div class="stat-copy">
            <div class="stat-value">{{ loading ? '—' : (overview?.[s.key] ?? 0) }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </div>
      </n-card>
    </div>

    <n-card class="usage-trend-card" title="Agent 用量趋势" :bordered="true">
      <template #header-extra>
        <n-select
          v-model:value="selectedAgentKey"
          :options="agentOptions"
          filterable
          placeholder="选择 Agent"
          style="width: 280px"
        />
      </template>
      <div v-if="usageLoading" class="chart-placeholder">正在加载用量趋势…</div>
      <n-empty v-else-if="!selectedAgentKey" description="暂无 Agent" />
      <div v-else class="trend-charts">
        <div v-for="metric in (['modelCalls', 'tokens'] as TrendMetric[])" :key="metric" class="trend-chart">
          <div class="trend-chart-header">
            <span>{{ metric === 'modelCalls' ? '调用次数' : 'Token 用量' }}</span>
            <strong>{{ formatTrendLabel(trendData.reduce((sum, item) => sum + item[metric], 0)) }}</strong>
          </div>
          <svg viewBox="0 0 560 190" preserveAspectRatio="none" role="img" :aria-label="metric === 'modelCalls' ? '调用次数趋势' : 'Token 用量趋势'">
            <line v-for="level in [0, 0.5, 1]" :key="level" x1="14" :y1="16 + (1 - level) * 158" x2="546" :y2="16 + (1 - level) * 158" class="chart-grid-line" />
            <path :d="metricPath(metric)" class="chart-line" :class="metric" />
            <circle
              v-for="(_, index) in trendData"
              :key="index"
              :cx="metricPoint(metric, index).x"
              :cy="metricPoint(metric, index).y"
              r="3"
              class="chart-point"
              :class="metric"
            />
          </svg>
          <div class="chart-axis">
            <span>{{ trendData[0]?.usageDate.slice(5) }}</span>
            <span>{{ trendData[Math.floor(trendData.length / 2)]?.usageDate.slice(5) }}</span>
            <span>{{ trendData[trendData.length - 1]?.usageDate.slice(5) }}</span>
          </div>
        </div>
      </div>
    </n-card>

    <n-card class="usage-trend-card api-key-usage-card" title="API Key 用量趋势" :bordered="true">
      <template #header-extra>
        <n-select
          v-model:value="selectedApiKeyId"
          :options="apiKeyOptions"
          filterable
          placeholder="选择 API Key"
          style="width: 280px"
        />
      </template>
      <div v-if="apiKeyUsageLoading" class="chart-placeholder">正在加载 API Key 用量…</div>
      <n-empty v-else-if="!selectedApiKeySummary" description="暂无 API Key 用量" />
      <template v-else>
        <div class="api-key-summary-grid">
          <div class="api-key-summary-item">
            <span>模型调用</span>
            <strong>{{ selectedApiKeySummary.modelCalls }}</strong>
          </div>
          <div class="api-key-summary-item">
            <span>Token 总量</span>
            <strong>{{ formatTrendLabel(selectedApiKeySummary.totalTokens) }}</strong>
          </div>
          <div class="api-key-summary-item">
            <span>成功率</span>
            <strong>{{ selectedApiKeySummary.modelCalls ? `${((selectedApiKeySummary.successCalls / selectedApiKeySummary.modelCalls) * 100).toFixed(1)}%` : '—' }}</strong>
          </div>
          <div class="api-key-summary-item">
            <span>平均耗时</span>
            <strong>{{ selectedApiKeySummary.averageDurationMs }} ms</strong>
          </div>
        </div>

        <div class="trend-charts">
          <div v-for="metric in (['modelCalls', 'tokens'] as TrendMetric[])" :key="metric" class="trend-chart">
            <div class="trend-chart-header">
              <span>{{ metric === 'modelCalls' ? '调用次数' : 'Token 用量' }}</span>
              <strong>{{ formatTrendLabel(apiKeyTrendData.reduce((sum, item) => sum + item[metric], 0)) }}</strong>
            </div>
            <svg viewBox="0 0 560 190" preserveAspectRatio="none" role="img" :aria-label="metric === 'modelCalls' ? 'API Key 调用次数趋势' : 'API Key Token 用量趋势'">
              <line v-for="level in [0, 0.5, 1]" :key="level" x1="14" :y1="16 + (1 - level) * 158" x2="546" :y2="16 + (1 - level) * 158" class="chart-grid-line" />
              <path :d="metricPath(metric, apiKeyTrendData)" class="chart-line" :class="metric" />
              <circle
                v-for="(_, index) in apiKeyTrendData"
                :key="index"
                :cx="metricPoint(metric, index, apiKeyTrendData).x"
                :cy="metricPoint(metric, index, apiKeyTrendData).y"
                r="3"
                class="chart-point"
                :class="metric"
              />
            </svg>
            <div class="chart-axis">
              <span>{{ apiKeyTrendData[0]?.usageDate.slice(5) }}</span>
              <span>{{ apiKeyTrendData[Math.floor(apiKeyTrendData.length / 2)]?.usageDate.slice(5) }}</span>
              <span>{{ apiKeyTrendData[apiKeyTrendData.length - 1]?.usageDate.slice(5) }}</span>
            </div>
          </div>
        </div>

        <div class="api-key-ranking">
          <div class="ranking-header">
            <span>API Key Token 用量排行</span>
            <span>总量</span>
          </div>
          <div v-for="summary in apiKeyRanking" :key="summary.apiKeyId ?? 'unassociated'" class="ranking-row">
            <span class="ranking-name">{{ summary.apiKeyName }}</span>
            <span class="ranking-bar"><i :style="{ width: apiKeyRankingWidth(summary) }" /></span>
            <strong>{{ formatTrendLabel(summary.totalTokens) }}</strong>
          </div>
          <n-empty v-if="!apiKeyRanking.length" description="暂无排行数据" size="small" />
        </div>
      </template>
    </n-card>

    <div class="quick-grid">
      <n-card title="快捷入口" :bordered="true">
        <div class="quick-list">
          <button
            v-for="link in quickLinks"
            :key="link.name"
            class="quick-item"
            type="button"
            @click="router.push({ name: link.name })"
          >
            <span class="quick-icon"><n-icon :component="link.icon" :size="18" /></span>
            <n-tooltip placement="top">
              <template #trigger>
                <span class="quick-label">{{ link.label }}</span>
              </template>
              {{ link.fullLabel }}
            </n-tooltip>
          </button>
        </div>
      </n-card>

      <n-card title="快速开始" :bordered="true">
        <div class="steps">
          <div v-for="(step, index) in quickStartSteps" :key="step.label" class="step-item">
            <span class="step-marker" :class="{ done: step.done }">{{ step.done ? '✓' : index + 1 }}</span>
            <div class="step-copy">
              <strong>{{ step.label }}</strong>
              <span>{{ step.description }}</span>
            </div>
            <n-button text size="small" :type="step.done ? 'default' : 'primary'" @click="router.push({ name: step.route })">
              {{ step.done ? '查看' : step.action }}
            </n-button>
          </div>
        </div>
        <p class="optional-hint">知识库、MCP、Skill 和工作流是可选增强能力，可在 Agent 创建后按需配置。</p>
      </n-card>
    </div>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  border-radius: 12px;
  min-height: 124px;
}

.stat-body {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 82px;
}

.stat-icon {
  display: grid;
  place-items: center;
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  border-radius: 10px;
}

.stat-copy {
  min-width: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  opacity: 0.6;
}

.usage-trend-card {
  margin-top: 16px;
  border-radius: 12px;
}

.api-key-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.api-key-summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid rgba(128, 128, 128, 0.16);
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.035);
}

.api-key-summary-item span {
  color: var(--text-secondary);
  font-size: 12px;
}

.api-key-summary-item strong {
  font-size: 19px;
  font-variant-numeric: tabular-nums;
}

.api-key-ranking {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed rgba(128, 128, 128, 0.25);
}

.ranking-header,
.ranking-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) minmax(120px, 3fr) 64px;
  gap: 12px;
  align-items: center;
}

.ranking-header {
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}

.ranking-header span:last-child {
  text-align: right;
}

.ranking-row {
  min-height: 30px;
  font-size: 13px;
}

.ranking-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-bar {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(124, 58, 237, 0.12);
}

.ranking-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #7c3aed, #a78bfa);
}

.ranking-row strong {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.trend-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.trend-chart {
  min-width: 0;
  padding: 14px 16px 10px;
  border: 1px solid rgba(128, 128, 128, 0.16);
  border-radius: 12px;
  background: rgba(128, 128, 128, 0.035);
}

.trend-chart-header,
.chart-axis {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.trend-chart-header {
  margin-bottom: 8px;
  font-size: 13px;
  opacity: 0.72;
}

.trend-chart-header strong {
  color: var(--brand);
  font-size: 18px;
  opacity: 1;
}

.trend-chart svg {
  display: block;
  width: 100%;
  height: 190px;
  overflow: visible;
}

.chart-grid-line {
  stroke: rgba(128, 128, 128, 0.18);
  stroke-dasharray: 3 5;
}

.chart-line {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.chart-line.modelCalls,
.chart-point.modelCalls {
  stroke: #7c3aed;
  fill: #7c3aed;
}

.chart-line.tokens,
.chart-point.tokens {
  stroke: #0891b2;
  fill: #0891b2;
}

.chart-point {
  stroke: var(--n-card-color, #fff);
  stroke-width: 2;
}

.chart-axis {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 11px;
}

.chart-placeholder {
  display: grid;
  place-items: center;
  min-height: 240px;
  opacity: 0.55;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.quick-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(128, 128, 128, 0.16);
  border-radius: 10px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.quick-item:hover {
  border-color: var(--brand);
  background: rgba(124, 58, 237, 0.08);
}

.quick-icon {
  display: grid;
  place-items: center;
  color: var(--brand);
}

.quick-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.steps {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
}

.step-marker {
  display: grid;
  place-items: center;
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
  border: 1px solid rgba(128, 128, 128, 0.32);
  border-radius: 50%;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.step-marker.done {
  border-color: var(--brand);
  background: var(--brand);
  color: white;
}

.step-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.step-copy strong {
  font-size: 13px;
}

.step-copy span,
.optional-hint {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.optional-hint {
  margin: 14px 0 0;
  padding-top: 12px;
  border-top: 1px dashed rgba(128, 128, 128, 0.22);
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-charts {
    grid-template-columns: 1fr;
  }

  .api-key-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .api-key-summary-grid {
    grid-template-columns: 1fr;
  }

  .ranking-header,
  .ranking-row {
    grid-template-columns: minmax(100px, 1fr) minmax(80px, 2fr) 54px;
    gap: 8px;
  }
}
</style>
