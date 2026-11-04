export const ACTIONS = Object.freeze([
  { key: 'sign', label: '签到' },
  { key: 'post', label: '发帖' },
  { key: 'forward', label: '转发' },
  { key: 'reply', label: '回复' },
  { key: 'view', label: '看帖' }
])

const toCount = value => {
  const count = Number(value)
  return Number.isFinite(count) && count > 0 ? Math.floor(count) : 0
}

export const getProgressItems = row => ACTIONS.map(action => ({
  ...action,
  target: toCount(row?.targets?.[action.key]),
  completed: toCount(row?.completed?.[action.key])
}))

export const getNextPendingAction = row => (
  getProgressItems(row).find(item => item.completed < item.target) || null
)

export const getNextActionLabel = row => (
  ACTIONS.find(action => action.key === row?.nextActionType)?.label || '下个任务'
)

export const getPlanHealth = row => {
  const states = row?.states || {}

  if (row?.paused || toCount(states.PAUSED) > 0) {
    return { key: 'paused', label: '账号暂停', tagType: 'warning' }
  }
  if (toCount(states.FAILED) > 0) {
    return { key: 'failed', label: '执行失败', tagType: 'danger' }
  }
  if (toCount(states.UNKNOWN) > 0) {
    return { key: 'unknown', label: '结果待确认', tagType: 'warning' }
  }
  if (toCount(states.EXPIRED) > 0) {
    return { key: 'expired', label: '任务已过期', tagType: 'info' }
  }
  if (toCount(states.RUNNING) > 0) {
    return { key: 'running', label: '正在执行', tagType: 'primary' }
  }
  if (toCount(states.PENDING) > 0) {
    return { key: 'pending', label: '等待执行', tagType: 'info' }
  }
  if (!getNextPendingAction(row)) {
    return { key: 'success', label: '今日已完成', tagType: 'success' }
  }
  return { key: 'pending', label: '等待执行', tagType: 'info' }
}
