<template>
  <div class="task-control">
    <section class="page-header">
      <div>
        <div class="eyebrow">每日自动任务</div>
        <h2>任务进度</h2>
        <p class="description">
          北京时间 08:00—22:00 由后台自动排队执行，关闭本页面不会中断任务。
          完成数只统计执行成功的任务。
        </p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="planCheckLoading" @click="ensureTodayPlan">
          检查/补建今日计划
        </el-button>
        <el-button :loading="refreshing" @click="refreshData">刷新</el-button>
      </div>
    </section>

    <div class="fetch-meta">
      <span v-if="lastFetchedAt">最近更新：{{ formatClock(lastFetchedAt) }}</span>
      <span v-else>尚未获取数据</span>
      <span v-if="refreshing && !initialLoading" class="refresh-note">正在刷新…</span>
    </div>

    <el-alert v-if="refreshError" class="stale-alert" type="warning" :closable="false" show-icon>
      <template #title>数据刷新失败，当前显示上次成功获取的结果</template>
      <div>{{ refreshError }}</div>
    </el-alert>

    <el-skeleton v-if="initialLoading" :rows="5" animated />

    <template v-else>
      <section v-if="todayPlans.length" class="summary-grid" aria-label="今日汇总">
        <div class="summary-card"><span>账号 / 超话</span><strong>{{ accountCount }} / {{ todayPlans.length }}</strong></div>
        <div class="summary-card"><span>今日成功</span><strong>{{ summary.completed }} / {{ summary.target }}</strong></div>
        <div class="summary-card"><span>执行中</span><strong>{{ summary.running }}</strong></div>
        <div class="summary-card" :class="{ attention: summary.attention > 0 }"><span>需关注</span><strong>{{ summary.attention }}</strong></div>
      </section>

      <section v-if="accountIssues.length" class="account-issues" aria-label="账号待处理事项">
        <div class="account-issues-title">
          <strong>账号待处理</strong>
          <span>恢复时会检查登录和验证码，并补建缺失的今日计划。</span>
        </div>
        <div v-for="account in accountIssues" :key="account.accountId" class="account-issue-row">
          <div class="account-issue-info">
            <strong>{{ account.accountName || '未命名账号' }}</strong>
            <div class="account-issue-tags">
              <el-tag
                v-for="issue in accountIssueLabels(account)"
                :key="issue"
                size="small"
                type="warning"
                effect="plain"
              >{{ issue }}</el-tag>
            </div>
          </div>
          <el-button
            size="small"
            type="warning"
            :loading="recoveringAccounts.has(String(account.accountId))"
            :disabled="recoveringAccounts.has(String(account.accountId))"
            @click="resumeAccount(account)"
          >检查并恢复</el-button>
        </div>
      </section>

      <el-empty v-if="!todayPlans.length" description="今天还没有任务计划，请先登录并检查/补建今日计划">
        <el-button type="primary" :loading="planCheckLoading" @click="ensureTodayPlan">检查/补建今日计划</el-button>
      </el-empty>

      <section v-else class="plan-grid">
        <article v-for="plan in todayPlans" :key="plan.id" class="plan-card">
          <div class="plan-heading">
            <div>
              <div class="account-name">{{ plan.accountName || '未命名账号' }}</div>
              <h3>{{ plan.topicName || '已删除的超话' }}</h3>
            </div>
            <el-tag :type="getPlanHealth(plan).tagType" effect="light">{{ getPlanHealth(plan).label }}</el-tag>
          </div>

          <div class="progress-list">
            <div v-for="item in getProgressItems(plan)" :key="item.key" class="progress-item">
              <span>{{ item.label }}</span>
              <strong :class="{ complete: item.target > 0 && item.completed >= item.target }">
                {{ item.completed }} / {{ item.target }}
              </strong>
            </div>
          </div>

          <el-progress
            :percentage="planPercent(plan)"
            :status="getPlanHealth(plan).key === 'success' ? 'success' : undefined"
            :stroke-width="8"
          />

          <div class="state-row">
            <el-tag
              v-for="state in visibleStates(plan)"
              :key="state.key"
              size="small"
              :type="state.type"
              effect="plain"
            >{{ state.label }} {{ state.count }}</el-tag>
          </div>

          <div class="next-run"><span class="next-label">下一步</span><span>{{ nextActionText(plan) }}</span></div>

          <div
            v-if="shouldShowMessage(plan)"
            class="plan-message"
            :class="`is-${getPlanHealth(plan).key}`"
          >{{ plan.lastMessage || fallbackMessage(plan) }}</div>

          <div class="card-actions">
            <el-button size="small" :disabled="!plan.topicId || !plan.topicName" @click="openTemplateDialog(plan)">编辑内容模板</el-button>
            <el-button
              v-if="plan.paused || Number(plan.states?.PAUSED || 0) > 0"
              size="small"
              type="warning"
              :loading="recoveringAccounts.has(String(plan.accountId))"
              :disabled="recoveringAccounts.has(String(plan.accountId))"
              @click="resumeAccount(plan)"
            >检查并恢复账号</el-button>
          </div>
        </article>
      </section>

      <section class="history-section">
        <div class="section-title">
          <h3>最近执行记录</h3>
          <p>最近 100 条，包含此前未能确认结果的任务。</p>
        </div>

        <el-empty v-if="!history.length" description="暂无执行记录" />
        <div v-else class="history-table-wrap">
          <el-table :data="history" stripe>
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><el-tag :type="historyStatus(row.status).type" size="small">{{ historyStatus(row.status).label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="任务" width="90"><template #default="{ row }">{{ actionLabel(row.actionType) }}</template></el-table-column>
            <el-table-column prop="accountName" label="账号" min-width="120" />
            <el-table-column prop="topicName" label="超话" min-width="130"><template #default="{ row }">{{ row.topicName || '已删除' }}</template></el-table-column>
            <el-table-column label="计划时间" min-width="165"><template #default="{ row }">{{ formatDateTime(row.dueAt, '—') }}</template></el-table-column>
            <el-table-column label="完成时间" min-width="165"><template #default="{ row }">{{ formatDateTime(row.finishedAt, '—') }}</template></el-table-column>
            <el-table-column prop="message" label="消息" min-width="220"><template #default="{ row }">{{ row.message || '—' }}</template></el-table-column>
          </el-table>
        </div>
      </section>
    </template>

    <el-dialog
      v-model="templateDialog.visible"
      :title="`${templateDialog.topicName || '超话'} · 内容模板`"
      width="min(680px, 92vw)"
      destroy-on-close
    >
      <div v-loading="templateDialog.loading">
        <el-alert
          v-if="templateDialog.unavailable"
          type="warning"
          :closable="false"
          show-icon
          title="该超话可能已删除，当前无法读取或保存模板"
        />
        <template v-else>
          <p class="template-help">每行一个备选内容，执行时随机选择。可使用 <code>{topic}</code> 代入超话名称。</p>
          <el-form label-position="top">
            <el-form-item label="发帖模板">
              <el-input v-model="templateDialog.postTemplates" type="textarea" :rows="6" maxlength="4000" show-word-limit placeholder="例如：今天也来支持 {topic}" />
            </el-form-item>
            <el-form-item label="回复模板">
              <el-input v-model="templateDialog.replyTemplates" type="textarea" :rows="6" maxlength="4000" show-word-limit placeholder="例如：一起关注 {topic}" />
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button @click="templateDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="templateDialog.saving"
          :disabled="templateDialog.loading || templateDialog.unavailable"
          @click="saveTemplates"
        >保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import {
  ACTIONS,
  getNextActionLabel,
  getPlanHealth,
  getProgressItems
} from '../utils/taskDashboard'

const POLL_INTERVAL = 15_000
const todayPlans = ref([])
const history = ref([])
const accounts = ref([])
const initialLoading = ref(true)
const refreshing = ref(false)
const refreshError = ref('')
const lastFetchedAt = ref(null)
const planCheckLoading = ref(false)
const recoveringAccounts = reactive(new Set())
let pollTimer = null
let disposed = false
let templateRequestSequence = 0

const templateDialog = reactive({
  visible: false,
  loading: false,
  saving: false,
  unavailable: false,
  topicId: '',
  topicName: '',
  postTemplates: '',
  replyTemplates: ''
})

const accountCount = computed(() => new Set(todayPlans.value.map(plan => String(plan.accountId))).size)
const accountIssues = computed(() => accounts.value.filter(account => (
  account.paused || Number(account.loginStatus) !== 1 || !account.hasPlan
)))
const accountIssueLabels = account => {
  const issues = []
  if (account.paused) issues.push('账号已暂停')
  if (Number(account.loginStatus) !== 1) issues.push('登录状态异常')
  if (!account.hasPlan) issues.push('缺少今日计划')
  return issues
}
const summary = computed(() => todayPlans.value.reduce((totals, plan) => {
  for (const item of getProgressItems(plan)) {
    totals.target += item.target
    totals.completed += Math.min(item.completed, item.target)
  }
  const states = plan.states || {}
  totals.running += Number(states.RUNNING || 0)
  totals.attention += Number(states.FAILED || 0) + Number(states.UNKNOWN || 0)
    + Number(states.EXPIRED || 0) + Number(states.PAUSED || 0)
  return totals
}, { target: 0, completed: 0, running: 0, attention: 0 }))

const errorMessage = error => error?.message || '请求失败，请稍后重试'
const parseServerTime = value => {
  if (!value) return null
  const raw = String(value)
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw)
  const date = new Date(hasZone || !raw.includes('T') ? raw : `${raw}+08:00`)
  return Number.isNaN(date.getTime()) ? null : date
}
const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
})
const clockFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
})
const formatDateTime = (value, emptyText = '等待系统安排') => {
  const date = parseServerTime(value)
  return date ? dateTimeFormatter.format(date) : emptyText
}
const formatClock = value => clockFormatter.format(value)

