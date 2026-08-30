<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
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
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import {
  createStoreInstance,
  deleteStoreInstances,
  listStoreInstances,
  updateStoreInstance,
  type StoreInstanceRequest,
  type StoreInstanceResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const instances = ref<StoreInstanceResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({
  name: '',
  category: null as number | null,
  type: null as number | null,
  status: null as number | null
})
const checkedRowKeys = ref<number[]>([])

const categoryOptions = [
  { label: '向量库', value: 1 },
  { label: '搜索引擎', value: 2 }
]

const typeOptions = [
  { label: 'PG_VECTOR', value: 1 },
  { label: 'MILVUS', value: 2 },
  { label: 'ELASTICSEARCH', value: 3 },
  { label: 'PG_FULLTEXT', value: 4 }
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

function categoryLabel(category?: number): string {
  return categoryOptions.find((o) => o.value === category)?.label ?? '—'
}

function typeLabel(type?: number): string {
  return typeOptions.find((o) => o.value === type)?.label ?? '—'
}

const columns: DataTableColumns<StoreInstanceResponse> = [
  {
    title: '名称',
    key: 'name',
    width: 200,
    render: (row) =>
      h('div', [
        row.name,
        row.isDefault ? h(NTag, { size: 'small', type: 'warning', bordered: false, style: 'margin-left: 6px' }, { default: () => '默认' }) : null
      ])
  },
  {
    title: '分类',
    key: 'category',
    width: 100,
    render: (row) => h(NTag, { size: 'small', type: row.category === 1 ? 'success' : 'info', bordered: false }, { default: () => categoryLabel(row.category) })
  },
  {
    title: '类型',
    key: 'type',
    width: 140,
    render: (row) => h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => typeLabel(row.type) })
  },
  {
    title: '连接参数',
    key: 'config',
    ellipsis: { tooltip: true },
    render: (row) => row.config || '—'
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) => h(NTag, { size: 'small', type: row.status === 1 ? 'success' : 'default', bordered: false }, { default: () => (row.status === 1 ? '启用' : '停用') })
  },
  { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 130,
    render: (row) =>
      h(RowActions, {
        actions: [
          { key: 'edit', label: '编辑', secondary: true, type: 'primary', onClick: () => openEdit(row) },
          { key: 'delete', label: '删除', secondary: true, type: 'error', confirm: `确认删除存储实例「${row.name}」？`, onClick: () => handleDelete(row) }
        ]
      })
  }
]

function fmtTime(value?: string | null): string {
  return value ? (formatDateTime(value) ?? '—') : '—'
}

async function reload() {
  loading.value = true
  try {
    const rows = await listStoreInstances(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      name: search.name || undefined,
      category: search.category ?? undefined,
      type: search.type ?? undefined,
      status: search.status ?? undefined
    })
    instances.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载存储实例失败')
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

// ---- 新增 / 编辑 ----

const showModal = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<StoreInstanceRequest>({
  name: '',
  category: 1,
  type: 1,
  config: '',
  status: 1,
  isDefault: false
})

/** 类型选项随分类联动：向量库仅 PG_VECTOR/MILVUS，搜索引擎仅 ELASTICSEARCH/PG_FULLTEXT */
const formTypeOptions = computed(() =>
  form.category === 1
    ? typeOptions.filter((o) => o.value === 1 || o.value === 2)
    : form.category === 2
      ? typeOptions.filter((o) => o.value === 3 || o.value === 4)
      : typeOptions
)

function resetForm() {
  editingId.value = null
  form.id = undefined
  form.name = ''
  form.category = 1
  form.type = 1
  form.config = ''
  form.status = 1
  form.isDefault = false
}

function openCreate() {
  resetForm()
  showModal.value = true
}

function openEdit(row: StoreInstanceResponse) {
  editingId.value = row.id
  form.id = row.id
  form.name = row.name
  form.category = row.category
  form.type = row.type
  form.config = row.config ?? ''
  form.status = row.status ?? 1
  form.isDefault = !!row.isDefault
  showModal.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    message.warning('请输入实例名称')
    return
  }
  saving.value = true
  try {
    const payload: StoreInstanceRequest = {
      ...form,
      name: form.name.trim(),
      config: form.config?.trim() || undefined
    }
    if (editingId.value != null) {
      await updateStoreInstance(adminToken.value, payload)
      message.success('存储实例已更新')
    } else {
      await createStoreInstance(adminToken.value, payload)
      message.success('存储实例已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function handleDelete(row: StoreInstanceResponse) {
  void deleteStoreInstances(adminToken.value, [row.id]).then(() => {
    message.success(`「${row.name}」已删除`)
    void reload()
  }).catch((e) => {
    message.error(e instanceof Error ? e.message : '删除失败')
  })
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${checkedRowKeys.value.length} 个存储实例？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteStoreInstances(adminToken.value, checkedRowKeys.value)
        message.success('已删除所选存储实例')
        checkedRowKeys.value = []
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
    <PageHeader title="存储实例" description="配置向量库 / 搜索引擎实例：连接参数与默认实例设置。">
      <template #actions>
        <n-button size="small" type="primary" @click="openCreate">
          <template #icon><n-icon :component="Plus" /></template>
          新建存储实例
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.name" placeholder="实例名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        <n-select v-model:value="search.category" placeholder="分类" clearable style="width: 130px" :options="categoryOptions" />
        <n-select v-model:value="search.type" placeholder="类型" clearable style="width: 150px" :options="typeOptions" />
        <n-select v-model:value="search.status" placeholder="状态" clearable style="width: 110px" :options="statusOptions" />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
        <n-button
          v-if="checkedRowKeys.length > 0"
          size="small"
          type="error"
          secondary
          @click="handleBatchDelete"
        >
          删除所选（{{ checkedRowKeys.length }}）
        </n-button>
      </n-space>

      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="instances"
        :bordered="false"
        :row-key="(row: StoreInstanceResponse) => row.id"
        :checked-row-keys="checkedRowKeys"
        @update:checked-row-keys="(keys: Array<string | number>) => (checkedRowKeys = keys.map(Number))"
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

    <n-modal v-model:show="showModal" preset="card" :title="editingId != null ? '编辑存储实例' : '新建存储实例'" style="width: 560px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="实例名称">
          <n-input v-model:value="form.name" placeholder="实例显示名称" />
        </n-form-item>
        <n-form-item label="分类">
          <n-select v-model:value="form.category" :options="categoryOptions" @update:value="form.type = form.category === 1 ? 1 : 3" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select v-model:value="form.type" :options="formTypeOptions" />
        </n-form-item>
        <n-form-item label="连接参数（JSON）">
          <n-input
            v-model:value="form.config"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder='{"url":"http://localhost:5432","database":"v5ai_nb","dimension":1536}'
          />
        </n-form-item>
        <n-form-item label="状态">
          <n-select v-model:value="form.status" :options="statusOptions" />
        </n-form-item>
        <n-form-item label="设为该分类下默认实例">
          <n-switch v-model:value="form.isDefault" />
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
