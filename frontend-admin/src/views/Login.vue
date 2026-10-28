<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧：精美校园与宿舍实景封面大图展示区 -->
      <aside class="login-cover-pane" aria-label="宿里智慧校园画卷">
        <div class="cover-image-layer" />
        <div class="cover-overlay">
          <div class="cover-top-brand">
            <img class="cover-logo" src="/suli-icon-v2.png" alt="宿里 Logo" />
            <div class="cover-brand-meta">
              <span class="cover-brand-name">宿里 · 智慧宿舍</span>
              <span class="cover-brand-tag">数字后勤工作空间</span>
            </div>
          </div>

          <div class="cover-hero-content">
            <div class="cover-pill">
              <span class="pill-dot"></span>
              <span>智慧高校后勤协同平台 · 宿里 v2.0</span>
            </div>
            <h1 class="cover-title">让宿舍生活更简单<br />更有温度</h1>
            <p class="cover-desc">
              深度贯通房态全景图谱、AI 多维生活习惯协同分配、绿色水电能耗管控、文明寝室量化评分与一站式调宿审批协同。
            </p>

            <div class="cover-feature-grid">
              <div class="feature-card">
                <div class="feature-icon">✨</div>
                <div class="feature-text">
                  <strong>智能分配</strong>
                  <span>四维习惯协同匹配</span>
                </div>
              </div>
              <div class="feature-card">
                <div class="feature-icon">⚡</div>
                <div class="feature-text">
                  <strong>绿色能耗</strong>
                  <span>水电账单自动结算</span>
                </div>
              </div>
              <div class="feature-card">
                <div class="feature-icon">🛡️</div>
                <div class="feature-text">
                  <strong>安全门禁</strong>
                  <span>访客通行动态核验</span>
                </div>
              </div>
            </div>
          </div>

          <div class="cover-footer">
            <span>实景封面：现代智慧大学示范园区</span>
            <span>© 宿里 · 智慧校园后勤中心</span>
          </div>
        </div>
      </aside>

      <!-- 右侧：Apple 极简风格管理登录面板 -->
      <main class="login-form-pane">
        <div class="form-container">
          <div class="login-header">
            <img class="logo-badge" src="/suli-icon-v2.png" alt="宿里 Logo" />
            <h2 class="heading-balance">欢迎登录管理后台</h2>
            <p class="body-pretty">请输入您的工号或管理账号，开始今天的高效宿务工作</p>
          </div>

          <!-- 演示账号快捷填充 -->
          <div class="quick-demo-accounts">
            <div class="demo-label">演示账号一键填入：</div>
            <div class="demo-chips">
              <button
                v-for="acc in demoAccounts"
                :key="acc.username"
                type="button"
                class="demo-chip"
                :class="{ active: form.username === acc.username }"
                :title="acc.desc"
                @click="fillDemo(acc)"
              >
                <span class="chip-title">{{ acc.title }}</span>
                <span class="chip-code">{{ acc.username }}</span>
              </button>
            </div>
          </div>

          <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
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
              <div v-if="errorMsg" class="inline-error" role="alert">
                <el-icon class="error-icon"><CircleCloseFilled /></el-icon>
                <span>{{ errorMsg }}</span>
              </div>
            </transition>

            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录验证中...' : '进入管理工作空间' }}
            </el-button>
          </el-form>

          <div class="form-footer">
            <span class="footer-note">内部管理系统 · 请妥善保管权限账号</span>
            <a class="student-entry-link" href="http://localhost:5174" target="_blank" rel="noopener">
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
  { username: 'admin', password: '123456', title: '系统管理员', desc: '后勤主任' },
  { username: 'dm01', password: '123456', title: '楼栋宿管员', desc: '紫荆1号楼' },
  { username: 'worker01', password: '123456', title: '维修工程师', desc: '水电维修' },
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
}

