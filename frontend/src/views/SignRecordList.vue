<template>
  <div class="sign-record-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>签到记录</span>
          <div class="filter-bar">
            <el-select v-model="filters.platform" placeholder="平台" clearable style="width: 120px">
              <el-option label="百度贴吧" value="tieba" />
              <el-option label="微博" value="weibo" />
            </el-select>
            <el-date-picker
              v-model="filters.signDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 150px"
            />
            <el-button type="primary" @click="loadRecords">
              <el-icon><Search /></el-icon>
              查询
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="records" style="width: 100%" v-loading="loading">
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'tieba' ? 'primary' : 'danger'">
              {{ row.platform === 'tieba' ? '百度贴吧' : '微博' }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column prop="message" label="消息" min-width="150" />
        <el-table-column prop="screenshotPath" label="截图" width="80">
          <template #default="{ row }">
            <el-button v-if="row.screenshotPath" type="primary" link @click="viewScreenshot(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetails(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadRecords"
        @current-change="loadRecords"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="签到详情" width="800">
      <el-table :data="details" style="width: 100%">
        <el-table-column prop="targetName" label="目标名称" width="150" />
        <el-table-column prop="targetType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.targetType === 'tieba'">贴吧</el-tag>
            <el-tag v-else-if="row.targetType === 'super_topic'" type="danger">超话</el-tag>
            <el-tag v-else type="info">{{ row.targetType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getDetailStatusType(row.status)">
              {{ getDetailStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" />
        <el-table-column prop="screenshotPath" label="截图" width="80">
          <template #default="{ row }">
            <el-button v-if="row.screenshotPath" type="primary" link @click="viewDetailScreenshot(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 截图预览 -->
    <el-dialog v-model="screenshotDialogVisible" title="截图预览" width="80%">
      <img v-if="currentScreenshot" :src="currentScreenshot" class="screenshot-img" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../utils/request'

const records = ref([])
const details = ref([])
const loading = ref(false)
const detailDialogVisible = ref(false)
const screenshotDialogVisible = ref(false)
const currentScreenshot = ref('')

const filters = reactive({
  platform: '',
  signDate: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const getStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'warning', 4: 'danger', 5: 'danger', 6: 'warning' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '未执行', 1: '执行中', 2: '成功', 3: '部分成功', 4: '失败', 5: '需重新登录', 6: '需人工验证' }
  return texts[status] || '未知'
}

const getDetailStatusType = (status) => {
  const types = { 0: 'danger', 1: 'success', 2: 'success', 3: 'info', 4: 'warning' }
  return types[status] || 'info'
}

const getDetailStatusText = (status) => {
  const texts = { 0: '失败', 1: '成功', 2: '已签到', 3: '跳过', 4: '需人工' }
  return texts[status] || '未知'
}

const loadRecords = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...filters
    }
    const res = await request.get('/sign/record/list', { params })
    records.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    console.error('加载记录失败', error)
  } finally {
    loading.value = false
  }
}

const viewDetails = async (row) => {
  try {
    const res = await request.get(`/sign/record/${row.id}/details`)
    details.value = res.data || []
    detailDialogVisible.value = true
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

const viewScreenshot = (row) => {
  currentScreenshot.value = `/api/screenshots/${row.screenshotPath}`
  screenshotDialogVisible.value = true
}

const viewDetailScreenshot = (row) => {
  currentScreenshot.value = `/api/screenshots/${row.screenshotPath}`
  screenshotDialogVisible.value = true
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  gap: 10px;
}

.screenshot-img {
  width: 100%;
  max-height: 600px;
  object-fit: contain;
}
</style>
