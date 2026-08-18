<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RoleTag from '../components/RoleTag.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createUser,
  listRoles,
  listTenants,
  listUsers,
  updateUserStatus,
  updateUserRoles,
  type Role,
  type Tenant,
  type UserAccount
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const users = ref<UserAccount[]>([])
const tenants = ref<Tenant[]>([])
const roles = ref<Role[]>([])

const showCreateModal = ref(false)
const showRolesModal = ref(false)
const saving = ref(false)

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  tenantId: null as number | null,
  roles: ['USER'] as string[]
})

const editingUser = ref<UserAccount | null>(null)
const roleForm = reactive({ roles: [] as string[] })

const roleOptions = () => roles.value.map((r) => ({ label: `${r.roleName} (${r.roleKey})`, value: r.roleKey }))
const tenantOptions = () => tenants.value.map((t) => ({ label: `${t.name} (${t.tenantKey})`, value: t.id }))

const columns: DataTableColumns<UserAccount> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '用户名', key: 'username' },
  { title: '显示名', key: 'displayName', render: (row) => row.displayName ?? '-' },
  { title: '租户', key: 'tenantId', width: 90, render: (row) => (row.tenantId ? `#${row.tenantId}` : '-') },
  {
    title: '角色',
    key: 'roles',
    render: (row) =>
      h(NSpace, { size: 4 }, { default: () => row.roles.map((role) => h(RoleTag, { role })) })
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(StatusTag, { status: row.status === '1' ? 'DISABLED' : 'ACTIVE' })
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => openRoles(row) }, { default: () => '分配角色' }),
        h(NButton, { size: 'small', secondary: true, type: row.status === '1' ? 'primary' : 'warning', onClick: () => handleToggleStatus(row) }, { default: () => (row.status === '1' ? '启用' : '禁用') })
      ] })
  }
]

async function reload() {
  loading.value = true
  try {
    const [userRows, tenantRows, roleRows] = await Promise.all([
      listUsers(adminToken.value),
      listTenants(adminToken.value),
      listRoles(adminToken.value)
    ])
    users.value = userRows.rows
    tenants.value = tenantRows.rows
    roles.value = roleRows.rows
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载用户失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.tenantId = null
  form.roles = ['USER']
  showCreateModal.value = true
}

async function handleCreate() {
  saving.value = true
  try {
    await createUser(adminToken.value, {
      username: form.username,
      password: form.password,
      displayName: form.displayName || undefined,
      tenantId: form.tenantId ?? undefined,
      roles: form.roles
    })
    message.success('用户已创建')
    showCreateModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建用户失败')
  } finally {
    saving.value = false
  }
}

function openRoles(row: UserAccount) {
  editingUser.value = row
  roleForm.roles = [...row.roles]
  showRolesModal.value = true
}

async function handleSaveRoles() {
  if (!editingUser.value) return
  saving.value = true
  try {
    await updateUserRoles(adminToken.value, editingUser.value.id, roleForm.roles)
    message.success(`${editingUser.value.username} 角色已更新`)
    showRolesModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新角色失败')
  } finally {
    saving.value = false
  }
}

async function handleToggleStatus(row: UserAccount) {
  try {
    const next = row.status === '1' ? '0' : '1'
    await updateUserStatus(adminToken.value, row.id, next)
    message.success(`${row.username} 已${next === '1' ? '禁用' : '启用'}`)
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="用户管理" description="管理平台账号、所属租户与角色分配。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建用户
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="columns" :data="users" :bordered="false" />
    </n-card>

    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      title="新建用户"
      style="width: 460px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="用户名">
          <n-input v-model:value="form.username" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="form.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="显示名">
          <n-input v-model:value="form.displayName" placeholder="可选" />
        </n-form-item>
        <n-form-item label="归属租户">
          <n-select v-model:value="form.tenantId" clearable :options="tenantOptions()" placeholder="可选，默认不归属" />
        </n-form-item>
        <n-form-item label="角色">
          <n-select v-model:value="form.roles" multiple :options="roleOptions()" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleCreate">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showRolesModal"
      preset="card"
      :title="`分配角色（${editingUser?.username ?? ''}）`"
      style="width: 440px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="角色">
          <n-select v-model:value="roleForm.roles" multiple :options="roleOptions()" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showRolesModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSaveRoles">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