const refreshData = async () => {
  if (disposed || refreshing.value) return
  refreshing.value = true
  try {
    const [todayResult, historyResult, accountsResult] = await Promise.all([
      request.get('/task/today'),
      request.get('/task/history'),
      request.get('/task/accounts')
    ])
    if (disposed) return
    todayPlans.value = Array.isArray(todayResult.data) ? todayResult.data : []
    history.value = Array.isArray(historyResult.data) ? historyResult.data : []
    accounts.value = Array.isArray(accountsResult.data) ? accountsResult.data : []
    lastFetchedAt.value = new Date()
    refreshError.value = ''
  } catch (error) {
    if (!disposed) refreshError.value = errorMessage(error)
  } finally {
    if (!disposed) {
      refreshing.value = false
      initialLoading.value = false
    }
  }
}

const ensureTodayPlan = async () => {
  planCheckLoading.value = true
  try {
    const result = await request.post('/task/all')
    if (disposed) return
    ElMessage.success(result.data?.message || '今日计划已检查')
    await refreshData()
  } catch (error) {
    // 全局请求拦截器已展示错误；页面仍保留上次成功数据。
  } finally {
    if (!disposed) planCheckLoading.value = false
  }
}

const resumeAccount = async plan => {
  const accountId = String(plan.accountId)
  if (recoveringAccounts.has(accountId)) return
  recoveringAccounts.add(accountId)
  try {
    const result = await request.post(`/task/account/${accountId}/resume`)
    if (disposed) return
    ElMessage.success(result.data?.message || '账号已恢复，今日缺失计划已补建')
    await refreshData()
  } catch (error) {
    // 全局请求拦截器已展示错误。
  } finally {
    if (!disposed) recoveringAccounts.delete(accountId)
  }
}

