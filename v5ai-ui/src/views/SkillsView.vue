<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NDivider, NForm, NFormItem, NInput, NSelect, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import {
  bindSkills,
  disableSkill,
  getSkillBindings,
  listApplications,
  listSkills,
  listSkillVersions,
  publishSkillVersion,
  rollbackSkill,
  uploadSkill,
  type ApplicationResponse,
  type SkillResponse,
  type SkillVersionResponse
} from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const skills = ref<SkillResponse[]>([])
const applications = ref<ApplicationResponse[]>([])
const selectedSkill = ref<SkillResponse | null>(null)
const versions = ref<SkillVersionResponse[]>([])

const uploadForm = reactive({
  description: ''
})
const file = ref<File | null>(null)

const bindForm = reactive({
  appKey: 'demo',
  skillIds: [] as number[]
})

const skillColumns: DataTableColumns<SkillResponse> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '名称', key: 'name' },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, { size: 'small', type: row.status === 'ACTIVE' ? 'success' : 'default' }, { default: () => row.status })
  },
  {
    title: '当前版本',
    key: 'currentVersion',
    render: row => row.currentVersion ? `v${row.currentVersion}` : '-'
  },
  {
    title: '操作',
    key: 'actions',
    width: 240,
    render: row => h(NSpace, { size: 6 }, { default: () => [
      h(NButton, { size: 'small', type: 'info', secondary: true, disabled: !isLoggedIn.value, onClick: () => handleShowVersions(row) }, { default: () => '版本' }),
      h(NButton, { size: 'small', type: 'error', secondary: true, disabled: !isLoggedIn.value || row.status !== 'ACTIVE', onClick: () => handleDisable(row) }, { default: () => '禁用' })
    ] })
  }
]

const versionColumns: DataTableColumns<SkillVersionResponse> = [
  { title: '版本', key: 'version', render: row => `v${row.version}` },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, { size: 'small', type: row.status === 'PUBLISHED' ? 'success' : 'warning' }, { default: () => row.status })
  },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '发布时间', key: 'publishedAt', render: row => row.publishedAt ? new Date(row.publishedAt).toLocaleString() : '-' },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: row => h(NSpace, { size: 6 }, { default: () => [
      row.status === 'DRAFT'
        ? h(NButton, { size: 'small', type: 'primary', disabled: !isLoggedIn.value, onClick: () => handlePublish(row) }, { default: () => '发布' })
        : h(NButton, { size: 'small', type: 'warning', secondary: true, disabled: !isLoggedIn.value || selectedSkill.value?.currentVersionId === row.id, onClick: () => handleRollback(row) }, { default: () => '回滚到此版本' })
    ] })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const [skillRows, appRows] = await Promise.all([
      listSkills(adminToken.value),
      listApplications(adminToken.value)
    ])
    skills.value = skillRows
    applications.value = appRows
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载 Skill 数据失败')
  } finally {
    loading.value = false
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

async function handleUpload() {
  if (!file.value) {
    message.warning('请选择 .zip 技能包')
    return
  }
  try {
    await uploadSkill(adminToken.value, file.value, uploadForm.description || undefined)
    message.success('Skill 包已上传（DRAFT 版本），请发布后绑定')
    file.value = null
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '上传失败')
  }
}

async function handleShowVersions(skill: SkillResponse) {
  selectedSkill.value = skill
  versions.value = await listSkillVersions(adminToken.value, skill.id)
}

async function handlePublish(version: SkillVersionResponse) {
  if (!selectedSkill.value) return
  try {
    await publishSkillVersion(adminToken.value, selectedSkill.value.id, version.id)
    message.success(`v${version.version} 已发布为当前版本`)
    await reload()
    await handleShowVersions(selectedSkill.value)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发布失败')
  }
}

async function handleRollback(version: SkillVersionResponse) {
  if (!selectedSkill.value) return
  try {
    await rollbackSkill(adminToken.value, selectedSkill.value.id, version.id)
    message.success(`已回滚到 v${version.version}`)
    await reload()
    await handleShowVersions(selectedSkill.value)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '回滚失败')
  }
}

async function handleDisable(skill: SkillResponse) {
  try {
    await disableSkill(adminToken.value, skill.id)
    message.success(`${skill.name} 已禁用`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '禁用失败')
  }
}

async function reloadBindings() {
  try {
    bindForm.skillIds = await getSkillBindings(adminToken.value, bindForm.appKey)
  } catch {
    bindForm.skillIds = []
  }
}

async function handleBind() {
  try {
    await bindSkills(adminToken.value, bindForm.appKey, bindForm.skillIds)
    message.success(`已更新 ${bindForm.appKey} 的 Skill 绑定`)
    await reloadBindings()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '绑定失败')
  }
}

onMounted(async () => {
  await reload()
  if (applications.value.length > 0) {
    bindForm.appKey = applications.value[0].appKey
  }
  await reloadBindings()
})
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再管理 Skill。
    </n-alert>

    <n-card title="上传 Skill 包" class="panel">
      <n-form label-placement="top">
        <n-alert type="info" :show-icon="true">
          支持 .zip / .skill 包，结构：SKILL.md（YAML Front Matter 含 name、description）+ prompts/ + resources/ 文本资源。
          上传后生成 DRAFT 版本，需发布后绑定到 Application 才会在运行时注入。
        </n-alert>
        <n-form-item label="技能包文件">
          <input type="file" accept=".zip,.skill" @change="onFileChange" />
        </n-form-item>
        <n-form-item label="版本描述">
          <n-input v-model:value="uploadForm.description" placeholder="例如：v1 支持天气查询" />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleUpload">上传 Skill 包</n-button>
      </n-form>
    </n-card>

    <n-card title="Application 绑定 Skill" class="panel">
      <n-form label-placement="top">
        <n-form-item label="Application App Key">
          <n-select
            v-model:value="bindForm.appKey"
            filterable
            tag
            :options="applications.map(item => ({ label: item.appKey, value: item.appKey }))"
            @update:value="reloadBindings"
          />
        </n-form-item>
        <n-form-item label="绑定 Skill">
          <n-select
            v-model:value="bindForm.skillIds"
            multiple
            clearable
            :options="skills.filter(s => s.status === 'ACTIVE' && s.currentVersionId !== null)
              .map(s => ({ label: `${s.name} (v${s.currentVersion})`, value: s.id }))"
            placeholder="选择该 Application 可用的 Skill（仅已发布可选）"
          />
        </n-form-item>
        <n-button secondary type="info" :disabled="!isLoggedIn" @click="handleBind">保存 Skill 绑定</n-button>
      </n-form>
    </n-card>

    <n-card title="Skill 列表" class="panel wide">
      <n-space vertical>
        <n-button secondary :disabled="!isLoggedIn" :loading="loading" @click="reload">刷新列表</n-button>
        <n-data-table :loading="loading" :columns="skillColumns" :data="skills" />
      </n-space>
    </n-card>

    <n-card v-if="selectedSkill" :title="`版本列表（${selectedSkill.name}）`" class="panel wide">
      <n-space vertical>
        <n-alert type="info" :show-icon="true">
          运行时注入的是「当前版本」（回滚会改变当前版本）；发布的新版本不会影响已发布的历史版本。
        </n-alert>
        <n-data-table :columns="versionColumns" :data="versions" />
      </n-space>
    </n-card>
  </div>
</template>
