<template>
  <div class="data-tables"><h2>数据库表管理</h2>
    <p style="color:#909399;margin-bottom:15px">浏览和编辑数据库表数据。实验室管理员仅可查看设备相关表。</p>

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
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span><el-button text @click="currentTable=null"><el-icon><ArrowLeft/></el-icon> 返回</el-button> <b>{{ currentTable }}</b> (共{{ total }}条)</span>
          <div style="display:flex;gap:8px">
            <el-input v-model="keyword" placeholder="搜索..." clearable style="width:200px" @keyup.enter="loadData"/>
            <el-button @click="loadData">查询</el-button>
            <el-button type="danger" v-if="selectedRows.length&&!readOnly" @click="batchDelete" :disabled="readOnly">批量删除({{selectedRows.length}})</el-button>
          </div>
        </div>
      </template>

      <!-- 批量编辑区域 -->
      <div v-if="selectedRows.length&&!readOnly" style="margin-bottom:12px;padding:10px;background:#f5f7fa;border-radius:4px">
        <span style="margin-right:8px">已选 {{selectedRows.length}} 行：</span>
        <el-select v-model="batchCol" placeholder="选择字段" size="small" style="width:150px">
          <el-option v-for="c in columns" :key="c.COLUMN_NAME" :label="c.COLUMN_COMMENT||c.COLUMN_NAME" :value="c.COLUMN_NAME"/>
        </el-select>
        <el-input v-model="batchVal" placeholder="新值" size="small" style="width:200px;margin-left:8px"/>
        <el-button type="warning" size="small" @click="batchUpdate" style="margin-left:8px" :disabled="!batchCol||!batchVal">批量修改</el-button>
      </div>

      <!-- 数据表格 -->
      <div style="overflow-x:auto">
        <el-table :data="rows" stripe border size="small" max-height="500"
          @selection-change="sel=>selectedRows=sel.map(r=>r.id)"
          @cell-click="(row,col)=>{if(col.property!=='_sel')editingCell={row:row.id,col:col.property,val:row[col.property]}}">
          <el-table-column type="selection" width="45" prop="_sel" v-if="!readOnly"/>
          <el-table-column v-for="c in columns" :key="c.COLUMN_NAME" :prop="c.COLUMN_NAME" :label="c.COLUMN_COMMENT||c.COLUMN_NAME"
            :width="c.COLUMN_NAME==='id'?60:c.DATA_TYPE&&c.DATA_TYPE.includes('text')?300:140"
            :sortable="'custom'" @sort-change="({prop,order})=>{sort=prop;sortOrder=order||'asc';loadData()}">
            <template #default="{row}">
              <div v-if="editingCell&&editingCell.row===row.id&&editingCell.col===c.COLUMN_NAME" style="display:flex;gap:4px">
                <el-input v-model="editingCell.val" size="small" @keyup.enter="saveCell(row.id,c.COLUMN_NAME,editingCell.val)"/>
                <el-button size="small" type="primary" @click="saveCell(row.id,c.COLUMN_NAME,editingCell.val)">✓</el-button>
                <el-button size="small" @click="editingCell=null">✗</el-button>
              </div>
              <span v-else :title="row[c.COLUMN_NAME]" style="cursor:pointer">{{ formatVal(row[c.COLUMN_NAME]) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" v-if="!readOnly">
            <template #default="{row}"><el-button size="small" type="danger" @click="deleteRow(row.id)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </div>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total,prev,pager,next" @current-change="loadData"/>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { ref,reactive } from 'vue';import axios from '@/api/request';import { ElMessage,ElMessageBox } from 'element-plus'
const tables=ref([]);const currentTable=ref(null);const columns=ref([]);const rows=ref([])
const page=ref(1);const size=ref(50);const total=ref(0);const keyword=ref('');const sort=ref(null);const sortOrder=ref('asc')
const selectedRows=ref([]);const editingCell=ref(null);const readOnly=ref(false)
const batchCol=ref('');const batchVal=ref('')

async function loadTables(){try{const{data}=await axios.get('/admin/data-tables/tables');tables.value=data||[]}catch(e){console.error(e)}}
async function openTable(row){currentTable.value=row.TABLE_NAME;page.value=1;keyword.value='';await loadData()}
async function loadData(){
  if(!currentTable.value)return
  try{const{data}=await axios.get(`/admin/data-tables/${currentTable.value}`,{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,sort:sort.value,order:sortOrder.value}})
    columns.value=data.columns||[];rows.value=data.rows||[];total.value=data.total||0;readOnly.value=data.readOnly||false
  }catch(e){ElMessage.error('查询失败: '+(e?.response?.data?.msg||e.message))}
}

async function saveCell(rowId,col,val){
  try{await axios.put(`/admin/data-tables/${currentTable.value}/${rowId}`,{[col]:val});ElMessage.success('已更新');editingCell.value=null;loadData()}catch(e){ElMessage.error('更新失败:'+e?.response?.data?.msg||e.message)}
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
    for(const id of selectedRows.value){await axios.delete(`/admin/data-tables/${currentTable.value}/${id}`)}
    ElMessage.success('批量删除完成');selectedRows.value=[];loadData()
  }catch{}
}
function formatVal(v){if(v===null||v===undefined)return '<空>';if(typeof v==='object')return JSON.stringify(v).slice(0,50);const s=String(v);return s.length>80?s.slice(0,80)+'...':s}
loadTables()
</script>
<style scoped>.data-tables{padding:20px}</style>
