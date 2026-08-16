<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NSelect, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import {
  createTenant,
  createUser,
  disableTenant,
  getAppDailyUsage,
  getAppQuota,
  listAgents,
  listTenants,
  listUsers,
  updateAppQuota,
  updateUserEnabled,
  updateUserRoles,
  type AppQuota,
  type Tenant,
  type UserAccount
} from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const tenants = ref<Tenant[]>([])
const users = ref<UserAccount[]>([])
const apps = ref<{ agentKey: string }[]>([])

const tenantForm = reactive({ tenantKey: 'acme', name: 'Acme Corp' })
const userForm = reactive({ username: 'bob', password: 'secret123', displayName: '', tenantId: null as number | null })
const quotaForm = reactive({ agentKey: 'demo', dailyModelCalls: 0, dailyTokens: 0, ratePerMinute: 0 })
const dailyUsage = ref('')

const roleOptions = [
  { label: 'admin', value: 'ADMIN' },
  { label: 'user', value: 'USER' }
]

const tenantColumns: DataTableColumns<Tenant> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: 'Key', key: 'tenantKey' },
  { title: '名称', key: 'name' },
  { title: '状态', key: 'status', render: row => h(NTag, { size: 'small', type: row.status === 'ACTIVE' ? 'success' : 'default' }, { default: () => row.status }) },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: row => h(NButton, { size: 'small', type: 'error', secondary: true, disabled: !isLoggedIn.value || row.status !== 'ACTIVE', onClick: () => handleDisableTenant(row) }, { default: () => '禁用' })
  }
]

const userColumns: DataTableColumns<UserAccount> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '用户名', key: 'username' },
  { title: '显示名', key: 'displayName', render: row => row.displayName ?? '-' },
  { title: '租户', key: 'tenantId', render: row => row.tenantId ? `#${row.tenantId}` : '-' },
  { title: '角色', key: 'roles', render: row => row.roles.map(role => h(NTag, { size: 'small', type: role === 'ADMIN' ? 'warning' : 'info', style: 'margin-right:4px' }, { default: () => role })) },
  {
    title: '状态',
    key: 'enabled',
    render: row => h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, { default: () => row.enabled ? '启用' : '禁用' })
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: row => h(NSpace, { size: 6 }, { default: () => [
      h(NSelect, {
        size: 'small',
        value: row.roles,
        multiple: true,
        options: roleOptions,
        disabled: !isLoggedIn.value,
        style: 'width:150px',
        onUpdateValue: (roles: string[]) => handleRoles(row, roles)
      }),
      h(NButton, { size: 'small', type: row.enabled ? 'warning' : 'primary', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleEnabled(row) }, { default: () => row.enabled ? '禁用' : '启用' })
    ] })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  const [tenantRows, userRows, appRows] = await Promise.all([
    listTenants(adminToken.value),
    listUsers(adminToken.value),
    listAgents(adminToken.value)
  ])
  tenants.value = tenantRows
  users.value = userRows
  apps.value = appRows
}

async function handleCreateTenant() {
  try {
    await createTenant(adminToken.value, tenantForm.tenantKey, tenantForm.name)
    message.success('租户已创建')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建租户失败')
  }
}

async function handleDisableTenant(row: Tenant) {
  try {
    await disableTenant(adminToken.value, row.id)
    message.success('租户已禁用')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '禁用失败')
  }
}

async function handleCreateUser() {
  try {
    await createUser(adminToken.value, {
      username: userForm.username,
      password: userForm.password,
      displayName: userForm.displayName || undefined,
      tenantId: userForm.tenantId ?? undefined
    })
    message.success('用户已创建（默认角色 user）')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建用户失败')
  }
}

async function handleRoles(row: UserAccount, roles: string[]) {
  try {
    await updateUserRoles(adminToken.value, row.id, roles)
    message.success(`${row.username} 角色已更新`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '更新角色失败')
  }
}

