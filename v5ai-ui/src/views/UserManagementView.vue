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
  NModal,
  NPagination,
  NRadioButton,
  NRadioGroup,
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
  changeUserStatus,
  createUser,
  deleteUsers,
  getUserDetail,
  listRoleOptions,
  listUsers,
  resetUserPwd,
  unlockUser,
  updateUser,
  type Role,
  type UserAccount,
  type UserForm
} from '../api/client'
import { adminToken } from '../stores/session'
import { formatDateTime } from '../utils/dateUtils'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const rows = ref<UserAccount[]>([])
const roles = ref<Role[]>([])
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })
const search = reactive({ userName: '', phoneNumber: '', status: null as string | null })

const columns: DataTableColumns<UserAccount> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 64 },
  { title: '账号', key: 'userName', width: 140 },
  { title: '昵称', key: 'nickName', width: 120, render: (r) => r.nickName ?? '-' },
  { title: '手机号', key: 'phoneNumber', width: 130, render: (r) => r.phoneNumber ?? '-' },
  { title: '邮箱', key: 'email', width: 140, render: (r) => r.email ?? '-' },
  /*{
    title: '角色',
    key: 'roles',
    width: 180,
    render: (r) =>
      h(NSpace, { size: 4 }, {
        default: () => (r.roles ?? []).map((role) => h(NTag, { size: 'small', bordered: false, round: true }, { default: () => role.roleName }))
      })
  },*/
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
  { title: '最后登录', key: 'loginDate', width: 185, render: (r) => (r.loginDate ? (formatDateTime(r.loginDate) ?? '-') : '-') },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (r) =>
      h(RowActions, {
        maxInline: 1,
        actions: [
          { key: 'edit', label: '编辑', quaternary: true, onClick: () => openEdit(r) },
          { key: 'resetPwd', label: '重置密码', quaternary: true, onClick: () => openResetPwd(r) },
          ...(r.status === '1'
            ? [{ key: 'unlock', label: '解锁', quaternary: true, onClick: () => handleUnlock(r) }]
            : []),
          {
            key: 'delete',
            label: '删除',
            quaternary: true,
            type: 'error',
            confirm: `确认删除用户「${r.userName}」？`,
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
    const res = await listUsers(adminToken.value, {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      userName: search.userName || undefined,
      phoneNumber: search.phoneNumber || undefined,
      status: search.status ?? undefined
    })
    rows.value = res.rows
    pagination.itemCount = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载用户失败')
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
const form = reactive<UserForm>({ userName: '', nickName: '', password: '', phoneNumber: '', email: '', gender: '', status: '0', remark: '', roleIds: [] })

/** 加载可选角色列表（角色下拉选项接口）。 */
async function loadRoleOptions() {
  try {
    roles.value = await listRoleOptions(adminToken.value)
  } catch {
    roles.value = []
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: undefined, userName: '', nickName: '', password: '', phoneNumber: '', email: '', gender: '', status: '0', remark: '', roleIds: [] })
  void loadRoleOptions()
  showModal.value = true
}

async function openEdit(row: UserAccount) {
  isEdit.value = true
  try {
    const detail = await getUserDetail(adminToken.value, row.id)
    roles.value = detail.roles
    Object.assign(form, {
      id: detail.user.id,
      userName: detail.user.userName,
      nickName: detail.user.nickName ?? '',
      password: '',
      phoneNumber: detail.user.phoneNumber ?? '',
      email: detail.user.email ?? '',
      gender: detail.user.gender ?? '',
      status: detail.user.status ?? '0',
      remark: detail.user.remark ?? '',
      roleIds: detail.roleIds ?? []
    })
    showModal.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载用户详情失败')
  }
}

async function handleSave() {
  if (!form.userName || !form.nickName || (!isEdit.value && !form.password)) {
    message.warning('请填写账号、昵称与密码')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      const { password, ...rest } = form
      await updateUser(adminToken.value, { ...rest, password: password || undefined })
      message.success('用户已更新')
    } else {
      await createUser(adminToken.value, { ...form, password: form.password! })
      message.success('用户已创建')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 重置密码 / 状态 / 解锁 / 删除 ----

const resetPwdUser = ref<UserAccount | null>(null)
const resetPwdValue = ref('')
const resetPwdSaving = ref(false)

function openResetPwd(row: UserAccount) {
  resetPwdUser.value = row
  resetPwdValue.value = ''
  showResetPwdModal.value = true
}

const showResetPwdModal = ref(false)

async function handleResetPwd() {
  if (!resetPwdUser.value || !resetPwdValue.value) {
    message.warning('请输入新密码')
    return
  }
  resetPwdSaving.value = true
  try {
    await resetUserPwd(adminToken.value, resetPwdUser.value.id, resetPwdValue.value)
    message.success('密码已重置')
    showResetPwdModal.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重置失败')
  } finally {
    resetPwdSaving.value = false
  }
}

async function toggleStatus(row: UserAccount) {
  const next = row.status === '0' ? '1' : '0'
  togglingId.value = row.id
  try {
    await changeUserStatus(adminToken.value, row.id, next)
    message.success(next === '0' ? '已启用' : '已停用')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    togglingId.value = null
  }
}

async function handleUnlock(row: UserAccount) {
  try {
    await unlockUser(adminToken.value, row.id)
    message.success('已解锁')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '解锁失败')
  }
}

async function handleDelete(targets: UserAccount[]) {
  try {
    await deleteUsers(adminToken.value, targets.map((t) => t.id))
    message.success('已删除')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function handleBatchDelete() {
  dialog.warning({
    title: '批量删除确认',
    content: `确认删除选中的 ${selectedIds.value.length} 个用户？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteUsers(adminToken.value, selectedIds.value)
        message.success('已删除所选用户')
        selectedIds.value = []
        await reload()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    }
  })
}

onMounted(() => {
  void reload()
  void loadRoleOptions()
})
</script>

<template>
  <div class="page">
    <PageHeader title="用户管理" description="管理平台登录账号、角色与状态。">
      <template #actions>
        <n-space size="small">
          <n-button size="small" type="primary" @click="openCreate">
            <template #icon><n-icon :component="Plus" /></template>
            新增用户
          </n-button>
        </n-space>
      </template>
    </PageHeader>

    <n-card :bordered="true">
      <n-space style="margin-bottom: 12px" :size="8" align="center">
        <n-input v-model:value="search.userName" placeholder="账号" clearable style="width: 180px" @keyup.enter="handleSearch" />
        <n-input v-model:value="search.phoneNumber" placeholder="手机号" clearable style="width: 160px" @keyup.enter="handleSearch" />
        <n-select
          v-model:value="search.status"
          placeholder="状态"
          clearable
          style="width: 120px"
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
        :row-key="(r: UserAccount) => r.id"
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

    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑用户' : '新增用户'" style="width: 560px" :bordered="false">
      <n-form label-placement="top">
        <n-divider title-placement="left" style="margin: 0 0 16px">账号</n-divider>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="账号" required>
            <n-input v-model:value="form.userName" :disabled="isEdit" placeholder="登录账号" />
          </n-form-item-gi>
          <n-form-item-gi :label="isEdit ? '密码（留空则不修改）' : '密码'" :required="!isEdit">
            <n-input v-model:value="form.password" type="password" show-password-on="click" placeholder="至少 5 位" />
          </n-form-item-gi>
        </n-grid>

        <n-divider title-placement="left" style="margin: 8px 0 16px">个人资料</n-divider>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="昵称" required>
            <n-input v-model:value="form.nickName" placeholder="显示名称" />
          </n-form-item-gi>
          <n-form-item-gi label="性别">
            <n-radio-group v-model:value="form.gender">
              <n-radio-button value="0">男</n-radio-button>
              <n-radio-button value="1">女</n-radio-button>
              <n-radio-button value="2">未知</n-radio-button>
            </n-radio-group>
          </n-form-item-gi>
          <n-form-item-gi label="手机号">
            <n-input v-model:value="form.phoneNumber" placeholder="手机号" />
          </n-form-item-gi>
          <n-form-item-gi label="邮箱">
            <n-input v-model:value="form.email" placeholder="邮箱" />
          </n-form-item-gi>
        </n-grid>

        <n-divider title-placement="left" style="margin: 8px 0 16px">权限与备注</n-divider>
        <n-form-item label="角色">
          <n-select
            v-model:value="form.roleIds"
            multiple
            clearable
            placeholder="选择角色"
            :options="roles.map((r) => ({ label: r.roleName, value: r.id }))"
          />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" type="textarea" placeholder="备注" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showResetPwdModal" preset="card" title="重置密码" style="width: 420px" :bordered="false">
      <p style="margin: 0 0 12px; opacity: 0.7">为账号「{{ resetPwdUser?.userName }}」设置新密码</p>
      <n-input v-model:value="resetPwdValue" type="password" show-password-on="click" placeholder="新密码（至少 5 位）" />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showResetPwdModal = false">取消</n-button>
          <n-button type="primary" :loading="resetPwdSaving" @click="handleResetPwd">确认重置</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>
