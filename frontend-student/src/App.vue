<template>
  <div v-if="!currentStudentId" class="login-stage">
    <form class="student-login" @submit.prevent="login">
      <h1>智慧宿舍</h1>
      <p>请使用学生账号登录</p>
      <label>学号 <input v-model.trim="loginForm.username" autocomplete="username" required /></label>
      <label>密码 <input v-model="loginForm.password" type="password" autocomplete="current-password" required /></label>
      <p v-if="loginError" class="login-error" role="alert">{{ loginError }}</p>
      <button type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中…' : '登录' }}</button>
    </form>
  </div>
  <div v-else class="mp-stage">
    <button class="student-logout" type="button" @click="logout">退出登录</button>

    <!-- 学生端 Vite H5 自适应高度容器（小程序形态 UI；非微信小程序工程） -->
    <div class="phone-frame">
      <!-- 顶部导航栏 -->
      <header class="wx-navbar" :class="{ scrolled: isHeaderCollapsed }">
        <div class="nav-title">{{ currentTab === 'home' ? currentDate : navTitle }}</div>
        <span class="nav-user">{{ dash.user?.realName || "同学" }}</span>
      </header>

      <!-- 顶部操作结果胶囊提示：区分成功、信息说明、警告与错误 -->
      <transition name="capsule-toast">
        <div v-if="toastInfo.text" class="top-capsule-toast" :class="toastInfo.type" role="status">
          <!-- 成功：绿色对勾 -->
          <svg v-if="toastInfo.type === 'success'" class="toast-svg draw-check" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 12.5l5.5 5.5L20 6.5" />
          </svg>
          <!-- 提示说明：蓝色圆圈 (i) -->
          <svg v-else-if="toastInfo.type === 'info'" class="toast-svg info-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <line x1="12" y1="8" x2="12" y2="8" />
            <line x1="12" y1="12" x2="12" y2="16" />
          </svg>
          <!-- 警告：黄色感叹号 (!) -->
          <svg v-else-if="toastInfo.type === 'warning'" class="toast-svg warn-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
          <!-- 错误：红色叉 (x) -->
          <svg v-else class="toast-svg err-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <line x1="15" y1="9" x2="9" y2="15" />
            <line x1="9" y1="9" x2="15" y2="15" />
          </svg>
          <span>{{ toastInfo.text }}</span>
        </div>
      </transition>

      <!-- 主滚动视图区 -->
      <main ref="scrollBodyRef" class="mp-scroll-body">
        <div ref="sentinelRef" class="scroll-sentinel" aria-hidden="true"></div>

        <!-- 骨架屏加载态 -->
        <div v-if="loading" class="skeleton-wrap" aria-busy="true">
          <div class="sk-hero shimmer"></div>
          <div class="sk-grid">
            <div class="sk-card shimmer"></div>
            <div class="sk-card shimmer"></div>
          </div>
          <div class="sk-list shimmer"></div>
        </div>

        <!-- 加载失败重试态 -->
        <div v-else-if="loadError" class="error-card" role="alert">
          <div class="err-title">数据加载失败</div>
          <p class="err-desc">无法连接宿管服务，请检查网络后重试。</p>
          <button type="button" class="retry-btn spring-btn" @click="loadDashboard">重新加载</button>
        </div>

        <template v-else>
          <!-- 个人信息头部：仅在首页展示，在问卷、报修、宿务页压缩隐藏，释放核心交互空间 -->
          <div v-if="currentTab === 'home'" class="large-header" :class="{ collapsed: isHeaderCollapsed }">
            <div class="greet-row">
              <h1 class="greet">我的宿舍</h1>
              <span class="status-badge" :class="dash.room ? 'alloc' : 'unalloc'">
                {{ dash.room ? '已入住' : '待分寝新生' }}
              </span>
            </div>
            <div class="sub-meta tabular-nums">
              你好，{{ dash.user?.realName || '同学' }}。
            </div>
          </div>

          <!-- 四个 Tab 页面视图 -->
          <TabHome
            v-if="currentTab === 'home'"
            :dash="dash"
            @go-tab="handleNavigate"
            @refresh="loadDashboard"
            @toast="showToast"
          />
          <TabHabitRadar
            v-else-if="currentTab === 'habit'"
            :dash="dash"
            @refresh="loadDashboard"
            @toast="showToast"
          />
          <TabRepair
            v-else-if="currentTab === 'repair'"
            :dash="dash"
            @refresh="loadDashboard"
            @toast="showToast"
          />
          <TabUtility
            v-else-if="currentTab === 'utility'"
            :dash="dash"
            :initial-action="utilityInitialAction"
            @clear-action="utilityInitialAction = ''"
            @go-tab="handleNavigate"
            @refresh="loadDashboard"
            @toast="showToast"
          />
          <TabAffair
            v-else-if="currentTab === 'affair'"
            :dash="dash"
            :initial-action="utilityInitialAction"
            :target-key="messageKey"
            @clear-action="utilityInitialAction = ''"
            @refresh="loadDashboard"
            @toast="showToast"
          />
        </template>
      </main>

      <!-- 底部导航栏 TabBar（统一 SVG 线性图标） -->
      <nav class="wx-tabbar" aria-label="底部主导航">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="tab-item spring-btn"
          :class="{ active: currentTab === tab.key }"
          :aria-current="currentTab === tab.key ? 'page' : undefined"
          @click="selectTab(tab.key)"
        >
          <svg class="tab-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path v-for="(d, idx) in tab.paths" :key="idx" :d="d" />
          </svg>
          <span class="tab-label">{{ tab.label }}</span>
        </button>
      </nav>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import api from './api'
