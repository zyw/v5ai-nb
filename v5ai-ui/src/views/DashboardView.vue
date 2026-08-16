<script setup lang="ts">
import { onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NIcon, NTag, useMessage } from 'naive-ui'
import { Activity, Bot, Boxes, Database, MessageSquare, Sparkles } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { getStatsOverview, type OverviewResponse } from '../api/client'
import { adminToken, displayName } from '../stores/session'

const router = useRouter()
const message = useMessage()
const loading = ref(true)
const overview = ref<OverviewResponse | null>(null)

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
    overview.value = await getStatsOverview(adminToken.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载统计失败')
  } finally {
    loading.value = false
  }
}

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
          <div class="stat-value">{{ loading ? '—' : (overview?.[s.key] ?? 0) }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </n-card>
    </div>

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
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

.stat-card {
  border-radius: 12px;
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
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
</style>