async function handleEnabled(row: UserAccount) {
  try {
    await updateUserEnabled(adminToken.value, row.id, !row.enabled)
    message.success(`${row.username} 已${row.enabled ? '禁用' : '启用'}`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function reloadQuota() {
  try {
    const [quota, usage] = await Promise.all([
      getAppQuota(adminToken.value, quotaForm.agentKey),
      getAppDailyUsage(adminToken.value, quotaForm.agentKey)
    ])
    quotaForm.dailyModelCalls = quota.dailyModelCalls
    quotaForm.dailyTokens = quota.dailyTokens
    quotaForm.ratePerMinute = quota.ratePerMinute
    dailyUsage.value = `今日调用 ${usage.modelCalls} 次 / ${usage.tokens} tokens`
  } catch {
    quotaForm.dailyModelCalls = 0
    quotaForm.dailyTokens = 0
    quotaForm.ratePerMinute = 0
    dailyUsage.value = '今日暂无用量'
  }
}

async function handleSaveQuota() {
  try {
    await updateAppQuota(adminToken.value, quotaForm.agentKey, {
      dailyModelCalls: quotaForm.dailyModelCalls,
      dailyTokens: quotaForm.dailyTokens,
      ratePerMinute: quotaForm.ratePerMinute
    })
    message.success('配额已更新（0 = 不限）')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '更新配额失败')
  }
}

onMounted(async () => {
  await reload()
  if (apps.value.length > 0) {
    quotaForm.agentKey = apps.value[0].agentKey
  }
  await reloadQuota()
})
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再管理租户、用户与配额（写操作需要 admin 角色）。
    </n-alert>

    <n-card title="创建租户" class="panel">
      <n-form label-placement="top">
        <n-form-item label="租户 Key">
          <n-input v-model:value="tenantForm.tenantKey" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="tenantForm.name" />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreateTenant">创建租户</n-button>
      </n-form>
    </n-card>

    <n-card title="创建用户" class="panel">
      <n-form label-placement="top">
        <n-form-item label="用户名">
          <n-input v-model:value="userForm.username" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="userForm.password" type="password" show-password-on="mousedown" />
        </n-form-item>
        <n-form-item label="显示名">
          <n-input v-model:value="userForm.displayName" placeholder="可选" />
        </n-form-item>
        <n-form-item label="归属租户">
          <n-select
            v-model:value="userForm.tenantId"
            clearable
            :options="tenants.map(t => ({ label: `${t.name} (${t.tenantKey})`, value: t.id }))"
            placeholder="可选，默认不归属"
          />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreateUser">创建用户</n-button>
      </n-form>
    </n-card>

    <n-card title="应用配额（0 = 不限）" class="panel">
      <n-form label-placement="top">
        <n-form-item label="Agent">
          <n-select
            v-model:value="quotaForm.agentKey"
            filterable
            tag
            :options="apps.map(a => ({ label: a.agentKey, value: a.agentKey }))"
            @update:value="reloadQuota"
          />
        </n-form-item>
        <n-form-item label="每日调用次数上限">
          <n-input-number v-model:value="quotaForm.dailyModelCalls" :min="0" />
        </n-form-item>
        <n-form-item label="每日 Token 上限">
          <n-input-number v-model:value="quotaForm.dailyTokens" :min="0" />
        </n-form-item>
        <n-form-item label="每分钟限流">
          <n-input-number v-model:value="quotaForm.ratePerMinute" :min="0" />
        </n-form-item>
        <n-button secondary type="info" :disabled="!isLoggedIn" @click="handleSaveQuota">保存配额</n-button>
        <n-tag v-if="dailyUsage" type="info" style="margin-left: 12px">{{ dailyUsage }}</n-tag>
      </n-form>
    </n-card>

    <n-card title="租户列表" class="panel wide">
      <n-space vertical>
        <n-button secondary :disabled="!isLoggedIn" @click="reload">刷新</n-button>
        <n-data-table :columns="tenantColumns" :data="tenants" />
      </n-space>
    </n-card>

    <n-card title="用户列表" class="panel wide">
      <n-data-table :columns="userColumns" :data="users" />
    </n-card>
  </div>
</template>
