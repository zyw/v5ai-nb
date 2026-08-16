<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RoleTag from '../components/RoleTag.vue'
import { createRole, deleteRole, listRoles, updateRole, type Role } from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const roles = ref<Role[]>([])
const showModal = ref(false)
const editingKey = ref<string | null>(null)
const saving = ref(false)

const form = reactive({ roleKey: '', name: '', description: '' })

const columns: DataTableColumns<Role> = [
  { title: 'Role Key', key: 'roleKey' },
  { title: '名称', key: 'name' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '内置',
    key: 'builtin',
    width: 90,
    render: (row) => h(NTag, { size: 'small', type: row.builtin ? 'warning' : 'default', bordered: false }, { default: () => (row.builtin ? '内置' : '自定义') })
  },
  { title: '创建时间', key: 'createdAt', width: 180, render: (row) => (row.createdAt ? new Date(row.createdAt).toLocaleString() : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => openEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', secondary: true, type: 'error', disabled: row.builtin, onClick: () => handleDelete(row) }, { default: () => '删除' })
      ] })
  }
]

async function reload() {
  loading.value = true
  try {
    roles.value = await listRoles(adminToken.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载角色失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingKey.value = null
  form.roleKey = ''
  form.name = ''
  form.description = ''
  showModal.value = true
}

function openEdit(row: Role) {
  editingKey.value = row.roleKey
  form.roleKey = row.roleKey
  form.name = row.name
  form.description = row.description ?? ''
  showModal.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (editingKey.value) {
      await updateRole(adminToken.value, editingKey.value, {
        name: form.name,
        description: form.description || undefined
      })
      message.success('角色已更新')
    } else {
      await createRole(adminToken.value, {
        roleKey: form.roleKey,
        name: form.name,
        description: form.description || undefined
      })
      message.success('角色已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存角色失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Role) {
  dialog.warning({
    title: '删除确认',
    content: `确认删除角色「${row.name}」？若该角色仍被用户占用，删除将被拒绝。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteRole(adminToken.value, row.roleKey)
        message.success('角色已删除')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除角色失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="角色管理" description="管理角色定义（名称与描述）。内置角色不可删除。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建角色
        </n-button>
      </template>
    </PageHeader>

    <n-alert type="info" :bordered="false">
      当前授权模型为二元判断：非 GET 的 <code>/api/admin/**</code> 写操作仅对
      <n-tag type="warning" size="small" :bordered="false">ADMIN</n-tag>
      角色开放。自定义角色目前作为「标签 / 分组」使用，不参与细粒度权限判定。
    </n-alert>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="columns" :data="roles" :bordered="false" />
    </n-card>

    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="editingKey ? '编辑角色' : '新建角色'"
      style="width: 440px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="Role Key">
          <n-input
            v-model:value="form.roleKey"
            :disabled="!!editingKey"
            placeholder="大写字母，如 OPERATOR"
          />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="如 运营人员" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
