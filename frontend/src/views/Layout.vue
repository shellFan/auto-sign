<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="aside">
      <div class="logo">
        <el-icon :size="24"><Monitor /></el-icon>
        <span>自动签到系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>首页看板</span>
        </el-menu-item>
        <el-menu-item index="/account">
          <el-icon><User /></el-icon>
          <span>平台账号</span>
        </el-menu-item>
        <el-menu-item index="/weibo-topic">
          <el-icon><ChatDotRound /></el-icon>
          <span>微博超话</span>
        </el-menu-item>
        <el-menu-item index="/sign-task">
          <el-icon><Timer /></el-icon>
          <span>签到任务</span>
        </el-menu-item>
        <el-menu-item index="/sign-record">
          <el-icon><Document /></el-icon>
          <span>签到记录</span>
        </el-menu-item>
        <el-menu-item index="/article">
          <el-icon><EditPen /></el-icon>
          <span>文章管理</span>
        </el-menu-item>
        <el-menu-item index="/task-control">
          <el-icon><VideoPlay /></el-icon>
          <span>任务控制</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentRoute.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="el-dropdown-link">
              <el-icon><User /></el-icon>
              {{ userStore.userInfo?.nickname || '管理员' }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const currentRoute = computed(() => route)

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (command === 'password') {
    // 打开修改密码对话框
  }
}

// 获取用户信息
userStore.getUserInfo()
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.aside {
  background-color: #304156;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  gap: 8px;
  background-color: #263445;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-right {
  display: flex;
  align-items: center;
}

.el-dropdown-link {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #606266;
}

.main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
