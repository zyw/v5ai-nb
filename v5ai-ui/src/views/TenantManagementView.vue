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
  NPagination,
  NSpace,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import { createTenant, disableTenant, listTenants, type Tenant } from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const tenants = ref<Tenant[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const showCreateModal = ref(false)
const saving = ref(false)
const form = reactive({ tenantKey: '', name: '' })

const columns: DataTableColumns<Tenant> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: 'Tenant Key', key: 'tenantKey' },
  { title: '名称', key: 'name' },
  { title: '状态', key: 'status', width: 100, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '创建时间', key: 'createdAt', width: 180, render: (row) => (row.createdAt ? new Date(row.createdAt).toLocaleString() : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (row) =>
      h(
        NButton,
        { size: 'small', type: 'error', secondary: true, disabled: row.status !== 'ACTIVE', onClick: () => handleDisable(row) },
        { default: () => '禁用' }
      )
  }
]

async function reload() {
  loading.value = true
  try {
    const rows = await listTenants(adminToken.value, { pageNum: pagination.page, pageSize: pagination.pageSize })
    tenants.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载租户失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.page = page
  void reload()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void reload()
}

async function handleCreate() {
  saving.value = true
  try {
    await createTenant(adminToken.value, form.tenantKey, form.name)
    message.success('租户已创建')
    showCreateModal.value = false
    form.tenantKey = ''
    form.name = ''
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建租户失败')
  } finally {
    saving.value = false
  }
}

async function handleDisable(row: Tenant) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用租户「${row.name}」？`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableTenant(adminToken.value, row.id)
        message.success('租户已禁用')
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '禁用失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="租户管理" description="管理平台租户（多租户隔离单元）。">
      <template #actions>
        <n-button size="small" type="primary" @click="showCreateModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          新建租户
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="columns" :data="tenants" :bordered="false" />
      <n-pagination
        :page="pagination.page"
        :page-size="pagination.pageSize"
        :item-count="pagination.itemCount"
        :page-sizes="[10, 20, 50, 100]"
        show-size-picker
        style="justify-content: flex-end; margin-top: 12px"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      title="新建租户"
      style="width: 420px"
      :bordered="false"
    >
      <n-form label-placement="top">
        <n-form-item label="租户 Key">
          <n-input v-model:value="form.tenantKey" placeholder="如 acme" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="如 Acme Corp" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleCreate">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
