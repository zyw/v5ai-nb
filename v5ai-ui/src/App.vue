<script setup lang="ts">
import { computed, h, type Component } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { NConfigProvider, NIcon, NLayout, NLayoutContent, NLayoutSider, NMenu, NMessageProvider, NTag, darkTheme, type MenuOption } from 'naive-ui'
import { Bot, Boxes, Database, LayoutDashboard, MessageSquare, Rocket, Settings } from 'lucide-vue-next'
import { isLoggedIn } from './stores/session'

const route = useRoute()
const router = useRouter()

const menuOptions: MenuOption[] = [
  { label: 'Dashboard', key: 'dashboard', icon: () => icon(LayoutDashboard) },
  { label: 'Models', key: 'models', icon: () => icon(Boxes) },
  { label: 'Applications', key: 'applications', icon: () => icon(Rocket) },
  { label: 'Knowledge Bases', key: 'knowledge-bases', icon: () => icon(Database) },
  { label: 'Chat Debug', key: 'chat', icon: () => icon(MessageSquare) },
  { label: 'Settings', key: 'settings', icon: () => icon(Settings) }
]

const active = computed(() => String(route.name ?? 'dashboard'))
const currentTitle = computed(() => menuOptions.find(item => item.key === active.value)?.label ?? 'Dashboard')

function icon(component: Component) {
  return h(NIcon, { component })
}

function handleMenuUpdate(key: string) {
  router.push({ name: key })
}
</script>

<template>
  <n-config-provider :theme="darkTheme">
    <n-message-provider>
      <n-layout has-sider class="shell">
        <n-layout-sider bordered :width="232" class="sider">
          <div class="brand">
            <div class="brand-mark"><n-icon :component="Bot" /></div>
            <div>
              <div class="brand-name">v5ai-nb</div>
              <div class="brand-sub">Agent Platform</div>
            </div>
          </div>
          <n-menu :value="active" :options="menuOptions" @update:value="handleMenuUpdate" />
        </n-layout-sider>

        <n-layout-content class="content">
          <header class="topbar">
            <div>
              <h1>{{ currentTitle }}</h1>
              <p>基于 AgentScope Java 的中心化 Dify 风格控制台</p>
            </div>
            <div class="topbar-actions">
              <n-tag :type="isLoggedIn ? 'success' : 'warning'" round>{{ isLoggedIn ? 'Admin 已登录' : '未登录' }}</n-tag>
              <n-tag type="info" round>Phase 1</n-tag>
            </div>
          </header>

          <main class="page">
            <router-view />
          </main>
        </n-layout-content>
      </n-layout>
    </n-message-provider>
  </n-config-provider>
</template>
