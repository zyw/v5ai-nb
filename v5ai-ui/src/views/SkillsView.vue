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
  NPagination,
  NSelect,
  NSpace,
  NSwitch,
  NUpload,
  useDialog,
  useMessage,
  type DataTableColumns,
  type UploadFileInfo
} from 'naive-ui'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  disableSkill,
  enableSkill,
  listSkills,
  listSkillVersions,
  publishSkillVersion,
  rollbackSkill,
  uploadSkill,
  type SkillResponse,
  type SkillVersionResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const skills = ref<SkillResponse[]>([])
const selectedSkill = ref<SkillResponse | null>(null)
const versions = ref<SkillVersionResponse[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ name: '', status: null as string | null })

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' }
]

const showUploadModal = ref(false)
const showVersionsModal = ref(false)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const uploadFile = ref<File | null>(null)
const uploadForm = reactive({ description: '' })

const skillColumns: DataTableColumns<SkillResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '名称', key: 'name', width: 220 },
  { title: '描述', key: 'description', width: 'auto', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(NSwitch, {
        value: row.status === 'ACTIVE',
        loading: togglingId.value === row.id,
        onUpdateValue: () => handleToggleStatus(row)
      })
  },
  { title: '当前版本', key: 'currentVersion', width: 100, render: (row) => (row.currentVersion ? `v${row.currentVersion}` : '-') },
  { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (row) =>
      h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => handleShowVersions(row) }, { default: () => '版本' })
  }
]

const versionColumns: DataTableColumns<SkillVersionResponse> = [
  { title: '版本', key: 'version', width: 80, render: (row) => `v${row.version}` },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '发布时间', key: 'publishedAt', width: 182, render: (row) => fmtTime(row.publishedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        row.status === 'DRAFT'
          ? h(NButton, { size: 'small', type: 'primary', onClick: () => handlePublish(row) }, { default: () => '发布' })
          : h(NButton, { size: 'small', secondary: true, type: 'warning', disabled: selectedSkill.value?.currentVersionId === row.id, onClick: () => handleRollback(row) }, { default: () => '回滚' })
      ] })
  }
]

async function reload() {
  loading.value = true
  try {
    const rows = await listSkills(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      name: search.name || undefined,
      status: search.status ?? undefined
    })
    skills.value = rows.rows
    pagination.itemCount = rows.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Skill 数据失败')
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

function handleFileChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (raw instanceof File) uploadFile.value = raw
}

function fmtTime(value?: string | null): string {
  return value ? (formatDateTime(value) ?? '—') : '—'
}

async function handleUpload() {
  if (!uploadFile.value) {
    message.warning('请选择 .zip 技能包')
    return
  }
  saving.value = true
  try {
    await uploadSkill(adminToken.value, uploadFile.value, uploadForm.description || undefined)
    message.success('Skill 包已上传（DRAFT 版本），请发布后绑定')
    uploadFile.value = null
    uploadForm.description = ''
    showUploadModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    saving.value = false
  }
}

async function handleShowVersions(skill: SkillResponse) {
  selectedSkill.value = skill
  versions.value = (await listSkillVersions(adminToken.value, skill.id)).rows
  showVersionsModal.value = true
}

async function handlePublish(version: SkillVersionResponse) {
  if (!selectedSkill.value) return
  try {
    await publishSkillVersion(adminToken.value, selectedSkill.value.id, version.id)
    message.success(`v${version.version} 已发布为当前版本`)
    await reload()
    await handleShowVersions(selectedSkill.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发布失败')
  }
}

async function handleRollback(version: SkillVersionResponse) {
  if (!selectedSkill.value) return
  try {
    await rollbackSkill(adminToken.value, selectedSkill.value.id, version.id)
    message.success(`已回滚到 v${version.version}`)
    await reload()
    await handleShowVersions(selectedSkill.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '回滚失败')
  }
}

async function handleToggleStatus(skill: SkillResponse) {
  const next = skill.status === 'ACTIVE' ? '禁用' : '启用'
  dialog.warning({
    title: `${next}确认`,
    content: `确认${next} Skill「${skill.name}」？`,
    positiveText: next,
    negativeText: '取消',
    onPositiveClick: async () => {
      togglingId.value = skill.id
      try {
        if (skill.status === 'ACTIVE') {
          await disableSkill(adminToken.value, skill.id)
        } else {
          await enableSkill(adminToken.value, skill.id)
        }
        message.success(`${skill.name} 已${next}`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : `${next}失败`)
      } finally {
        togglingId.value = null
      }
    }
  })
}

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader title="Skill 管理" description="上传技能包、管理版本，发布后可在 Agent 运行时注入。">
      <template #actions>
        <n-button size="small" type="primary" @click="showUploadModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          上传 Skill 包
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.name" placeholder="名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <n-select
          v-model:value="search.status"
          placeholder="状态"
          clearable
          style="width: 130px"
          :options="statusOptions"
        />
        <n-button size="small" type="primary" secondary @click="handleSearch">
          <template #icon><n-icon :component="Search" /></template>
          查询
        </n-button>
        <n-button size="small" quaternary @click="handleSearch">
          <template #icon><n-icon :component="RefreshCw" /></template>
          刷新
        </n-button>
      </n-space>

      <n-data-table :loading="loading" :columns="skillColumns" :data="skills" :bordered="false" />
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
      v-model:show="showUploadModal"
      preset="card"
      title="上传 Skill 包"
      style="width: 50%"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        支持 .zip / .skill 包，结构：SKILL.md（YAML Front Matter 含 name、description）+ prompts/ + resources/。
        上传后生成 DRAFT 版本，需发布后绑定到 Agent。
      </n-alert>
      <n-form label-placement="top">
        <n-form-item label="技能包文件">
          <n-upload :default-upload="false" :show-file-list="false" accept=".zip,.skill" @change="handleFileChange">
            <n-button secondary>选择文件</n-button>
          </n-upload>
        </n-form-item>
        <n-form-item label="版本描述">
          <n-input v-model:value="uploadForm.description" placeholder="例如：v1 支持天气查询" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showUploadModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleUpload">上传</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showVersionsModal"
      preset="card"
      :title="`版本列表（${selectedSkill?.name ?? ''}）`"
      style="width: 50%"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        运行时注入的是「当前版本」（回滚会改变当前版本）；发布的新版本不会影响历史版本。
      </n-alert>
      <n-data-table :columns="versionColumns" :data="versions" :bordered="false" />
    </n-modal>
  </div>
</template>
