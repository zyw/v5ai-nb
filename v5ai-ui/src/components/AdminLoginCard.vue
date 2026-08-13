<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NCard, NForm, NFormItem, NInput, NSpace, useMessage } from 'naive-ui'
import { login } from '../api/client'
import { adminToken, clearAdminToken, setAdminToken } from '../stores/session'

const emit = defineEmits<{
  loggedIn: []
}>()

const message = useMessage()
const username = ref('admin')
const password = ref('admin')
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    const result = await login(username.value, password.value)
    setAdminToken(`${result.tokenType} ${result.token}`)
    message.success('登录成功')
    emit('loggedIn')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <n-card title="管理员登录" class="panel">
    <n-form label-placement="top">
      <n-form-item label="用户名">
        <n-input v-model:value="username" />
      </n-form-item>
      <n-form-item label="密码">
        <n-input v-model:value="password" type="password" show-password-on="mousedown" />
      </n-form-item>
      <n-space>
        <n-button type="primary" :loading="loading" @click="handleLogin">登录</n-button>
        <n-button secondary @click="clearAdminToken">清除 Token</n-button>
      </n-space>
    </n-form>
    <p v-if="adminToken" class="token-preview">{{ adminToken }}</p>
  </n-card>
</template>
