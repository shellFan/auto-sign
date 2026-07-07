<template>
  <div class="account-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>平台账号管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加账号
          </el-button>
        </div>
      </template>

      <el-table :data="accounts" style="width: 100%" v-loading="loading">
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'tieba' ? 'primary' : 'danger'">
              {{ row.platform === 'tieba' ? '百度贴吧' : '微博' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="accountName" label="账号备注" width="150" />
        <el-table-column prop="loginStatus" label="登录状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getLoginStatusType(row.loginStatus)">
              {{ getLoginStatusText(row.loginStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="180" />
        <el-table-column prop="lastSignTime" label="最后签到" width="180" />
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
        <el-table-column label="操作" width="350">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openBrowser(row)">
              <el-icon><Monitor /></el-icon>
              浏览器登录
            </el-button>
            <el-button type="warning" size="small" @click="showCookieDialog(row)">
              <el-icon><Document /></el-icon>
              输入Cookie
            </el-button>
            <el-button type="success" size="small" @click="checkLogin(row)">
              <el-icon><Check /></el-icon>
              检查登录
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加账号对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑账号' : '添加账号'" width="500">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台">
            <el-option label="百度贴吧" value="tieba" />
            <el-option label="微博" value="weibo" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号备注" prop="accountName">
          <el-input v-model="form.accountName" placeholder="请输入账号备注" />
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

    <!-- 输入 Cookie 对话框 -->
    <el-dialog v-model="cookieDialogVisible" title="输入 Cookie" width="600">
      <el-alert title="如何获取 Cookie" type="info" :closable="false" style="margin-bottom: 15px">
        <template #default>
          <p>1. 打开浏览器，登录微博/百度贴吧</p>
          <p>2. 按 F12 打开开发者工具</p>
          <p>3. 切换到 "Network"(网络) 标签</p>
          <p>4. 刷新页面，点击任意请求</p>
          <p>5. 在 "Headers"(标头) 中找到 "Cookie" 字段</p>
          <p>6. 复制完整的 Cookie 值粘贴到下方</p>
        </template>
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="账号">
          <el-input :value="currentAccount?.accountName" disabled />
        </el-form-item>
        <el-form-item label="Cookie">
          <el-input
            v-model="cookieValue"
            type="textarea"
            :rows="8"
            placeholder="请粘贴完整的 Cookie 值"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cookieDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCookie" :loading="savingCookie">保存</el-button>
      </template>
    </el-dialog>

    <!-- 浏览器登录对话框 -->
    <el-dialog v-model="browserDialogVisible" title="浏览器登录" width="80%" fullscreen>
      <div class="browser-container">
        <div class="browser-toolbar">
          <el-button type="success" @click="saveLoginState">
            <el-icon><Check /></el-icon>
            保存登录态
          </el-button>
          <el-button type="danger" @click="closeBrowser">
            <el-icon><Close /></el-icon>
            关闭浏览器
          </el-button>
        </div>
        <iframe
          v-if="vncUrl"
          :src="vncUrl"
          class="browser-iframe"
          frameborder="0"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const accounts = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const browserDialogVisible = ref(false)
const cookieDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const savingCookie = ref(false)
const formRef = ref(null)
const vncUrl = ref('')
const currentSessionId = ref('')
const currentAccount = ref(null)
const cookieValue = ref('')

const form = ref({
  platform: '',
  accountName: '',
  remark: ''
})

const rules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  accountName: [{ required: true, message: '请输入账号备注', trigger: 'blur' }]
}

const getLoginStatusType = (status) => {
  const types = { 0: 'info', 1: 'success', 2: 'danger', 3: 'warning' }
  return types[status] || 'info'
}

const getLoginStatusText = (status) => {
  const texts = { 0: '未登录', 1: '已登录', 2: '已失效', 3: '需验证' }
  return texts[status] || '未知'
}

const loadAccounts = async () => {
  loading.value = true
  try {
    const res = await request.get('/account/list')
    accounts.value = res.data || []
  } catch (error) {
    console.error('加载账号失败', error)
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { platform: '', accountName: '', remark: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    submitting.value = true
    if (isEdit.value) {
      await request.put(`/account/${form.value.id}`, form.value)
    } else {
      await request.post('/account/create', form.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
    dialogVisible.value = false
    loadAccounts()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

const handleEnableChange = async (row) => {
  try {
    await request.post(`/account/${row.id}/${row.enableStatus ? 'enable' : 'disable'}`)
    ElMessage.success('操作成功')
  } catch (error) {
    row.enableStatus = row.enableStatus ? 0 : 1
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该账号吗？', '提示', { type: 'warning' })
    await request.delete(`/account/${row.id}`)
    ElMessage.success('删除成功')
    loadAccounts()
  } catch (error) {
    // 取消删除
  }
}

const openBrowser = async (row) => {
  try {
    const res = await request.post('/browser/session/create', { accountId: row.id })
    currentSessionId.value = res.data.sessionId
    vncUrl.value = res.data.vncUrl
    currentAccount.value = row
    browserDialogVisible.value = true
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

const saveLoginState = async () => {
  try {
    await request.post(`/browser/session/${currentSessionId.value}/save`)
    ElMessage.success('登录态已保存')
    browserDialogVisible.value = false
    loadAccounts()
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

const closeBrowser = async () => {
  try {
    await request.post(`/browser/session/${currentSessionId.value}/close`)
    browserDialogVisible.value = false
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

const showCookieDialog = (row) => {
  currentAccount.value = row
  cookieValue.value = ''
  cookieDialogVisible.value = true
}

const saveCookie = async () => {
  if (!cookieValue.value.trim()) {
    ElMessage.warning('请输入 Cookie')
    return
  }

  savingCookie.value = true
  try {
    await request.post(`/account/${currentAccount.value.id}/save-cookie`, {
      cookie: cookieValue.value.trim()
    })
    ElMessage.success('Cookie 保存成功')
    cookieDialogVisible.value = false
    loadAccounts()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    savingCookie.value = false
  }
}

const checkLogin = async (row) => {
  try {
    await request.post(`/account/${row.id}/check-login`)
    ElMessage.success('检查完成')
    loadAccounts()
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

onMounted(() => {
  loadAccounts()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.browser-container {
  height: calc(100vh - 150px);
  display: flex;
  flex-direction: column;
}

.browser-toolbar {
  padding: 10px 0;
  display: flex;
  gap: 10px;
}

.browser-iframe {
  flex: 1;
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>
