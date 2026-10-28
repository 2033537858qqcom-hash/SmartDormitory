<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapsed ? '72px' : '216px'" class="sidebar">
      <div class="brand" :class="{ collapsed: isCollapsed }">
        <el-icon class="brand-mark" :size="22"><OfficeBuilding /></el-icon>
        <div v-show="!isCollapsed" class="brand-text"><div class="title">智慧宿舍</div><div class="sub">DORMITORY</div></div>
      </div>
      <div v-show="!isCollapsed" class="nav-caption">{{ roleLabel }}空间</div>
      <el-menu
        :default-active="$route.path"
        :collapse="isCollapsed"
        :collapse-transition="false"
        router
        class="side-menu"
      >
        <el-menu-item v-if="canAccess('dashboard')" index="/dashboard" aria-label="数据概览">
          <el-icon><DataLine /></el-icon>
          <template #title>数据概览</template>
        </el-menu-item>
        <el-menu-item v-if="canAccess('smart-alloc')" index="/smart-alloc" aria-label="智能分寝">
          <el-icon><Cpu /></el-icon>
          <template #title>智能分寝</template>
        </el-menu-item>
        <el-menu-item v-if="canAccess('dorm-visual')" index="/dorm-visual" aria-label="房态管理">
          <el-icon><OfficeBuilding /></el-icon>
          <template #title>房态管理</template>
        </el-menu-item>
        <el-menu-item v-if="canAccess('repair-dispatch')" index="/repair-dispatch" aria-label="报修工单">
          <el-icon><Tools /></el-icon>
          <template #title>{{ currentUser.role === 'REPAIRMAN' ? '我的维修工单' : '报修工单' }}</template>
        </el-menu-item>
        <el-menu-item v-if="canAccess('utility-manage')" index="/utility-manage" aria-label="水电管理">
          <el-icon><Odometer /></el-icon>
          <template #title>水电管理</template>
        </el-menu-item>
        <el-menu-item v-if="canAccess('civilized-affair')" index="/civilized-affair" aria-label="综合宿务">
          <el-icon><Trophy /></el-icon>
          <template #title>综合宿务</template>
        </el-menu-item>
        <el-menu-item v-if="canAccess('user-notice')" index="/user-notice" aria-label="人员与公告">
          <el-icon><UserFilled /></el-icon>
          <template #title>人员与公告</template>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <div class="account-summary"><span class="account-avatar">{{ displayName.slice(0, 1) }}</span><div v-show="!isCollapsed"><b>{{ displayName }}</b><small>{{ roleLabel }}</small></div></div>
        <a v-show="!isCollapsed" class="student-link" href="http://localhost:5174" target="_blank" rel="noopener">打开学生端 <el-icon><TopRight /></el-icon></a>
      </div>
    </el-aside>
    <el-container class="right-container">
      <el-header class="top-header">
        <div class="header-left">
          <el-button text class="collapse-btn" :aria-label="isCollapsed ? '展开侧边栏' : '收起侧边栏'" @click="isCollapsed = !isCollapsed"><el-icon :size="18"><Expand v-if="isCollapsed" /><Fold v-else /></el-icon></el-button>
          <span class="breadcrumb-parent">{{ roleLabel }}</span><span class="breadcrumb-slash">/</span><span>{{ $route.meta.title }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" placement="bottom-end">
            <button type="button" class="account-menu" aria-label="个人账号菜单"><span class="header-user-name">{{ displayName }}</span><span class="account-avatar small">{{ displayName.slice(0, 1) }}</span></button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item @click="pwdVisible = true">修改密码</el-dropdown-item><el-dropdown-item divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <div class="content-wrap">
          <header class="page-intro">
            <div class="page-eyebrow">{{ currentDate }} · {{ roleBadgeText }}</div>
            <h1 class="page-title">{{ $route.path === '/dashboard' ? '今日概览' : $route.meta.title }}</h1>
            <p>{{ pageDescription }}</p>
          </header>
          <router-view :key="currentUser.id || currentUser.username" />
        </div>
      </el-main>
    </el-container>
    <el-dialog v-model="pwdVisible" title="修改登录密码" width="400px">
      <el-form :model="pwdForm" label-width="85px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitChangePwd">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()
const currentDate = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'Asia/Shanghai' }).format(new Date())
const descriptions = {
  '/dashboard': '查看待办、住宿与能耗，安排今天的宿舍工作。',
  '/smart-alloc': '了解生活习惯，为每位新生找到合适的室友。',
  '/dorm-visual': '从楼栋到床位，清楚掌握每一间宿舍。',
  '/repair-dispatch': '及时响应每一次报修，让日常生活顺畅运转。',
  '/utility-manage': '查看月度用量与账单，关注需要核查的变化。',
  '/civilized-affair': '卫生检查、住宿申请、门禁考勤与来访登记，集中办理。',
  '/user-notice': '管理人员信息，传递校园里的重要消息。'
}
const pageDescription = computed(() => descriptions[route.path] || '智慧宿舍 · 后勤管理中心')
const currentUser = ref(JSON.parse(localStorage.getItem('dorm_admin_user') || '{}'))
const displayName = computed(() => (currentUser.value.realName || currentUser.value.username || '管理人员').split(/[（(]/)[0])
const isCollapsed = ref(window.innerWidth < 1180)
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: ''
})

