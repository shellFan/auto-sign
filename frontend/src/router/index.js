import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '首页看板' }
      },
      {
        path: 'account',
        name: 'Account',
        component: () => import('../views/AccountList.vue'),
        meta: { title: '平台账号' }
      },
      {
        path: 'weibo-topic',
        name: 'WeiboTopic',
        component: () => import('../views/WeiboTopicList.vue'),
        meta: { title: '微博超话' }
      },
      {
        path: 'sign-task',
        name: 'SignTask',
        component: () => import('../views/SignTaskList.vue'),
        meta: { title: '签到任务' }
      },
      {
        path: 'sign-record',
        name: 'SignRecord',
        component: () => import('../views/SignRecordList.vue'),
        meta: { title: '签到记录' }
      },
      {
        path: 'article',
        name: 'Article',
        component: () => import('../views/ArticleList.vue'),
        meta: { title: '文章管理' }
      },
      {
        path: 'article/edit/:id?',
        name: 'ArticleEdit',
        component: () => import('../views/ArticleEdit.vue'),
        meta: { title: '编辑文章' }
      },
      {
        path: 'task-control',
        name: 'TaskControl',
        component: () => import('../views/TaskControl.vue'),
        meta: { title: '任务控制' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
