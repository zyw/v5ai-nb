<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NIcon,
  NInput,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  NTabs,
  NTabPane,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import { RefreshCw, Search } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import {
  cleanLoginLogs,
  cleanOperLogs,
  deleteLoginLogs,
  deleteOperLogs,
  listLoginLogs,
  listOperLogs,
  unlockLoginUser,
  type LoginLog,
  type OperLog
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const activeTab = ref('login')

const loginLoading = ref(false)
const loginRows = ref<LoginLog[]>([])
const loginPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const loginSearch = reactive({ userName: '', ipaddr: '', status: null as string | null })
const loginSelected = ref<number[]>([])

const operLoading = ref(false)
const operRows = ref<OperLog[]>([])
const operPagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const operSearch = reactive({ title: '', operName: '', status: null as number | null })
const operSelected = ref<number[]>([])

const BUSINESS_TYPE_LABELS: Record<number, string> = {
  0: '其它', 1: '新增', 2: '修改', 3: '删除', 4: '授权', 5: '导出', 6: '导入', 7: '强退', 8: '生成代码', 9: '清空数据'
}

// ---- 登录日志 ----

const loginColumns: DataTableColumns<LoginLog> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 64 },
  { title: '账号', key: 'userName', width: 100 },
  { title: 'IP', key: 'ipaddr', width: 160 },
  { title: '归属地', key: 'loginLocation', width: 110, render: (r) => r.loginLocation ?? '-' },
  { title: '浏览器', key: 'browser', width: 100, render: (r) => r.browser ?? '-' },
  { title: '操作系统', key: 'os', width: 100, render: (r) => r.os ?? '-' },
  {
    title: '状态',
    key: 'status',
    width: 70,
    render: (r) => (r.status === '0'
      ? h(NTag, { type: 'success', size: 'small', bordered: false, round: true }, { default: () => '成功' })
      : h(NTag, { type: 'error', size: 'small', bordered: false, round: true }, { default: () => '失败' }))
  },
  { title: '信息', key: 'msg', width: 100, render: (r) => r.msg ?? '-' },
  { title: '登录时间', key: 'loginTime', width: 190, render: (r) => (r.loginTime ? (formatDateTime(r.loginTime) ?? '-') : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: (r) =>
      r.status === '1'
        ? h(NButton, { size: 'small', quaternary: true, onClick: () => handleUnlock(r) }, { default: () => '解锁' })
        : null
  }
]

async function reloadLogin() {
  loginLoading.value = true
  try {
    const res = await listLoginLogs(adminToken.value, {
      pageNum: loginPagination.page,
      pageSize: loginPagination.pageSize,
      userName: loginSearch.userName || undefined,
      ipaddr: loginSearch.ipaddr || undefined,
      status: loginSearch.status ?? undefined
    })
    loginRows.value = res.rows
    loginPagination.itemCount = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载登录日志失败')
  } finally {
    loginLoading.value = false
  }
}

function handleLoginPageChange(page: number) {
  loginPagination.page = page
  void reloadLogin()
}

function handleLoginPageSizeChange(pageSize: number) {
  loginPagination.pageSize = pageSize
  loginPagination.page = 1
  void reloadLogin()
}

async function handleUnlock(row: LoginLog) {
  try {
    await unlockLoginUser(adminToken.value, row.userName ?? '')
    message.success('已解锁')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '解锁失败')
  }
}

