<template>
  <div class="article-edit">
    <div class="header">
      <el-button @click="$router.back()">返回</el-button>
      <div>
        <el-button @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" @click="publish">发布</el-button>
      </div>
    </div>

    <div class="editor-container">
      <div class="title-input">
        <el-input v-model="form.title" placeholder="请输入文章标题" size="large" />
      </div>

      <div class="summary-input">
        <el-input v-model="form.summary" placeholder="请输入文章摘要（可选）" type="textarea" :rows="2" />
      </div>

      <div class="editor-area">
        <div class="toolbar">
          <button @click="execCommand('bold')" title="加粗"><b>B</b></button>
          <button @click="execCommand('italic')" title="斜体"><i>I</i></button>
          <button @click="execCommand('underline')" title="下划线"><u>U</u></button>
          <button @click="execCommand('strikeThrough')" title="删除线"><s>S</s></button>
          <span class="separator">|</span>
          <button @click="execCommand('formatBlock', 'h1')" title="标题1">H1</button>
          <button @click="execCommand('formatBlock', 'h2')" title="标题2">H2</button>
          <button @click="execCommand('formatBlock', 'h3')" title="标题3">H3</button>
          <button @click="execCommand('formatBlock', 'p')" title="正文">P</button>
          <span class="separator">|</span>
          <button @click="execCommand('justifyLeft')" title="左对齐">&#8676;</button>
          <button @click="execCommand('justifyCenter')" title="居中">&#8633;</button>
          <button @click="execCommand('justifyRight')" title="右对齐">&#8677;</button>
          <span class="separator">|</span>
          <button @click="execCommand('insertUnorderedList')" title="无序列表">&#8226;</button>
          <button @click="execCommand('insertOrderedList')" title="有序列表">1.</button>
          <span class="separator">|</span>
          <button @click="insertLink" title="插入链接">&#128279;</button>
          <button @click="insertImage" title="插入图片">&#128247;</button>
        </div>
        <div
          ref="editorRef"
          class="editor"
          contenteditable="true"
          @input="onInput"
          @paste="onPaste"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const editorRef = ref(null)

const form = ref({
  title: '',
  summary: '',
  content: ''
})

const articleId = ref(route.params.id || null)

onMounted(async () => {
  if (articleId.value) {
    try {
      const res = await request.get(`/api/article/${articleId.value}`)
      const article = res.data
      form.value.title = article.title
      form.value.summary = article.summary || ''
      form.value.content = article.content || ''
      if (editorRef.value) {
        editorRef.value.innerHTML = form.value.content
      }
    } catch (e) {
      ElMessage.error('获取文章失败')
    }
  }
})

const onInput = () => {
  if (editorRef.value) {
    form.value.content = editorRef.value.innerHTML
  }
}

const onPaste = (e) => {
  // 粘贴为纯文本
  e.preventDefault()
  const text = e.clipboardData.getData('text/plain')
  document.execCommand('insertText', false, text)
}

const execCommand = (command, value = null) => {
  if (value) {
    document.execCommand(command, false, value)
  } else {
    document.execCommand(command, false, null)
  }
  editorRef.value?.focus()
}

const insertLink = () => {
  const url = prompt('请输入链接地址：')
  if (url) {
    document.execCommand('createLink', false, url)
    editorRef.value?.focus()
  }
}

const insertImage = () => {
  const url = prompt('请输入图片地址：')
  if (url) {
    document.execCommand('insertImage', false, url)
    editorRef.value?.focus()
  }
}

const saveDraft = async () => {
  if (!form.value.title) {
    ElMessage.warning('请输入标题')
    return
  }

  try {
    if (articleId.value) {
      await request.put(`/api/article/${articleId.value}`, form.value)
    } else {
      const res = await request.post('/api/article/create', form.value)
      articleId.value = res.data.id
    }
    ElMessage.success('草稿已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const publish = async () => {
  if (!form.value.title) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.value.content) {
    ElMessage.warning('请输入内容')
    return
  }

  try {
    // 先保存
    if (articleId.value) {
      await request.put(`/api/article/${articleId.value}`, form.value)
    } else {
      const res = await request.post('/api/article/create', form.value)
      articleId.value = res.data.id
    }

    // 再发布
    await request.post(`/api/article/${articleId.value}/publish`)
    ElMessage.success('文章已发布')
    router.push('/article')
  } catch (e) {
    ElMessage.error('发布失败')
  }
}
</script>

<style scoped>
.article-edit { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.editor-container { max-width: 900px; margin: 0 auto; }
.title-input { margin-bottom: 15px; }
.summary-input { margin-bottom: 15px; }
.editor-area { border: 1px solid #dcdfe6; border-radius: 4px; }
.toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  flex-wrap: wrap;
}
.toolbar button {
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
  min-width: 28px;
  height: 28px;
}
.toolbar button:hover { background: #e6f7ff; border-color: #409eff; }
.toolbar .separator { color: #dcdfe6; margin: 0 4px; }
.editor {
  min-height: 500px;
  padding: 20px;
  outline: none;
  font-size: 16px;
  line-height: 1.8;
}
.editor:focus { box-shadow: inset 0 0 0 1px #409eff; }
</style>
