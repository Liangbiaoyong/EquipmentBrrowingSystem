<template>
  <div class="scrap-page"><h2>报废管理</h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="报废评估" name="devices">
        <!-- 筛选栏 -->
        <el-card shadow="never" style="margin-bottom:12px">
          <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
            <el-input v-model="keyword" placeholder="搜索名称/资产编号/国标分类" clearable style="width:240px" @keyup.enter="loadDevices"/>
            <el-select v-model="catFilter" placeholder="设备分类" clearable style="width:160px" @change="loadDevices">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
            </el-select>
            <el-select v-model="eligibleFilter" placeholder="可报废状态" clearable style="width:140px" @change="loadDevices">
              <el-option label="可报废" :value="true"/><el-option label="未达年限" :value="false"/>
            </el-select>
            <el-button type="primary" @click="loadDevices">查询</el-button>
            <span style="margin-left:auto;color:#909399;font-size:13px">共 {{ total }} 台设备</span>
          </div>
        </el-card>

        <!-- 设备表格 -->
        <el-card shadow="never">
          <el-table :data="devices" stripe v-loading="loading" @sort-change="onSort">
            <el-table-column prop="id" label="ID" width="60" sortable="custom"/>
            <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip/>
            <el-table-column prop="assetNo" label="资产编号" width="120"/>
            <el-table-column prop="gbCategoryName" label="国标分类" min-width="140" show-overflow-tooltip/>
            <el-table-column prop="purchaseDate" label="购置日期" width="110" sortable="custom"/>
            <el-table-column label="使用年限" width="90" sortable="custom"><template #default="{row}"><span :style="{color:row.yearsUsed>=row.minYears?'#67C23A':'#909399'}">{{ row.yearsUsed }}年</span></template></el-table-column>
            <el-table-column label="最低报废年限" width="120" sortable="custom"><template #default="{row}"><el-tag size="small" :type="row.minYears<=6?'success':row.minYears<=10?'warning':'danger'">{{ row.minYears }}年</el-tag><span style="font-size:11px;color:#909399;margin-left:4px">{{ row.ruleMatch }}</span></template></el-table-column>
            <el-table-column label="可报废" width="80"><template #default="{row}"><el-tag :type="row.scrapEligible?'success':'info'">{{ row.scrapEligible?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right"><template #default="{row}">
              <el-button v-if="row.scrapEligible" size="small" type="danger" @click="scrapDevice(row)">报废</el-button>
              <el-tooltip v-else :content="'还需'+row.remainingYears+'年'"><el-button size="small" disabled>报废</el-button></el-tooltip>
            </template></el-table-column>
          </el-table>
          <div style="margin-top:12px;display:flex;justify-content:flex-end">
            <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20,100,500]" :total="total" layout="total,sizes,prev,pager,next,jumper" @current-change="loadDevices" @size-change="s=>{size=s;page=1;loadDevices()}"/>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 报废规则管理 -->
      <el-tab-pane label="报废规则" name="rules">
        <el-card shadow="never">
          <div style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
            <el-button type="primary" @click="ruleForm={gbKeyword:'',minYears:6,priority:100,remark:''};ruleVisible=true">+ 新增规则</el-button>
            <el-button @click="showBatch=true">批量录入</el-button>
            <el-button type="warning" @click="initDefaultRules">初始化默认规则</el-button>
            <el-button type="danger" plain @click="doDeduplicate" :loading="dedupLoading">去重</el-button>
            <span style="color:#909399;font-size:12px;margin-left:8px">{{ rules.length }}条规则，按优先级排序，关键词与国标分类名包含匹配即生效</span>
          </div>
          <el-table :data="rules" stripe>
            <el-table-column prop="id" label="ID" width="60"/>
            <el-table-column prop="gbKeyword" label="国标关键词" min-width="180"/>
            <el-table-column prop="minYears" label="最低年限" width="90"><template #default="{row}"><el-tag :type="row.minYears<=6?'success':row.minYears<=10?'warning':'danger'">{{ row.minYears }}年</el-tag></template></el-table-column>
            <el-table-column prop="priority" label="优先级" width="80"/>
            <el-table-column prop="remark" label="说明" min-width="140"/>
            <el-table-column label="状态" width="70"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'" size="small">{{ row.status===1?'启用':'禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><div style="white-space:nowrap"><el-button size="small" @click="editRule(row)">编辑</el-button><el-button size="small" @click="toggleRule(row)">{{ row.status===1?'禁用':'启用' }}</el-button><el-button size="small" type="danger" @click="deleteRule(row.id)">删除</el-button></div></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 批量录入对话框 -->
    <el-dialog v-model="showBatch" title="批量录入规则" width="550px">
      <p style="color:#909399;font-size:13px;margin-bottom:8px">每行一条规则，格式: <code>国标关键词,最低年限,优先级,说明</code> (逗号分隔)</p>
      <el-input v-model="batchText" type="textarea" :rows="10" placeholder="计算机设备,6,10,计算机及外设&#10;办公设备,6,10,办公用设备&#10;车辆,8,10,机动车辆"/>
      <template #footer><el-button @click="showBatch=false">取消</el-button><el-button type="primary" @click="doBatchImport" :loading="batchLoading">批量导入</el-button></template>
    </el-dialog>

    <!-- 规则编辑对话框 -->
    <el-dialog v-model="ruleVisible" :title="ruleForm.id?'编辑规则':'新增规则'" width="450px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="国标关键词" required><el-input v-model="ruleForm.gbKeyword" placeholder="如: 计算机设备"/></el-form-item>
        <el-form-item label="最低年限" required><el-input-number v-model="ruleForm.minYears" :min="1" :max="50"/></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="1" :max="999"/></el-form-item>
        <el-form-item label="说明"><el-input v-model="ruleForm.remark"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleVisible=false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage,ElMessageBox } from 'element-plus'

