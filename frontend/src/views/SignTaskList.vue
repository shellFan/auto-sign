<template>
  <div class="sign-task-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>签到任务管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            新增任务
          </el-button>
        </div>
      </template>

      <el-table :data="tasks" style="width: 100%" v-loading="loading">
        <el-table-column prop="taskName" label="任务名称" width="200" />
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'tieba' ? 'primary' : 'danger'">
              {{ row.platform === 'tieba' ? '百度贴吧' : '微博' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpr" label="执行时间" width="150" />
        <el-table-column prop="lastRunTime" label="最后执行" width="180" />
        <el-table-column prop="enableStatus" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.enableStatus"
              :active-value="1"
              :inactive-value="0"
              @change="handleEnableChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="runTask(row)">
              <el-icon><VideoPlay /></el-icon>
              立即执行
            </el-button>
            <el-button type="warning" size="small" @click="showEditDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑任务' : '新增任务'" width="500">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="平台账号" prop="accountId">
          <el-select v-model="form.accountId" placeholder="请选择平台账号">
            <el-option
              v-for="account in accounts"
              :key="account.id"
              :label="`${account.accountName} (${account.platform === 'tieba' ? '百度贴吧' : '微博'})`"
              :value="account.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="执行时间" prop="cronExpr">
          <el-input v-model="form.cronExpr" placeholder="Cron表达式，如：0 10 8 * * ?" />
          <div class="cron-help">
            <p>常用示例：</p>
            <p>每天8点10分：0 10 8 * * ?</p>
            <p>每天8点20分：0 20 8 * * ?</p>
            <p>每天8点30分：0 30 8 * * ?</p>
          </div>
        </el-form-item>
        <el-form-item label="重试次数">
          <el-input-number v-model="form.retryCount" :min="0" :max="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const tasks = ref([])
const accounts = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({
  id: null,
  accountId: '',
  taskName: '',
  cronExpr: '',
  retryCount: 0
})

const rules = {
  accountId: [{ required: true, message: '请选择平台账号', trigger: 'change' }],
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  cronExpr: [{ required: true, message: '请输入执行时间', trigger: 'blur' }]
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await request.get('/sign/task/list')
    tasks.value = res.data || []
  } catch (error) {
    console.error('加载任务失败', error)
  } finally {
    loading.value = false
  }
}

const loadAccounts = async () => {
  try {
    const res = await request.get('/account/list')
    accounts.value = res.data || []
  } catch (error) {
    console.error('加载账号失败', error)
  }
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { id: null, accountId: '', taskName: '', cronExpr: '', retryCount: 0 }
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    submitting.value = true
    if (isEdit.value) {
      await request.put(`/sign/task/${form.value.id}`, form.value)
    } else {
      await request.post('/sign/task/create', form.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    loadTasks()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

const handleEnableChange = async (row) => {
  try {
    await request.post(`/sign/task/${row.id}/${row.enableStatus ? 'enable' : 'disable'}`)
    ElMessage.success('操作成功')
  } catch (error) {
    row.enableStatus = row.enableStatus ? 0 : 1
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该任务吗？', '提示', { type: 'warning' })
    await request.delete(`/sign/task/${row.id}`)
    ElMessage.success('删除成功')
    loadTasks()
  } catch (error) {
    // 取消删除
  }
}

const runTask = async (row) => {
  try {
    await request.post(`/sign/task/${row.id}/run`)
    ElMessage.success('任务已触发')
    loadTasks()
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

onMounted(() => {
  loadTasks()
  loadAccounts()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cron-help {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.cron-help p {
  margin: 4px 0;
}
</style>
