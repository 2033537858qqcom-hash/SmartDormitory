<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapsed ? '72px' : '216px'" class="sidebar">
      <div class="brand" :class="{ collapsed: isCollapsed }">
        <img class="brand-mark" src="/suli-icon-v2.png" alt="" />
        <div v-show="!isCollapsed" class="brand-text"><div class="title">宿里</div><div class="sub">SULI · 宿舍管理</div></div>
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
        <el-sub-menu v-if="canAccess('civilized-affair')" index="civilized-affair-group" aria-label="综合宿务">
          <template #title>
            <el-icon><Trophy /></el-icon>
            <span>综合宿务</span>
          </template>
          <el-menu-item index="/civilized-affair/todo">
            <el-icon class="sub-icon"><Finished /></el-icon>
            <template #title>待办首页</template>
          </el-menu-item>
          <el-menu-item index="/civilized-affair/eval">
            <el-icon class="sub-icon"><Medal /></el-icon>
            <template #title>文明评比</template>
          </el-menu-item>
          <el-menu-item index="/civilized-affair/hygiene">
            <el-icon class="sub-icon"><CircleCheck /></el-icon>
            <template #title>卫生检查</template>
          </el-menu-item>
          <el-menu-item index="/civilized-affair/adjust">
            <el-icon class="sub-icon"><Switch /></el-icon>
            <template #title>调宿审批</template>
          </el-menu-item>
          <el-menu-item index="/civilized-affair/visitor">
            <el-icon class="sub-icon"><Lock /></el-icon>
            <template #title>门禁访客</template>
          </el-menu-item>
        </el-sub-menu>
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
          <ThemeToggle />
          <el-popover v-if="canAccess('dashboard') || canAccess('civilized-affair')" placement="bottom-end" :width="340" trigger="click" @show="loadMessages">
            <template #reference>
              <button type="button" class="msg-bell" aria-label="站内消息">
                <el-icon :size="18"><Bell /></el-icon>
                <span v-if="msgUnread > 0" class="msg-dot">{{ msgUnread > 99 ? '99+' : msgUnread }}</span>
              </button>
            </template>
            <div class="msg-panel">
              <div class="msg-panel-head"><strong>站内消息</strong><el-button link type="primary" size="small" @click="markAllRead">全部已读</el-button></div>
              <p v-if="!messages.length" class="msg-empty">暂无消息</p>
              <button v-for="m in messages" :key="m.id" type="button" class="msg-item" :class="{ unread: m.readFlag === 0 }" @click="openMessage(m)">
                <b>{{ m.title }}</b>
                <small>{{ m.content }}</small>
              </button>
            </div>
          </el-popover>
          <el-dropdown trigger="click" placement="bottom-end">
            <button type="button" class="account-menu" aria-label="个人账号菜单">
              <span class="account-avatar small">{{ displayName.slice(0, 1) }}</span>
              <span class="header-user-meta">
                <span class="header-user-name">{{ displayName }}</span>
                <span class="header-user-role">{{ roleLabel }}</span>
              </span>
              <el-icon class="dropdown-caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu class="header-account-popover">
                <div class="user-popover-card">
                  <span class="account-avatar medium">{{ displayName.slice(0, 1) }}</span>
                  <div class="user-popover-info">
                    <strong class="user-popover-name">{{ displayName }}</strong>
                    <span class="user-popover-badge">{{ roleBadgeText }}</span>
                  </div>
                </div>
                <el-dropdown-item divided @click="pwdVisible = true">
                  <el-icon><Lock /></el-icon>
                  <span>修改密码</span>
                </el-dropdown-item>
                <el-dropdown-item class="logout-item" @click="logout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
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
// Bell icon is auto-imported by unplugin in this project when used in template
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import ThemeToggle from '@/components/ThemeToggle.vue'

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
  '/civilized-affair/todo': '待审调宿、申诉与超时访客，一门岗核验入口。',
  '/civilized-affair/eval': '文明寝室评分与五星补贴。',
  '/civilized-affair/hygiene': '四项内务卫生检查台账。',
  '/civilized-affair/adjust': '调宿与退宿审批。',
  '/civilized-affair/visitor': '门禁稽核、申诉与门岗输码核验。',
  '/user-notice': '管理人员信息，传递校园里的重要消息。'
}
const pageDescription = computed(() => descriptions[route.path] || descriptions['/civilized-affair'] || '智慧宿舍 · 后勤管理中心')
const currentUser = ref(JSON.parse(localStorage.getItem('dorm_admin_user') || '{}'))
const displayName = computed(() => (currentUser.value.realName || currentUser.value.username || '管理人员').split(/[（(]/)[0])
const isCollapsed = ref(window.innerWidth < 1180)
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: ''
})
const messages = ref([])
const msgUnread = ref(0)
const loadMessages = async () => {
  try {
    const [listRes, countRes] = await Promise.all([
      request.get('/messages', { params: { limit: 20 } }),
      request.get('/messages/unread-count')
    ])
    messages.value = listRes.data || []
    msgUnread.value = countRes.data?.count || 0
  } catch (_) { /* ignore */ }
}
const markAllRead = async () => {
  await request.post('/messages/read', {})
  await loadMessages()
}
const openMessage = async (m) => {
  if (m.readFlag === 0) {
    await request.post('/messages/read', { id: m.id })
    await loadMessages()
  }
  if (m.msgType === 'REPAIR' && /^\d+$/.test(String(m.bizId || ''))) {
    await router.push({ path: '/repair-dispatch', query: { orderId: m.bizId } })
  } else if (m.msgType === 'ADJUST') {
    await router.push('/civilized-affair/adjust')
  } else if (m.msgType === 'APPEAL' || m.msgType === 'VISITOR') {
    await router.push('/civilized-affair/visitor')
  } else if (m.msgType === 'RESIDENCE') {
    await router.push('/civilized-affair/residence')
  } else if (m.msgType === 'UTILITY') {
    await router.push('/utility-manage')
  }
}