import { signInStudent } from './session'
import TabHome from './components/TabHome.vue'
import TabHabitRadar from './components/TabHabitRadar.vue'
import TabRepair from './components/TabRepair.vue'
import TabUtility from './components/TabUtility.vue'
import TabAffair from './components/TabAffair.vue'

const tabs = [
  {
    key: 'home',
    label: '首页',
    paths: ['M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1V9.5z']
  },
  {
    key: 'habit',
    label: '习惯',
    paths: [
      'M12 2l8.5 5v10L12 22l-8.5-5V7L12 2z',
      'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z'
    ]
  },
  {
    key: 'repair',
    label: '报修',
    paths: [
      'M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z'
    ]
  },
  {
    key: 'utility',
    label: '水电',
    paths: ['M13 2L3 14h9l-1 8 10-12h-9l1-8z']
  },
  {
    key: 'affair',
    label: '宿务',
    paths: [
      'M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4'
    ]
  }
]

const currentDate = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'Asia/Shanghai' }).format(new Date())
const currentStudentId = ref(localStorage.getItem('dorm_student_token') ? Number(localStorage.getItem('dorm_student_id')) || null : null)
const loginForm = reactive({ username: '', password: '' })
const loginError = ref('')
const loginLoading = ref(false)
const currentTab = ref('home')
const utilityInitialAction = ref('')
const messageKey = ref('')
const loading = ref(true)
const loadError = ref(false)
const dash = ref({})
const isHeaderCollapsed = ref(false)

const toastInfo = reactive({
  text: '',
  type: 'success'
})

const scrollBodyRef = ref(null)
const sentinelRef = ref(null)
let sentinelObserver = null

const navTitle = computed(() => {
  const found = tabs.find(t => t.key === currentTab.value)
  return found ? found.label : '智慧宿舍'
})

const showToast = (payload) => {
  if (typeof payload === 'object' && payload !== null) {
    toastInfo.text = payload.msg || ''
    toastInfo.type = payload.type || 'success'
  } else {
    toastInfo.text = String(payload || '')
    toastInfo.type = 'success'
  }
  if (navigator.vibrate) navigator.vibrate(15)
  setTimeout(() => {
    toastInfo.text = ''
  }, 2400)
}

const selectTab = (key) => {
  utilityInitialAction.value = ''
  messageKey.value = ''
  currentTab.value = key
  if (scrollBodyRef.value) {
    scrollBodyRef.value.scrollTop = 0
  }
}

const handleNavigate = async (target) => {
  messageKey.value = target && typeof target === 'object' ? target.key || '' : ''
  if (typeof target === 'object' && target !== null) {
    utilityInitialAction.value = target.action || ''
    currentTab.value = target.tab || 'home'
  } else {
    utilityInitialAction.value = ''
    currentTab.value = target
  }
  if (scrollBodyRef.value) {
    scrollBodyRef.value.scrollTop = 0
  }
  if (target && typeof target === 'object' && target.key && !target.key.startsWith('RESIDENCE:')) {
    await nextTick()
    const record = Array.from(scrollBodyRef.value?.querySelectorAll('[data-message-key]') || [])
      .find(item => item.dataset.messageKey === target.key)
    if (record) record.scrollIntoView({ behavior: 'smooth', block: 'center' })
    else showToast({ msg: '相关记录不可用或已不在当前列表', type: 'info' })
  }
}

