<template>
  <div class="create">
    <div v-if="fromDetailDeviceId" style="margin-bottom:15px;display:flex;align-items:center;gap:8px">
      <el-button text @click="router.push('/devices/'+fromDetailDeviceId)"><el-icon><ArrowLeft/></el-icon> 返回设备详情</el-button>
    </div>
    <h2>借用申请</h2>
    <el-card style="max-width:750px"><el-form :model="f" label-width="100px">
      <!-- 设备选择 -->
      <el-form-item label="选择设备" required>
        <el-select v-model="f.deviceIds" filterable multiple placeholder="输入关键词搜索设备..." style="width:100%"
          :filter-method="deviceSearch" :loading="deviceLoading" @change="onDeviceChange"
          @visible-change="onPickerOpen" popper-class="device-picker-dropdown">
          <el-option v-for="d in deviceOptions" :key="d.id" :label="`${d.name} (${d.assetNo}) — ${d.location||''}`" :value="d.id">
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>{{ d.name }} <small style="color:#909399">{{ d.assetNo }}</small></span>
              <span>
                <el-tag size="small" :type="d.borrowType===1?'warning':''" effect="plain">{{ d.borrowType===1?'仅现场':'可借出' }}</el-tag>
                <el-tag size="small" :type="d.borrowStatus===1?'success':'danger'" style="margin-left:4px">{{ d.borrowStatus===1?'可借':'已借' }}</el-tag>
              </span>
            </div>
          </el-option>
        </el-select>
        <div style="margin-top:4px;font-size:12px;color:#909399">输入设备名称或资产编号搜索，支持多选 | 仅显示可借用设备</div>
      </el-form-item>

      <!-- 现场借用标识 -->
      <el-form-item v-if="hasOnsiteDevice" label="借用方式">
        <el-alert title="您选择了「仅现场借用」设备" type="warning" :closable="false" show-icon>
          <template #default>此类设备仅限实验室内现场使用，不可带走。借用时间系统自动设为当天。</template>
        </el-alert>
      </el-form-item>

      <!-- 时间选择（仅可借出设备显示） -->
      <template v-if="!allOnsite">
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="f.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/>
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="f.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="使用时间"><el-tag type="warning">现场借用默认为当天使用</el-tag></el-form-item>
      </template>

      <el-form-item label="借用目的" required>
        <el-select v-model="f.purposeCategory" placeholder="选择目的大类" style="width:100%" @change="f.purposeSubcategory=''">
          <el-option v-for="c in purposeCategories" :key="c.value" :label="c.label" :value="c.value"/>
        </el-select>
        <div v-if="currentPurposeDesc" style="margin-top:4px;font-size:12px;color:#909399;line-height:1.4">💡 {{ currentPurposeDesc }}</div>
      </el-form-item>
      <el-form-item label="子分类" required v-if="f.purposeCategory">
        <!-- 选择"其他"大类时，子分类用输入框自由填写 -->
        <el-input v-if="f.purposeCategory==='其他'" v-model="f.purposeSubcategory" placeholder="请输入具体用途分类..." :rules="[{required:true,message:'请填写子分类'}]"/>
        <el-select v-else v-model="f.purposeSubcategory" placeholder="选择具体用途（必选）" style="width:100%">
          <template v-if="f.purposeCategory==='教学与培养'"><el-option label="课堂教学" value="课堂教学"/><el-option label="毕业设计/论文" value="毕业设计/论文"/><el-option label="课程设计/大作业" value="课程设计/大作业"/><el-option label="实习/实训" value="实习/实训"/><el-option label="其他" value="其他"/></template>
          <template v-else-if="f.purposeCategory==='科研与项目'"><el-option label="纵向科研项目" value="纵向科研项目"/><el-option label="横向科研项目" value="横向科研项目"/><el-option label="校级科研启动/培育" value="校级科研启动/培育"/><el-option label="研究生学位论文研究" value="研究生学位论文研究"/><el-option label="其他" value="其他"/></template>
          <template v-else-if="f.purposeCategory==='学科竞赛与创新'"><el-option label="学生竞赛" value="学生竞赛"/><el-option label="教师指导竞赛" value="教师指导竞赛"/><el-option label="大创项目" value="大创项目"/><el-option label="其他" value="其他"/></template>
          <template v-else-if="f.purposeCategory==='学术交流与合作'"><el-option label="学术会议/展览" value="学术会议/展览"/><el-option label="联合教学/工作坊" value="联合教学/工作坊"/><el-option label="访问学者/博士后研究" value="访问学者/博士后研究"/><el-option label="其他" value="其他"/></template>
          <template v-else-if="f.purposeCategory==='社会服务与文化传承'"><el-option label="科普活动/开放日" value="科普活动/开放日"/><el-option label="校企合作基地建设" value="校企合作基地建设"/><el-option label="古建筑测绘/乡村振兴" value="古建筑测绘/乡村振兴"/><el-option label="其他" value="其他"/></template>
          <template v-else-if="f.purposeCategory==='行政与公共服务'"><el-option label="学院行政活动" value="学院行政活动"/><el-option label="日常办公" value="日常办公"/><el-option label="其他" value="其他"/></template>
          <template v-else-if="f.purposeCategory==='个人发展与兴趣'"><el-option label="自主学习与训练" value="自主学习与训练"/><el-option label="其他" value="其他"/></template>
        </el-select>
      </el-form-item>
      <el-form-item label="目的详情" required><el-input v-model="f.purpose" type="textarea" :rows="2" placeholder="请详细描述借用目的，如项目名称/课程名称/竞赛名称等..."/></el-form-item>
      <el-form-item label="备注"><el-input v-model="f.reason" type="textarea" :rows="1" placeholder="其他补充说明(可选)"/></el-form-item>
      <el-divider content-position="left">审批流程</el-divider>
      <div v-if="f.deviceIds.length>1" style="margin-bottom:12px;padding:8px 12px;background:#fdf6ec;border-radius:4px;font-size:12px;color:#e6a23c">多设备申请将为每台设备独立创建借用单，初审人按设备分别匹配</div>
      <el-form-item label="初审人">
        <div style="display:flex;flex-wrap:wrap;gap:6px">
          <template v-if="f.deviceIds.length<=1">
            <el-tag type="primary">{{ approverLevel1 || '设备使用人（自动匹配）' }}</el-tag>
          </template>
          <template v-else>
            <el-tag v-for="a in multiApprovers" :key="a.deviceId" type="primary" size="small" effect="plain">
              {{ a.deviceName }}: {{ a.approver||'自动匹配' }}
            </el-tag>
          </template>
        </div>
      </el-form-item>
      <el-form-item label="终审人"><el-tag type="success">{{ approverLevel2 || '实验室管理员（自动分配）' }}</el-tag></el-form-item>
      <el-form-item><el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button></el-form-item>
    </el-form></el-card>
  </div>
