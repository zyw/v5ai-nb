import { computed, ref, watch } from 'vue'

/**
 * 门户主题：light / dark / system（跟随系统偏好）。
 *
 * 不用 Pinia —— 只有一个模式 ref。落地的副作用只有两件：`<html data-theme>` 与 `color-scheme`；
 * 具体颜色全部交给 `styles/base.css` 的语义令牌与 Naive 主题（App.vue 的 darkTheme + overrides），
 * 组件里不出现硬编码品牌色。
 */
export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE = 'v5ai_chat_theme'
const MODES: ThemeMode[] = ['light', 'dark', 'system']

function readStored(): ThemeMode {
  const raw = localStorage.getItem(STORAGE)
  return MODES.includes(raw as ThemeMode) ? (raw as ThemeMode) : 'system'
}

export const themeMode = ref<ThemeMode>(readStored())

/** 系统是否偏好深色（mode=system 时决定实际主题）；监听变化，切系统外观时页面跟着走 */
const systemDark = ref(false)
const media = window.matchMedia?.('(prefers-color-scheme: dark)')
if (media) {
  systemDark.value = media.matches
  media.addEventListener('change', (event) => (systemDark.value = event.matches))
}

export const isDark = computed(() =>
  themeMode.value === 'system' ? systemDark.value : themeMode.value === 'dark'
)

export const themeModeLabel = computed(
  () => ({ light: '浅色', dark: '深色', system: '跟随系统' })[themeMode.value]
)

export function setThemeMode(mode: ThemeMode) {
  themeMode.value = mode
  localStorage.setItem(STORAGE, mode)
}

watch(
  isDark,
  (dark) => {
    const root = document.documentElement
    root.dataset.theme = dark ? 'dark' : 'light'
    root.style.colorScheme = dark ? 'dark' : 'light'
  },
  { immediate: true }
)