const loadDashboard = async () => {
  loadError.value = false
  try {
    const res = await api.get(`/auth/student-dashboard/${currentStudentId.value}`)
    dash.value = res.data || {}
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const clearSession = () => {
  localStorage.removeItem('dorm_student_token')
  localStorage.removeItem('dorm_student_id')
  currentStudentId.value = null
  dash.value = {}
}

const login = async () => {
  loginLoading.value = true
  loginError.value = ''
  try {
    currentStudentId.value = await signInStudent(api, localStorage, loginForm.username, loginForm.password)
    loginForm.password = ''
    loading.value = true
    await loadDashboard()
  } catch (error) {
    loginError.value = error.response?.data?.message || error.message || '登录失败'
  } finally {
    loginLoading.value = false
  }
}

const logout = async () => {
  try { await api.post('/auth/logout') } catch (_) { /* local logout also works offline */ }
  clearSession()
}

const sessionExpired = () => {
  clearSession()
  loginError.value = '登录已失效，请重新登录'
}

onMounted(() => {
  window.addEventListener('student-session-expired', sessionExpired)
  if (currentStudentId.value) {
  loading.value = true
  loadDashboard()
  }
  if (window.IntersectionObserver && sentinelRef.value && scrollBodyRef.value) {
    sentinelObserver = new IntersectionObserver(
      (entries) => {
        isHeaderCollapsed.value = !entries[0].isIntersecting
      },
      { root: scrollBodyRef.value, threshold: 0 }
    )
    sentinelObserver.observe(sentinelRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('student-session-expired', sessionExpired)
  if (sentinelObserver) {
    sentinelObserver.disconnect()
  }
})
</script>

<style>
.login-stage { min-height: 100dvh; display: grid; place-items: center; padding: 24px; background: var(--bg-canvas); }
.student-login { width: min(100%, 360px); padding: 32px; border-radius: 24px; background: white; box-shadow: 0 12px 40px rgb(29 29 31 / 8%); }
.student-login h1 { font-size: 25px; margin: 0 0 8px; }.student-login p { color: #6e6e73; margin-bottom: 22px; }
.student-login label { display: grid; gap: 7px; margin: 16px 0; font-size: 14px; }.student-login input { padding: 12px; border: 1px solid #c9c9ce; border-radius: 9px; font: inherit; }
.student-login button { width: 100%; border: 0; border-radius: 10px; padding: 12px; color: white; background: #176bd6; font: inherit; cursor: pointer; }.student-login .login-error { color: #b42318; margin: 10px 0; }
.student-logout { align-self: flex-end; margin: 0 0 8px; border: 0; background: transparent; color: #555; cursor: pointer; }
.mp-stage { height: 100dvh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 14px 20px 18px; overflow: hidden; }
.demo-switcher { display: flex; align-items: center; gap: 5px; margin-bottom: 12px; max-width: 100%; flex-shrink: 0; padding: 4px; background: #e4e4e9; border-radius: 12px; overflow-x: auto; scrollbar-width: none; }
.demo-select-label { display: none; }
.demo-title { color: var(--text-muted); font-size: 11px; padding: 0 10px; white-space: nowrap; }
.switch-chip { border: 0; background: transparent; color: var(--text-secondary); padding: 7px 11px; border-radius: 9px; font-size: 12px; white-space: nowrap; }
.switch-chip.active { background: white; color: var(--text-primary); box-shadow: 0 1px 5px rgb(29 29 31 / 6%); }
.phone-frame { width: 430px; max-width: 100%; flex: 1; min-height: 0; max-height: 900px; background: var(--bg-canvas); border: 1px solid #dedee3; border-radius: 32px; box-shadow: 0 12px 40px rgb(29 29 31 / 5%); position: relative; display: flex; flex-direction: column; overflow: hidden; }
.wx-navbar { min-height: 53px; padding: 12px 22px 7px; display: flex; align-items: center; justify-content: space-between; gap: 14px; background: var(--bg-canvas); z-index: 30; flex-shrink: 0; }
.wx-navbar.scrolled { background: rgb(245 245 247 / 92%); }
.nav-title, .nav-user { font-size: 12px; color: var(--text-muted); }
.nav-user { max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-capsule-toast { position: absolute; top: 58px; left: 50%; transform: translateX(-50%); z-index: 90; background: #29292d; color: white; padding: 11px 16px; border-radius: 16px; font-size: 13px; display: flex; align-items: center; gap: 9px; box-shadow: 0 5px 20px rgb(29 29 31 / 14%); width: max-content; max-width: calc(100% - 32px); line-height: 1.5; }
.toast-svg { width: 18px; height: 18px; flex-shrink: 0; }
.draw-check { fill: none; stroke: #8cdaa5; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
.info-svg { color: #99caff; }.warn-svg { color: #f3ce84; }.err-svg { color: #ffaaa4; }
.capsule-toast-enter-active, .capsule-toast-leave-active { transition: transform 200ms ease, opacity 200ms ease; }
.capsule-toast-enter-from, .capsule-toast-leave-to { opacity: 0; transform: translate(-50%, -8px); }
.mp-scroll-body { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 5px 22px 18px; scrollbar-width: none; }
.mp-scroll-body::-webkit-scrollbar { width: 0; }
.scroll-sentinel { height: 1px; }
.large-header { padding: 2px 0 22px; }
.greet-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.large-header .greet { color: var(--text-primary); font-size: 31px; font-weight: 600; line-height: 1.25; letter-spacing: -1px; }
.large-header .sub-meta { color: var(--text-muted); font-size: 13px; margin-top: 8px; }
.status-badge { font-size: 11px; font-weight: 400; color: var(--text-muted); }
.status-badge.unalloc { color: var(--status-warning); }
.skeleton-wrap { padding-top: 5px; }
.shimmer { background: #e7e7ec; border-radius: var(--radius-card); animation: pulseOpacity 1.4s ease-in-out infinite; }
.sk-hero { height: 190px; margin-bottom: 22px; }.sk-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 22px; }.sk-card { height: 68px; }.sk-list { height: 160px; }
@keyframes pulseOpacity { 0%,100% { opacity: .5; } 50% { opacity: .9; } }
.error-card { background: var(--bg-surface); border-radius: var(--radius-card); padding: 30px 22px; text-align: center; margin-top: 20px; }
.err-title { font-size: 18px; font-weight: 600; }.err-desc { font-size: 14px; color: var(--text-muted); margin: 10px 0 18px; }
.retry-btn { background: var(--accent-primary); color: white; border: 0; padding: 11px 22px; border-radius: 12px; font-size: 14px; }
.wx-tabbar { display: flex; align-items: center; justify-content: space-around; flex-shrink: 0; min-height: 64px; margin: 0 14px 12px; padding: 7px 3px; border-radius: 24px; background: rgb(255 255 255 / 94%); box-shadow: 0 4px 18px rgb(29 29 31 / 5%); z-index: 40; }
.tab-item { display: flex; flex-direction: column; align-items: center; gap: 4px; border: 0; background: transparent; color: var(--text-muted); padding: 5px 11px; min-height: 46px; font-size: 11px; border-radius: 17px; }
.tab-item.active { color: var(--accent-primary); }
.tab-svg { width: 20px; height: 20px; }
@supports (backdrop-filter: blur(15px)) { .wx-tabbar, .wx-navbar.scrolled { backdrop-filter: blur(15px); } }
@media (max-width: 480px) { .mp-stage { padding: 0; }.demo-switcher { width: 100%; overflow-x: auto; scrollbar-width: none; border-radius: 0; margin: 0; padding: 5px 9px; background: #eeeef1; }.demo-title { display: none; }.switch-chip { font-size: 11px; padding: 6px 10px; }.phone-frame { width: 100%; border: 0; box-shadow: none; border-radius: 0; max-height: none; }.wx-navbar { padding-top: calc(10px + env(safe-area-inset-top, 0px)); }.wx-tabbar { margin-bottom: max(10px, env(safe-area-inset-bottom, 0px)); } }
@media (max-width: 640px) { .demo-title, .switch-chip { display: none; }.demo-select-label { display: flex; align-items: center; gap: 12px; padding: 2px 8px; font-size: 11px; color: var(--text-muted); }.demo-select-label select { border: 0; background: transparent; color: var(--text-secondary); padding: 5px 16px 5px 4px; max-width: 230px; font-size: 12px !important; } }
@media (max-width: 360px) { .mp-scroll-body { padding-right: 17px; padding-left: 17px; }.wx-navbar { padding-left: 17px; padding-right: 17px; }.tab-item { padding-right: 8px; padding-left: 8px; } }
</style>
