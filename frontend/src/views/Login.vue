<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-wrapper">
      <div class="login-card">
        <!-- 标题区 -->
        <div class="lc-header">
          <div class="lc-icon">
            <el-icon :size="28"><Monitor/></el-icon>
          </div>
          <h1 class="lc-title">建筑学院仪器共享平台</h1>
          <p class="lc-subtitle">广州大学仪器共享平台</p>
        </div>

        <!-- CAS 提示 -->
        <el-alert type="info" :closable="false" show-icon class="lc-alert">
          <template #title>首次使用？</template>
          输入您的<strong>广州大学CAS统一认证</strong>账号密码即可登录，系统将自动创建账户。
        </el-alert>

        <!-- 登录表单 -->
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" class="lc-form">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="学工号 / 用户名" :prefix-icon="User" size="large" clearable/>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password @keyup.enter="handleLogin"/>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="lc-btn-primary" :loading="logging" @click="handleLogin" round>登 录</el-button>
          </el-form-item>
        </el-form>

        <!-- 分割线 -->
        <div class="lc-divider"><span>或</span></div>

        <!-- CAS 登录按钮 -->
        <el-button size="large" class="lc-btn-cas" :loading="logging" @click="handleCasLogin" round>
          广州大学 CAS 统一认证登录
        </el-button>

        <!-- 忘记密码 -->
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
import { reactive,ref,onMounted } from 'vue';import { useRouter,useRoute } from 'vue-router';import { User,Lock,Monitor } from '@element-plus/icons-vue';import { useUserStore } from '@/store/user';import { authApi } from '@/api/auth';import { ElMessage } from 'element-plus'
const router=useRouter();const route=useRoute();const userStore=useUserStore();const formRef=ref(null);const logging=ref(false)
const form=reactive({username:'',password:''})
const rules={username:[{required:true,message:'请输入用户名',trigger:'blur'}],password:[{required:true,message:'请输入密码',trigger:'blur'}]}

onMounted(async()=>{
  const token=route.query.token||route.query.uniToken||route.query.access_token||route.query.ticket
  if(token){try{const res=await authApi.casLogin(token,'');userStore.loginCas(res.data);router.push('/dashboard')}catch(e){ElMessage.error('CAS登录失败');router.replace({query:{}})}}
})

async function handleLogin(){
  const valid=await formRef.value.validate().catch(()=>false);if(!valid)return;logging.value=true
  try{await userStore.login(form);router.push('/')}catch(e){
    const msg=e?.response?.data?.msg||e?.message||''
    if(msg.includes('CAS')||msg.includes('统一认证')){try{const res=await authApi.casCredentialLogin(form.username,form.password);userStore.loginCas(res.data);router.push('/dashboard')}catch(e2){ElMessage.error(e2?.response?.data?.msg||e2?.message||'登录失败')}}
    else{ElMessage.error(msg||'登录失败')}
  }finally{logging.value=false}
}

async function handleCasLogin(){
  logging.value=true
  try{const res=await authApi.casCredentialLogin(form.username,form.password);userStore.loginCas(res.data);router.push('/dashboard')}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'CAS登录失败')}finally{logging.value=false}
}
</script>

<style scoped>
.login-page{min-height:100vh;display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden}
.login-bg{position:absolute;inset:0;background:linear-gradient(160deg,#E8F4FD 0%,#F0F7FF 30%,#E8F0FE 60%,#F5F8FC 100%)}
/* 蓝图网格纹理 */
.login-bg::before{content:'';position:absolute;inset:0;background-image:linear-gradient(rgba(64,158,255,.04) 1px,transparent 1px),linear-gradient(90deg,rgba(64,158,255,.04) 1px,transparent 1px);background-size:40px 40px}
/* 装饰圆弧 */
.login-bg::after{content:'';position:absolute;top:-120px;right:-80px;width:360px;height:360px;border-radius:50%;border:1px solid rgba(64,158,255,.08);background:radial-gradient(circle,rgba(64,158,255,.03) 0%,transparent 70%)}
.login-wrapper{position:relative;z-index:1;width:100%;max-width:420px;padding:24px}
.login-card{background:#fff;border-radius:16px;padding:36px 32px 28px;box-shadow:0 2px 24px rgba(0,0,0,.06),0 0 0 1px rgba(0,0,0,.03)}

/* 标题 */
.lc-header{text-align:center;margin-bottom:24px}
.lc-icon{width:52px;height:52px;border-radius:14px;background:linear-gradient(135deg,#409EFF,#2C6FCE);display:inline-flex;align-items:center;justify-content:center;color:#fff;margin-bottom:14px}
.lc-title{font-size:22px;font-weight:700;color:#1D2B3A;margin:0;letter-spacing:.5px}
.lc-subtitle{font-size:13px;color:#909399;margin:6px 0 0}

.lc-alert{margin-bottom:20px}

.lc-form{margin-top:4px}
.lc-btn-primary{width:100%;font-weight:500;letter-spacing:1px}

/* 分割线 */
.lc-divider{display:flex;align-items:center;margin:18px 0;color:#C0C4CC;font-size:13px}
.lc-divider::before,.lc-divider::after{content:'';flex:1;height:1px;background:#EBEEF5}
.lc-divider span{padding:0 14px}

.lc-btn-cas{width:100%;background:#F0F7FF;border:1px solid #D6E8FA;color:#409EFF;font-weight:500}
.lc-btn-cas:hover{background:#E6F1FD;border-color:#B3D8F7;color:#337ECC}

.lc-forgot{text-align:center;margin-top:16px}

@media(max-width:480px){.login-card{padding:28px 20px 22px}.lc-title{font-size:19px}}
</style>
