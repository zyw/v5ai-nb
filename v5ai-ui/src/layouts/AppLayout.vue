<script setup lang="ts">
import { computed, h, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAvatar,
  NButton,
  NDropdown,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  type MenuOption
} from 'naive-ui'
import {
  Activity,
  Bot,
  Boxes,
  Building2,
  Cable,
  Database,
  Gauge,
  Info,
  Layers,
  LayoutDashboard,
  LogOut,
  MessageSquare,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  ShieldCheck,
  Sparkles,
  Sun,
  Users
} from 'lucide-vue-next'
import { isDark, toggleTheme } from '../stores/theme'
import { clearAdminToken, displayName, isAdmin, loadCurrentUser } from '../stores/session'

const route = useRoute()
const router = useRouter()

const collapsed = ref(window.innerWidth < 900)

function icon(component: Component) {
  return () => h(NIcon, { component })
}

type NavItem = MenuOption & { key: string }

const nav: NavItem[] = [
  { label: '总览', key: 'dashboard', icon: icon(LayoutDashboard) },
  {
    type: 'submenu',
    label: '资源',
    key: 'g-resource',
    icon: icon(Layers),
    children: [
      { label: '模型管理', key: 'models', icon: icon(Boxes) },
      { label: 'Agent 管理', key: 'agents', icon: icon(Bot) },
      { label: '知识库管理', key: 'knowledge-bases', icon: icon(Database) },
      { label: 'MCP 管理', key: 'mcp-servers', icon: icon(Cable) },
      { label: 'Skill 管理', key: 'skills', icon: icon(Sparkles) }
    ]
  },
  {
    type: 'submenu',
    label: '系统管理',
    key: 'g-system',
    icon: icon(Settings),
    children: [
      { label: '用户管理', key: 'system-users', icon: icon(Users) },
      { label: '角色管理', key: 'system-roles', icon: icon(ShieldCheck) },
      { label: '租户管理', key: 'system-tenants', icon: icon(Building2) }
    ]
  },
  {
    type: 'submenu',
    label: '运维',
    key: 'g-ops',
    icon: icon(Gauge),
    children: [
      { label: '可观测性', key: 'observability', icon: icon(Activity) },
      { label: '调试工具', key: 'chat', icon: icon(MessageSquare) },
      { label: '系统信息', key: 'settings', icon: icon(Info) }
    ]
  }
]

const activeKey = computed(() => String(route.name ?? 'dashboard'))
const pageTitle = computed(() => String(route.meta.title ?? '总览'))

// 一级菜单默认全部展开，且导航到某页时自动展开其所属分组
const sectionKeys = ['g-resource', 'g-system', 'g-ops']
const expandedKeys = ref<string[]>([...sectionKeys])

const sectionOf: Record<string, string> = {
  models: 'g-resource',
  agents: 'g-resource',
  'knowledge-bases': 'g-resource',
  'mcp-servers': 'g-resource',
  skills: 'g-resource',
  'system-users': 'g-system',
  'system-roles': 'g-system',
  'system-tenants': 'g-system',
  observability: 'g-ops',
  chat: 'g-ops',
  settings: 'g-ops'
}

watch(activeKey, (key) => {
  const section = sectionOf[key]
  if (section && !expandedKeys.value.includes(section)) {
    expandedKeys.value = [...expandedKeys.value, section]
  }
})

function handleMenuUpdate(key: string) {
  router.push({ name: key })
}

const userOptions = [{ label: '退出登录', key: 'logout', icon: icon(LogOut) }]

function handleUserSelect(key: string) {
  if (key === 'logout') {
    clearAdminToken()
    router.push({ name: 'login' })
  }
}

onMounted(() => {
  loadCurrentUser()
})
</script>

<template>
  <n-layout has-sider position="absolute" class="shell">
    <n-layout-sider
      bordered
      :width="248"
      :collapsed-width="64"
      :collapsed="collapsed"
      collapse-mode="width"
      :native-scrollbar="false"
      class="sider"
    >
      <div class="sider-brand">
        <div class="brand-mark"><n-icon :component="Bot" :size="20" /></div>
        <div v-if="!collapsed" class="brand-text">
          <div class="brand-name">v5ai-nb</div>
          <div class="brand-sub">Agent Platform</div>
        </div>
      </div>

      <n-menu
        class="sider-menu"
        :value="activeKey"
        :options="nav"
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="20"
        :expanded-keys="expandedKeys"
        @update:value="handleMenuUpdate"
        @update:expanded-keys="(keys: string[]) => (expandedKeys = keys)"
      />

      <div class="sider-footer">
        <n-button quaternary block :title="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
          <template #icon>
            <n-icon :component="collapsed ? PanelLeftOpen : PanelLeftClose" />
          </template>
          <template v-if="!collapsed" #default>收起</template>
        </n-button>
      </div>
    </n-layout-sider>

    <n-layout class="main">
      <n-layout-header bordered class="topbar">
        <div class="topbar-left">
          <h1>{{ pageTitle }}</h1>
          <span class="topbar-sub">基于 AgentScope 的中心化 AI 平台</span>
        </div>
        <div class="topbar-actions">
          <n-button
            quaternary
            circle
            :aria-label="isDark ? '切换为亮色' : '切换为暗色'"
            @click="toggleTheme"
          >
            <template #icon><n-icon :component="isDark ? Sun : Moon" /></template>
          </n-button>
          <n-dropdown :options="userOptions" trigger="click" @select="handleUserSelect">
            <button class="user-chip" type="button">
              <n-avatar round :size="30" class="user-avatar">
                {{ displayName.charAt(0).toUpperCase() }}
              </n-avatar>
              <span class="user-meta">
                <span class="user-name">{{ displayName }}</span>
                <span class="user-role">{{ isAdmin ? '管理员' : '成员' }}</span>
              </span>
            </button>
          </n-dropdown>
        </div>
      </n-layout-header>

      <n-layout-content :native-scrollbar="false" content-style="padding: 20px 24px 32px;">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped>
.shell {
  width: 100%;
  height: 100vh;
}

.sider {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sider-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 14px;
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  overflow: hidden;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
}

.brand-sub {
  font-size: 11px;
  opacity: 0.55;
}

.sider-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 8px;
}

.sider-footer {
  flex-shrink: 0;
  padding: 8px;
  border-top: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
}

.main {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 20px;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.topbar-left h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.topbar-sub {
  font-size: 13px;
  opacity: 0.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 6px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.user-chip:hover {
  background: rgba(128, 128, 128, 0.12);
}

.user-avatar {
  background: linear-gradient(135deg, #7c3aed, #0891b2);
  color: #fff;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.user-role {
  font-size: 11px;
  opacity: 0.55;
}

@media (max-width: 640px) {
  .topbar-sub {
    display: none;
  }
  .user-meta {
    display: none;
  }
}
</style>