const rolePermissions = {
  ADMIN: ['dashboard', 'smart-alloc', 'dorm-visual', 'repair-dispatch', 'utility-manage', 'civilized-affair', 'user-notice'],
  DORM_MANAGER: ['dashboard', 'dorm-visual', 'repair-dispatch', 'utility-manage', 'civilized-affair'],
  REPAIRMAN: ['repair-dispatch'],
  COUNSELOR: ['dashboard', 'dorm-visual', 'civilized-affair']
}

const canAccess = (menuKey) => {
  const role = currentUser.value.role
  const allowed = rolePermissions[role] || []
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
    REPAIRMAN: '维修工程师',
    COUNSELOR: '辅导员（只读）'
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
  if (currentUser.value.role === 'COUNSELOR') {
    return `${displayName.value} · 辅导员只读视图（评分/违纪/房态）`
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
  loadMessages()
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
.brand-mark { width: 32px; height: 32px; border-radius: 9px; object-fit: cover; flex-shrink: 0; }
.brand-text { white-space: nowrap; }
.brand-text .title { font-size: 17px; font-weight: 600; letter-spacing: -.6px; }
.brand-text .sub { margin-top: 3px; font-size: 10px; color: var(--text-muted); letter-spacing: 1.3px; }
.nav-caption { padding: 0 25px 10px; font-size: 11px; color: var(--text-muted); }
.side-menu {
  flex: 1;
  border: 0;
  background: transparent;
  padding: 0 12px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #65656b;
  --el-menu-hover-bg-color: rgb(255 255 255 / 55%);
  --el-menu-active-color: var(--text-primary);
}
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  margin-bottom: 4px;
  border-radius: 10px;
  font-size: 14px;
  padding: 0 14px !important;
  gap: 8px;
  text-decoration: none !important;
  transition: all 0.16s ease;
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.6);
  color: var(--text-primary);
  text-decoration: none !important;
}
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--bg-surface);
  box-shadow: 0 1px 4px rgb(29 29 31 / 4%);
  color: var(--accent-primary) !important;
  font-weight: 600;
  text-decoration: none !important;
}
.side-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--accent-primary);
  font-weight: 600;
}
.side-menu :deep(.el-menu-item *),
.side-menu :deep(.el-sub-menu__title *) {
  text-decoration: none !important;
}
.side-menu :deep(.el-icon) {
  font-size: 18px;
  flex-shrink: 0;
}