.login-shell {
  width: 100%;
  max-width: 1180px;
  min-height: 680px;
  background: #ffffff;
  border-radius: 24px;
  overflow: hidden;
  display: flex;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

/* 左侧封面大图展示区 */
.login-cover-pane {
  flex: 1.15;
  position: relative;
  min-height: 680px;
  background-color: #0f172a;
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
  transition: transform 6s cubic-bezier(0.16, 1, 0.3, 1);
}

.login-shell:hover .cover-image-layer {
  transform: scale(1.06);
}

.cover-overlay {
  position: relative;
  z-index: 2;
  inset: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 42px 48px;
  background: linear-gradient(
    145deg,
    rgba(15, 23, 42, 0.55) 0%,
    rgba(15, 23, 42, 0.85) 100%
  );
  backdrop-filter: blur(2px);
  color: #ffffff;
}

.cover-top-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.cover-brand-meta {
  display: flex;
  flex-direction: column;
}

.cover-brand-name {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.3px;
  color: #ffffff;
}

.cover-brand-tag {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.5px;
}

.cover-hero-content {
  margin: auto 0;
  padding: 24px 0;
}

.cover-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.92);
  margin-bottom: 20px;
}

.pill-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34c759;
  box-shadow: 0 0 8px #34c759;
}

.cover-title {
  font-size: 38px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -1px;
  color: #ffffff;
  margin-bottom: 14px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
}

.cover-desc {
  font-size: 14px;
  line-height: 1.65;
  color: rgba(255, 255, 255, 0.82);
  max-width: 460px;
  margin-bottom: 28px;
}

.cover-feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.feature-icon {
  font-size: 18px;
}

.feature-text {
  display: flex;
  flex-direction: column;
}

.feature-text strong {
  font-size: 12.5px;
  color: #ffffff;
  font-weight: 600;
}

.feature-text span {
  font-size: 10.5px;
  color: rgba(255, 255, 255, 0.65);
  margin-top: 1px;
}

.cover-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: 18px;
}

/* 右侧表单操作区 */
.login-form-pane {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 44px;
  background: #ffffff;
}

.form-container {
  width: 100%;
  max-width: 380px;
}

.login-header {
  text-align: left;
  margin-bottom: 24px;
}

.logo-badge {
  width: 44px;
  height: 44px;
  border-radius: 11px;
  display: inline-block;
  object-fit: cover;
  margin-bottom: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.login-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}

.login-header p {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.5;
}

/* 快速账号填充 */
.quick-demo-accounts {
  margin-bottom: 20px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8f8fa;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.demo-label {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-bottom: 8px;
  font-weight: 500;
}

.demo-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.demo-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 7px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #ffffff;
  color: var(--text-secondary);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.16s ease;
  text-decoration: none !important;
}

.demo-chip:hover {
  background: #f0f0f3;
  color: var(--text-primary);
  border-color: rgba(0, 0, 0, 0.15);
}

.demo-chip.active {
  background: rgba(0, 113, 227, 0.08);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
  font-weight: 600;
}

.chip-title {
  font-weight: 500;
}

.chip-code {
  font-size: 10px;
  opacity: 0.7;
}

.inline-error {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--status-danger);
  background: var(--status-danger-bg);
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 14px;
  border: 1px solid rgba(255, 59, 48, 0.15);
}

.error-icon {
  font-size: 15px;
  flex-shrink: 0;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 14.5px;
  font-weight: 600;
  border-radius: 10px;
  margin-top: 4px;
  box-shadow: 0 4px 12px rgba(0, 113, 227, 0.25);
  transition: all 0.16s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(0, 113, 227, 0.32);
}

.login-btn:active {
  transform: scale(0.98);
}

.form-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.footer-note {
  font-size: 11px;
  color: var(--text-muted);
}

.student-entry-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--accent-primary);
  text-decoration: none !important;
  font-weight: 500;
  transition: opacity 0.16s ease;
}

.student-entry-link:hover {
  opacity: 0.8;
  text-decoration: none !important;
}

/* 响应式适配 */
@media (max-width: 1024px) {
  .login-shell {
    flex-direction: column;
    max-width: 500px;
    min-height: auto;
  }

  .login-cover-pane {
    min-height: 240px;
    flex: none;
  }

  .cover-overlay {
    padding: 24px;
  }

  .cover-hero-content {
    margin: 12px 0 0;
    padding: 0;
  }

  .cover-title {
    font-size: 24px;
  }

  .cover-desc,
  .cover-feature-grid,
  .cover-footer {
    display: none;
  }

  .login-form-pane {
    padding: 32px 24px;
  }
}
</style>
