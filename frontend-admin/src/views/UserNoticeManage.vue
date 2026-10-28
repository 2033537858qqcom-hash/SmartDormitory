<template>
  <div v-loading="loading" class="page-stack">
    <div v-if="loadError" class="error-banner" role="alert">
      <span>数据加载失败，请重试。</span>
      <el-button type="danger" plain size="small" @click="initPage">重试</el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="surface-card">
      <!-- Tab 1: 人员与角色管理 -->
      <el-tab-pane label="人员档案管理" name="users">
        <div class="section-header">
          <div class="toolbar-actions">
            <el-radio-group v-model="roleFilter" size="small" @change="loadUsers">
              <el-radio-button label="">全部人员</el-radio-button>
              <el-radio-button label="STUDENT">学生</el-radio-button>
              <el-radio-button label="DORM_MANAGER">宿管员</el-radio-button>
              <el-radio-button label="REPAIRMAN">维修师傅</el-radio-button>
              <el-radio-button label="COUNSELOR">辅导员</el-radio-button>
              <el-radio-button label="ADMIN">管理员</el-radio-button>
            </el-radio-group>
            <el-input
              v-model="keyword"
              placeholder="按姓名或账号搜索"
              size="small"
              clearable
              class="search-input"
              @keyup.enter="loadUsers"
              @clear="loadUsers"
            />
            <el-button size="small" type="primary" plain icon="Search" @click="loadUsers">查询</el-button>
          </div>
          <div class="toolbar-actions">
            <el-button size="small" type="success" plain icon="Download" @click="exportStudents">导出名单</el-button>
            <el-button size="small" type="primary" icon="Plus" @click="openUserModal()">新增账号</el-button>
          </div>
        </div>

        <p v-if="!users.length" class="list-empty">暂无符合条件的用户</p>
        <ul v-else class="person-list" aria-label="用户列表">
          <li v-for="row in users" :key="row.id" class="person-row">
            <div class="person-identity">
              <span class="person-avatar" aria-hidden="true">{{ (row.realName || '?').slice(0, 1) }}</span>
              <div><strong>{{ row.realName || '-' }}</strong><small>{{ row.username }} · {{ row.gender === 2 ? '女' : '男' }} · {{ row.phone || '-' }}</small></div>
            </div>
            <div class="person-details">
              <strong>{{ roleName(row.role) }}</strong>
              <small v-if="row.role === 'STUDENT'">{{ row.college }} · {{ row.className }} ({{ row.grade }})</small>
              <small v-else-if="row.role === 'REPAIRMAN'">工种：{{ row.repairSpecialty || '-' }}</small>
              <small v-else>{{ row.email || '后勤保障部' }}</small>
            </div>
            <div class="person-details"><strong :class="{ 'status-disabled': row.status !== 1 }">{{ row.status === 1 ? '正常' : '禁用' }}</strong></div>
            <div class="person-actions">
              <el-button link type="primary" @click="openUserModal(row)">编辑</el-button>
              <el-button link type="warning" @click="handleResetPwd(row)">重置密码</el-button>
              <el-button v-if="row.id !== 1" link type="danger" @click="handleDeleteUser(row)">删除</el-button>
            </div>
          </li>
        </ul>
      </el-tab-pane>

      <!-- Tab 2: 园区通知公告发布 -->
      <el-tab-pane label="通知公告管理" name="notices">
        <div class="section-header">
          <span class="section-subtitle">发布或管理园区通知公告，学生端首页同步展示</span>
          <el-button type="primary" size="small" icon="Plus" @click="openNoticeModal()">发布公告</el-button>
        </div>

        <p v-if="!notices.length" class="list-empty">暂无公告</p>
        <ul v-else class="notice-list" aria-label="公告列表">
          <li v-for="row in notices" :key="row.id" class="notice-row">
            <details class="notice-details">
              <summary><span class="notice-title"><span v-if="row.isTop === 1" class="notice-pinned">置顶</span>{{ row.title }}</span><span class="notice-meta">{{ row.noticeType }} · {{ row.publisherName || '-' }} · {{ row.viewCount ?? 0 }} 次阅读 · {{ String(row.createTime || '').replace('T', ' ').slice(0, 16) || '-' }} <span class="notice-toggle" aria-hidden="true"></span></span></summary>
              <p class="notice-content">{{ row.content }}</p>
            </details>
            <div class="person-actions">
              <el-button link type="primary" @click="openNoticeModal(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDeleteNotice(row)">删除</el-button>
            </div>
          </li>
        </ul>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑用户弹窗 -->
    <el-dialog v-model="userVisible" :title="userForm.id ? '编辑人员信息' : '新增人员账号'" width="480px">
      <el-form :model="userForm" label-width="95px">
        <el-form-item label="登录账号">
          <el-input v-model="userForm.username" placeholder="学号或工号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="系统角色">
          <el-select v-model="userForm.role" class="full-width">
            <el-option label="学生" value="STUDENT" />
            <el-option label="宿管员" value="DORM_MANAGER" />
            <el-option label="维修师傅" value="REPAIRMAN" />
            <el-option label="辅导员（只读）" value="COUNSELOR" />
            <el-option label="系统管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="userForm.gender">
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <template v-if="userForm.role === 'STUDENT'">
          <el-form-item label="所属学院">
            <el-input v-model="userForm.college" placeholder="如 计算机科学与技术学院" />
          </el-form-item>
          <el-form-item label="专业班级">
            <el-input v-model="userForm.className" placeholder="如 软工2601班" />
          </el-form-item>
          <el-form-item label="入学年级">
            <el-input v-model="userForm.grade" placeholder="如 2026级" />
          </el-form-item>
        </template>
        <template v-if="userForm.role === 'REPAIRMAN'">
          <el-form-item label="维修工种">
            <el-select v-model="userForm.repairSpecialty" class="full-width">
              <el-option label="ELECTRIC - 电力照明" value="ELECTRIC" />
              <el-option label="WATER - 水暖卫浴" value="WATER" />
              <el-option label="WOOD - 门窗木工" value="WOOD" />
              <el-option label="NETWORK - 网络空调" value="NETWORK" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="账号状态">
          <el-switch v-model="userForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑公告弹窗 -->
    <el-dialog v-model="noticeVisible" :title="noticeForm.id ? '编辑通知公告' : '发布新公告'" width="500px">
      <el-form :model="noticeForm" label-width="90px">
        <el-form-item label="公告标题">
          <el-input v-model="noticeForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告类型">
          <el-select v-model="noticeForm.noticeType" class="full-width">
            <el-option label="SYSTEM - 园区通知" value="SYSTEM" />
            <el-option label="SAFETY - 安全提醒" value="SAFETY" />
            <el-option label="HYGIENE - 卫生通报" value="HYGIENE" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布部门">
          <el-input v-model="noticeForm.publisherName" />
        </el-form-item>
        <el-form-item label="是否置顶">
          <el-switch v-model="noticeForm.isTop" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="公告正文">
          <el-input v-model="noticeForm.content" type="textarea" :rows="4" placeholder="请输入详细通知内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveNotice">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request, { downloadExport } from '@/api/request'

