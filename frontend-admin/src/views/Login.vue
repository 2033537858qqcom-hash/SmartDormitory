<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧：Apple 风格现代校园实景画卷 -->
      <aside class="login-cover-pane" aria-label="宿里智慧校园画卷">
        <div class="cover-image-layer" />
        <div class="cover-overlay">
          <!-- 顶部品牌 -->
          <div class="cover-brand-header">
            <img class="cover-logo" src="/suli-icon-v2.png" alt="宿里 Logo" />
            <div class="cover-brand-text">
              <span class="brand-title">宿里 · 智慧宿舍</span>
              <span class="brand-subtitle">数字后勤协同系统</span>
            </div>
          </div>

          <!-- 主体标语 -->
          <div class="cover-center-content">
            <h1 class="cover-headline">让宿舍生活更简单<br />更有温度</h1>
            <p class="cover-subtext">
              深度贯通房态全景图谱、多维生活习惯协同分配、绿色水电能耗管控、文明寝室量化评分与一站式调宿审批协同。
            </p>
          </div>

          <!-- 底部版权/摄影标注 -->
          <div class="cover-bottom-bar">
            <span>实景封面：现代智慧大学示范园区</span>
            <span>© 宿里 · 智慧后勤中心</span>
          </div>
        </div>
      </aside>

      <!-- 右侧：Apple 极简原生交互登录面板 -->
      <main class="login-form-pane">
        <div class="form-wrapper">
          <div class="login-intro">
            <div class="intro-eyebrow">管理中心</div>
            <h2 class="intro-title">登录宿里管理后台</h2>
            <p class="intro-desc">使用您的工号或管理员账号安全登录</p>
          </div>

          <!-- Apple 原生分段选择器 (Segmented Control) 快速切换演示账号 -->
          <div class="segmented-wrapper">
            <div class="segmented-caption">快速体验角色：</div>
            <div class="apple-segmented" role="tablist" aria-label="演示角色切换">
              <button
                v-for="acc in demoAccounts"
                :key="acc.username"
                type="button"
                class="segmented-tab"
                :class="{ active: form.username === acc.username }"
                :title="acc.desc"
                @click="fillDemo(acc)"
              >
                <span class="tab-label">{{ acc.title }}</span>
                <span class="tab-code">{{ acc.username }}</span>
              </button>
            </div>
          </div>

          <el-form :model="form" label-position="top" class="apple-login-form" @submit.prevent="handleLogin">
            <el-form-item label="登录账号">
              <el-input
                v-model="form.username"
                placeholder="请输入工号 / 管理员账号"
                size="large"
                prefix-icon="User"
                clearable
              />
            </el-form-item>
            <el-form-item label="登录密码">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="请输入密码（演示密码 123456）"
                size="large"
                prefix-icon="Lock"
              />
            </el-form-item>

            <transition name="el-zoom-in-top">
              <div v-if="errorMsg" class="apple-alert-error" role="alert">
                <el-icon><CircleCloseFilled /></el-icon>
                <span>{{ errorMsg }}</span>
              </div>
            </transition>

            <el-button
              type="primary"
              size="large"
              class="apple-submit-btn"
              :loading="loading"
              @click="handleLogin"
            >
              {{ loading ? '正在验证身份...' : '进入管理工作空间' }}
            </el-button>
          </el-form>

          <div class="login-footer">
            <span class="footer-hint">高校内部专有后勤系统 · 严禁违规越权访问</span>
            <a class="student-link" href="http://localhost:5174" target="_blank" rel="noopener">
              打开学生端 Vite H5 <el-icon><TopRight /></el-icon>
            </a>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: 'admin',
  password: '123456'
})

const demoAccounts = [
  { username: 'admin', password: '123456', title: '管理员', desc: '后勤系统主管' },
  { username: 'dm01', password: '123456', title: '宿管员', desc: '紫荆1号楼' },
  { username: 'worker01', password: '123456', title: '维修工', desc: '水电专职' },
  { username: 'counselor01', password: '123456', title: '辅导员', desc: '只读权限' }
]

const fillDemo = (acc) => {
  form.username = acc.username
  form.password = acc.password
  errorMsg.value = ''
}

const handleLogin = async () => {
  errorMsg.value = ''
  if (!form.username || !form.password) {
    errorMsg.value = '请输入登录账号和密码'
    return
  }
  loading.value = true
  try {
    const res = await request.post('/auth/login', form)
    const user = res.data.user
    if (!['ADMIN', 'DORM_MANAGER', 'REPAIRMAN', 'COUNSELOR'].includes(user.role)) {
      errorMsg.value = '请使用管理人员账号登录'
      return
    }
    localStorage.setItem('dorm_admin_user', JSON.stringify(user))
    localStorage.setItem('dorm_admin_token', res.data.token || '')
    if (res.data.managedBuilding) {
      localStorage.setItem('dorm_managed_building', JSON.stringify(res.data.managedBuilding))
    } else {
      localStorage.removeItem('dorm_managed_building')
    }
    ElMessage.success(`欢迎回来，${user.realName}`)
    router.push(user.role === 'REPAIRMAN' ? '/repair-dispatch' : '/dashboard')
  } catch (e) {
    errorMsg.value = e.message || '登录失败，请检查账号或密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100dvh;
  background-color: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'PingFang SC', sans-serif;
  -webkit-font-smoothing: antialiased;
}

.login-shell {
  width: 100%;
  max-width: 1140px;
  min-height: 660px;
  background: #ffffff;
  border-radius: 28px;
  overflow: hidden;
  display: flex;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

/* ================= 左侧封面展示区 ================= */
.login-cover-pane {
  flex: 1.18;
  position: relative;
  min-height: 660px;
  background-color: #0b1120;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cover-image-layer {
  position: absolute;
  inset: 0;
  background-image: url('/login-cover.jpg');
  background-size: cover;
  background-position: center;
  transform: scale(1.02);
  transition: transform 7s cubic-bezier(0.16, 1, 0.3, 1);
}

.login-shell:hover .cover-image-layer {
  transform: scale(1.05);
}

.cover-overlay {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 44px 48px;
  background: linear-gradient(
    145deg,
    rgba(11, 17, 32, 0.45) 0%,
    rgba(11, 17, 32, 0.8) 100%
  );
  backdrop-filter: blur(2px);
  color: #ffffff;
}

.cover-brand-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-logo {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.25);
}

.cover-brand-text {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.3px;
  color: #ffffff;
}

.brand-subtitle {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.65);
  letter-spacing: 0.3px;
}