const openTemplateDialog = async plan => {
  if (!plan.topicId || !plan.topicName) return
  const requestedTopicId = String(plan.topicId)
  const requestSequence = ++templateRequestSequence
  templateDialog.visible = true
  templateDialog.loading = true
  templateDialog.saving = false
  templateDialog.unavailable = false
  templateDialog.topicId = requestedTopicId
  templateDialog.topicName = plan.topicName || '已删除的超话'
  templateDialog.postTemplates = ''
  templateDialog.replyTemplates = ''
  try {
    const result = await request.get(`/task/templates/${requestedTopicId}`)
    if (disposed || requestSequence !== templateRequestSequence || templateDialog.topicId !== requestedTopicId) return
    templateDialog.postTemplates = result.data?.postTemplates || ''
    templateDialog.replyTemplates = result.data?.replyTemplates || ''
  } catch (error) {
    if (!disposed && requestSequence === templateRequestSequence && templateDialog.topicId === requestedTopicId) {
      templateDialog.unavailable = true
    }
  } finally {
    if (!disposed && requestSequence === templateRequestSequence && templateDialog.topicId === requestedTopicId) {
      templateDialog.loading = false
    }
  }
}

const saveTemplates = async () => {
  const postTemplates = templateDialog.postTemplates.trim()
  const replyTemplates = templateDialog.replyTemplates.trim()
  if (!postTemplates || !replyTemplates) {
    ElMessage.warning('发帖模板和回复模板都不能为空')
    return
  }
  if (postTemplates.length > 4000 || replyTemplates.length > 4000) {
    ElMessage.warning('每类模板不能超过 4000 个字符')
    return
  }
  templateDialog.saving = true
  try {
    const result = await request.put(`/task/templates/${templateDialog.topicId}`, { postTemplates, replyTemplates })
    if (disposed) return
    ElMessage.success(result.data?.message || '模板已保存')
    templateDialog.visible = false
  } catch (error) {
    // 全局请求拦截器已展示错误。
  } finally {
    if (!disposed) templateDialog.saving = false
  }
}