const tab = ref('devices')
const devices = ref([]); const loading = ref(false)
const page = ref(1); const size = ref(20); const total = ref(0)
const keyword = ref(''); const catFilter = ref(null); const eligibleFilter = ref(null)
const categories = ref([]); const sortBy = ref(''); const sortOrder = ref('')

const rules = ref([])
const ruleForm = ref({}); const ruleVisible = ref(false)
const showBatch = ref(false); const batchText = ref(''); const batchLoading = ref(false)
const dedupLoading = ref(false)

const defaultRules = `台式计算机,6,10,计算机设备
便携式计算机,6,10,计算机设备
平板式计算机,6,10,计算机设备
移动工作站,6,10,计算机设备
服务器,6,10,计算机设备
其他计算机,6,10,计算机设备
液晶显示器,6,10,计算机设备
其他输入输出设备,6,10,计算机设备
其他存储设备,6,10,计算机设备
网络存储设备,6,10,计算机设备
磁盘机,6,10,计算机设备
移动存储设备,6,10,计算机设备
阅读器,6,10,计算机设备
数据录入设备,6,10,计算机设备
触控一体机,6,10,计算机设备
其他信息化设备,6,10,计算机设备
应用软件,6,15,计算机设备(软件)
基础软件,6,15,计算机设备(软件)
其他计算机软件,6,15,计算机设备(软件)
多功能一体机,6,20,办公设备
A4黑白打印机,6,20,办公设备
A3黑白打印机,6,20,办公设备
其他打印机,6,20,办公设备
复印机,6,20,办公设备
扫描仪,6,20,办公设备
碎纸机,6,20,办公设备
投影仪,6,20,办公设备
其他办公设备,6,20,办公设备
通用照相机,5,30,广播、电视、电影设备
专用照相机,5,30,广播、电视、电影设备
其他照相机及器材,5,30,广播、电视、电影设备
镜头及器材,5,30,广播、电视、电影设备
通用摄像机,5,30,广播、电视、电影设备
专业摄像机和信号源设备,5,30,广播、电视、电影设备
录像机,5,30,广播、电视、电影设备
录放音机,5,30,广播、电视、电影设备
其他电影设备,5,30,广播、电视、电影设备
其他视频设备,5,30,广播、电视、电影设备
普通电视设备,5,30,广播、电视、电影设备
特种成像应用电视设备,5,30,广播、电视、电影设备
调音台,5,30,广播、电视、电影设备
话筒设备,5,30,广播、电视、电影设备
音箱,5,30,广播、电视、电影设备
音频功率放大器设备,5,30,广播、电视、电影设备
放大器,5,30,广播、电视、电影设备
声处理设备,5,30,广播、电视、电影设备
其他音频设备,5,30,广播、电视、电影设备
其他广播发射设备,5,30,广播、电视、电影设备
播控设备,5,30,广播、电视、电影设备
舞台设备,5,30,广播、电视、电影设备
影剧院设备,5,30,广播、电视、电影设备
乐器,5,30,广播、电视、电影设备
安全、检查、监视、报警设备,5,30,广播、电视、电影设备
视频监控设备,5,30,广播、电视、电影设备
其他广播、电视、电影设备,5,30,广播、电视、电影设备
固定翼飞机,15,35,航空器
无人机,15,35,航空器
开关柜,5,40,电气设备
不间断电源,5,40,电气设备
其他电源设备,5,40,电气设备
空调机,10,50,机械设备(制冷空调)
空调机组,10,50,机械设备(制冷空调)
专用制冷空调设备,10,50,机械设备(制冷空调)
去湿机组,10,50,机械设备(制冷空调)
电冰箱,10,50,机械设备(制冷空调)
冷藏箱柜,10,50,机械设备(制冷空调)
吸尘器,10,60,机械设备
饮水器,10,60,机械设备
金属切削机床,10,60,机械设备
金属焊接设备,10,60,机械设备
电动工具,10,60,机械设备
其他泵,10,60,机械设备
木材采伐和集运机械,10,60,机械设备
木材干燥设备,10,60,机械设备
水泥及水泥制品设备,10,60,机械设备
玻璃及玻璃制品制造设备,10,60,机械设备
家用电器生产设备,10,60,机械设备
其他造纸和印刷机械,10,60,机械设备
无醇饮料加工设备,10,60,机械设备
植物管理机械,10,60,机械设备
其他机械设备,10,60,机械设备
钻探机,10,60,机械设备
其他体育设备设施,10,60,机械设备
物理光学仪器,5,70,仪器仪表
光学测试仪器,5,70,仪器仪表
光学式分析仪器,5,70,仪器仪表
电化学分析仪器,5,70,仪器仪表
其他分析仪器,5,70,仪器仪表
真空检测仪器,5,70,仪器仪表
核子及核辐射测量仪器,5,70,仪器仪表
红外仪器,5,70,仪器仪表
航空仪器,5,70,仪器仪表
天文仪器,5,70,仪器仪表
测绘仪器,5,70,仪器仪表
气象仪器,5,70,仪器仪表
地质勘探钻采及人工地震仪器,5,70,仪器仪表
环境监测仪器及综合分析装置,5,70,仪器仪表
生理仪器,5,70,仪器仪表
心理仪器,5,70,仪器仪表
医用电子生理参数检测仪器设备,5,70,仪器仪表
临床检验设备,5,70,仪器仪表
物证检验鉴定设备,5,70,仪器仪表
试验箱及气候环境试验设备,5,70,仪器仪表
其他试验仪器及装置,5,70,仪器仪表
其他试验机,5,70,仪器仪表
金属材料试验机,5,70,仪器仪表
温度仪表,5,70,仪器仪表
物位及机械量仪表,5,70,仪器仪表
数字仪表及装置,5,70,仪器仪表
气动电动单元组合仪表,5,70,仪器仪表
集中控制装置,5,70,仪器仪表
自动成套控制系统,5,70,仪器仪表
其他电工仪器仪表,5,70,仪器仪表
其他仪器仪表,5,70,仪器仪表
其他模型,5,70,仪器仪表
航模海模及其他模型设备,5,70,仪器仪表
声源声振信号发生器,5,80,电子和通信测量设备
电子示波器,5,80,电子和通信测量设备
记录电表电磁示波器,5,80,电子和通信测量设备
通讯导航测试仪器,5,80,电子和通信测量设备
电视用测量仪,5,80,电子和通信测量设备
其他元件器件参数测量仪,5,80,电子和通信测量设备
其他电子和通信测量仪器,5,80,电子和通信测量设备
地上衡,5,90,计量标准器具
测力仪器,5,90,计量标准器具
角度量仪,5,90,计量标准器具
其他普通图书,5,100,图书档案设备
办公桌,15,110,家具
其他台桌类,15,110,家具
其他椅凳类,15,110,家具
其他沙发类,15,110,家具
其他柜类,15,110,家具
保密柜,15,110,家具
其他厨卫用具,15,110,家具
其他室内装具,15,110,家具
其他用具,15,110,家具
交换设备,5,120,通信设备
移动通信网设备,5,120,通信设备
其他传真通信设备,5,120,通信设备
地面导航雷达,10,130,雷达导航
地面舰船无线电导航设备,10,130,雷达导航
交通管理设备,5,140,电气设备`

