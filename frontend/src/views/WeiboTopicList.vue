<template>
  <div class="weibo-topic-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>微博超话管理</span>
          <div>
            <el-button type="warning" @click="initDefault">
              <el-icon><Star /></el-icon>
              初始化默认超话
            </el-button>
            <el-button type="primary" @click="showAddDialog">
              <el-icon><Plus /></el-icon>
              添加超话
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="topics" style="width: 100%" v-loading="loading">
        <el-table-column prop="topicName" label="超话名称" width="150" />
        <el-table-column prop="topicUrl" label="超话链接" min-width="200">
          <template #default="{ row }">
            <el-link :href="row.topicUrl" target="_blank" type="primary">
              {{ row.topicUrl || '未设置' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="lastSignDate" label="最后签到" width="120" />
        <el-table-column prop="lastSignStatus" label="签到状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getSignStatusType(row.lastSignStatus)">
              {{ getSignStatusText(row.lastSignStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
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
            <el-button type="primary" size="small" @click="signTopic(row)">
              <el-icon><VideoPlay /></el-icon>
              签到
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑超话' : '添加超话'" width="500">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="微博账号" prop="accountId">
          <el-select v-model="form.accountId" placeholder="请选择微博账号">
            <el-option
              v-for="account in weiboAccounts"
              :key="account.id"
              :label="account.accountName"
              :value="account.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="超话名称" prop="topicName">
          <el-input v-model="form.topicName" placeholder="请输入超话名称" />
        </el-form-item>
        <el-form-item label="超话链接">
          <el-input v-model="form.topicUrl" placeholder="请输入超话链接（建议填写）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
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

const topics = ref([])
const weiboAccounts = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({
  id: null,
  accountId: '',
  topicName: '',
  topicUrl: '',
  sort: 0,
  remark: ''
})

const rules = {
  accountId: [{ required: true, message: '请选择微博账号', trigger: 'change' }],
  topicName: [{ required: true, message: '请输入超话名称', trigger: 'blur' }]
}

const getSignStatusType = (status) => {
  const types = { 0: 'info', 1: 'success', 2: 'success', 3: 'danger', 4: 'warning' }
  return types[status] || 'info'
}

const getSignStatusText = (status) => {
  const texts = { 0: '未执行', 1: '成功', 2: '已签到', 3: '失败', 4: '需验证' }
  return texts[status] || '未知'
}

const loadTopics = async () => {
  loading.value = true
  try {
    const res = await request.get('/weibo/topic/list')
    topics.value = res.data || []
  } catch (error) {
    console.error('加载超话失败', error)
  } finally {
    loading.value = false
  }
}

const loadWeiboAccounts = async () => {
  try {
    const res = await request.get('/account/list')
    weiboAccounts.value = (res.data || []).filter(a => a.platform === 'weibo')
  } catch (error) {
    console.error('加载账号失败', error)
  }
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { id: null, accountId: '', topicName: '', topicUrl: '', sort: 0, remark: '' }
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
      await request.put(`/weibo/topic/${form.value.id}`, form.value)
    } else {
      await request.post('/weibo/topic/create', form.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
    dialogVisible.value = false
    loadTopics()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

const handleEnableChange = async (row) => {
  try {
    await request.post(`/weibo/topic/${row.id}/${row.enableStatus ? 'enable' : 'disable'}`)
    ElMessage.success('操作成功')
  } catch (error) {
    row.enableStatus = row.enableStatus ? 0 : 1
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该超话吗？', '提示', { type: 'warning' })
    await request.delete(`/weibo/topic/${row.id}`)
    ElMessage.success('删除成功')
    loadTopics()
  } catch (error) {
    // 取消删除
  }
}

const signTopic = async (row) => {
  try {
    await request.post(`/weibo/topic/${row.id}/sign`)
    ElMessage.success('签到任务已触发')
    loadTopics()
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

const initDefault = async () => {
  try {
    if (weiboAccounts.value.length === 0) {
      ElMessage.warning('请先添加微博账号')
      return
    }
    await ElMessageBox.confirm('将为第一个微博账号初始化默认超话（易烊千玺、子瑜），确定吗？', '提示', { type: 'info' })
    await request.post('/weibo/topic/init-default', {
      userId: null,
      accountId: weiboAccounts.value[0].id
    })
    ElMessage.success('初始化成功')
    loadTopics()
  } catch (error) {
    // 取消或失败
  }
}

onMounted(() => {
  loadTopics()
  loadWeiboAccounts()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