const loading = ref(false)
const loadError = ref(false)
const submitting = ref(false)
const activeTab = ref('users')
const users = ref([])
const roleFilter = ref('')
const keyword = ref('')
const notices = ref([])

const userVisible = ref(false)
const userForm = reactive({
  id: null,
  username: '',
  realName: '',
  role: 'STUDENT',
  gender: 1,
  phone: '',
  college: '计算机科学与技术学院',
  className: '软工2601班',
  grade: '2026级',
  repairSpecialty: 'ELECTRIC',
  status: 1
})

const noticeVisible = ref(false)
const noticeForm = reactive({
  id: null,
  title: '',
  noticeType: 'SYSTEM',
  publisherName: '校后勤宿管中心',
  isTop: 1,
  content: ''
})

const roleName = (r) => {
  const m = { ADMIN: '系统管理员', DORM_MANAGER: '楼栋宿管员', REPAIRMAN: '维修师傅', COUNSELOR: '辅导员', STUDENT: '学生' }
  return m[r] || r
}

const roleTag = (r) => {
  if (r === 'ADMIN') return 'danger'
  if (r === 'DORM_MANAGER') return 'success'
  if (r === 'REPAIRMAN') return 'warning'
  if (r === 'COUNSELOR') return 'info'
  return 'primary'
}

