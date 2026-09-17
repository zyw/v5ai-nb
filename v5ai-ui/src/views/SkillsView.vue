<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
import { FilePlus2, Plus, RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createSkillOnline,
  deleteSkill,
  deleteSkillVersion,
  disableSkill,
  enableSkill,
  getSkillUsage,
  listSkills,
  listSkillVersions,
  offlineSkillVersion,
  publishSkillVersion,
  uploadSkill,
  type SkillResponse,
  type SkillVersionResponse
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const router = useRouter()
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

const showCreateModal = ref(false)
const createForm = reactive({ name: '', description: '', versionDescription: '' })
const creating = ref(false)
const showUploadModal = ref(false)
const showVersionsModal = ref(false)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const uploadFile = ref<File | null>(null)
const uploadForm = reactive({ description: '' })

const skillColumns: DataTableColumns<SkillResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '名称', key: 'name', width: 180 },
  { title: '描述', key: 'description', width: 300, ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(NSwitch, {
        value: row.status === 'ACTIVE',
        loading: togglingId.value === row.id,
        onUpdateValue: () => handleToggleStatus(row)
      },{
        checked: () => h('span', '启'),   // 插槽返回 VNode
        unchecked: () => h('span', '禁')
      })
  },
  { title: '当前版本', key: 'currentVersion', width: 100, render: (row) => (row.currentVersion ? `v${row.currentVersion}` : '-') },
  { title: '创建时间', key: 'createdAt', width: 182, render: (row) => fmtTime(row.createdAt) },
  { title: '更新时间', key: 'updatedAt', width: 182, render: (row) => fmtTime(row.updatedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => handleShowVersions(row) }, { default: () => '版本' }),
        h(NButton, { size: 'small', secondary: true, type: 'primary', onClick: () => handleEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDeleteSkill(row) }, { default: () => '删除' })
      ] })
  }
]

/** 表格横向滚动：列宽总和（auto 列按 240px 估算），窄屏时与 MCP 列表一样出现横向滚动条 */
const skillScrollX = computed(() =>
  skillColumns.reduce((sum, col) => sum + (typeof col.width === 'number' ? col.width : 240), 0)
)

