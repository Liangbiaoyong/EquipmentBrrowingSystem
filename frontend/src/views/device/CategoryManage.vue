<template>
  <div class="category"><h2>分类管理</h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="业务分类" name="categories">
        <el-card>
          <div style="margin-bottom:12px;display:flex;gap:8px">
            <el-button type="primary" @click="openCreateCategory">+ 新增分类</el-button>
            <el-button @click="openBatchCategories">批量预置分类</el-button>
            <span style="margin-left:auto;color:#909399;font-size:12px">共 {{ categories.length }} 个分类</span>
          </div>
          <el-table :data="categories" stripe>
            <el-table-column prop="id" label="ID" width="80"/>
            <el-table-column prop="name" label="分类名称" min-width="160"/>
            <el-table-column prop="code" label="编码" width="120"/>
            <el-table-column prop="sort" label="排序" width="80"/>
            <el-table-column label="状态" width="80">
              <template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{ row.status===1?'启用':'禁用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{row}">
                <div style="white-space:nowrap">
                  <el-button size="small" @click="editCategory(row)">编辑</el-button>
                  <el-button size="small" :type="row.status===1?'danger':''" @click="toggleCategory(row)">{{ row.status===1?'禁用':'启用' }}</el-button>
                  <el-popconfirm title="确定删除此分类？" @confirm="deleteCategory(row.id)">
                    <template #reference><el-button size="small" type="danger">删除</el-button></template>
                  </el-popconfirm>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="映射规则" name="mappings">
        <el-card>
          <div style="margin-bottom:12px;display:flex;gap:10px;flex-wrap:wrap">
            <el-select v-model="mappingFilter" placeholder="按分类筛选" clearable @change="loadMappings" style="width:200px">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
            </el-select>
            <el-input v-model="mappingSearch" placeholder="搜索国标名/关键词" clearable style="width:200px" @keyup.enter="loadMappings"/>
            <el-button @click="loadMappings">搜索</el-button>
            <el-input v-model="testGbName" placeholder="测试用国标分类名" style="width:180px"/>
            <el-button @click="testClassify">测试分类</el-button>
            <el-button type="primary" @click="openMappingForm(null)">+ 新增规则</el-button>
          </div>
          <el-alert v-if="classifyResult" :title="classifyResult" type="info" :closable="false" style="margin-bottom:12px"/>
          <el-table :data="mappings" stripe>
            <el-table-column prop="id" label="ID" width="80"/>
            <el-table-column prop="gbCategoryName" label="国标分类名" min-width="160"/>
            <el-table-column prop="keyword" label="关键词" width="120"/>
            <el-table-column label="目标分类" width="140"><template #default="{row}">{{ catName(row.categoryId) }}</template></el-table-column>
            <el-table-column prop="priority" label="优先级" width="80"/>
            <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.isActive===1?'success':'info'">{{ row.isActive===1?'启用':'禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{row}">
                <div style="white-space:nowrap">
                  <el-button size="small" @click="openMappingForm(row)">编辑</el-button>
                  <el-button size="small" @click="toggleMapping(row)">{{ row.isActive===1?'禁用':'启用' }}</el-button>
                  <el-button size="small" type="danger" @click="deleteMapping(row.id)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑分类对话框 -->
    <el-dialog v-model="catDlg.visible" :title="catDlg.isEdit?'编辑分类':'新增分类'" width="450px" destroy-on-close>
      <el-form :model="catDlg.form" label-width="100px">
        <el-form-item label="分类名称" required><el-input v-model="catDlg.form.name" placeholder="如: 计算机及外设"/></el-form-item>
        <el-form-item label="编码"><el-input v-model="catDlg.form.code" placeholder="如: COMPUTER（可选英文缩写）"/></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catDlg.form.sort" :min="0" :max="999"/></el-form-item>
        <el-form-item label="状态"><el-switch v-model="catDlg.form._status" active-value="1" inactive-value="0"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDlg.visible=false">取消</el-button>
        <el-button type="primary" @click="saveCategory" :loading="catDlg.loading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 映射规则编辑对话框 -->
    <el-dialog v-model="mappingVisible" :title="mappingForm.id?'编辑规则':'新增规则'" width="540px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px;line-height:1.6">
        <strong>规则说明：</strong>导入时对每行资产的"国标分类名"做关键词包含匹配，命中则归入对应业务分类。<br/>
        <strong>匹配方式：</strong>系统使用 <code>keyword</code> 做 <code>String.contains()</code> 包含匹配（非精确匹配）。<br/>
        <strong>国标分类名后缀约定：</strong><br/>
        - <code>-泛匹配</code>：关键词较短，容错性好，适合大类（如"计算机"可匹配"台式计算机""笔记本电脑"）<br/>
        - <code>-精确匹配</code>：关键词较长，需完整包含该词才匹配（如"激光打印机-A4"只匹配指定型号）<br/>
        <strong>优先级：</strong>数字越小越优先匹配，规则按优先级升序遍历。
      </el-alert>
      <el-form :model="mappingForm" label-width="110px">
        <el-form-item label="国标分类名" required>
          <el-input v-model="mappingForm.gbCategoryName" placeholder="如: 计算机及外设-泛匹配"/>
        </el-form-item>
        <el-form-item label="匹配关键词" required>
          <el-input v-model="mappingForm.keyword" placeholder="用于包含匹配的关键词（如: 计算机）"/>
        </el-form-item>
        <el-form-item label="目标分类" required>
          <el-select v-model="mappingForm.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="mappingForm.priority" :min="1" :max="999"/>
          <div style="font-size:11px;color:#909399;margin-top:2px">数值越小越优先匹配</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingVisible=false">取消</el-button>
        <el-button type="primary" @click="saveMapping" :loading="mappingLoading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量预置分类对话框 -->
    <el-dialog v-model="batchDlg.visible" title="批量预置分类" width="600px">
      <p style="color:#909399;margin-bottom:10px;font-size:13px">
        勾选需要添加的分类，点确认将一次性创建（已存在同名的跳过）：
      </p>
      <el-checkbox-group v-model="batchDlg.selected">
        <el-checkbox v-for="c in batchDlg.list" :key="c.name" :label="c.name" border style="margin:4px">
          <div style="display:flex;gap:8px;align-items:center">
            <span>{{ c.name }}</span>
            <span style="font-size:11px;color:#909399">{{ c.code }}</span>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="batchDlg.visible=false">取消</el-button>
        <el-button type="primary" @click="doBatchCategories" :loading="batchDlg.loading">确认添加（{{ batchDlg.selected.length }}项）</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import axios from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('categories')
const categories = ref([])
const mappings = ref([])
const mappingFilter = ref(null)
const mappingForm = ref({})
const mappingVisible = ref(false)
const mappingLoading = ref(false)
const testGbName = ref('')
const classifyResult = ref('')
const mappingSearch = ref('')
const catDlg = reactive({ visible: false, isEdit: false, form: { name: '', code: '', sort: 0, _status: '1' }, loading: false })
const batchDlg = reactive({ visible: false, loading: false, selected: [], list: [] })

// 预置分类列表（15个常用 + 已有10个之外的扩展）
const presetCategories = [
  { name: '计算机及外设', code: 'COMPUTER' },
  { name: '摄影摄像与光学器材', code: 'CAMERA' },
  { name: '音频与广播设备', code: 'AUDIO' },
  { name: '空调与制冷设备', code: 'HVAC' },
  { name: '仪器仪表与测量设备', code: 'INSTRUMENT' },
  { name: '家具与装具', code: 'FURNITURE' },
  { name: '无人机及航模', code: 'UAV' },
  { name: '软件与信息服务', code: 'SOFTWARE' },
  { name: '安全与监控设备', code: 'SECURITY' },
  { name: '其他设备', code: 'OTHER' },
  { name: '办公设备', code: 'OFFICE' },
  { name: '通信设备', code: 'COMMUNICATION' },
  { name: '电子与电工设备', code: 'ELECTRONIC' },
  { name: '机械设备', code: 'MACHINERY' },
  { name: '医疗设备', code: 'MEDICAL' },
  { name: '体育设备', code: 'SPORTS' },
  { name: '图书档案设备', code: 'LIBRARY' },
  { name: '车辆与交通设备', code: 'VEHICLE' },
  { name: '电器设备', code: 'APPLIANCE' },
  { name: '网络设备', code: 'NETWORK' },
]

function catName(id) { const c = categories.value.find(x => x.id === id); return c ? c.name : '未知分类#' + id }

async function loadCategories() {
  try { const { data } = await axios.get('/categories/'); categories.value = data || [] } catch { console.error }
}
async function loadMappings() {
  try { const { data } = await axios.get('/categories/mappings', { params: { categoryId: mappingFilter.value, keyword: mappingSearch.value || undefined } }); mappings.value = data || [] } catch { console.error }
}

// 业务分类 CRUD
function openCreateCategory() {
  catDlg.isEdit = false
  catDlg.form = { name: '', code: '', sort: categories.value.length, _status: '1' }
  catDlg.visible = true
}
function editCategory(row) {
  catDlg.isEdit = true
  catDlg.form = { ...row, _status: String(row.status ?? 1) }
  catDlg.visible = true
}
async function saveCategory() {
  if (!catDlg.form.name) { ElMessage.warning('请输入分类名称'); return }
  catDlg.loading = true
  try {
    const body = { name: catDlg.form.name, code: catDlg.form.code || undefined, sort: catDlg.form.sort ?? 0, status: Number(catDlg.form._status) }
    if (catDlg.isEdit) {
      await axios.put('/categories/' + catDlg.form.id, body)
      ElMessage.success('已更新')
    } else {
      await axios.post('/categories', body)
      ElMessage.success('已创建')
    }
    catDlg.visible = false
    loadCategories()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
  finally { catDlg.loading = false }
}
async function toggleCategory(row) {
  try {
    await axios.put('/categories/' + row.id + '/toggle')
    ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
    loadCategories()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
}
async function deleteCategory(id) {
  try {
    await axios.delete('/categories/' + id)
    ElMessage.success('已删除')
    loadCategories()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '删除失败') }
}

// 批量预置分类
function openBatchCategories() {
  const existNames = new Set(categories.value.map(c => c.name))
  batchDlg.list = presetCategories.filter(c => !existNames.has(c.name))
  batchDlg.selected = batchDlg.list.map(c => c.name)
  batchDlg.visible = true
}
async function doBatchCategories() {
  if (!batchDlg.selected.length) { ElMessage.warning('请选择至少一个分类'); return }
  batchDlg.loading = true
  let ok = 0, fail = 0
  for (const name of batchDlg.selected) {
    const p = presetCategories.find(c => c.name === name)
    try {
      await axios.post('/categories', { name: p.name, code: p.code, sort: 0, status: 1 })
      ok++
    } catch { fail++ }
  }
  ElMessage.success(`添加完成：成功${ok}个${fail ? '，失败' + fail + '个' : ''}`)
  batchDlg.visible = false
  batchDlg.loading = false
  loadCategories()
}

// 映射规则
function openMappingForm(row) {
  if (row) {
    mappingForm.value = { ...row }
  } else {
    mappingForm.value = { gbCategoryName: '', keyword: '', categoryId: null, priority: 100 }
  }
  mappingVisible.value = true
}
async function saveMapping() {
  if (!mappingForm.value.gbCategoryName || !mappingForm.value.keyword || !mappingForm.value.categoryId) {
    ElMessage.warning('请填写完整信息'); return
  }
  mappingLoading.value = true
  try {
    if (mappingForm.value.id) {
      await axios.put('/categories/mappings/' + mappingForm.value.id, mappingForm.value)
      ElMessage.success('已更新')
    } else {
      await axios.post('/categories/mappings', mappingForm.value)
      ElMessage.success('已创建')
    }
    mappingVisible.value = false
    loadMappings()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
  finally { mappingLoading.value = false }
}
async function toggleMapping(row) {
  try {
    await axios.put('/categories/mappings/' + row.id + '/toggle')
    loadMappings()
  } catch { ElMessage.error('操作失败') }
}
async function deleteMapping(id) {
  try {
    await ElMessageBox.confirm('确认删除此映射规则？')
    await axios.delete('/categories/mappings/' + id)
    loadMappings()
  } catch { }
}
async function testClassify() {
  if (!testGbName.value) return
  try {
    const { data } = await axios.get('/categories/classify', { params: { gbName: testGbName.value } })
    classifyResult.value = data
    setTimeout(() => classifyResult.value = '', 8000)
  } catch { }
}

onMounted(() => { loadCategories(); loadMappings() })
</script>
<style scoped>.category{padding:20px}</style>