/* 综合宿务等二级折叠菜单的精致内嵌容器与树状导向线 */
.side-menu :deep(.el-sub-menu .el-menu) {
  background: rgba(0, 0, 0, 0.025);
  border-radius: 10px;
  padding: 4px 6px;
  margin: 2px 0 6px 8px;
  border-left: 2px solid rgba(0, 0, 0, 0.08);
}
.side-menu :deep(.el-sub-menu .el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin-bottom: 2px;
  border-radius: 8px;
  font-size: 13.5px;
  padding: 0 12px 0 16px !important;
  gap: 8px;
  color: #65656b;
  text-decoration: none !important;
}
.side-menu :deep(.el-sub-menu .el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.7);
  color: var(--text-primary);
  text-decoration: none !important;
}
.side-menu :deep(.el-sub-menu .el-menu-item.is-active) {
  background: var(--bg-surface);
  color: var(--accent-primary) !important;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(29, 29, 31, 0.05);
  text-decoration: none !important;
}
.side-menu :deep(.sub-icon) {
  font-size: 15px !important;
  opacity: 0.75;
}
.side-menu.el-menu--collapse {
  width: 72px;
  padding: 0 12px;
}
.sidebar-footer { padding: 18px 20px 22px; }
.account-summary { display: flex; align-items: center; gap: 10px; }
.account-summary b { display: block; font-size: 13px; }
.account-summary small { display: block; color: var(--text-muted); font-size: 11px; margin-top: 2px; }
.account-avatar { display: grid; place-items: center; width: 34px; height: 34px; flex-shrink: 0; border-radius: 50%; background: #fafafa; color: #686870; font-size: 13px; }
.account-avatar.small {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0071e3 0%, #388bfd 100%);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  display: grid;
  place-items: center;
  box-shadow: 0 1px 3px rgba(0, 113, 227, 0.28);
  flex-shrink: 0;
}
.account-avatar.medium {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0071e3 0%, #388bfd 100%);
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  display: grid;
  place-items: center;
  box-shadow: 0 2px 6px rgba(0, 113, 227, 0.25);
  flex-shrink: 0;
}
.student-link { margin: 19px 0 0 4px; display: flex; align-items: center; gap: 8px; color: var(--text-muted); text-decoration: none; font-size: 12px; }
.student-link:hover { color: var(--accent-primary); }
.right-container { min-width: 0; flex: 1; }
.top-header { height: 62px; background: rgb(245 245 247 / 90%); display: flex; align-items: center; justify-content: space-between; padding: 0 32px; gap: 15px; z-index: var(--z-header); }
.header-left { display: flex; align-items: center; gap: 13px; color: var(--text-secondary); font-size: 12px; min-width: 0; }
.header-right { display: flex; align-items: center; gap: 12px; }
.role-quick-switcher { display: flex; align-items: center; gap: 4px; background: #e6e6eb; padding: 3px 4px; border-radius: 10px; }
.switcher-caption { font-size: 11px; color: var(--text-muted); padding: 0 6px; white-space: nowrap; }
.role-pill { border: 0; background: transparent; color: var(--text-secondary); font-size: 12px; padding: 5px 10px; border-radius: 7px; cursor: pointer; white-space: nowrap; }
.role-pill.active { background: var(--bg-surface); color: var(--accent-primary); font-weight: 600; box-shadow: 0 1px 4px rgb(29 29 31 / 6%); }
.collapse-btn { color: var(--text-muted); padding: 6px; }
.breadcrumb-parent, .breadcrumb-slash { color: var(--text-muted); }

/* 头部个人账号胶囊按钮 */
.account-menu {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 5px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(0, 0, 0, 0.08);
  color: var(--text-primary);
  cursor: pointer;
  text-decoration: none !important;
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}
.account-menu:hover {
  background: var(--bg-surface);
  border-color: rgba(0, 0, 0, 0.15);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  text-decoration: none !important;
}
.account-menu:active {
  transform: scale(0.98);
}
.account-menu:focus-visible {
  outline: 2px solid var(--accent-primary);
  outline-offset: 2px;
}
.account-menu * {
  text-decoration: none !important;
}
.header-user-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  text-decoration: none !important;
}
.header-user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1;
  text-decoration: none !important;
}
.header-user-role {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.08);
  color: var(--accent-primary);
  font-weight: 500;
  line-height: 1.4;
  text-decoration: none !important;
}
.dropdown-caret {
  font-size: 11px;
  color: var(--text-muted);
  transition: transform 0.2s ease, color 0.2s ease;
  margin-left: 1px;
}
.account-menu:hover .dropdown-caret {
  color: var(--text-primary);
}

