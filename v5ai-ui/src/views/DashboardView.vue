<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NIcon, NTag, useMessage } from 'naive-ui'
import { Activity, Bot, Boxes, Database, MessageSquare, Sparkles } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getStatsOverview, listAgents, listAppUsage, type AgentResponse, type AppUsage, type OverviewResponse } from '../api/client'
import { adminToken, displayName } from '../stores/session'

const router = useRouter()
const message = useMessage()
const loading = ref(true)
const overview = ref<OverviewResponse | null>(null)
const agents = ref<AgentResponse[]>([])
const selectedAgentKey = ref('')
const usageTrend = ref<AppUsage[]>([])
const usageLoading = ref(false)

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
  { name: 'models', label: '配置模型', icon: Boxes },
  { name: 'agents', label: '创建 Agent', icon: Bot },
  { name: 'knowledge-bases', label: '构建知识库', icon: Database },
  { name: 'skills', label: '上传 Skill', icon: Sparkles },
  { name: 'chat', label: '调试运行', icon: MessageSquare }
]

async function reload() {
  loading.value = true
  try {
    const [overviewData, agentPage] = await Promise.all([
      getStatsOverview(adminToken.value),
      listAgents(adminToken.value, { pageNum: 1, pageSize: 100 })
    ])
    overview.value = overviewData
    agents.value = agentPage.rows
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

type TrendMetric = 'modelCalls' | 'tokens'

function metricMax(metric: TrendMetric): number {
  return Math.max(...trendData.value.map((item) => item[metric]), 1)
}

function metricPath(metric: TrendMetric): string {
  const data = trendData.value
  const max = metricMax(metric)
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

function metricPoint(metric: TrendMetric, index: number): { x: number; y: number } {
  const max = metricMax(metric)
  const width = 560
  const height = 190
  const left = 14
  const top = 16
  const innerWidth = width - left * 2
  const innerHeight = height - top * 2
  const value = trendData.value[index]?.[metric] ?? 0
  return {
    x: left + (trendData.value.length === 1 ? innerWidth / 2 : (index / (trendData.value.length - 1)) * innerWidth),
    y: top + innerHeight - (value / max) * innerHeight
  }
}

function formatTrendLabel(value: number): string {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

watch(selectedAgentKey, () => void reloadUsageTrend())

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
            <span>{{ link.label }}</span>
          </button>
        </div>
      </n-card>

      <n-card title="快速开始" :bordered="true">
        <ol class="steps">
          <li>在「模型管理」中配置 Provider 与 Model。</li>
          <li>在「知识库管理」中导入文档并等待索引完成。</li>
          <li>创建 Agent，绑定模型 / 知识库 / MCP / Skill。</li>
          <li>发布 Agent，在「调试工具」中运行验证。</li>
        </ol>
        <n-tag type="info" size="small" :bordered="false">默认管理员账号 admin / admin</n-tag>
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

.steps {
  margin: 0 0 14px;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-charts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