const rolePermissions = {
  ADMIN: ['dashboard', 'smart-alloc', 'dorm-visual', 'repair-dispatch', 'utility-manage', 'civilized-affair', 'user-notice'],
  DORM_MANAGER: ['dashboard', 'dorm-visual', 'repair-dispatch', 'utility-manage', 'civilized-affair'],
  REPAIRMAN: ['repair-dispatch', 'dashboard']
}

const canAccess = (menuKey) => {
  const role = currentUser.value.role || 'ADMIN'
  const allowed = rolePermissions[role] || rolePermissions.ADMIN
  return allowed.includes(menuKey)
}

const handleResize = () => {
  if (window.innerWidth < 1100 && !isCollapsed.value) {
    isCollapsed.value = true
  }
}

const roleLabel = computed(() => {
  const map = {
    ADMIN: '系统管理员',
    DORM_MANAGER: '楼栋宿管员',
    REPAIRMAN: '维修工程师'
  }
  return map[currentUser.value.role] || '管理人员'
})

const roleBadgeText = computed(() => {
  if (currentUser.value.role === 'DORM_MANAGER') {
    return `${displayName.value} · 管辖楼栋宿务视图`
  }
  if (currentUser.value.role === 'REPAIRMAN') {
    return `${displayName.value} · 工种专职接单视图`
  }
  return `${displayName.value} · 全局管理权限`
})

const submitChangePwd = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  pwdSubmitting.value = true
  try {
    await request.post('/auth/change-password', {
      userId: currentUser.value.id,
      ...pwdForm
    })
    ElMessage.success('密码修改成功，请重新登录')
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    localStorage.removeItem('dorm_admin_user')
    localStorage.removeItem('dorm_admin_token')
    localStorage.removeItem('dorm_managed_building')
    router.push('/login')
  } finally {
    pwdSubmitting.value = false
  }
}