/* 站内消息铃铛按钮 */
.msg-bell {
  position: relative;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 50%;
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  text-decoration: none !important;
}
.msg-bell:hover {
  background: var(--bg-surface);
  color: var(--accent-primary);
  border-color: rgba(0, 0, 0, 0.15);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}
.msg-bell:active {
  transform: scale(0.96);
}
.msg-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: #ff3b30;
  color: #ffffff;
  font-size: 10px;
  font-weight: 600;
  line-height: 13px;
  text-align: center;
  border: 2px solid #ffffff;
  box-shadow: 0 1px 3px rgba(255, 59, 48, 0.35);
}

/* 账号弹出卡片 */
:deep(.header-account-popover) {
  padding: 6px;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}
.user-popover-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 10px;
  margin-bottom: 2px;
  text-decoration: none !important;
}
.user-popover-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  text-decoration: none !important;
}
.user-popover-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
}
.user-popover-badge {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
}
:deep(.logout-item) {
  color: #ff3b30 !important;
}
:deep(.logout-item:hover) {
  background: rgba(255, 59, 48, 0.08) !important;
  color: #ff3b30 !important;
}

.main-content { padding: 18px 32px 36px; overflow-y: auto; background: var(--bg-canvas); }
.content-wrap { max-width: 1520px; margin: 0 auto; }
.page-intro { margin-bottom: 29px; }
.page-eyebrow { color: var(--text-muted); font-size: 12px; margin-bottom: 7px; }
.page-title { font-size: 32px; font-weight: 600; letter-spacing: -1px; line-height: 1.25; }
.page-intro p { color: var(--text-muted); font-size: 13px; margin-top: 9px; }
@supports (backdrop-filter: blur(16px)) { .top-header { backdrop-filter: blur(16px); } }
@media (max-width: 900px) { .top-header { padding: 0 22px; } .main-content { padding: 12px 22px 30px; } .switcher-caption { display: none; } }
@media (max-width: 600px) {
  .top-header { height: 54px; padding: 0 15px; }
  .main-content { padding: 12px 15px 25px; }
  .breadcrumb-parent, .breadcrumb-slash, .header-user-meta, .dropdown-caret, .role-quick-switcher { display: none; }
  .account-menu { padding: 3px; }
  .page-title { font-size: 28px; }
  .page-intro { margin-bottom: 23px; }
  .sidebar-footer { padding: 18px; }
}
.msg-panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.msg-empty { color: var(--text-muted); font-size: 13px; padding: 12px 0; }
.msg-item { display: block; width: 100%; text-align: left; border: 0; border-bottom: 1px solid var(--border-subtle); background: transparent; padding: 10px 0; cursor: pointer; }
.msg-item b { display: block; font-size: 13px; }
.msg-item small { display: block; margin-top: 4px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.msg-item.unread b { color: var(--accent-primary); }
</style>