function handleDeleteLoginLogs(ids: number[]) {
  if (ids.length === 0) {
    message.warning('请先选择日志')
    return
  }
  dialog.warning({
    title: '删除登录日志',
    content: `确认删除选中的 ${ids.length} 条登录日志？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteLoginLogs(adminToken.value, ids)
        message.success('已删除')
        await reloadLogin()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

function handleCleanLoginLogs() {
  dialog.warning({
    title: '清空登录日志',
    content: '确认清空全部登录日志？该操作不可恢复',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await cleanLoginLogs(adminToken.value)
        message.success('已清空')
        await reloadLogin()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '清空失败')
      }
    }
  })
}

// ---- 操作日志 ----

const operColumns: DataTableColumns<OperLog> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 70 },
  { title: '模块', key: 'title', width: 120 },
  { title: '类型', key: 'businessType', width: 80, render: (r) => BUSINESS_TYPE_LABELS[r.businessType ?? 0] ?? r.businessType ?? '-' },
  { title: '操作人', key: 'operName', width: 100, render: (r) => r.operName ?? '-' },
  { title: '请求方式', key: 'requestMethod', width: 90, render: (r) => r.requestMethod ?? '-' },
  { title: 'URL', key: 'operUrl', ellipsis: { tooltip: true }, render: (r) => r.operUrl ?? '-' },
  { title: 'IP', key: 'operIp', width: 160 },
  {
    title: '状态',
    key: 'status',
    width: 70,
    render: (r) => (r.status === 0
      ? h(NTag, { type: 'success', size: 'small', bordered: false, round: true }, { default: () => '成功' })
      : h(NTag, { type: 'error', size: 'small', bordered: false, round: true }, { default: () => '失败' }))
  },
  { title: '耗时(ms)', key: 'costTime', width: 90, render: (r) => r.costTime ?? '-' },
  { title: '操作时间', key: 'operTime', width: 190, render: (r) => (r.operTime ? (formatDateTime(r.operTime) ?? '-') : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: (r) => h(NButton, { size: 'small', quaternary: true, onClick: () => openDetail(r) }, { default: () => '详情' })
  }
]

const detailOper = ref<OperLog | null>(null)
const showDetail = ref(false)

function openDetail(row: OperLog) {
  detailOper.value = row
  showDetail.value = true
}

async function reloadOper() {
  operLoading.value = true
  try {
    const res = await listOperLogs(adminToken.value, {
      pageNum: operPagination.page,
      pageSize: operPagination.pageSize,
      title: operSearch.title || undefined,
      operName: operSearch.operName || undefined,
      status: operSearch.status ?? undefined
    })
    operRows.value = res.rows
    operPagination.itemCount = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载操作日志失败')
  } finally {
    operLoading.value = false
  }
}

function handleOperPageChange(page: number) {
  operPagination.page = page
  void reloadOper()
}

function handleOperPageSizeChange(pageSize: number) {
  operPagination.pageSize = pageSize
  operPagination.page = 1
  void reloadOper()
}

function handleDeleteOperLogs(ids: number[]) {
  if (ids.length === 0) {
    message.warning('请先选择日志')
    return
  }
  dialog.warning({
    title: '删除操作日志',
    content: `确认删除选中的 ${ids.length} 条操作日志？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteOperLogs(adminToken.value, ids)
        message.success('已删除')
        await reloadOper()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

function handleCleanOperLogs() {
  dialog.warning({
    title: '清空操作日志',
    content: '确认清空全部操作日志？该操作不可恢复',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await cleanOperLogs(adminToken.value)
        message.success('已清空')
        await reloadOper()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '清空失败')
      }
    }
  })
}

function handleTabChange(tab: string) {
  if (tab === 'login') {
    loginPagination.page = 1
    void reloadLogin()
  } else {
    operPagination.page = 1
    void reloadOper()
  }
}

onMounted(reloadLogin)
</script>

