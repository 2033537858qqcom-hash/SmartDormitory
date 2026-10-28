import { createRouter, createWebHistory } from 'vue-router'

const rolePermissions = {
  ADMIN: ['dashboard', 'smart-alloc', 'dorm-visual', 'repair-dispatch', 'utility-manage', 'civilized-affair', 'user-notice'],
  DORM_MANAGER: ['dashboard', 'dorm-visual', 'repair-dispatch', 'utility-manage', 'civilized-affair'],
  REPAIRMAN: ['repair-dispatch'],
  COUNSELOR: ['dashboard', 'dorm-visual', 'civilized-affair']
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        meta: { title: '数据概览', roles: ['ADMIN', 'DORM_MANAGER', 'COUNSELOR'] },
        component: () => import('@/views/Dashboard.vue')
      },
      {
        path: 'smart-alloc',
        name: 'SmartAllocation',
        meta: { title: '智能分寝', roles: ['ADMIN'] },
        component: () => import('@/views/SmartAllocation.vue')
      },
      {
        path: 'dorm-visual',
        name: 'DormVisual',
        meta: { title: '房态管理', roles: ['ADMIN', 'DORM_MANAGER', 'COUNSELOR'] },
        component: () => import('@/views/DormVisual.vue')
      },
      {
        path: 'repair-dispatch',
        name: 'RepairDispatch',
        meta: { title: '报修工单', roles: ['ADMIN', 'DORM_MANAGER', 'REPAIRMAN'] },
        component: () => import('@/views/RepairDispatch.vue')
      },
      {
        path: 'utility-manage',
        name: 'UtilityManage',
        meta: { title: '水电管理', roles: ['ADMIN', 'DORM_MANAGER'] },
        component: () => import('@/views/UtilityManage.vue')
      },
      {
        path: 'civilized-affair',
        redirect: '/civilized-affair/todo'
      },
      {
        path: 'civilized-affair/:section',
        name: 'CivilizedAffair',
        meta: { title: '综合宿务', roles: ['ADMIN', 'DORM_MANAGER', 'COUNSELOR'] },
        component: () => import('@/views/CivilizedAffair.vue'),
        props: true
      },
      {
        path: 'user-notice',
        name: 'UserNoticeManage',
        meta: { title: '人员与公告', roles: ['ADMIN'] },
        component: () => import('@/views/UserNoticeManage.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStr = localStorage.getItem('dorm_admin_user')
  const token = localStorage.getItem('dorm_admin_token')
  if (to.path !== '/login' && (!userStr || !token)) {
    next('/login')
    return
  }
  if (to.path !== '/login' && to.meta?.roles) {
    try {
      const user = JSON.parse(userStr || '{}')
      if (!to.meta.roles.includes(user.role)) {
        const allowed = rolePermissions[user.role] || []
        next(allowed[0] ? `/${allowed[0]}` : '/login')
        return
      }
    } catch (e) {
      next('/login')
      return
    }
  }
  next()
})

export default router
