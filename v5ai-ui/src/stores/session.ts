import { computed, ref } from 'vue'

const storageKey = 'v5ai.adminToken'

export const adminToken = ref(localStorage.getItem(storageKey) ?? '')
export const isLoggedIn = computed(() => adminToken.value.length > 0)

export function setAdminToken(token: string): void {
  adminToken.value = token
  localStorage.setItem(storageKey, token)
}

export function clearAdminToken(): void {
  adminToken.value = ''
  localStorage.removeItem(storageKey)
}
