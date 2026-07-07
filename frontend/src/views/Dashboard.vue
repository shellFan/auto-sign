<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #409eff">
              <el-icon :size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.total }}</div>
              <div class="stat-label">今日签到总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #67c23a">
              <el-icon :size="32"><SuccessFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.success }}</div>
              <div class="stat-label">今日成功数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #f56c6c">
              <el-icon :size="32"><CircleCloseFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.fail }}</div>
              <div class="stat-label">今日失败数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #e6a23c">
              <el-icon :size="32"><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.needVerify }}</div>
              <div class="stat-label">需要人工验证</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card>
          <template #header>
            <span>最近执行记录</span>
          </template>
          <el-table :data="records" style="width: 100%">
            <el-table-column prop="platform" label="平台" width="100">
              <template #default="{ row }">
                <el-tag :type="row.platform === 'tieba' ? 'primary' : 'danger'">
                  {{ row.platform === 'tieba' ? '百度贴吧' : '微博' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="accountName" label="账号" width="120" />
            <el-table-column prop="signDate" label="日期" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="successCount" label="成功" width="80" />
            <el-table-column prop="failCount" label="失败" width="80" />
            <el-table-column prop="message" label="消息" />
            <el-table-column prop="createTime" label="执行时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/account')">
              <el-icon><Plus /></el-icon>
              添加账号
            </el-button>
            <el-button type="success" @click="$router.push('/weibo-topic')">
              <el-icon><Plus /></el-icon>
              添加超话
            </el-button>
            <el-button type="warning" @click="runAllTasks">
              <el-icon><VideoPlay /></el-icon>
              立即执行全部
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'

const stats = ref({
  total: 0,
  success: 0,
  fail: 0,
  needVerify: 0
})

const records = ref([])

const getStatusType = (status) => {
  const types = {
    0: 'info',
    1: 'warning',
    2: 'success',
    3: 'warning',
    4: 'danger',
    5: 'danger',
    6: 'warning'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    0: '未执行',
    1: '执行中',
    2: '成功',
    3: '部分成功',
    4: '失败',
    5: '需重新登录',
    6: '需人工验证'
  }
  return texts[status] || '未知'
}

const loadData = async () => {
  try {
    const res = await request.get('/sign/record/today')
    records.value = res.data || []
    
    // 计算统计
    stats.value.total = records.value.length
    stats.value.success = records.value.filter(r => r.status === 2).length
    stats.value.fail = records.value.filter(r => r.status === 4).length
    stats.value.needVerify = records.value.filter(r => r.status === 6).length
  } catch (error) {
    console.error('加载数据失败', error)
  }
}

const runAllTasks = async () => {
  try {
    await request.post('/sign/task/run-all')
    ElMessage.success('已触发全部任务')
    loadData()
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
}

.stat-item {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-right: 16px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-actions .el-button {
  width: 100%;
}
</style>