const planPercent = plan => {
  const items = getProgressItems(plan)
  const target = items.reduce((sum, item) => sum + item.target, 0)
  if (!target) return 0
  const completed = items.reduce((sum, item) => sum + Math.min(item.completed, item.target), 0)
  return Math.min(100, Math.round(completed / target * 100))
}

const STATE_META = {
  PENDING: { label: '待执行', type: 'info' }, RUNNING: { label: '执行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' }, FAILED: { label: '失败', type: 'danger' },
  PAUSED: { label: '暂停', type: 'warning' }, UNKNOWN: { label: '待确认', type: 'warning' },
  EXPIRED: { label: '过期', type: 'info' }
}
const visibleStates = plan => Object.entries(STATE_META)
  .map(([key, meta]) => ({ key, ...meta, count: Number(plan.states?.[key] || 0) }))
  .filter(state => state.count > 0)

const nextActionText = plan => {
  const health = getPlanHealth(plan)
  if (health.key === 'paused') return '恢复账号后继续安排'
  if (health.key === 'success') return '全部目标已成功完成'
  if (['failed', 'unknown', 'expired'].includes(health.key) && !plan.nextRunAt) return '请先处理异常结果'
  return `${getNextActionLabel(plan)} · ${plan.nextRunAt ? `预计 ${formatDateTime(plan.nextRunAt)}` : '等待系统安排'}`
}
const shouldShowMessage = plan => ['paused', 'failed', 'unknown', 'expired'].includes(getPlanHealth(plan).key)
const fallbackMessage = plan => ({
  paused: '账号已暂停，请检查登录状态或完成验证码后恢复。',
  failed: '最近一次执行失败，系统会按计划继续处理后续任务。',
  unknown: '平台返回结果不明确，该任务不会计入成功数。',
  expired: '该任务已错过今日执行窗口，不会计入成功数。'
}[getPlanHealth(plan).key] || '')

const actionLabel = action => ACTIONS.find(item => item.key === action)?.label || action || '未知'
const HISTORY_STATUS = {
  PENDING: { label: '待执行', type: 'info' }, RUNNING: { label: '执行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' }, FAILED: { label: '失败', type: 'danger' },
  PAUSED: { label: '暂停', type: 'warning' }, UNKNOWN: { label: '待确认', type: 'warning' },
  EXPIRED: { label: '过期', type: 'info' }
}
const historyStatus = status => HISTORY_STATUS[String(status || '').toUpperCase()] || { label: status || '未知', type: 'info' }

