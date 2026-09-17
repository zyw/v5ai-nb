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
  Cable,
  Database,
  Files,
  Gauge,
  Info,
  KeyRound,
  Layers,
  LayoutDashboard,
  ListTree,
  LogOut,
  MessageSquare,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  ScrollText,
  Server,
  Settings,
  ShieldCheck,
  Sparkles,
  Sun,
  Users,
  Workflow
} from 'lucide-vue-next'
import { isDark, toggleTheme } from '../stores/theme'
import { adminToken, clearAdminToken, currentMenus, displayName, ensureSession, isAdmin, refreshToken } from '../stores/session'
import { logout, type RouterVo } from '../api/client'

const route = useRoute()
const router = useRouter()

const collapsed = ref(window.innerWidth < 900)

function icon(component: Component) {
  return () => h(NIcon, { component })
}

type NavItem = MenuOption & { key: string }

// 菜单 icon 名 → lucide 组件映射（与后端种子 / 菜单管理界面填写的 icon 字段一致）
const iconMap: Record<string, Component> = {
  dashboard: LayoutDashboard,
  resource: Layers,
  system: Settings,
  ops: Gauge,
  models: Boxes,
  agents: Bot,
  'knowledge-bases': Database,
  'store-instances': Server,
  files: Files,
  'mcp-servers': Cable,
  skills: Sparkles,
  workflows: Workflow,
  users: Users,
  roles: ShieldCheck,
  menus: ListTree,
  clients: KeyRound,
  logs: ScrollText,
  observability: Activity,
  chat: MessageSquare,
  settings: Info
}

function iconFor(name?: string | null): Component {
  return (name && iconMap[name]) || LayoutDashboard
}

// 由 getRouters 返回的路由菜单树构建导航（目录 -> submenu，叶子 -> 按 path 跳转）
function normalizePath(path?: string): string {
  if (!path) return ''
  return path.startsWith('/') ? path : `/${path}`
}

const nav = computed<NavItem[]>(() => {
  const build = (menus: RouterVo[]): NavItem[] =>
    menus
      .filter((m) => !m.hidden)
      .map((m) => {
        if (m.children && m.children.length > 0) {
          // RuoYi 规则：仅 alwaysShow 或子节点多于 1 个才渲染为子菜单；
          // 单子节点的框架包装（如顶级菜单被后端包一层）直接展示其唯一子节点，避免出现二级菜单
          if (m.alwaysShow || m.children.length > 1) {
            return {
              type: 'submenu' as const,
              label: m.meta?.title ?? m.name,
              key: m.path,
              icon: icon(iconFor(m.meta?.icon)),
              children: build(m.children)
            }
          }
          const only = m.children[0]
          return {
            label: only.meta?.title ?? only.name,
            key: normalizePath(only.path),
            icon: icon(iconFor(only.meta?.icon ?? m.meta?.icon))
          }
        }
        return {
          label: m.meta?.title ?? m.name,
          key: normalizePath(m.path),
          icon: icon(iconFor(m.meta?.icon))
        }
      })
  return build(currentMenus.value)
})

const activeKey = computed(() => String(route.path))
const pageTitle = computed(() => String(route.meta.title ?? '总览'))

// 分组默认全部展开，且导航到某页时自动展开其所属分组
const expandedKeys = ref<string[]>([])

const dirPaths = computed(() => {
  const collect = (menus: RouterVo[]): string[] =>
    menus
      .filter((m) => m.children && m.children.length > 0 && (m.alwaysShow || m.children.length > 1))
      .flatMap((m) => [m.path, ...collect(m.children ?? [])])
  return collect(currentMenus.value)
})
watch(dirPaths, (paths) => {
  expandedKeys.value = [...new Set([...expandedKeys.value, ...paths])]
}, { immediate: true })

watch(activeKey, (path) => {
  const parts = path.split('/').filter(Boolean)
  for (let i = parts.length - 1; i >= 1; i--) {
    const parent = `/${parts.slice(0, i).join('/')}`
    if (dirPaths.value.includes(parent)) {
      if (!expandedKeys.value.includes(parent)) {
        expandedKeys.value = [...expandedKeys.value, parent]
      }
      return
    }
  }
})

function handleMenuUpdate(key: string) {
  router.push(key)
}

const userOptions = [{ label: '退出登录', key: 'logout', icon: icon(LogOut) }]

async function handleUserSelect(key: string) {
  if (key === 'logout') {
    // 吊销服务端会话与刷新令牌（失败不阻断本地清理）
    try {
      await logout(adminToken.value, refreshToken.value)
    } catch {
      // 忽略：本地会话照常清理
    }
    clearAdminToken()
    router.push({ name: 'login' })
  }
}

onMounted(() => {
  ensureSession()
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
      <div class="sider-brand" :class="{ collapsed }">
        <div class="brand-mark"><n-icon :component="Bot" :size="20" /></div>
        <div v-if="!collapsed" class="brand-text">
          <div class="brand-name">V5Ai-NB</div>
          <div class="brand-sub">Agent Platform</div>
        </div>
      </div>

      <n-menu
        class="sider-menu"
        :class="{ collapsed }"
        :value="activeKey"
        :options="nav"
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="20"
        :expanded-keys="expandedKeys"
        @update:value="handleMenuUpdate"
        @update:expanded-keys="(keys: string[]) => (expandedKeys = keys)"
      />

      <div class="sider-footer" :class="{ collapsed }">
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

.sider-brand.collapsed {
  justify-content: center;
  padding: 0;
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

/* 收起时去掉左右留白，让菜单项图标与悬停背景在 64px 内居中 */
.sider-menu.collapsed {
  padding: 4px 0;
}

.sider-footer {
  flex-shrink: 0;
  padding: 8px;
  border-top: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
}

.sider-footer.collapsed {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

.main {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  /* 顶栏高度在这里定义一次，往下继承：需要「页面可用高度」的子页面（如知识库详情页
     问答 tab）用 calc(100% - var(--app-topbar-height)) 就能拿到，不必各自写死 64px。
     变量挂在 .main 上，router-view 里的页面都是它的后代，能自然继承到。 */
  --app-topbar-height: 64px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--app-topbar-height);
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
