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
  NScrollbar,
  useDialog,
  type MenuOption
} from 'naive-ui'
import { LogOut, Moon, PanelLeftClose, PanelLeftOpen, Sun } from 'lucide-vue-next'
import { resolveMenuIcon } from '../utils/menuIcons'
import { isDark, toggleTheme } from '../stores/theme'
import { adminToken, clearAdminToken, currentMenus, displayName, ensureSession, isAdmin, refreshToken } from '../stores/session'
import { logout, appTitle, appDescription, type RouterVo } from '../api/client'

const route = useRoute()
const router = useRouter()
const dialog = useDialog()

const collapsed = ref(window.innerWidth < 900)

/**
 * 展开态侧栏宽度：可以拖着调，夹在下面这对上下限里，并把上次拖到的值记在本地。
 * 默认值就是原先写死的 248 —— 没拖过的人看到的还是原来那个侧栏，宽度不因这次改动而变。
 *
 * 这里只管展开态。收起后宽度是 :collapsed-width="64"，由 naive 自己控制，
 * 那时把手也一并藏起来（见 .sider-resizer.is-hidden）：收起态没有「宽度」可调。
 */
const SIDER_WIDTH_STORAGE = 'v5ai_ui_sider_width'
const SIDER_WIDTH_DEFAULT = 248
const SIDER_WIDTH_MIN = 200
const SIDER_WIDTH_MAX = 420

function readSiderWidth(): number {
  const raw = Number(localStorage.getItem(SIDER_WIDTH_STORAGE))
  if (!Number.isFinite(raw) || raw <= 0) return SIDER_WIDTH_DEFAULT
  return Math.min(SIDER_WIDTH_MAX, Math.max(SIDER_WIDTH_MIN, Math.round(raw)))
}

const siderWidth = ref(readSiderWidth())
const resizing = ref(false)

function applySiderWidth(px: number) {
  siderWidth.value = Math.min(SIDER_WIDTH_MAX, Math.max(SIDER_WIDTH_MIN, Math.round(px)))
}

function persistSiderWidth() {
  localStorage.setItem(SIDER_WIDTH_STORAGE, String(siderWidth.value))
}

/**
 * 拖拽调宽：从按下那一刻起在 window 上听 pointermove，指针移出把手（甚至移出窗口）也不断链。
 * 用 pointer 事件而不是 mouse，触屏与触控板同样能拖。
 */
function startResize(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  const startX = event.clientX
  const startWidth = siderWidth.value
  resizing.value = true
  const onMove = (move: PointerEvent) => applySiderWidth(startWidth + (move.clientX - startX))
  const onUp = () => {
    resizing.value = false
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onUp)
    persistSiderWidth()
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  window.addEventListener('pointercancel', onUp)
}

/**
 * 拖动之外的第二条路：把手聚焦后 ←/→ 微调（Shift 加速），Home/End 直达两端，双击复位。
 * 不光是为了好用 —— 拖拽类操作必须另有一个单指针 / 键盘入口，否则用不了指针设备的人就没法调宽度。
 */
function onResizerKeydown(event: KeyboardEvent) {
  const step = event.shiftKey ? 40 : 16
  if (event.key === 'ArrowLeft') applySiderWidth(siderWidth.value - step)
  else if (event.key === 'ArrowRight') applySiderWidth(siderWidth.value + step)
  else if (event.key === 'Home') applySiderWidth(SIDER_WIDTH_MIN)
  else if (event.key === 'End') applySiderWidth(SIDER_WIDTH_MAX)
  else return
  event.preventDefault()
  persistSiderWidth()
}

function resetSiderWidth() {
  applySiderWidth(SIDER_WIDTH_DEFAULT)
  persistSiderWidth()
}

function icon(component: Component) {
  return () => h(NIcon, { component })
}

