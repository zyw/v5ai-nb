<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { createApplication, listApplications, publishApplication, type ApplicationResponse } from '../api/client'
import { adminToken, isLoggedIn } from '../stores/session'

const message = useMessage()
const loading = ref(false)
const applications = ref<ApplicationResponse[]>([])

const form = reactive({
  appKey: 'demo',
  name: 'Demo App',
  modelId: 1
})

const publishDescription = ref('Phase 1 publish')

const columns: DataTableColumns<ApplicationResponse> = [
  { title: 'App Key', key: 'appKey' },
  { title: '名称', key: 'name' },
  {
    title: '状态',
    key: 'status',
    render: row => h(NTag, { type: row.status === 'PUBLISHED' ? 'success' : 'warning', size: 'small' }, { default: () => row.status })
  },
  { title: 'Model ID', key: 'modelId' },
  { title: '发布版本', key: 'publishedVersion', render: row => row.publishedVersion ?? '-' },
  {
    title: '操作',
    key: 'actions',
    render: row => h(NButton, {
      size: 'small',
      type: 'primary',
      disabled: !isLoggedIn.value,
      onClick: () => handlePublish(row.appKey)
    }, { default: () => '发布' })
  }
]

async function reload() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    applications.value = await listApplications(adminToken.value)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载 Application 失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  try {
    await createApplication(adminToken.value, form)
    message.success('Application 已创建')
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建 Application 失败')
  }
}

async function handlePublish(appKey: string) {
  try {
    await publishApplication(adminToken.value, appKey, publishDescription.value)
    message.success(`${appKey} 已发布`)
    await reload()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发布 Application 失败')
  }
}

onMounted(reload)
</script>

<template>
  <div class="grid">
    <n-alert v-if="!isLoggedIn" class="wide" type="warning" title="需要管理员 Token">
      请先在 Dashboard 登录，再创建或发布 Application。
    </n-alert>

    <n-card title="创建 Application" class="panel">
      <n-form label-placement="top">
        <n-form-item label="App Key">
          <n-input v-model:value="form.appKey" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item label="Model ID">
          <n-input-number v-model:value="form.modelId" :min="1" />
        </n-form-item>
        <n-button type="primary" :disabled="!isLoggedIn" @click="handleCreate">创建 Application</n-button>
      </n-form>
    </n-card>

    <n-card title="发布设置" class="panel">
      <n-form label-placement="top">
        <n-form-item label="发布说明">
          <n-input v-model:value="publishDescription" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
        </n-form-item>
        <div class="hint">点击列表中的“发布”会调用 `/api/admin/applications/{appKey}/publish`，运行时只读取已发布版本。</div>
      </n-form>
    </n-card>

    <n-card title="Application 列表" class="panel wide">
      <n-space vertical>
        <n-button secondary :disabled="!isLoggedIn" :loading="loading" @click="reload">刷新列表</n-button>
        <n-data-table :loading="loading" :columns="columns" :data="applications" />
      </n-space>
    </n-card>
  </div>
</template>
