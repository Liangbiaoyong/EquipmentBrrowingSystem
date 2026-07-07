<template>
  <div class="forbidden"><svg width="120" height="120" viewBox="0 0 120 120"><circle cx="60" cy="60" r="55" fill="#fef0f0" stroke="#F56C6C" stroke-width="2"/><text x="60" y="55" text-anchor="middle" fill="#F56C6C" font-size="36" font-weight="bold">403</text><text x="60" y="75" text-anchor="middle" fill="#909399" font-size="10">FORBIDDEN</text></svg><h2 style="margin-top:20px;color:#303133">抱歉，您没有权限访问此页面</h2><p style="color:#909399;margin-top:10px">如需访问，请联系系统管理员</p><el-button type="primary" @click="$router.push('/dashboard')" style="margin-top:20px">返回首页</el-button>
  </div>
</template>
<style scoped>.forbidden{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:60px 20px;text-align:center}</style>