function onSort({prop,order}){sortBy.value=order?prop:'';sortOrder.value=order==='ascending'?'asc':order==='descending'?'desc':'';loadDevices()}

async function loadDevices(){
  loading.value=true
  try{
    const{data}=await axios.get('/scrap/devices',{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,categoryId:catFilter.value,sortBy:sortBy.value||undefined,order:sortOrder.value||undefined}})
    const records = data.records || []; total.value = data.total || 0
    devices.value = eligibleFilter.value === null ? records : eligibleFilter.value ? records.filter(r=>r.scrapEligible) : records.filter(r=>!r.scrapEligible)
  }catch(e){console.error(e)}finally{loading.value=false}
}

async function scrapDevice(row){
  try{
    const{value: remark} = await ElMessageBox.prompt('报废原因（可选）', '确认报废', {confirmButtonText:'确认报废',confirmButtonClass:'el-button--danger',inputPlaceholder:'报废原因'})
    await axios.put(`/scrap/${row.id}/confirm`,null,{params:{remark}})
    ElMessage.success('设备已报废')
    loadDevices()
  }catch(e){if(e!=='cancel')ElMessage.error(e?.response?.data?.msg||'报废失败')}
}

async function loadRules(){try{const{data}=await axios.get('/scrap/rules');rules.value=data||[]}catch{}}

