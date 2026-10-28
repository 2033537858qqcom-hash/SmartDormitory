import { createRouter, createWebHistory } from 'vue-router'

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
        meta: { title: '数据概览' },
        component: () => import('@/views/Dashboard.vue')
      },
      {
        path: 'smart-alloc',
        name: 'SmartAllocation',
        meta: { title: '智能分寝' },
        component: () => import('@/views/SmartAllocation.vue')
      },
      {
        path: 'dorm-visual',
        name: 'DormVisual',
        meta: { title: '房态管理' },
        component: () => import('@/views/DormVisual.vue')
      },
      {
        path: 'repair-dispatch',
        name: 'RepairDispatch',
        meta: { title: '报修工单' },
        component: () => import('@/views/RepairDispatch.vue')
      },
      {
        path: 'utility-manage',
        name: 'UtilityManage',
        meta: { title: '水电管理' },
        component: () => import('@/views/UtilityManage.vue')
      },
      {
        path: 'civilized-affair',
        name: 'CivilizedAffair',
        meta: { title: '综合宿务' },
        component: () => import('@/views/CivilizedAffair.vue')
      },
      {
        path: 'user-notice',
        name: 'UserNoticeManage',
        meta: { title: '人员与公告' },
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
  if (to.path !== '/login' && !userStr) {
    next('/login')
  } else {
    next()
  }
})

export default router