<template>
  <div class="page">
    <PageHeader title="日志管理" description="登录日志与操作日志查询、删除与清空。" />

    <n-card :bordered="true">
      <n-tabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
        <!-- 登录日志 -->
        <n-tab-pane name="login" tab="登录日志">
          <n-space style="margin-bottom: 12px" :size="8" align="center">
            <n-input v-model:value="loginSearch.userName" placeholder="账号" clearable style="width: 160px" @keyup.enter="reloadLogin" />
            <n-input v-model:value="loginSearch.ipaddr" placeholder="IP" clearable style="width: 140px" @keyup.enter="reloadLogin" />
            <n-select
              v-model:value="loginSearch.status"
              placeholder="状态"
              clearable
              style="width: 110px"
              :options="[{ label: '成功', value: '0' }, { label: '失败', value: '1' }]"
            />
            <n-button size="small" type="primary" secondary @click="reloadLogin">
              <template #icon><n-icon :component="Search" /></template>
              查询
            </n-button>
            <n-button size="small" quaternary @click="reloadLogin">
              <template #icon><n-icon :component="RefreshCw" /></template>
              刷新
            </n-button>
            <n-space style="margin-left: auto" :size="8">
              <n-button
                v-if="loginSelected.length > 0"
                size="small"
                secondary
                type="error"
                @click="handleDeleteLoginLogs(loginSelected)"
              >
                删除所选（{{ loginSelected.length }}）
              </n-button>
              <n-button size="small" secondary type="error" @click="handleCleanLoginLogs">清空</n-button>
            </n-space>
          </n-space>

          <n-data-table
            :loading="loginLoading"
            :columns="loginColumns"
            :data="loginRows"
            :row-key="(r: LoginLog) => r.id"
            :checked-row-keys="loginSelected"
            @update:checked-row-keys="(keys: Array<string | number>) => (loginSelected = keys as number[])"
            :bordered="false"
          />
          <n-pagination
            :page="loginPagination.page"
            :page-size="loginPagination.pageSize"
            :item-count="loginPagination.itemCount"
            :page-sizes="[10, 20, 50, 100]"
            show-size-picker
            style="justify-content: flex-end; margin-top: 12px"
            @update:page="handleLoginPageChange"
            @update:page-size="handleLoginPageSizeChange"
          />
        </n-tab-pane>

        <!-- 操作日志 -->
        <n-tab-pane name="oper" tab="操作日志">
          <n-space style="margin-bottom: 12px" :size="8" align="center">
            <n-input v-model:value="operSearch.title" placeholder="模块" clearable style="width: 160px" @keyup.enter="reloadOper" />
            <n-input v-model:value="operSearch.operName" placeholder="操作人" clearable style="width: 140px" @keyup.enter="reloadOper" />
            <n-select
              v-model:value="operSearch.status"
              placeholder="状态"
              clearable
              style="width: 110px"
              :options="[{ label: '成功', value: 0 }, { label: '失败', value: 1 }]"
            />
            <n-button size="small" type="primary" secondary @click="reloadOper">
              <template #icon><n-icon :component="Search" /></template>
              查询
            </n-button>
            <n-button size="small" quaternary @click="reloadOper">
              <template #icon><n-icon :component="RefreshCw" /></template>
              刷新
            </n-button>
            <n-space style="margin-left: auto" :size="8">
              <n-button
                v-if="operSelected.length > 0"
                size="small"
                secondary
                type="error"
                @click="handleDeleteOperLogs(operSelected)"
              >
                删除所选（{{ operSelected.length }}）
              </n-button>
              <n-button size="small" secondary type="error" @click="handleCleanOperLogs">清空</n-button>
            </n-space>
          </n-space>

          <n-data-table
            :loading="operLoading"
            :columns="operColumns"
            :data="operRows"
            :row-key="(r: OperLog) => r.id"
            :checked-row-keys="operSelected"
            @update:checked-row-keys="(keys: Array<string | number>) => (operSelected = keys as number[])"
            :bordered="false"
            :scroll-x="operColumns.reduce((s, c) => s + (typeof c.width === 'number' ? c.width : 240), 0)"
          />
          <n-pagination
            :page="operPagination.page"
            :page-size="operPagination.pageSize"
            :item-count="operPagination.itemCount"
            :page-sizes="[10, 20, 50, 100]"
            show-size-picker
            style="justify-content: flex-end; margin-top: 12px"
            @update:page="handleOperPageChange"
            @update:page-size="handleOperPageSizeChange"
          />
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- 操作日志详情 -->
    <n-modal v-model:show="showDetail" preset="card" title="操作日志详情" style="width: 50%;" :bordered="false">
      <n-descriptions v-if="detailOper" :column="2" label-placement="left" bordered size="small">
        <n-descriptions-item label="模块" label-style="width: 85px;">{{ detailOper.title ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="类型">{{ BUSINESS_TYPE_LABELS[detailOper.businessType ?? 0] ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="操作人">{{ detailOper.operName ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="请求方式">{{ detailOper.requestMethod ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="URL" :span="2">{{ detailOper.operUrl ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="请求参数" :span="2">
          <pre style="white-space: pre-wrap; word-break: break-all; margin: 0">{{ detailOper.operParam ?? '-' }}</pre>
        </n-descriptions-item>
        <n-descriptions-item label="返回结果" :span="2">
          <pre style="white-space: pre-wrap; word-break: break-all; margin: 0">{{ detailOper.jsonResult ?? '-' }}</pre>
        </n-descriptions-item>
        <n-descriptions-item v-if="detailOper.errorMsg" label="错误信息" :span="2">
          <pre style="white-space: pre-wrap; word-break: break-all; margin: 0; color: #d03050">{{ detailOper.errorMsg }}</pre>
        </n-descriptions-item>
        <n-descriptions-item label="IP">{{ detailOper.operIp ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="耗时(ms)">{{ detailOper.costTime ?? '-' }}</n-descriptions-item>
        <n-descriptions-item label="操作时间" :span="2">{{ detailOper.operTime ? (formatDateTime(detailOper.operTime) ?? '-') : '-' }}</n-descriptions-item>
      </n-descriptions>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showDetail = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
