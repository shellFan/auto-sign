<template>
  <div class="article-list">
    <div class="header">
      <h2>文章管理</h2>
      <el-button type="primary" @click="createArticle">新建文章</el-button>
    </div>

    <el-table :data="articles" v-loading="loading" stripe>
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="summary" label="摘要" min-width="150" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="editArticle(row)">编辑</el-button>
          <el-button size="small" type="success" @click="publishArticle(row)" :disabled="row.status === 1">发布</el-button>
          <el-button size="small" type="warning" @click="pushToWechat(row)" :disabled="row.status !== 1">推送到公众号</el-button>
          <el-button size="small" type="danger" @click="deleteArticle(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const articles = ref([])
const loading = ref(false)

const fetchArticles = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/article/list')
    articles.value = res.data || []
  } catch (e) {
    ElMessage.error('获取文章列表失败')
  } finally {
    loading.value = false
  }
}

const createArticle = () => {
  router.push('/article/edit')
}

const editArticle = (article) => {
  router.push(`/article/edit/${article.id}`)
}

const publishArticle = async (article) => {
  try {
    await request.post(`/api/article/${article.id}/publish`)
    ElMessage.success('文章已发布')
    fetchArticles()
  } catch (e) {
    ElMessage.error('发布失败')
  }
}

const pushToWechat = async (article) => {
  try {
    await ElMessageBox.confirm('确定要推送到微信公众号吗？', '确认')
    const res = await request.post(`/api/article/${article.id}/push-wechat`)
    ElMessage.success('已推送到微信公众号')
    fetchArticles()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('推送失败: ' + (e.response?.data?.message || e.message))
    }
  }
}

const deleteArticle = async (article) => {
  try {
    await ElMessageBox.confirm('确定要删除这篇文章吗？', '确认')
    await request.delete(`/api/article/${article.id}`)
    ElMessage.success('文章已删除')
    fetchArticles()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const statusText = (status) => {
  const map = { 0: '草稿', 1: '已发布', 2: '已推公众号' }
  return map[status] || '未知'
}

const statusType = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'warning' }
  return map[status] || 'info'
}

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(fetchArticles)
</script>

<style scoped>
.article-list { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
</style>
