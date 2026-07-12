<template>
  <div class="profile" v-if="userStore.userInfo"><h2>个人中心</h2>
    <el-card style="max-width:650px;margin-bottom:16px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ userStore.userInfo.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ userStore.userInfo.realName }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ userStore.userInfo.userTypeName }}</el-descriptions-item>
        <el-descriptions-item label="学院/部门">{{ userStore.userInfo.department }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ userStore.userInfo.className }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ userStore.userInfo.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ userStore.userInfo.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证方式">{{ userStore.userInfo.authSource==='C'?'CAS统一认证':'本地账户' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 修改密码（仅本地账户可用） -->
    <el-card v-if="userStore.userInfo.authSource==='L'" header="修改密码" style="max-width:450px">
      <el-form :model="pwd" label-width="90px" @submit.prevent>
        <el-form-item label="旧密码" required><el-input v-model="pwd.oldPassword" type="password" show-password placeholder="输入当前密码"/></el-form-item>
        <el-form-item label="新密码" required><el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少8位字符"/></el-form-item>
        <el-form-item label="确认密码" required><el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码"/></el-form-item>
        <el-form-item><el-button type="primary" @click="doChangePwd" :loading="changing">修改密码</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-alert v-else title="CAS认证账户" type="info" :closable="false" show-icon style="max-width:450px">
      CAS统一认证用户的密码由学校CAS系统管理，请通过学校门户修改密码。下次CAS登录时将自动同步。
    </el-alert>
  </div>
</template>
<script setup>
import { ref,reactive } from 'vue';import { useUserStore } from '@/store/user';import { authApi } from '@/api/auth';import { ElMessage } from 'element-plus'

const userStore=useUserStore()
const pwd=reactive({oldPassword:'',newPassword:'',confirm:''});const changing=ref(false)

async function doChangePwd(){
  if(!pwd.oldPassword){ElMessage.warning('请输入旧密码');return}
  if(!pwd.newPassword||pwd.newPassword.length<8){ElMessage.warning('新密码至少8位');return}
  if(pwd.newPassword!==pwd.confirm){ElMessage.warning('两次密码不一致');return}
  if(pwd.oldPassword===pwd.newPassword){ElMessage.warning('新密码不能与旧密码相同');return}
  changing.value=true
  try{
    await authApi.changePassword(pwd.oldPassword,pwd.newPassword)
    ElMessage.success('密码修改成功，请用新密码重新登录')
    Object.assign(pwd,{oldPassword:'',newPassword:'',confirm:''})
  }catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'修改失败')}
  finally{changing.value=false}
}
</script>
<style scoped>.profile{padding:20px}</style>
