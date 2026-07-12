<template>
  <div class="category"><h2>分类管理</h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="业务分类" name="categories">
        <el-card><el-table :data="categories" stripe>
          <el-table-column prop="id" label="ID" width="80"/><el-table-column prop="name" label="分类名称" min-width="160"/><el-table-column prop="code" label="编码" width="120"/><el-table-column prop="sort" label="排序" width="80"/>
          <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{ row.status===1?'启用':'禁用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="120"><template #default="{row}"><el-button size="small" @click="editCategory(row)">编辑</el-button><el-button size="small" type="danger" @click="toggleCategory(row)">{{ row.status===1?'禁用':'启用' }}</el-button></template></el-table-column>
        </el-table></el-card>
      </el-tab-pane>

      <el-tab-pane label="映射规则" name="mappings">
        <el-card>
          <div style="margin-bottom:12px;display:flex;gap:10px;flex-wrap:wrap"><el-select v-model="mappingFilter" placeholder="按分类筛选" clearable @change="loadMappings" style="width:200px"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/></el-select><el-input v-model="mappingSearch" placeholder="搜索国标名/关键词" clearable style="width:200px" @keyup.enter="loadMappings"/><el-button @click="loadMappings">搜索</el-button><el-input v-model="testGbName" placeholder="测试用国标分类名" style="width:180px"/><el-button @click="testClassify">测试分类</el-button><el-button type="primary" @click="mappingForm={};mappingVisible=true">+ 新增规则</el-button></div>
          <el-alert v-if="classifyResult" :title="classifyResult" type="info" :closable="false" style="margin-bottom:12px"/>
          <el-table :data="mappings" stripe>
            <el-table-column prop="id" label="ID" width="80"/><el-table-column prop="gbCategoryName" label="国标分类名" min-width="160"/><el-table-column prop="keyword" label="关键词" width="120"/><el-table-column label="目标分类" width="140"><template #default="{row}">{{ catName(row.categoryId) }}</template></el-table-column>
            <el-table-column prop="priority" label="优先级" width="80"/><el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.isActive===1?'success':'info'">{{ row.isActive===1?'启用':'禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="240" fixed="right"><template #default="{row}"><div style="white-space:nowrap"><el-button size="small" @click="editMapping(row)">编辑</el-button><el-button size="small" @click="toggleMapping(row)">{{ row.isActive===1?'禁用':'启用' }}</el-button><el-button size="small" type="danger" @click="deleteMapping(row.id)">删除</el-button></div></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 映射规则编辑对话框 -->
    <el-dialog v-model="mappingVisible" :title="mappingForm.id?'编辑规则':'新增规则'" width="500px"><el-form :model="mappingForm" label-width="100px">
      <el-form-item label="国标分类名" required><el-input v-model="mappingForm.gbCategoryName"/></el-form-item>
      <el-form-item label="匹配关键词" required><el-input v-model="mappingForm.keyword" placeholder="用于包含匹配的关键词"/></el-form-item>
      <el-form-item label="目标分类" required><el-select v-model="mappingForm.categoryId" style="width:100%"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/></el-select></el-form-item>
      <el-form-item label="优先级"><el-input-number v-model="mappingForm.priority" :min="1" :max="999"/></el-form-item>
    </el-form><template #footer><el-button @click="mappingVisible=false">取消</el-button><el-button type="primary" @click="saveMapping">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref,onMounted } from 'vue';import axios from '@/api/request';import { ElMessage,ElMessageBox } from 'element-plus'
const tab=ref('categories');const categories=ref([]);const mappings=ref([]);const mappingFilter=ref(null)
const mappingForm=ref({});const mappingVisible=ref(false);const testGbName=ref('');const classifyResult=ref('');const mappingSearch=ref('')

function catName(id){const c=categories.value.find(x=>x.id===id);return c?c.name:'未知分类#'+id}
async function loadCategories(){try{const{data}=await axios.get('/categories');categories.value=data||[]}catch{}}
async function loadMappings(){try{const{data}=await axios.get('/categories/mappings',{params:{categoryId:mappingFilter.value,keyword:mappingSearch.value||undefined}});mappings.value=data||[]}catch{}}
function editMapping(row){mappingForm.value={...row};mappingVisible.value=true}
function editCategory(row){mappingForm.value={id:row.id,name:row.name,code:row.code,sort:row.sort,isEdit:true};mappingVisible.value=true}
async function saveMapping(){try{if(mappingForm.value.isEdit){await axios.put('/devices/'+(mappingForm.value.id),{name:mappingForm.value.name});ElMessage.success('已更新')}else if(mappingForm.value.id){await axios.put('/categories/mappings/'+mappingForm.value.id,mappingForm.value);ElMessage.success('已更新');loadMappings()}else{await axios.post('/categories/mappings',mappingForm.value);ElMessage.success('已创建');loadMappings()};mappingVisible.value=false}catch(e){ElMessage.error(e?.response?.data?.msg||'操作失败')}}
async function toggleCategory(row){try{await axios.put('/devices/'+row.id,{status:row.status===1?0:1});loadCategories()}catch(e){}}
async function toggleMapping(row){try{await axios.put('/categories/mappings/'+row.id+'/toggle');loadMappings()}catch{}}
async function deleteMapping(id){try{await ElMessageBox.confirm('确认删除此映射规则？');await axios.delete('/categories/mappings/'+id);loadMappings()}catch{}}
async function testClassify(){try{const{data}=await axios.get('/categories/classify',{params:{gbName:testGbName.value}});classifyResult.value=data;setTimeout(()=>classifyResult.value='',8000)}catch{}}
onMounted(()=>{loadCategories();loadMappings()})
</script>
<style scoped>.category{padding:20px}</style>
