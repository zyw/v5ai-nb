<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  NUpload,
  useDialog,
  useMessage,
  type DataTableColumns,
  type UploadFileInfo
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import RowActions from '../components/RowActions.vue'
import {
  deleteResources,
  downloadResource,
  listResources,
  previewResource,
  updateResource,
  uploadResource,
  type ResourceBizType,
  type ResourceResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime, parseTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const resources = ref<ResourceResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const checkedRowKeys = ref<number[]>([])
const search = reactive({
  originalName: '',
  bizType: null as string | null,
  dateRange: null as [number, number] | null
})

const bizTypeOptions: { label: string; value: ResourceBizType }[] = [
  { label: '通用', value: 'GENERAL' },
  { label: '头像', value: 'AVATAR' },
  { label: '附件', value: 'ATTACHMENT' },
  { label: '文档', value: 'DOCUMENT' }
]

const storageTypeOptions = [
  { label: '本地存储', value: 'LOCAL' },
  { label: 'MinIO', value: 'MINIO' }
]

function bizTypeLabel(bizType?: string | null): string {
  return bizTypeOptions.find((o) => o.value === bizType)?.label ?? '—'
}

function storageTypeLabel(storageType?: string | null): string {
  return storageTypeOptions.find((o) => o.value === storageType)?.label ?? '—'
}

/** 文件大小格式化：B/KB/MB/GB。 */
function formatSize(size?: number | null): string {
  if (size === undefined || size === null || size < 0) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const columns: DataTableColumns<ResourceResponse> = [
  {
    title: '文件名',
    key: 'originalName',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) => row.originalName
  },
  {
    title: '大小',
    key: 'fileSize',
    width: 110,
    render: (row) => formatSize(row.fileSize)
  },
  {
    title: '业务类型',
    key: 'bizType',
    width: 110,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => bizTypeLabel(row.bizType) })
  },
  {
    title: '存储类型',
    key: 'storageType',
    width: 110,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.storageType === 'MINIO' ? 'warning' : 'default' }, { default: () => storageTypeLabel(row.storageType) })
  },
  { title: '创建时间', key: 'createDt', width: 182, render: (row) => fmtTime(row.createDt) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      h(RowActions, {
        maxInline: 3,
        actions: [
          { key: 'preview', label: '预览', secondary: true, type: 'primary', onClick: () => handlePreview(row) },
          { key: 'download', label: '下载', secondary: true, onClick: () => handleDownload(row) },
          { key: 'edit', label: '编辑', secondary: true, onClick: () => openEdit(row) },
          { key: 'delete', label: '删除', secondary: true, type: 'error', confirm: `确认删除「${row.originalName}」？`, onClick: () => handleDelete(row) }
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
    const rows = await listResources(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      originalName: search.originalName || undefined,
      bizType: search.bizType ?? undefined,
      beginTime: search.dateRange ? (parseTime(search.dateRange[0], '{y}-{m}-{d} {h}:{i}:{s}') ?? undefined) : undefined,
      endTime: search.dateRange ? (parseTime(search.dateRange[1], '{y}-{m}-{d} {h}:{i}:{s}') ?? undefined) : undefined
    })
    resources.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载资源失败')
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

// ---- 上传 ----

const showUploadModal = ref(false)
const saving = ref(false)
const uploadFile = ref<File | null>(null)
const uploadForm = reactive({ bizType: 'GENERAL' as ResourceBizType })

function handleFileChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (raw instanceof File) uploadFile.value = raw
}

async function handleUpload() {
  if (!uploadFile.value) {
    message.warning('请选择要上传的文件')
    return
  }
  saving.value = true
  try {
    const saved = await uploadResource(adminToken.value, uploadFile.value, { bizType: uploadForm.bizType })
    message.success(`「${saved.originalName}」上传成功`)
    uploadFile.value = null
    uploadForm.bizType = 'GENERAL'
    showUploadModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    saving.value = false
  }
}

// ---- 编辑（业务类型 / 关联业务ID） ----

const showEditModal = ref(false)
const editingId = ref<number | null>(null)
const editForm = reactive({ bizType: 'GENERAL' as ResourceBizType, bizId: undefined as number | undefined })

function openEdit(row: ResourceResponse) {
  editingId.value = row.id
  editForm.bizType = (row.bizType as ResourceBizType) ?? 'GENERAL'
  editForm.bizId = row.bizId ?? undefined
  showEditModal.value = true
}

async function handleSaveEdit() {
  if (editingId.value == null) return
  saving.value = true
  try {
    await updateResource(adminToken.value, {
      id: editingId.value,
      bizType: editForm.bizType,
      bizId: editForm.bizId
    })
    message.success('资源已更新')
    showEditModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 预览 / 下载 / 删除 ----

const showPreviewModal = ref(false)
const previewUrl = ref('')
const previewName = ref('')

async function handlePreview(row: ResourceResponse) {
  const mime = (row.mimeType ?? '').toLowerCase()
  const isImage = mime.startsWith('image/')
  const isPdf = mime === 'application/pdf'
  if (!isImage && !isPdf) {
    message.info('该类型不支持在线预览，已转为下载')
    await handleDownload(row)
    return
  }
  try {
    const blob = await previewResource(adminToken.value, row.id)
    const url = URL.createObjectURL(blob)
    if (isImage) {
      previewName.value = row.originalName
      previewUrl.value = url
      showPreviewModal.value = true
    } else {
      window.open(url, '_blank')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '预览失败')
  }
}

function closePreview() {
  showPreviewModal.value = false
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

async function handleDownload(row: ResourceResponse) {
  try {
    const blob = await downloadResource(adminToken.value, row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.originalName
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载失败')
  }
}

function handleDelete(row: ResourceResponse) {
  void deleteResources(adminToken.value, [row.id]).then(() => {
    message.success(`「${row.originalName}」已删除`)
    void reload()
  }).catch((e) => {
    message.error(e instanceof Error ? e.message : '删除失败')
  })
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${checkedRowKeys.value} 个资源？文件将被物理删除，不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteResources(adminToken.value, checkedRowKeys.value)
        message.success('已删除所选资源')
        checkedRowKeys.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

onMounted(reload)</script>

<template>
  <div class="page">
    <PageHeader title="资源存储" description="通用资源存储：上传、预览、下载与删除文件，支持按名称/业务类型/创建时间检索。">
      <template #actions>
        <n-button size="small" type="primary" @click="showUploadModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          上传文件
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center" wrap>
        <n-input v-model:value="search.originalName" placeholder="文件名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <n-select v-model:value="search.bizType" placeholder="业务类型" clearable style="width: 140px" :options="bizTypeOptions" />
        <n-date-picker
          v-model:value="search.dateRange"
          type="datetimerange"
          clearable
          style="width: 360px"
          :is-date-disabled="(ts: number) => ts > Date.now()"
          @update:value="handleSearch"
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
        :data="resources"
        :bordered="false"
        :row-key="(row: ResourceResponse) => row.id"
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

    <!-- 上传弹窗 -->
    <n-modal v-model:show="showUploadModal" preset="card" title="上传文件" style="width: 520px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="文件">
          <n-upload :default-upload="false" :show-file-list="false" @change="handleFileChange">
            <n-button secondary>
              {{ uploadFile ? uploadFile.name : '选择文件' }}
            </n-button>
          </n-upload>
        </n-form-item>
        <n-form-item label="业务类型">
          <n-select v-model:value="uploadForm.bizType" :options="bizTypeOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showUploadModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleUpload">上传</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 编辑弹窗 -->
    <n-modal v-model:show="showEditModal" preset="card" title="编辑资源" style="width: 440px" :bordered="false">
      <n-form label-placement="top">
        <n-form-item label="业务类型">
          <n-select v-model:value="editForm.bizType" :options="bizTypeOptions" />
        </n-form-item>
        <n-form-item label="关联业务ID">
          <n-input-number v-model:value="editForm.bizId" placeholder="可选" style="width: 100%" clearable />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSaveEdit">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 图片预览弹窗 -->
    <n-modal v-model:show="showPreviewModal" preset="card" :title="previewName" style="width: 720px" :bordered="false" @after-leave="closePreview">
      <div style="display: flex; justify-content: center; max-height: 70vh; overflow: auto">
        <img :src="previewUrl" alt="预览" style="max-width: 100%" />
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="closePreview">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