function initDefaultRules(){batchText.value=defaultRules;showBatch.value=true}
async function doBatchImport(){
  const lines=batchText.value.split('\n').filter(l=>l.trim());if(!lines.length){ElMessage.warning('请输入规则');return}
  batchLoading.value=true;let ok=0,fail=0
  for(const line of lines){
    const p=line.split(',');if(p.length<2)continue
    try{await axios.post('/scrap/rules',{gbKeyword:p[0].trim(),minYears:parseInt(p[1]),priority:p[2]?parseInt(p[2]):100,remark:p[3]?p[3].trim():''});ok++}catch(e){fail++;console.error('规则导入失败:',e?.response?.data?.msg||e.message)}
  }
  ElMessage.success(`导入完成: 成功${ok}个, 失败${fail}个`);batchLoading.value=false;showBatch.value=false;loadRules()
}

async function doDeduplicate(){
  dedupLoading.value=true
  try{const{data}=await axios.post('/scrap/rules/deduplicate');ElMessage.success(`去重完成：${data.beforeCount}条→${data.afterCount}条，合并${data.deletedCount}条`);loadRules()}catch(e){ElMessage.error('去重失败')}finally{dedupLoading.value=false}
}
function editRule(row){ruleForm.value={...row};ruleVisible.value=true}
async function saveRule(){try{if(ruleForm.value.id){await axios.put('/scrap/rules/'+ruleForm.value.id,ruleForm.value)}else{await axios.post('/scrap/rules',ruleForm.value)};ElMessage.success('已保存');ruleVisible.value=false;loadRules()}catch(e){ElMessage.error(e?.response?.data?.msg||'保存失败')}}
async function deleteRule(id){try{await ElMessageBox.confirm('确认删除？');await axios.delete('/scrap/rules/'+id);ElMessage.success('已删除');loadRules()}catch{}}
async function toggleRule(row){try{await axios.put('/scrap/rules/'+row.id+'/toggle');ElMessage.success('已切换');loadRules()}catch{}}

onMounted(async()=>{
  try{const{data}=await axios.get('/categories');categories.value=data||[]}catch{}
  loadDevices(); loadRules()
})
</script>

<style scoped>
.scrap-page{padding:20px;max-width:1280px;margin:0 auto}
</style>
