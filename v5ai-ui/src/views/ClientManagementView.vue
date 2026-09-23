<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSwitch,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import {
  changeClientStatus,
  createClient,
  deleteClients,
  getClient,
  listClients,
  updateClient,
  type Client,
  type ClientForm
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const rows = ref<Client[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ clientKey: '', status: null as string | null })

const columns: DataTableColumns<Client> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 64 },
  { title: '客户端ID', key: 'clientId', width: 305, render: (r) => r.clientId || '-' },
  { title: '客户端Key', key: 'clientKey', width: 110 },
  { title: '授权类型', key: 'grantType', width: 130, render: (r) => r.grantType || (r.grantTypeList ?? []).join(',') || '-' },
  { title: '设备类型', key: 'deviceType', width: 100, render: (r) => r.deviceType ?? '-' },
  {
    title: '访问路径',
    key: 'accessPath',
    width: 200,
    render: (r) => (r.accessPathList && r.accessPathList.length ? r.accessPathList.join(' ; ') : r.accessPath ?? '-')
  },
  { title: '有效期(s)', key: 'timeout', width: 90, render: (r) => r.timeout ?? '-' },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (r) =>
      h(NSwitch, {
        value: r.status === '0',
        loading: togglingId.value === r.id,
        onUpdateValue: () => toggleStatus(r)
      }, {
        checked: () => h('span', '启'),
        unchecked: () => h('span', '停')
      })
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (r) =>
      h(RowActions, {
        actions: [
          { key: 'edit', label: '编辑', quaternary: true, onClick: () => openEdit(r) },
          {
            key: 'delete',
            label: '删除',
            quaternary: true,
            type: 'error',
            confirm: `确认删除客户端「${r.clientKey}」？`,
            onClick: () => handleDelete([r])
          }
        ]
      })
  }
]

const selectedIds = ref<number[]>([])
const togglingId = ref<number | null>(null)

async function reload() {
  loading.value = true
  try {
    const res = await listClients(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      clientKey: search.clientKey || undefined,
      status: search.status ?? undefined
    })
    rows.value = res.rows
    pagination.itemCount = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载客户端失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  void reload()
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

// ---- 新增/编辑 ----

const showModal = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<ClientForm>({
  clientKey: '',
  clientSecret: '',
  grantTypeList: ['password'],
  deviceType: 'pc',
  accessPath: '',
  ipWhitelist: '',
  activeTimeout: 1800,
  timeout: 604800,
  status: '0'
})

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    clientKey: '',
    clientSecret: '',
    grantTypeList: ['password'],
    deviceType: 'pc',
    accessPath: '',
    ipWhitelist: '',
    activeTimeout: 1800,
    timeout: 604800,
    status: '0'
  })
  showModal.value = true
}

async function openEdit(row: Client) {
  isEdit.value = true
  try {
    const detail = await getClient(adminToken.value, row.id)
    Object.assign(form, {
      id: detail.id,
      clientKey: detail.clientKey,
      clientSecret: detail.clientSecret ?? '',
      grantTypeList: detail.grantTypeList && detail.grantTypeList.length ? detail.grantTypeList : [detail.grantType ?? 'password'],
      deviceType: detail.deviceType ?? 'pc',
      accessPath: detail.accessPath ?? '',
      ipWhitelist: detail.ipWhitelist ?? '',
      activeTimeout: detail.activeTimeout ?? 1800,
      timeout: detail.timeout ?? 604800,
      status: detail.status ?? '0'
    })
    showModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载客户端详情失败')
  }
}

async function handleSave() {
  if (!form.clientKey || !form.clientSecret || form.grantTypeList.length === 0) {
    message.warning('请填写客户端 Key、秘钥与授权类型')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateClient(adminToken.value, { ...form })
      message.success('客户端已更新')
    } else {
      await createClient(adminToken.value, { ...form })
      message.success('客户端已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 状态 / 删除 ----

async function toggleStatus(row: Client) {
  const next = row.status === '0' ? '1' : '0'
  togglingId.value = row.id
  try {
    await changeClientStatus(adminToken.value, row.clientId, next)
    message.success(next === '0' ? '已启用' : '已停用')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(targets: Client[]) {
  try {
    await deleteClients(adminToken.value, targets.map((t) => t.id))
    message.success('已删除')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${selectedIds.value.length} 个客户端？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteClients(adminToken.value, selectedIds.value)
        message.success('已删除所选客户端')
        selectedIds.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="客户端管理" description="配置登录客户端（clientId 与 token 绑定校验）。">
      <template #actions>
        <n-space size="small">
          <n-button size="small" type="primary" @click="openCreate">
            <template #icon><n-icon :component="Plus" /></template>
            新增客户端
          </n-button>
        </n-space>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.clientKey" placeholder="客户端Key" clearable style="width: 160px" @keyup.enter="handleSearch" />
        <n-select
          v-model:value="search.status"
          placeholder="状态"
          clearable
          style="width: 110px"
          :options="[{ label: '正常', value: '0' }, { label: '停用', value: '1' }]"
        />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
        <n-button
          v-if="selectedIds.length > 0"
          size="small"
          type="error"
          secondary
          @click="handleBatchDelete"
        >
          删除所选（{{ selectedIds.length }}）
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="rows"
        :row-key="(r: Client) => r.id"
        :checked-row-keys="selectedIds"
        @update:checked-row-keys="(keys: Array<string | number>) => (selectedIds = keys as number[])"
        :bordered="false"
      />
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

    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑客户端' : '新增客户端'" style="width: 560px" :bordered="false">
      <n-form label-placement="top">
        <n-divider title-placement="left" style="margin: 0 0 16px">客户端信息</n-divider>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="客户端 Key" required>
            <n-input v-model:value="form.clientKey" :disabled="isEdit" placeholder="如 pc" />
          </n-form-item-gi>
          <n-form-item-gi label="客户端秘钥" required>
            <n-input v-model:value="form.clientSecret" placeholder="客户端秘钥" />
          </n-form-item-gi>
        </n-grid>
        <n-form-item label="授权类型" required>
          <n-select
            v-model:value="form.grantTypeList"
            multiple
            :options="[
              { label: 'password（密码）', value: 'password' },
              { label: 'sms（短信）', value: 'sms' },
              { label: 'email（邮箱）', value: 'email' }
            ]"
          />
        </n-form-item>
        <n-form-item label="设备类型">
          <n-radio-group v-model:value="form.deviceType">
            <n-radio-button value="pc">PC</n-radio-button>
            <n-radio-button value="app">APP</n-radio-button>
            <n-radio-button value="h5">H5</n-radio-button>
          </n-radio-group>
        </n-form-item>

        <n-divider title-placement="left" style="margin: 8px 0 16px">访问与限制</n-divider>
        <n-form-item label="允许访问路径（每行一个，留空不限制）">
          <n-input v-model:value="form.accessPath" type="textarea" :rows="2" placeholder="/api/v1/**" />
        </n-form-item>
        <n-form-item label="IP 白名单（逗号分隔，留空不限制）">
          <n-input v-model:value="form.ipWhitelist" type="textarea" :rows="2" placeholder="如 192.168.1.0/24,10.0.0.1" />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="token 有效期(s)">
            <n-input-number v-model:value="form.timeout" :min="60" style="width: 100%" />
          </n-form-item-gi>
          <n-form-item-gi label="活跃超时(s)">
            <n-input-number v-model:value="form.activeTimeout" :min="60" style="width: 100%" />
          </n-form-item-gi>
        </n-grid>
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
