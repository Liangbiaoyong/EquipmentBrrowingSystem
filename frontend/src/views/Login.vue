<template>
  <div class="login-page">
    <div class="login-bg"></div>

    <div class="login-wrapper">
      <div class="login-card">
        <div class="lc-header">
          <div class="lc-icon">仪</div>
          <h1 class="lc-title">仪器共享平台</h1>
          <p class="lc-subtitle">广州大学建筑学院 · 仪器共享平台</p>
        </div>

        <el-alert type="info" :closable="false" show-icon class="lc-alert">
          <template #title>首次使用？</template>
          输入您的<strong>广州大学CAS统一认证</strong>账号密码即可登录，系统将自动创建账户。
        </el-alert>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" class="lc-form">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="学工号 / 用户名" :prefix-icon="User" size="large" clearable/>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password @keyup.enter="handleLogin"/>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="lc-btn-primary" :loading="logging" @click="handleLogin">登 录</el-button>
          </el-form-item>
        </el-form>

        <div class="lc-divider"><span>或</span></div>

        <el-button size="large" class="lc-btn-cas" :loading="logging" @click="handleCasLogin">
          广州大学 CAS 统一认证登录
        </el-button>

        <div class="lc-forgot">
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
import { User, Lock } from '@element-plus/icons-vue'
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
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: #f6f8fb;
  background-image: radial-gradient(rgba(37, 99, 235, 0.08) 1px, transparent 1px);
  background-size: 26px 26px;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 20%, rgba(37, 99, 235, 0.10), transparent 42%),
    radial-gradient(circle at 85% 75%, rgba(250, 204, 21, 0.16), transparent 42%),
    linear-gradient(160deg, #eef4ff 0%, #f8fafc 50%, #f1f5f9 100%);
}

.login-wrapper {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 24px;
}

.login-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(16px);
  border-radius: 22px;
  padding: 40px 34px 30px;
  box-shadow: 0 24px 70px rgba(31, 45, 61, 0.14), 0 0 0 1px rgba(255, 255, 255, 0.7) inset;
  transform: translateY(0);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.login-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 30px 80px rgba(31, 45, 61, 0.18);
}

.lc-header {
  text-align: center;
  margin-bottom: 26px;
}

.lc-icon {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 26px;
  font-weight: 800;
  margin-bottom: 16px;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.28);
}

.lc-title {
  font-size: 22px;
  font-weight: 800;
  color: #111827;
  margin: 0;
  letter-spacing: 0.5px;
}

.lc-subtitle {
  font-size: 13px;
  color: #6b7280;
  margin: 8px 0 0;
}

.lc-alert {
  margin-bottom: 22px;
  border-radius: 12px;
}

.lc-form {
  margin-top: 4px;
}

.lc-btn-primary {
  width: 100%;
  font-weight: 600;
  letter-spacing: 2px;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.24);
}

.lc-divider {
  display: flex;
  align-items: center;
  margin: 20px 0;
  color: #9ca3af;
  font-size: 13px;
}

.lc-divider::before,
.lc-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #eef2f7;
}

.lc-divider span {
  padding: 0 14px;
}

.lc-btn-cas {
  width: 100%;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  color: #2563eb;
  font-weight: 500;
  border-radius: 12px;
}

.lc-btn-cas:hover {
  background: #dbeafe;
  border-color: #bfdbfe;
  color: #1d4fd7;
}

.lc-forgot {
  text-align: center;
  margin-top: 16px;
}

@media (max-width: 480px) {
  .login-card {
    padding: 30px 22px 24px;
  }
  .lc-title {
    font-size: 19px;
  }
}
</style>
