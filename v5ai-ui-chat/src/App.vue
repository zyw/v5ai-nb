<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider,
  NMessageProvider,
  darkTheme,
  dateZhCN,
  zhCN,
  type GlobalThemeOverrides
} from 'naive-ui'
import { isDark } from './stores/theme'

/**
 * 全局主题接线：亮色走 Naive 默认主题，暗色走 darkTheme，两套共用同一份品牌令牌
 * （靛蓝→青主色、圆角、系统字体栈）。页面自身的深/浅由 base.css 的语义令牌负责。
 */
const overrides = computed<GlobalThemeOverrides>(() => {
  const dark = isDark.value
  return {
    common: {
      primaryColor: dark ? '#818cf8' : '#4f46e5',
      primaryColorHover: dark ? '#a5b4fc' : '#6366f1',
      primaryColorPressed: dark ? '#6366f1' : '#4338ca',
      primaryColorSuppl: dark ? '#6366f1' : '#6366f1',
      borderRadius: '10px',
      borderRadiusSmall: '8px',
      fontFamily:
        "system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
      fontFamilyMono: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
    },
    Input: {
      color: dark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(15, 23, 42, 0.03)',
      colorFocus: dark ? 'rgba(255, 255, 255, 0.06)' : '#ffffff',
      border: dark ? '1px solid rgba(255, 255, 255, 0.09)' : '1px solid rgba(15, 23, 42, 0.1)',
      borderFocus: dark
        ? '1px solid rgba(129, 140, 248, 0.8)'
        : '1px solid rgba(79, 70, 229, 0.7)',
      boxShadowFocus: dark
        ? '0 0 0 2px rgba(99, 102, 241, 0.28)'
        : '0 0 0 2px rgba(79, 70, 229, 0.18)'
    },
    Button: {
      borderRadiusMedium: '10px',
      borderRadiusLarge: '12px'
    },
    /*
     * Tooltip 必须同时给底色和文字色：它是「Popover 穿马甲」（Tooltip.mjs 把主题的 self
     * 当 builtinThemeOverrides 传给 NPopover），只改底色而文字色仍取主题的 baseColor（浅色下是白），
     * 就会白字白底。
     *
     * 另外这里不要再覆盖 Popover：naive 会把全局的 themeOverrides.Popover 作为
     * peerOverrides 合进 Tooltip 的弹层（use-theme.mjs 的 globalPeersOverrides），
     * 一旦把 Popover 底色设成浅色，Tooltip 的底色也会跟着被改成浅色。
     * 门户自己的弹层（AgentSwitcher / UserMenu）是 raw 模式 + 自带底色，不需要这份覆盖。
     */
    Tooltip: {
      color: dark ? '#12151e' : '#1f2430',
      textColor: dark ? '#edeff5' : '#f5f7fa'
    }
  }
})
</script>

<template>
  <n-config-provider
    :locale="zhCN"
    :date-locale="dateZhCN"
    :theme="isDark ? darkTheme : null"
    :theme-overrides="overrides"
  >
    <n-message-provider>
      <router-view />
    </n-message-provider>
  </n-config-provider>
</template>
