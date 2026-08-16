import { computed, ref, watchEffect } from 'vue'

type ThemeMode = 'light' | 'dark'

const storageKey = 'v5ai.themeMode'

function detectInitial(): ThemeMode {
  const stored = localStorage.getItem(storageKey)
  if (stored === 'light' || stored === 'dark') return stored
  const prefersDark =
    typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
  return prefersDark ? 'dark' : 'light'
}

export const themeMode = ref<ThemeMode>(detectInitial())
export const isDark = computed(() => themeMode.value === 'dark')

export function setThemeMode(mode: ThemeMode): void {
  themeMode.value = mode
  localStorage.setItem(storageKey, mode)
}

export function toggleTheme(): void {
  setThemeMode(themeMode.value === 'dark' ? 'light' : 'dark')
}

// 让原生滚动条/输入框等跟随主题，并保持刷新后一致
watchEffect(() => {
  if (typeof document !== 'undefined') {
    document.documentElement.style.colorScheme = themeMode.value
    document.documentElement.dataset.theme = themeMode.value
  }
})
