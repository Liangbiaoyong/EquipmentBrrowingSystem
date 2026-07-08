<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">建筑学院设备借用系统</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="username"><el-input v-model="form.username" placeholder="用户名" :prefix-icon="User"/></el-form-item>
        <el-form-item prop="password"><el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password/></el-form-item>
        <el-form-item><el-button type="primary" class="login-btn" :loading="logging" @click="handleLogin">登 录</el-button></el-form-item>
      </el-form>
      <div class="cas-login"><el-divider>或</el-divider><el-button @click="handleLogin" class="cas-btn" :loading="logging">广州大学 CAS 登录</el-button></div>
    </el-card>
  </div>
</template>
<script setup>
import { reactive,ref,onMounted } from 'vue';import { useRouter,useRoute } from 'vue-router';import { User,Lock } from '@element-plus/icons-vue';import { useUserStore } from '@/store/user';import { authApi } from '@/api/auth';import { ElMessage } from 'element-plus'
const router=useRouter();const route=useRoute();const userStore=useUserStore();const formRef=ref(null);const logging=ref(false)
const form=reactive({username:'',password:''})
const rules={username:[{required:true,message:'请输入用户名',trigger:'blur'}],password:[{required:true,message:'请输入密码',trigger:'blur'}]}

onMounted(async()=>{
  const token=route.query.token||route.query.uniToken||route.query.access_token
  if(token){
    try{const res=await authApi.casLogin(token,'');userStore.loginCas(res.data);router.push('/dashboard')}catch(e){ElMessage.error('CAS登录失败');router.replace({query:{}})}
  }
})

async function handleLogin(){
  const valid=await formRef.value.validate().catch(()=>false)
  if(!valid)return
  logging.value=true
  try{
    // 优先CAS服务端无感登录
    const res=await authApi.casCredentialLogin(form.username,form.password)
    userStore.loginCas(res.data)
    router.push('/dashboard')
  }catch(casErr){
    // CAS失败则尝试本地登录
    try{await userStore.login(form);router.push('/')}catch(e){}
  }finally{logging.value=false}
}
</script>
<style scoped>
.login-container{height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%)}
.login-card{width:400px}.login-title{text-align:center;margin-bottom:24px;color:#303133}
.login-btn,.cas-btn{width:100%}
</style>
