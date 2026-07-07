<template>
  <div class="profile" v-if="userStore.userInfo"><h2>个人中心</h2>
    <el-card style="max-width:600px"><el-descriptions :column="1" border><el-descriptions-item label="用户名">{{ userStore.userInfo.username }}</el-descriptions-item><el-descriptions-item label="姓名">{{ userStore.userInfo.realName }}</el-descriptions-item><el-descriptions-item label="角色">{{ userStore.userInfo.userTypeName }}</el-descriptions-item><el-descriptions-item label="学院/部门">{{ userStore.userInfo.department }}</el-descriptions-item><el-descriptions-item label="班级">{{ userStore.userInfo.className }}</el-descriptions-item><el-descriptions-item label="手机号">{{ userStore.userInfo.phone || '-' }}</el-descriptions-item><el-descriptions-item label="邮箱">{{ userStore.userInfo.email || '-' }}</el-descriptions-item><el-descriptions-item label="认证方式">{{ userStore.userInfo.authSource==='C'?'CAS统一认证':'本地账户' }}</el-descriptions-item><el-descriptions-item label="权限标识"><el-tag v-for="p in userStore.userInfo.permissions" :key="p" size="small" style="margin:2px">{{ p }}</el-tag></el-descriptions-item></el-descriptions></el-card>
  </div>
</template>
<script setup>import { useUserStore } from '@/store/user';const userStore=useUserStore()</script>
<style scoped>.profile{padding:20px}</style>