</template>
<script setup>
import { ref,reactive,onMounted,computed } from 'vue';import { useRoute,useRouter } from 'vue-router';import { borrowApi } from '@/api/borrow';import axios from '@/api/request';import { descriptionApi } from '@/api/categoryDescription';import { ElMessage } from 'element-plus'
const route=useRoute();const router=useRouter();const deviceOptions=ref([]);const deviceLoading=ref(false);const submitting=ref(false)
const approverLevel1=ref('');const approverLevel2=ref('');const fromDetailDeviceId=ref(null)
const f=reactive({deviceIds:[],startTime:'',endTime:'',reason:'',purpose:'',purposeCategory:'教学与培养',purposeSubcategory:'',approverId:null})

// 目的分类描述
const purposeDescriptions = ref({})

const hasOnsiteDevice=computed(()=>f.deviceIds.some(id=>{const d=deviceOptions.value.find(x=>x.id===id);return d&&d.borrowType===1}))
const allOnsite=computed(()=>f.deviceIds.length>0&&f.deviceIds.every(id=>{const d=deviceOptions.value.find(x=>x.id===id);return d&&d.borrowType===1}))
const currentPurposeDesc=computed(()=>f.purposeCategory ? purposeDescriptions.value[f.purposeCategory] || '' : '')

