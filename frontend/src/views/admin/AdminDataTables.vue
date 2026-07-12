<template>
  <div class="data-tables"><h2>数据库表管理</h2>
    <p style="color:#909399;margin-bottom:15px">
      <template v-if="isAdmin">系统管理员可浏览、编辑、新增、删除数据和列结构，并可导出。</template>
      <template v-else>实验室管理员仅可查看设备相关表，不支持修改操作。</template>
    </p>

    <!-- 表列表 -->
    <el-card v-if="!currentTable" header="数据表列表">
      <el-table :data="tables" stripe @row-click="openTable" style="cursor:pointer">
        <el-table-column prop="TABLE_NAME" label="表名" width="200"/>
        <el-table-column prop="TABLE_COMMENT" label="说明" min-width="200"/>
        <el-table-column prop="TABLE_ROWS" label="行数" width="100"/>
        <el-table-column prop="SIZE_MB" label="大小(MB)" width="100"/>
      </el-table>
    </el-card>

    <!-- 表数据浏览 -->
    <el-card v-else>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
          <span><el-button text @click="currentTable=null"><el-icon><ArrowLeft/></el-icon> 返回</el-button> <b>{{ currentTable }}</b> (共{{ total }}条) <el-tag v-if="readOnly" size="small" type="info">只读</el-tag></span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-input v-model="keyword" placeholder="搜索..." clearable style="width:180px" @keyup.enter="loadData"/>
            <el-button @click="loadData">查询</el-button>
            <el-dropdown v-if="isAdmin" @command="handleExport">
              <el-button>导出 <el-icon><ArrowDown/></el-icon></el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="csv">CSV 格式</el-dropdown-item><el-dropdown-item command="xlsx">Excel 格式</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            <el-button v-if="isAdmin&&!readOnly" type="success" @click="openNewRow">新增行</el-button>
            <el-button type="danger" v-if="isAdmin&&selectedRows.length&&!readOnly" @click="batchDelete">批量删除({{selectedRows.length}})</el-button>
          </div>
        </div>
      </template>

      <!-- 批量编辑区域（仅系统管理员） -->
      <div v-if="isAdmin&&selectedRows.length&&!readOnly" style="margin-bottom:12px;padding:10px;background:#f5f7fa;border-radius:4px;display:flex;align-items:center;gap:8px;flex-wrap:wrap">
        <span>已选 {{selectedRows.length}} 行：</span>
        <el-select v-model="batchCol" placeholder="选择字段" size="small" style="width:150px">
          <el-option v-for="c in columns" :key="c.COLUMN_NAME" :label="c.COLUMN_COMMENT||c.COLUMN_NAME" :value="c.COLUMN_NAME"/>
        </el-select>
        <el-input v-model="batchVal" placeholder="新值" size="small" style="width:200px"/>
        <el-button type="warning" size="small" @click="batchUpdate" :disabled="!batchCol||!batchVal">批量修改</el-button>
      </div>

      <!-- 数据表格 -->
      <div style="overflow-x:auto">
        <el-table :data="rows" stripe border size="small" max-height="500"
          @selection-change="sel=>selectedRows=sel.map(r=>r.id)"
          @cell-click="(row,col)=>{if(isAdmin&&!readOnly&&col.property!=='_sel')editingCell={row:row.id,col:col.property,val:row[col.property]}}">
          <el-table-column type="selection" width="45" prop="_sel" v-if="isAdmin&&!readOnly"/>
          <el-table-column v-for="c in columns" :key="c.COLUMN_NAME" :prop="c.COLUMN_NAME" :label="c.COLUMN_COMMENT||c.COLUMN_NAME"
            :width="c.COLUMN_NAME==='id'?60:c.DATA_TYPE&&c.DATA_TYPE.includes('text')?280:140"
            :sortable="isAdmin?'custom':false" @sort-change="({prop,order})=>{sort=prop;sortOrder=order||'asc';loadData()}">
            <template #default="{row}">
              <div v-if="isAdmin&&!readOnly&&editingCell&&editingCell.row===row.id&&editingCell.col===c.COLUMN_NAME" style="display:flex;gap:4px">
                <el-input v-model="editingCell.val" size="small" @keyup.enter="saveCell(row.id,c.COLUMN_NAME,editingCell.val)"/>
                <el-button size="small" type="primary" @click="saveCell(row.id,c.COLUMN_NAME,editingCell.val)">✓</el-button>
                <el-button size="small" @click="editingCell=null">✗</el-button>
              </div>
              <span v-else :title="row[c.COLUMN_NAME]" :style="{cursor:isAdmin?'pointer':'default'}">{{ formatVal(row[c.COLUMN_NAME]) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" v-if="isAdmin&&!readOnly">
            <template #default="{row}"><el-button size="small" type="danger" @click="deleteRow(row.id)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </div>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="loadData"/>
      </div>
    </el-card>

    <!-- 新增行对话框 -->
    <el-dialog v-model="newRowVisible" title="新增数据行" width="600px">
      <el-form label-width="120px" v-if="newRowVisible">
        <el-form-item v-for="c in columns" :key="c.COLUMN_NAME" :label="c.COLUMN_COMMENT||c.COLUMN_NAME">
          <el-input v-if="c.COLUMN_NAME!=='id'" v-model="newRow[c.COLUMN_NAME]" :placeholder="c.DATA_TYPE" size="small"/>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="newRowVisible=false">取消</el-button><el-button type="primary" @click="submitNewRow">确认新增</el-button></template>
    </el-dialog>

  </div>
</template>
<script setup>
import { ref,reactive,onMounted } from 'vue';import axios from '@/api/request';import { ElMessage,ElMessageBox } from 'element-plus'
const tables=ref([]);const currentTable=ref(null);const columns=ref([]);const rows=ref([])
const page=ref(1);const size=ref(50);const total=ref(0);const keyword=ref('');const sort=ref(null);const sortOrder=ref('asc')
const selectedRows=ref([]);const editingCell=ref(null);const readOnly=ref(false);const isAdmin=ref(false)
const batchCol=ref('');const batchVal=ref('')

// 新增行
const newRowVisible=ref(false);const newRow=reactive({})
function checkAdmin(){try{const p=JSON.parse(localStorage.getItem('permissions')||'[]');isAdmin.value=p.includes('admin:user')}catch{isAdmin.value=false}}

async function loadTables(){try{const{data}=await axios.get('/admin/data-tables/tables');tables.value=data||[]}catch(e){console.error(e)}}
async function openTable(row){currentTable.value=row.TABLE_NAME;page.value=1;keyword.value='';await loadData()}
async function loadData(){
  if(!currentTable.value)return
  try{const{data}=await axios.get(`/admin/data-tables/${currentTable.value}`,{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,sort:sort.value,order:sortOrder.value}})
    columns.value=data.columns||[];rows.value=data.rows||[];total.value=data.total||0;readOnly.value=!!data.readOnly
  }catch(e){ElMessage.error('查询失败: '+(e?.response?.data?.msg||e.message))}
}

// 单元格编辑
async function saveCell(rowId,col,val){
  try{await axios.put(`/admin/data-tables/${currentTable.value}/${rowId}`,{[col]:val});ElMessage.success('已更新');editingCell.value=null;loadData()}catch(e){ElMessage.error('更新失败:'+(e?.response?.data?.msg||e.message))}
}
async function deleteRow(id){
  try{await ElMessageBox.confirm('确认删除？','警告',{type:'warning'});await axios.delete(`/admin/data-tables/${currentTable.value}/${id}`);ElMessage.success('已删除');loadData()}catch{}
}
async function batchUpdate(){
  if(!batchCol.value||!batchVal.value){ElMessage.warning('请选择字段和输入新值');return}
  try{await ElMessageBox.confirm(`将修改${selectedRows.value.length}行，确认？`,'批量修改',{type:'warning'})
    await axios.put(`/admin/data-tables/${currentTable.value}/batch`,{ids:selectedRows.value,updates:{[batchCol.value]:batchVal.value}})
    ElMessage.success('批量修改完成');batchCol.value='';batchVal.value='';selectedRows.value=[];loadData()
  }catch{}
}
async function batchDelete(){
  try{await ElMessageBox.confirm(`将删除${selectedRows.value.length}行，不可恢复！`,'危险操作',{type:'error'})
    await axios.delete(`/admin/data-tables/${currentTable.value}/rows/batch`,{data:selectedRows.value})
    ElMessage.success('批量删除完成');selectedRows.value=[];loadData()
  }catch{}
}

// 新增行
function openNewRow(){
  const obj={};columns.value.forEach(c=>{if(c.COLUMN_NAME!=='id')obj[c.COLUMN_NAME]=''})
  Object.assign(newRow,obj);newRowVisible.value=true
}
async function submitNewRow(){
  try{await axios.post(`/admin/data-tables/${currentTable.value}`,{...newRow});ElMessage.success('新增成功');newRowVisible.value=false;loadData()}catch(e){ElMessage.error('新增失败: '+(e?.response?.data?.msg||e.message))}
}

// 导出
function handleExport(format){
  const url=`/admin/data-tables/${currentTable.value}/export?format=${format}`
  const ext=format==='xlsx'?'xlsx':'csv'
  const mime=ext==='xlsx'?'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':'text/csv'
  axios.get(url,{responseType:'blob'}).then(r=>{
    const blob=new Blob([r.data],{type:mime});const a=document.createElement('a')
    a.href=URL.createObjectURL(blob);a.download=`${currentTable.value}_${new Date().toISOString().slice(0,10)}.${ext}`;a.click()
  }).catch(e=>ElMessage.error('导出失败: '+(e?.response?.data?.msg||e.message)))
}

function formatVal(v){if(v===null||v===undefined)return'<空>';if(typeof v==='object')return JSON.stringify(v).slice(0,50);const s=String(v);return s.length>80?s.slice(0,80)+'...':s}

onMounted(()=>{checkAdmin();loadTables()})
</script>
<style scoped>.data-tables{padding:20px}</style>