const versionColumns: DataTableColumns<SkillVersionResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '版本', key: 'version', width: 80, render: (row) => `v${row.version}` },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '发布时间', key: 'publishedAt', width: 182, render: (row) => fmtTime(row.publishedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) =>
      row.status === 'PUBLISHED'
        ? h(NButton, { size: 'small', secondary: true, type: 'warning', onClick: () => handleOffline(row) }, { default: () => '下线' })
        : h(NSpace, { size: 6 }, { default: () => [
            h(NButton, { size: 'small', type: 'primary', onClick: () => handlePublish(row) }, { default: () => '发布' }),
            h(NButton, { size: 'small', secondary: true, type: 'error', onClick: () => handleDeleteVersion(row) }, { default: () => '删除' })
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

async function handleCreateOnline() {
  const name = createForm.name.trim()
  if (!name) {
    message.warning('请输入 Skill 名称')
    return
  }
  if (!createForm.description.trim()) {
    message.warning('请输入 Skill 描述')
    return
  }
  creating.value = true
  try {
    const created = await createSkillOnline(
      adminToken.value,
      name,
      createForm.description,
      createForm.versionDescription.trim() || undefined
    )
    message.success(`Skill「${created.name}」已创建，请编辑文件后发布`)
    showCreateModal.value = false
    createForm.name = ''
    createForm.description = ''
    createForm.versionDescription = ''
    await router.push({ name: 'skill-editor', params: { id: created.id } })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    creating.value = false
  }
}

function handleEdit(skill: SkillResponse) {
  void router.push({ name: 'skill-editor', params: { id: skill.id } })
}

function handleDeleteSkill(skill: SkillResponse) {
  if (skill.currentVersionId != null) {
    message.warning('该 Skill 有已发布的版本，不能删除；请先在「版本」中下线后再删除')
    return
  }
  dialog.warning({
    title: '删除确认',
    content: `确认删除 Skill「${skill.name}」？删除将同步删除其全部版本与文件，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteSkill(adminToken.value, skill.id)
        message.success(`Skill「${skill.name}」已删除`)
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

async function handleDeleteVersion(version: SkillVersionResponse) {
  if (!selectedSkill.value) return
  dialog.warning({
    title: '删除确认',
    content: `确认删除 v${version.version}？该版本及其文件将被一并删除，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteSkillVersion(adminToken.value, selectedSkill.value!.id, version.id)
        message.success(`v${version.version} 已删除`)
        await reload()
        await handleShowVersions(selectedSkill.value!)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
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

async function handleOffline(version: SkillVersionResponse) {
  const skill = selectedSkill.value
  if (!skill) return
  let usageCount = 0
  try {
    usageCount = (await getSkillUsage(adminToken.value, skill.id)).count
  } catch (e) {
    message.error(e instanceof Error ? e.message : '查询使用情况失败')
    return
  }
  const doOffline = async () => {
    try {
      await offlineSkillVersion(adminToken.value, skill.id, version.id)
      message.success(`v${version.version} 已下线`)
      await reload()
      await handleShowVersions(skill)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '下线失败')
    }
  }
  if (usageCount > 0) {
    dialog.warning({
      title: '下线确认',
      content: `该 Skill 已被 ${usageCount} 个 Agent 使用，下线后将不再注入到这些 Agent，是否确认下线？`,
      positiveText: '下线',
      negativeText: '取消',
      onPositiveClick: doOffline
    })
  } else {
    await doOffline()
  }
}

async function handleToggleStatus(skill: SkillResponse) {
  const next = skill.status === 'ACTIVE' ? '禁用' : '启用'
  if (next === '禁用' && skill.currentVersionId != null) {
    message.warning('该 Skill 有已发布的版本，请先在「版本」中下线后再禁用')
    return
  }
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
        <n-button size="small" type="primary" secondary @click="showCreateModal = true">
          <template #icon><n-icon :component="FilePlus2" /></template>
          在线新建
        </n-button>
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

      <n-data-table :loading="loading" :columns="skillColumns" :data="skills" :bordered="false" :scroll-x="skillScrollX" />
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
      title="在线新建 Skill"
      style="width: 520px"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        创建后将自动生成 DRAFT 版本与 SKILL.md 骨架，并跳转到文件编辑页在线编辑。
      </n-alert>
      <n-form label-placement="top">
        <n-form-item label="Skill 名称">
          <n-input v-model:value="createForm.name" placeholder="如 weather（全局唯一）" @keyup.enter="handleCreateOnline" />
        </n-form-item>
        <n-form-item label="Skill 描述">
          <n-input v-model:value="createForm.description" type="textarea" :maxlength="2000" :rows="3" show-count placeholder="技能用途描述" />
        </n-form-item>
        <n-form-item label="版本描述（可选）">
          <n-input v-model:value="createForm.versionDescription" type="textarea" :maxlength="1000" :rows="3" show-count placeholder="如 v1 初版（写入该草稿版本的版本描述）" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="creating" @click="handleCreateOnline">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showUploadModal"
      preset="card"
      title="上传 Skill 包"
      style="width: 520px"
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
        <n-form-item label="版本描述（可选）">
          <n-input v-model:value="uploadForm.description" type="textarea" :maxlength="1000" :rows="3" show-count placeholder="如 v1 支持天气查询（可选）" />
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
      style="width: 80%"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        运行时注入的是「当前版本」；发布新版本会切换当前版本；「下线」把已发布版本置为已下线（OFFLINE：不再注入、内容冻结，可重新发布或删除）；Skill 存在任何已发布版本时不可禁用或删除。
      </n-alert>
      <n-data-table
        :columns="versionColumns"
        :data="versions"
        :bordered="false"
        :scroll-x="versionColumns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
      />
    </n-modal>
  </div>
</template>
