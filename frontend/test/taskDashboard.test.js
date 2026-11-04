import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getNextActionLabel,
  getNextPendingAction,
  getPlanHealth,
  getProgressItems
} from '../src/utils/taskDashboard.js'

test('scheduled action label comes only from nextActionType and otherwise stays generic', () => {
  assert.equal(getNextActionLabel({ nextActionType: 'reply' }), '回复')
  assert.equal(getNextActionLabel({ nextActionType: 'unexpected' }), '下个任务')
  assert.equal(getNextActionLabel({ targets: { sign: 1 }, completed: { sign: 0 } }), '下个任务')
})

test('progress uses backend targets and completed SUCCESS counts without inventing work', () => {
  const items = getProgressItems({
    targets: { sign: 1, post: 1, forward: 3, reply: 5, view: 45 },
    completed: { sign: 1, post: 0, forward: 2, reply: null }
  })

  assert.deepEqual(
    items.map(({ key, completed, target }) => [key, completed, target]),
    [
      ['sign', 1, 1],
      ['post', 0, 1],
      ['forward', 2, 3],
      ['reply', 0, 5],
      ['view', 0, 45]
    ]
  )
})

test('next action follows the daily execution order and reports completion only when every target is met', () => {
  const row = {
    targets: { sign: 1, post: 1, forward: 2, reply: 4, view: 40 },
    completed: { sign: 1, post: 1, forward: 2, reply: 3, view: 40 }
  }

  assert.equal(getNextPendingAction(row)?.key, 'reply')
  assert.equal(getNextPendingAction({ ...row, completed: { ...row.targets } }), null)
})

test('uncertain outcomes outrank apparent completion so the UI never presents false success', () => {
  const completeButUnknown = {
    paused: false,
    states: { SUCCESS: 49, UNKNOWN: 1 },
    targets: { sign: 1, post: 1, forward: 3, reply: 5, view: 40 },
    completed: { sign: 1, post: 1, forward: 3, reply: 5, view: 40 }
  }

  assert.equal(getPlanHealth(completeButUnknown).key, 'unknown')
  assert.equal(getPlanHealth({ ...completeButUnknown, paused: true }).key, 'paused')
})

test('queued state prevents a plan from being presented as complete even when counters are full', () => {
  const row = {
    states: { PENDING: 1, SUCCESS: 49 },
    targets: { sign: 1, post: 1, forward: 3, reply: 5, view: 40 },
    completed: { sign: 1, post: 1, forward: 3, reply: 5, view: 40 }
  }

  assert.equal(getPlanHealth(row).key, 'pending')
})