const logout = async () => {
  await ElMessageBox.confirm('确认退出当前管理账号吗？', '退出确认', {
    confirmButtonText: '确认退出',
    cancelButtonText: '取消',
    type: 'warning'
  })
  try { await request.post('/auth/logout') } catch (_) { /* local logout also works offline */ }
  localStorage.removeItem('dorm_admin_user')
  localStorage.removeItem('dorm_admin_token')
  localStorage.removeItem('dorm_managed_building')
  router.push('/login')
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.admin-layout { height: 100dvh; overflow: hidden; }
.sidebar { background: #ededf0; display: flex; flex-direction: column; border-right: 1px solid rgb(29 29 31 / 4%); flex-shrink: 0; overflow-x: hidden; }
.brand { display: flex; align-items: center; gap: 11px; padding: 28px 24px 30px; min-height: 105px; }
.brand.collapsed { padding: 30px 0; justify-content: center; }
.brand-mark { color: var(--text-primary); flex-shrink: 0; }
.brand-text { white-space: nowrap; }
.brand-text .title { font-size: 17px; font-weight: 600; letter-spacing: -.6px; }
.brand-text .sub { margin-top: 3px; font-size: 10px; color: var(--text-muted); letter-spacing: 1.3px; }
.nav-caption { padding: 0 25px 10px; font-size: 11px; color: var(--text-muted); }
.side-menu { flex: 1; border: 0; background: transparent; padding: 0 12px; --el-menu-bg-color: transparent; --el-menu-text-color: #65656b; --el-menu-hover-bg-color: rgb(255 255 255 / 50%); --el-menu-active-color: var(--text-primary); }
.side-menu :deep(.el-menu-item) { height: 43px; margin-bottom: 5px; border-radius: 10px; font-size: 14px; padding: 0 13px !important; gap: 3px; }
.side-menu :deep(.el-menu-item.is-active) { background: var(--bg-surface); box-shadow: 0 1px 4px rgb(29 29 31 / 3%); font-weight: 600; }
.side-menu :deep(.el-icon) { font-size: 18px; }
.side-menu.el-menu--collapse { width: 72px; padding: 0 12px; }
.sidebar-footer { padding: 18px 20px 22px; }
.account-summary { display: flex; align-items: center; gap: 10px; }
.account-summary b { display: block; font-size: 13px; }
.account-summary small { display: block; color: var(--text-muted); font-size: 11px; margin-top: 2px; }
.account-avatar { display: grid; place-items: center; width: 34px; height: 34px; flex-shrink: 0; border-radius: 50%; background: #fafafa; color: #686870; font-size: 13px; }
.account-avatar.small { width: 30px; height: 30px; background: #e7e7ec; font-size: 12px; }
.student-link { margin: 19px 0 0 4px; display: flex; align-items: center; gap: 8px; color: var(--text-muted); text-decoration: none; font-size: 12px; }
.student-link:hover { color: var(--accent-primary); }
.right-container { min-width: 0; flex: 1; }
.top-header { height: 62px; background: rgb(245 245 247 / 90%); display: flex; align-items: center; justify-content: space-between; padding: 0 32px; gap: 15px; z-index: var(--z-header); }
.header-left { display: flex; align-items: center; gap: 13px; color: var(--text-secondary); font-size: 12px; min-width: 0; }
.header-right { display: flex; align-items: center; gap: 14px; }
.role-quick-switcher { display: flex; align-items: center; gap: 4px; background: #e6e6eb; padding: 3px 4px; border-radius: 10px; }
.switcher-caption { font-size: 11px; color: var(--text-muted); padding: 0 6px; white-space: nowrap; }
.role-pill { border: 0; background: transparent; color: var(--text-secondary); font-size: 12px; padding: 5px 10px; border-radius: 7px; cursor: pointer; white-space: nowrap; }
.role-pill.active { background: #fff; color: var(--accent-primary); font-weight: 600; box-shadow: 0 1px 4px rgb(29 29 31 / 6%); }
.collapse-btn { color: var(--text-muted); padding: 6px; }
.breadcrumb-parent, .breadcrumb-slash { color: var(--text-muted); }
.account-menu { display: flex; align-items: center; gap: 10px; border: 0; background: transparent; color: var(--text-secondary); border-radius: 20px; cursor: pointer; }
.header-user-name { font-size: 12px; }
.main-content { padding: 18px 32px 36px; overflow-y: auto; background: var(--bg-canvas); }
.content-wrap { max-width: 1520px; margin: 0 auto; }
.page-intro { margin-bottom: 29px; }
.page-eyebrow { color: var(--text-muted); font-size: 12px; margin-bottom: 7px; }
.page-title { font-size: 32px; font-weight: 600; letter-spacing: -1px; line-height: 1.25; }
.page-intro p { color: var(--text-muted); font-size: 13px; margin-top: 9px; }
@supports (backdrop-filter: blur(16px)) { .top-header { backdrop-filter: blur(16px); } }
@media (max-width: 900px) { .top-header { padding: 0 22px; } .main-content { padding: 12px 22px 30px; } .switcher-caption { display: none; } }
@media (max-width: 600px) { .top-header { height: 54px; padding: 0 15px; } .main-content { padding: 12px 15px 25px; } .breadcrumb-parent, .breadcrumb-slash, .header-user-name, .role-quick-switcher { display: none; } .page-title { font-size: 28px; } .page-intro { margin-bottom: 23px; } .sidebar-footer { padding: 18px; } }
</style>