const loadUsers = async () => {
  const res = await request.get('/auth/users', {
    params: { role: roleFilter.value, keyword: keyword.value }
  })
  users.value = res.data || []
}

const loadNotices = async () => {
  const res = await request.get('/affair/notices')
  notices.value = res.data || []
}

const initPage = async () => {
  loading.value = true
  loadError.value = false
  try {
    await Promise.all([loadUsers(), loadNotices()])
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const openUserModal = (row) => {
  if (row) {
    Object.assign(userForm, row)
  } else {
    Object.assign(userForm, {
      id: null,
      username: '',
      realName: '',
      role: 'STUDENT',
      gender: 1,
      phone: '15800001234',
      college: '人工智能学院',
      className: 'AI2602班',
      grade: '2026级',
      repairSpecialty: 'ELECTRIC',
      status: 1
    })
  }
  userVisible.value = true
}

const saveUser = async () => {
  if (!userForm.username.trim() || !userForm.realName.trim()) {
    ElMessage.warning('请填写账号与姓名')
    return
  }
  submitting.value = true
  try {
    await request.post('/auth/user', userForm)
    ElMessage.success('人员信息已保存')
    userVisible.value = false
    await loadUsers()
  } finally {
    submitting.value = false
  }
}

const handleResetPwd = async (row) => {
  await ElMessageBox.confirm(`确认将【${row.realName}】的密码重置为 123456 吗？`, '重置密码', {
    confirmButtonText: '确认重置',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.post(`/auth/reset-password/${row.id}`)
  ElMessage.success(`已将 ${row.realName} 的密码重置为 123456`)
}

const handleDeleteUser = async (row) => {
  await ElMessageBox.confirm(`确认删除账号【${row.realName}】吗？`, '删除确认', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.delete(`/auth/user/${row.id}`)
  ElMessage.success('账号已删除')
  loadUsers()
}

const openNoticeModal = (row) => {
  if (row) {
    Object.assign(noticeForm, row)
  } else {
    Object.assign(noticeForm, {
      id: null,
      title: '',
      noticeType: 'SYSTEM',
      publisherName: '校后勤宿管中心',
      isTop: 1,
      content: ''
    })
  }
  noticeVisible.value = true
}

const saveNotice = async () => {
  if (!noticeForm.title.trim()) {
    ElMessage.warning('请填写公告标题')
    return
  }
  submitting.value = true
  try {
    await request.post('/affair/notice', noticeForm)
    ElMessage.success('公告已发布')
    noticeVisible.value = false
    await loadNotices()
  } finally {
    submitting.value = false
  }
}

const handleDeleteNotice = async (row) => {
  await ElMessageBox.confirm(`确认删除公告《${row.title}》吗？`, '删除确认', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.delete(`/affair/notice/${row.id}`)
  ElMessage.success('公告已删除')
  loadNotices()
}

const exportStudents = () => {
  downloadExport('students', '学生住宿.csv').catch(() => ElMessage.error('导出失败，请检查登录状态'))
}

onMounted(() => {
  initPage()
})
</script>

<style scoped>
.search-input {
  width: 200px;
}
.tab-table {
  margin-top: 14px;
}
.top-tag {
  margin-right: 6px;
}
.full-width {
  width: 100%;
}
</style>