// 多设备时显示每个设备的初审人
const multiApprovers=computed(()=>f.deviceIds.map(id=>{
  const d=deviceOptions.value.find(x=>x.id===id)
  return {deviceId:id,deviceName:d?d.name:'设备#'+id,approver:d?d.custodian||'自动匹配':'自动匹配'}
}))

// 目的大类选项（含描述tooltip）
const purposeCategories = [
  {label:'教学与培养',value:'教学与培养'},{label:'科研与项目',value:'科研与项目'},
  {label:'学科竞赛与创新',value:'学科竞赛与创新'},{label:'学术交流与合作',value:'学术交流与合作'},
  {label:'社会服务与文化传承',value:'社会服务与文化传承'},{label:'行政与公共服务',value:'行政与公共服务'},
  {label:'个人发展与兴趣',value:'个人发展与兴趣'},{label:'其他',value:'其他'}
]

onMounted(async()=>{
  await loadDevices()
  try{const{data}=await axios.get('/auth/approvers');const labAdmins=(data||[]).filter(u=>u.userType===2);if(labAdmins.length)approverLevel2.value=labAdmins[0].realName||labAdmins[0].username}catch{}
  if(route.query.deviceId){const id=Number(route.query.deviceId);fromDetailDeviceId.value=id;f.deviceIds=[id];updateApproverInfo()}
  loadPurposeDescriptions()
})

async function loadPurposeDescriptions(){
  try{const{data}=await descriptionApi.listByType('PURPOSE');
    const map={};(data||[]).forEach(d=>{map[d.categoryName]=d.description});purposeDescriptions.value=map
  }catch{/* 描述加载失败不影响借用流程 */}
}

async function loadDevices(keyword){
  deviceLoading.value=true
  try{const{data}=await axios.get('/devices/picker',{params:{page:1,size:200,keyword:keyword||undefined}});deviceOptions.value=data.records||[]}catch(e){console.error('加载设备列表失败',e)}finally{deviceLoading.value=false}
}

function deviceSearch(keyword){loadDevices(keyword||'')}
function onPickerOpen(visible){if(visible&&deviceOptions.value.length===0)loadDevices()}

function onDeviceChange(ids){
  if(ids&&ids.length){
    const d=deviceOptions.value.find(x=>x.id===ids[0])
    if(d){f.approverId=d.defaultApproverId||null;approverLevel1.value=d.custodian||'设备使用人（自动匹配）'}
    // 纯现场借用设备自动设当天时间
    if(allOnsite.value){const now=new Date();f.startTime=now.toISOString().slice(0,19);f.endTime=new Date(now.getTime()+8*3600000).toISOString().slice(0,19)}
  }
}

function updateApproverInfo(){if(f.deviceIds.length){const d=deviceOptions.value.find(x=>x.id===f.deviceIds[0]);if(d)approverLevel1.value=d.custodian||'设备使用人（自动匹配）'}}

async function submit(){
  if(!f.deviceIds.length){ElMessage.warning('请选择设备');return}
  if(!f.purposeCategory){ElMessage.warning('请选择借用目的大类');return}
  if(!f.purposeSubcategory||!f.purposeSubcategory.trim()){ElMessage.warning('请选择或填写子目标分类');return}
  if(!f.purpose||!f.purpose.trim()){ElMessage.warning('请填写借用目的');return}
  if(!allOnsite.value&&(!f.startTime||!f.endTime)){ElMessage.warning('请选择借用时间');return}
  if(allOnsite.value){const now=new Date();f.startTime=now.toISOString().slice(0,19);f.endTime=new Date(now.getTime()+8*3600000).toISOString().slice(0,19)}
  submitting.value=true
  try{await borrowApi.create({...f});ElMessage.success('申请已提交');router.push('/borrows/my')}catch(e){ElMessage.error(e?.response?.data?.msg||e?.message||'提交失败')}finally{submitting.value=false}
}
</script>
<style scoped>.create{padding:20px}</style>
