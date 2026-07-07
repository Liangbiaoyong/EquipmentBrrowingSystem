<template>
  <router-view />
</template>

<script setup>
// 根组件
</script>

<style>
*{margin:0;padding:0;box-sizing:border-box}
html,body,#app{height:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif}

/* 移动端适配 */
@media(max-width:768px){
  .el-table{font-size:12px}
  .el-card{padding:10px}
  .el-col[class*=span-]{margin-bottom:10px}
  .el-dialog{width:95%!important}
  .el-form-item__label{width:60px!important}
  .el-pagination{justify-content:center!important}
}
</style>