type NavItem = MenuOption & { key: string }

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
              icon: icon(resolveMenuIcon(m.meta?.icon)),
              children: build(m.children)
            }
          }
          const only = m.children[0]
          return {
            label: only.meta?.title ?? only.name,
            key: normalizePath(only.path),
            icon: icon(resolveMenuIcon(only.meta?.icon ?? m.meta?.icon))
          }
        }
        return {
          label: m.meta?.title ?? m.name,
          key: normalizePath(m.path),
          icon: icon(resolveMenuIcon(m.meta?.icon))
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

function handleUserSelect(key: string) {
  if (key !== 'logout') {
    return
  }
  // 退出会吊销服务端会话并跳回登录页，先二次确认避免误触
  dialog.warning({
    title: '退出登录',
    content: '确认退出当前账号？退出后需要重新登录。',
    positiveText: '退出登录',
    negativeText: '取消',
    onPositiveClick: async () => {
      // 吊销服务端会话与刷新令牌（失败不阻断本地清理）
      try {
        await logout(adminToken.value, refreshToken.value)
      } catch {
        // 忽略：本地会话照常清理
      }
      clearAdminToken()
      router.push({ name: 'login' })
    }
  })
}

onMounted(() => {
  ensureSession()
})
</script>

<template>
  <n-layout has-sider position="absolute" class="shell" :class="{ 'is-resizing': resizing }">
    <!--
      content-style 把侧栏的内容层变成「定高的弹性列」：品牌行 / 菜单 / 栏底各归各位，三者里只有菜单滚动。
      不这么设，naive 的滚动容器会把这三段当成一整块内容一起滚 —— 菜单一长，顶上的品牌标识和底下的按钮
      就跟着滚出视野（原来就是这个毛病）。

      用 content-style 而不是 :deep() 去改滚动容器：这是 naive 公开的内容样式入口，
      升级换版本时不依赖它内部的类名。
    -->
    <n-layout-sider
      bordered
      :width="siderWidth"
      :collapsed-width="64"
      :collapsed="collapsed"
      collapse-mode="width"
      :native-scrollbar="false"
      class="sider"
      :class="{ 'is-resizing': resizing }"
      content-style="display: flex; flex-direction: column; height: 100%;"
    >
      <div class="sider-brand" :class="{ collapsed }">
        <div class="brand-mark" aria-hidden="true"></div>
        <div v-if="!collapsed" class="brand-text">
          <div class="brand-name">{{ appTitle }}</div>
          <div class="brand-sub">{{ appDescription }}</div>
        </div>
        <!-- 收起：与品牌标识同一行、贴这一行的最右。收起后这一行只剩 44px 标识，塞不下它，
             展开入口换到栏底（见 .sider-footer）—— 两个入口按状态互斥渲染，不会同时出现 -->
        <n-button
          v-if="!collapsed"
          quaternary
          circle
          class="brand-toggle"
          title="收起菜单"
          aria-label="收起菜单"
          @click="collapsed = true"
        >
          <template #icon><n-icon :component="PanelLeftClose" /></template>
        </n-button>
      </div>

      <!-- 菜单自己再套一层滚动容器：上面那条 content-style 已经把外层滚动废掉了
           （内容层正好铺满容器，外层无处可滚），不套的话菜单会退回浏览器默认滚动条 -->
      <n-scrollbar class="sider-menu-wrap">
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
      </n-scrollbar>

      <div v-if="collapsed" class="sider-footer">
        <n-button quaternary block title="展开菜单" aria-label="展开菜单" @click="collapsed = false">
          <template #icon><n-icon :component="PanelLeftOpen" /></template>
        </n-button>
      </div>

      <!--
        拖拽调宽：8px 命中区骑在侧栏右边缘上（左右各 4px），看得见的只有那 2px 线。

        放在侧栏里面而不是外面，是为了让它永远咬着边缘 —— 它按 right: -4px 定位，
        测的是侧栏当下的实际宽度，所以收起/展开那 0.3s 里边框动画到哪儿它就在哪儿，
        不必再补一条同起点、同曲线的过渡去对齐（那样两边算出来的位置很难逐帧相等）。

        收起后整根藏掉：那时宽度由 naive 按 collapsed-width 管。用 visibility 而不是
        只写 pointer-events:none —— 后者挡得住鼠标，键盘仍然 Tab 得到这根看不见的把手。
      -->
      <div
        class="sider-resizer"
        :class="{ 'is-hidden': collapsed }"
        role="separator"
        aria-orientation="vertical"
        aria-label="调整侧栏宽度"
        :aria-valuenow="siderWidth"
        :aria-valuemin="SIDER_WIDTH_MIN"
        :aria-valuemax="SIDER_WIDTH_MAX"
        tabindex="0"
        @pointerdown="startResize"
        @keydown="onResizerKeydown"
        @dblclick="resetSiderWidth"
      />
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

/* 拖拽调宽期间：整页禁选中，指针锁成 col-resize。
   user-select 会继承到所有后代，所以拖过正文也不会变成选词 */
.shell.is-resizing {
  user-select: none;
  cursor: col-resize;
}

/* 侧栏外壳。这里不管三段的排布 —— 真正的弹性列在内容层（见模板上的 content-style），
   三段都在那一层里。这一条只做两件事：
   一是给出确定的高度，底下那串百分比高度（.n-scrollbar → 滚动容器 → 内容层）才有得可解析；
   二是把方向掰回纵向 —— naive 给 .n-layout-sider 的是 display:flex + 横向 + justify-content:flex-end
   （为的是把自带触发器推到右边），不覆盖方向的话这一层就成了横向弹性行。 */
.sider {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 拖动期间必须掐掉侧栏自己的过渡。naive 的收起动画走的是 min-width/max-width 的过渡，
   而展开态它把 max-width 也设成当前宽度 —— 不掐掉的话，每动一格 max-width 都要用 0.3s
   追上来，侧栏就会一直落在指针后面。松手时 width 与 max-width 已经相等，
   过渡恢复也不会补一段动画，等于原地接着走。 */
.sider.is-resizing {
  transition: none;
}

/* 把手：8px 命中区骑在右边缘上，可见部分只有 2px 线。
   touch-action: none 让触屏上是拖而不是滚页面 */
.sider-resizer {
  position: absolute;
  top: 0;
  right: -4px;
  width: 8px;
  height: 100%;
  z-index: 2;
  cursor: col-resize;
  touch-action: none;
}

.sider-resizer::after {
  content: '';
  position: absolute;
  top: 0;
  right: 3px;
  width: 2px;
  height: 100%;
  border-radius: 2px;
  background: transparent;
  transition: background 0.2s ease;
}

/* 悬停 / 聚焦 / 正在拖，同一个反馈：线亮起来。线正好压在侧栏那道 1px 分隔线上 */
.sider-resizer:hover::after,
.sider-resizer:focus-visible::after,
.shell.is-resizing .sider-resizer::after {
  background: var(--brand);
}

/* 焦点环由上面那条通高的 2px 线兼任。这里是个贴着边缘的窄条，
   画全局那圈 outline 会糊到相邻栏上去 */
.sider-resizer:focus-visible {
  outline: none;
}

.sider-resizer.is-hidden {
  visibility: hidden;
  pointer-events: none;
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
  /* 吃掉标识与按钮之间的剩余宽度，收起按钮因此被顶到这一行的最右；
     min-width: 0 是让它能被压缩（否则文案会把按钮挤出可视区），压不下时由 overflow 裁掉 */
  flex: 1;
  min-width: 0;
}

/* 与顶栏的主题切换按钮同款：图标按钮一律 quaternary + circle */
.brand-toggle {
  flex: none;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
}

.brand-sub {
  font-size: 11px;
  opacity: 0.55;
}

/* 三段里唯一可伸缩的一段，也是唯一滚动的一段。
   min-height: 0 不能省：弹性项默认的最小高度是内容高度，不归零就压不下去、只能整体撑高外框 */
.sider-menu-wrap {
  flex: 1;
  min-height: 0;
}

/* 滚动交给外面那层 .sider-menu-wrap，这里只留内边距 */
.sider-menu {
  padding: 4px 8px;
}

/* 收起时去掉左右留白，让菜单项图标与悬停背景在 64px 内居中 */
.sider-menu.collapsed {
  padding: 4px 0;
}

/* 只在收起态出现：展开时「收起」在品牌行右侧，这一行留给收起后唯一还塞得下的「展开」。
   按钮 block 铺满 64px 窄栏，与收起前那块通栏按钮的位置一致，切过去不跳 */
.sider-footer {
  flex-shrink: 0;
  padding: 8px 0;
  border-top: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.16));
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
