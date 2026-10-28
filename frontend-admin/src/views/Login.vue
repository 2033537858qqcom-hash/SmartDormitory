<template>
  <div class="login-page">
    <div class="login-card surface-card">
      <div class="login-header">
        <img class="logo-badge" src="/suli-icon-v2.png" alt="" />
        <h2 class="heading-balance">宿里</h2>
        <p class="body-pretty">智慧宿舍 · 后勤工作空间</p>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="登录账号">
          <el-input v-model="form.username" placeholder="请输入工号/管理员账号" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item label="登录密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" size="large" prefix-icon="Lock" />
        </el-form-item>

        <p v-if="errorMsg" class="inline-error" role="alert">{{ errorMsg }}</p>

        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登录管理后台
        </el-button>
      </el-form>
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
  username: '',
  password: ''
})

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
  background-color: var(--bg-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-card {
  width: 420px;
  max-width: 100%;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  padding: 42px 36px;
  box-shadow: var(--shadow-md);
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.logo-badge {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: inline-block;
  object-fit: cover;
  margin-bottom: 12px;
}
.login-header h2 {
  font-size: 27px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.login-header p {
  font-size: 13px;
  color: var(--text-muted);
}
.quick-roles {
  margin: 4px 0 18px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.quick-roles .label {
  font-size: 12px;
  color: var(--text-muted);
}
.role-chip {
  cursor: pointer;
}
.inline-error {
  font-size: 13px;
  color: var(--status-danger);
  background: var(--status-danger-bg);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  margin-bottom: 14px;
}
.login-btn {
  width: 100%;
  font-weight: 600;
}
</style>