.cover-center-content {
  margin: auto 0;
  padding: 20px 0;
}

.cover-headline {
  font-size: 38px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.8px;
  color: #ffffff;
  margin-bottom: 16px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

.cover-subtext {
  font-size: 14.5px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.82);
  max-width: 460px;
}

.cover-bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: 16px;
}

/* ================= 右侧表单操作区 ================= */
.login-form-pane {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 44px;
  background: #ffffff;
}

.form-wrapper {
  width: 100%;
  max-width: 360px;
}

.login-intro {
  margin-bottom: 24px;
}

.intro-eyebrow {
  font-size: 11.5px;
  font-weight: 600;
  color: #0071e3;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 4px;
}

.intro-title {
  font-size: 25px;
  font-weight: 600;
  color: #1d1d1f;
  letter-spacing: -0.6px;
  line-height: 1.25;
  margin-bottom: 6px;
}

.intro-desc {
  font-size: 13px;
  color: #86868b;
  line-height: 1.45;
}

/* Apple 原生分段控制器 (Segmented Control) */
.segmented-wrapper {
  margin-bottom: 22px;
}

.segmented-caption {
  font-size: 11.5px;
  color: #86868b;
  margin-bottom: 6px;
  font-weight: 500;
}

.apple-segmented {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 2px;
  padding: 3px;
  background: rgba(118, 118, 128, 0.08);
  border-radius: 11px;
}

.segmented-tab {
  border: 0;
  background: transparent;
  padding: 6px 2px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
  text-decoration: none !important;
}

.segmented-tab:hover:not(.active) {
  background: rgba(255, 255, 255, 0.5);
}

.segmented-tab.active {
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08), 0 0 1px rgba(0, 0, 0, 0.12);
}

.tab-label {
  font-size: 12px;
  font-weight: 500;
  color: #1d1d1f;
  line-height: 1.3;
}

.segmented-tab.active .tab-label {
  color: #0071e3;
  font-weight: 600;
}

.tab-code {
  font-size: 10px;
  color: #86868b;
}

.segmented-tab.active .tab-code {
  color: rgba(0, 113, 227, 0.8);
}

/* 表单组件定制 */
.apple-login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.apple-login-form :deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 500;
  color: #1d1d1f;
  padding-bottom: 6px;
}

.apple-login-form :deep(.el-input__wrapper) {
  height: 44px;
  background: #fbfbfd;
  border-radius: 11px;
  border: 1px solid #d2d2d7;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: all 0.18s ease;
}

.apple-login-form :deep(.el-input__wrapper:hover) {
  border-color: #86868b;
  background: #ffffff;
}

.apple-login-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  border-color: #0071e3;
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.15) !important;
}

.apple-login-form :deep(.el-input__inner) {
  font-size: 13.5px;
  color: #1d1d1f;
}

.apple-alert-error {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: #ff3b30;
  background: rgba(255, 59, 48, 0.08);
  padding: 8px 12px;
  border-radius: 9px;
  margin-bottom: 14px;
  border: 1px solid rgba(255, 59, 48, 0.15);
}

.apple-submit-btn {
  width: 100%;
  height: 44px;
  font-size: 14.5px;
  font-weight: 500;
  border-radius: 11px;
  background: #0071e3;
  border: 0;
  margin-top: 4px;
  box-shadow: 0 2px 8px rgba(0, 113, 227, 0.24);
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

.apple-submit-btn:hover {
  background: #0077ed;
  box-shadow: 0 4px 14px rgba(0, 113, 227, 0.34);
  transform: translateY(-1px);
}

.apple-submit-btn:active {
  transform: scale(0.985);
}

.login-footer {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.footer-hint {
  font-size: 11px;
  color: #86868b;
}

.student-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #0071e3;
  text-decoration: none !important;
  font-weight: 500;
  transition: opacity 0.16s ease;
}

.student-link:hover {
  opacity: 0.8;
  text-decoration: none !important;
}

/* 响应式断点 */
@media (max-width: 1024px) {
  .login-shell {
    flex-direction: column;
    max-width: 480px;
    min-height: auto;
  }

  .login-cover-pane {
    min-height: 220px;
    flex: none;
  }

  .cover-overlay {
    padding: 24px;
  }

  .cover-center-content {
    margin: 10px 0 0;
    padding: 0;
  }

  .cover-headline {
    font-size: 23px;
  }

  .cover-subtext,
  .cover-bottom-bar {
    display: none;
  }

  .login-form-pane {
    padding: 32px 24px;
  }
}
</style>
