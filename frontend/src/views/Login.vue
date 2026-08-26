<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <div class="brand-panel">
      <div class="brand-inner">
        <div class="brand-logo">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <circle cx="12" cy="12" r="3.5"/>
            <ellipse cx="12" cy="12" rx="9" ry="4" transform="rotate(30 12 12)"/>
            <ellipse cx="12" cy="12" rx="9" ry="4" transform="rotate(-30 12 12)"/>
          </svg>
        </div>
        <h1 class="brand-title">仪器共享平台</h1>
        <p class="brand-subtitle">高效管理学院仪器与借用流程</p>

        <ul class="brand-features">
          <li>
            <span class="feature-icon"><el-icon><Monitor /></el-icon></span>
            <span>设备资产管理</span>
          </li>
          <li>
            <span class="feature-icon"><el-icon><Calendar /></el-icon></span>
            <span>在线预约与审批</span>
          </li>
          <li>
            <span class="feature-icon"><el-icon><TrendCharts /></el-icon></span>
            <span>多维度数据统计</span>
          </li>
        </ul>

        <div class="brand-deco"></div>
      </div>
    </div>

    <!-- 右侧登录区 -->
    <div class="form-panel">
      <div class="form-card">
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-subtitle">请使用广州大学统一认证账号登录</p>
        </div>

        <el-alert type="info" :closable="false" show-icon class="login-alert">
          <template #title>首次使用？</template>
          输入广州大学CAS统一认证账号密码即可登录，系统将自动创建账户。
        </el-alert>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" class="login-form">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="学工号 / 用户名" :prefix-icon="User" size="large" clearable/>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password @keyup.enter="handleLogin"/>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="login-btn" :loading="logging" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="divider"><span>或</span></div>

        <el-button size="large" class="cas-btn" :loading="logging" @click="handleCasLogin">
          广州大学 CAS 统一认证登录
        </el-button>

        <div class="forgot-link">
          <el-popover placement="top" :width="280" trigger="click">
            <template #reference>
              <el-link type="info" :underline="false">忘记密码？</el-link>
            </template>
            <div style="font-size:13px;line-height:1.7">
              <p><strong>本地账户：</strong>请使用CAS登录重置密码或联系管理员。</p>
              <p><strong>CAS账户：</strong>使用下方「CAS统一认证登录」，系统自动同步密码。</p>
              <p style="color:#909399;font-size:12px;margin-top:4px">CAS密码即学校统一认证密码。</p>
            </div>
          </el-popover>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock, Monitor, Calendar, TrendCharts } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { authApi } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)
const logging = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  const token = route.query.token || route.query.uniToken || route.query.access_token || route.query.ticket
  if (token) {
    try {
      const res = await authApi.casLogin(token, '')
      userStore.loginCas(res.data)
      router.push('/dashboard')
    } catch (e) {
      ElMessage.error('CAS登录失败')
      router.replace({ query: {} })
    }
  }
})

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  logging.value = true
  try {
    await userStore.login(form)
    router.push('/')
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || ''
    if (msg.includes('CAS') || msg.includes('统一认证')) {
      try {
        const res = await authApi.casCredentialLogin(form.username, form.password)
        userStore.loginCas(res.data)
        router.push('/dashboard')
      } catch (e2) {
        ElMessage.error(e2?.response?.data?.msg || e2?.message || '登录失败')
      }
    } else {
      ElMessage.error(msg || '登录失败')
    }
  } finally {
    logging.value = false
  }
}

async function handleCasLogin() {
  logging.value = true
  try {
    const res = await authApi.casCredentialLogin(form.username, form.password)
    userStore.loginCas(res.data)
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || 'CAS登录失败')
  } finally {
    logging.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: #f6f8fb;
  background-image: radial-gradient(rgba(37, 99, 235, 0.07) 1px, transparent 1px);
  background-size: 26px 26px;
}

/* ===== 左侧品牌区 ===== */
.brand-panel {
  flex: 1.1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 20%, rgba(255,255,255,0.14), transparent 40%),
    linear-gradient(150deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%);
  color: #fff;
}

.brand-inner {
  position: relative;
  z-index: 2;
  max-width: 420px;
  width: 100%;
}

.brand-logo {
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: rgba(255,255,255,0.16);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 800;
  margin-bottom: 28px;
  box-shadow: 0 16px 40px rgba(0,0,0,0.18);
}

.brand-title {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 1px;
  margin: 0 0 10px;
}

.brand-subtitle {
  font-size: 15px;
  opacity: 0.85;
  margin: 0 0 36px;
}

.brand-features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 14px;
  font-weight: 500;
  background: rgba(255,255,255,0.1);
  padding: 14px 18px;
  border-radius: 16px;
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.12);
}

.feature-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(255,255,255,0.18);
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-deco {
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  right: -80px;
  bottom: -80px;
  border: 1px solid rgba(255,255,255,0.16);
  background: radial-gradient(circle, rgba(255,255,255,0.08), transparent 70%);
}

/* ===== 右侧登录区 ===== */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-card {
  width: 100%;
  max-width: 420px;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(16px);
  border-radius: 24px;
  padding: 40px 36px 30px;
  box-shadow: 0 24px 70px rgba(31, 45, 61, 0.12), 0 0 0 1px rgba(255,255,255,0.7) inset;
}

.form-header {
  margin-bottom: 24px;
}

.form-title {
  font-size: 24px;
  font-weight: 800;
  color: #111827;
  margin: 0 0 6px;
}

.form-subtitle {
  font-size: 13px;
  color: #6b7280;
  margin: 0;
}

.login-alert {
  border-radius: 12px;
  margin-bottom: 22px;
}

.login-form {
  margin-top: 2px;
}

.login-btn {
  width: 100%;
  font-weight: 600;
  letter-spacing: 2px;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.24);
}

.divider {
  display: flex;
  align-items: center;
  margin: 20px 0;
  color: #9ca3af;
  font-size: 13px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #eef2f7;
}

.divider span {
  padding: 0 14px;
}

.cas-btn {
  width: 100%;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  color: #2563eb;
  font-weight: 500;
  border-radius: 12px;
}

.cas-btn:hover {
  background: #dbeafe;
  border-color: #bfdbfe;
  color: #1d4fd7;
}

.forgot-link {
  text-align: center;
  margin-top: 16px;
}

/* ===== 响应式 ===== */
@media (max-width: 860px) {
  .login-page {
    flex-direction: column;
  }

  .brand-panel {
    padding: 32px 24px;
    flex: none;
  }

  .brand-inner {
    max-width: 100%;
  }

  .brand-features {
    display: none;
  }

  .brand-title {
    font-size: 26px;
  }

  .form-panel {
    padding: 24px 16px;
  }

  .form-card {
    padding: 28px 22px 24px;
  }
}
</style>