onMounted(async () => {
  await refreshData()
  if (!disposed) pollTimer = window.setInterval(refreshData, POLL_INTERVAL)
})
onBeforeUnmount(() => {
  disposed = true
  templateRequestSequence += 1
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<style scoped>
.task-control { max-width: 1180px; margin: 0 auto; padding: 8px 0 32px; }
.page-header { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; margin-bottom: 8px; }
.eyebrow { margin-bottom: 6px; color: #409eff; font-size: 12px; font-weight: 700; letter-spacing: .12em; }
.page-header h2 { margin: 0; color: #303133; font-size: 28px; }
.description { max-width: 720px; margin: 10px 0 0; color: #606266; font-size: 14px; line-height: 1.7; }
.header-actions { display: flex; flex-shrink: 0; }
.fetch-meta { min-height: 24px; color: #909399; font-size: 12px; }
.refresh-note { margin-left: 12px; color: #409eff; }
.stale-alert { margin: 8px 0 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 14px 0 18px; }
.summary-card { display: flex; flex-direction: column; gap: 8px; padding: 18px; border: 1px solid #ebeef5; border-radius: 10px; background: #fff; }
.summary-card span { color: #909399; font-size: 13px; }
.summary-card strong { color: #303133; font-size: 24px; }
.summary-card.attention { border-color: #f3d19e; background: #fdf6ec; }
.account-issues { margin: 0 0 18px; border: 1px solid #f3d19e; border-radius: 10px; background: #fdf6ec; overflow: hidden; }
.account-issues-title { display: flex; flex-wrap: wrap; gap: 8px 14px; align-items: baseline; padding: 13px 16px; border-bottom: 1px solid #f3d19e; color: #b88230; }
.account-issues-title span { font-size: 12px; }
.account-issue-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 12px 16px; }
.account-issue-row + .account-issue-row { border-top: 1px solid #faecd8; }
.account-issue-info { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; min-width: 0; color: #606266; }
.account-issue-info > strong { word-break: break-word; }
.account-issue-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.plan-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.plan-card { min-width: 0; padding: 20px; border: 1px solid #ebeef5; border-radius: 12px; background: #fff; box-shadow: 0 3px 12px rgb(0 0 0 / 4%); }
.plan-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.account-name { margin-bottom: 4px; color: #909399; font-size: 12px; }
.plan-heading h3 { margin: 0; color: #303133; font-size: 18px; word-break: break-word; }
.progress-list { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin: 20px 0 14px; }
.progress-item { min-width: 0; padding: 9px 6px; border-radius: 8px; background: #f5f7fa; text-align: center; }
.progress-item span, .progress-item strong { display: block; }
.progress-item span { margin-bottom: 5px; color: #909399; font-size: 12px; }
.progress-item strong { color: #606266; font-size: 14px; white-space: nowrap; }
.progress-item strong.complete { color: #67c23a; }
.state-row { display: flex; flex-wrap: wrap; gap: 6px; min-height: 24px; margin-top: 14px; }
.next-run { display: flex; gap: 10px; margin-top: 14px; color: #606266; font-size: 13px; line-height: 1.5; }
.next-label { flex-shrink: 0; color: #909399; }
.plan-message { margin-top: 12px; padding: 10px 12px; border-radius: 8px; background: #fdf6ec; color: #b88230; font-size: 13px; line-height: 1.5; word-break: break-word; }
.plan-message.is-failed { background: #fef0f0; color: #c45656; }
.plan-message.is-expired { background: #f4f4f5; color: #73767a; }
.card-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.card-actions :deep(.el-button + .el-button) { margin-left: 0; }
.history-section { margin-top: 28px; padding: 20px; border: 1px solid #ebeef5; border-radius: 12px; background: #fff; }
.section-title h3 { margin: 0; color: #303133; font-size: 18px; }
.section-title p { margin: 6px 0 16px; color: #909399; font-size: 13px; }
.history-table-wrap { overflow-x: auto; }
.history-table-wrap :deep(.el-table) { min-width: 980px; }
.template-help { margin: 0 0 18px; color: #606266; font-size: 13px; line-height: 1.6; }
.template-help code { padding: 2px 5px; border-radius: 4px; background: #f2f3f5; color: #409eff; }

@media (max-width: 900px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .plan-grid { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .task-control { padding-top: 0; }
  .page-header { flex-direction: column; gap: 16px; }
  .page-header h2 { font-size: 24px; }
  .header-actions { display: grid; grid-template-columns: 1fr 1fr; width: 100%; }
  .header-actions :deep(.el-button) { margin: 0; }
  .summary-card { padding: 14px; }
  .summary-card strong { font-size: 20px; }
  .account-issue-row { align-items: stretch; flex-direction: column; }
  .account-issue-row :deep(.el-button) { width: 100%; }
  .plan-card, .history-section { padding: 16px; }
  .progress-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .card-actions { display: grid; }
  .card-actions :deep(.el-button) { width: 100%; }
}
</style>
