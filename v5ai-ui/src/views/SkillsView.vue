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
  NUpload,
  useDialog,
  useMessage,
  type DataTableColumns,
  type UploadFileInfo
} from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  disableSkill,
  listSkills,
  listSkillVersions,
  publishSkillVersion,
  rollbackSkill,
  uploadSkill,
  type SkillResponse,
  type SkillVersionResponse
} from '../api/client'
import { adminToken } from '../stores/session'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const skills = ref<SkillResponse[]>([])
const selectedSkill = ref<SkillResponse | null>(null)
const versions = ref<SkillVersionResponse[]>([])

const showUploadModal = ref(false)
const showVersionsModal = ref(false)
const saving = ref(false)
const uploadFile = ref<File | null>(null)
const uploadForm = reactive({ description: '' })

const skillColumns: DataTableColumns<SkillResponse> = [
  { title: 'ID', key: 'id', width: 64 },
  { title: '名称', key: 'name' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 100, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '当前版本', key: 'currentVersion', width: 100, render: (row) => (row.currentVersion ? `v${row.currentVersion}` : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) =>
      h(NSpace, { size: 6 }, { default: () => [
        h(NButton, { size: 'small', secondary: true, type: 'info', onClick: () => handleShowVersions(row) }, { default: () => '版本' }),
        h(NButton, { size: 'small', secondary: true, type: 'error', disabled: row.status !== 'ACTIVE', onClick: () => handleDisable(row) }, { default: () => '禁用' })
      ] })
  }
]

const versionColumns: DataTableColumns<SkillVersionResponse> = [
  { title: '版本', key: 'version', width: 80, render: (row) => `v${row.version}` },
  { title: '状态', key: 'status', width: 110, render: (row) => h(StatusTag, { status: row.status }) },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '发布时间', key: 'publishedAt', width: 180, render: (row) => (row.publishedAt ? new Date(row.publishedAt).toLocaleString() : '-') },
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
    skills.value = await listSkills(adminToken.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载 Skill 数据失败')
  } finally {
    loading.value = false
  }
}

function handleFileChange(options: { file: UploadFileInfo }) {
  const raw = options.file.file
  if (raw instanceof File) uploadFile.value = raw
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
  versions.value = await listSkillVersions(adminToken.value, skill.id)
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

async function handleDisable(skill: SkillResponse) {
  dialog.warning({
    title: '禁用确认',
    content: `确认禁用 Skill「${skill.name}」？`,
    positiveText: '禁用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await disableSkill(adminToken.value, skill.id)
        message.success(`${skill.name} 已禁用`)
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
    <PageHeader title="Skill 管理" description="上传技能包、管理版本，发布后可在 Agent 运行时注入。">
      <template #actions>
        <n-button size="small" type="primary" @click="showUploadModal = true">
          <template #icon><n-icon :component="Plus" /></template>
          上传 Skill 包
        </n-button>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-data-table :loading="loading" :columns="skillColumns" :data="skills" :bordered="false" />
    </n-card>

    <n-modal
      v-model:show="showUploadModal"
      preset="card"
      title="上传 Skill 包"
      style="width: 500px"
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
      style="width: 640px"
      :bordered="false"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
        运行时注入的是「当前版本」（回滚会改变当前版本）；发布的新版本不会影响历史版本。
      </n-alert>
      <n-data-table :columns="versionColumns" :data="versions" :bordered="false" />
    </n-modal>
  </div>
</template>
