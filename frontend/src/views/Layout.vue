<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse?'64px':'220px'" class="sidebar" :class="{mobile:isMobile}">
      <div class="logo"><span v-if="!isCollapse">设备借用系统</span><span v-else>设备</span></div>
      <el-menu :default-active="route.path" :collapse="isCollapse" :collapse-transition="false" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <template v-for="item in menuItems" :key="item.index">
          <el-sub-menu v-if="item.children" :index="item.index"><template #title><el-icon><component:is="item.icon"/></el-icon><span>{{item.title}}</span></template><el-menu-item v-for="sub in item.children" :key="sub.index" :index="sub.index">{{sub.title}}</el-menu-item></el-sub-menu>
          <el-menu-item v-else :index="item.index"><el-icon><component:is="item.icon"/></el-icon><span>{{item.title}}</span></el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left"><el-icon class="collapse-btn" @click="isCollapse=!isCollapse"><Fold v-if="!isCollapse"/><Expand v-else/></el-icon></div>
        <div class="header-right">
          <el-badge :value="unread" :hidden="!unread" class="notify-badge" @click="$router.push('/notifications')"><el-icon :size="20"><Bell/></el-icon></el-badge>
          <el-dropdown trigger="click"><span class="user-info">{{userStore.userInfo?.realName||'用户'}}<el-icon><ArrowDown/></el-icon></span><template #dropdown><el-dropdown-menu><el-dropdown-item @click="$router.push('/profile')">个人中心</el-dropdown-item><el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </el-header>
      <el-main class="main"><router-view/></el-main>
    </el-container>
  </el-container>
</template>
<script setup>
import {ref,computed,onMounted,onUnmounted} from 'vue';import {useRoute,useRouter} from 'vue-router';import {useUserStore} from '@/store/user';import {notifyApi} from '@/api/notification'
import {DataAnalysis,Monitor,Reading,EditPen,TrendCharts,Setting,User,Bell,Fold,Expand,ArrowDown} from '@element-plus/icons-vue'
const route=useRoute();const router=useRouter();const userStore=useUserStore()
const isCollapse=ref(false);const unread=ref(0);const isMobile=ref(window.innerWidth<768)
let ws=null

const allMenus=[{index:'/dashboard',title:'仪表盘',icon:DataAnalysis,perm:'dashboard:view'},{index:'/devices',title:'设备浏览',icon:Monitor,perm:'device:view'},{index:'device-manage',title:'设备管理',icon:Setting,perm:'device:manage',children:[{index:'/devices/manage/list',title:'设备列表'},{index:'/devices/manage/import',title:'批量导入'},{index:'/devices/manage/missing-images',title:'缺少图片'}]},{index:'/borrows/create',title:'借用申请',icon:Reading,perm:'borrow:create'},{index:'/borrows/my',title:'我的借用',icon:Reading,perm:'borrow:my'},{index:'approval',title:'借用审批',icon:EditPen,perm:'approval:first',children:[{index:'/approvals/first',title:'一级审批',perm:'approval:first'},{index:'/approvals/second',title:'二级审批',perm:'approval:second'}]},{index:'/returns/overdue',title:'逾期管理',icon:Monitor,perm:'return:manage'},{index:'/repairs',title:'维修管理',icon:Setting,perm:'repair:manage'},{index:'/statistics',title:'数据统计',icon:TrendCharts,perm:'statistics:view'},{index:'/notifications',title:'消息中心',icon:Bell,perm:'notification:view'},{index:'admin',title:'系统管理',icon:Setting,perm:'admin:user',children:[{index:'/admin/users',title:'用户管理'},{index:'/admin/settings',title:'系统设置'},{index:'/admin/logs',title:'操作日志'}]}]
const menuItems=computed(()=>allMenus.filter(m=>!m.perm||userStore.hasPermission(m.perm)).map(m=>m.children?{...m,children:m.children.filter(c=>!c.perm||userStore.hasPermission(c.perm))}:m).filter(m=>!m.children||m.children.length>0))

function connectWs(){
  if(!userStore.userInfo)return
  const proto=location.protocol==='https:'?'wss':'ws';ws=new WebSocket(`${proto}://${location.host}/api/v1/ws/notification/${userStore.userInfo.id}`)
  ws.onmessage=e=>{try{const d=JSON.parse(e.data);unread.value++;setTimeout(()=>fetchUnread(),1000)}catch{}}
  ws.onclose=()=>{setTimeout(connectWs,10000)}
}
async function fetchUnread(){try{const r=await notifyApi.unreadCount();unread.value=r.data.unreadCount}catch{}}
function handleLogout(){if(ws)ws.close();userStore.logout();router.push('/login')}
function onResize(){isMobile.value=window.innerWidth<768;if(isMobile.value)isCollapse.value=true}

onMounted(async()=>{if(!userStore.userInfo)await userStore.fetchUserInfo();fetchUnread();connectWs();window.addEventListener('resize',onResize);onResize()})
onUnmounted(()=>{if(ws)ws.close();window.removeEventListener('resize',onResize)})
</script>
<style scoped>
.layout-container{height:100vh}
.sidebar{background-color:#304156;transition:width 0.3s;overflow:hidden}
.sidebar.mobile{position:fixed;z-index:100;height:100vh}
.logo{height:60px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:18px;font-weight:bold;border-bottom:1px solid rgba(255,255,255,0.1)}
.header{background:#fff;border-bottom:1px solid #e6e6e6;display:flex;align-items:center;justify-content:space-between;padding:0 20px;height:60px}
.header-right{display:flex;align-items:center;gap:20px}
.notify-badge{cursor:pointer}
.collapse-btn{font-size:20px;cursor:pointer}
.user-info{cursor:pointer;color:#303133}
.main{background:#f0f2f5;min-height:calc(100vh - 60px)}
@media(max-width:768px){.header{padding:0 10px}.main{padding:10px}}
</style>
