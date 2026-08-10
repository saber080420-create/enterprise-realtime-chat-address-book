<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

const notices = ref([])
const loading = ref(false)
const unreadCount = ref(0)
const limit = ref(20)
const offset = ref(0)

const loadUnread = async () => {
  try {
    const res = await api.notice.inboxUnreadCount()
    if (res.code === 0) unreadCount.value = res.data || 0
  } catch (_) {}
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await api.notice.inboxList(limit.value, offset.value)
    if (res.code === 0) {
      notices.value = res.data || []
    } else {
      ElMessage.error(res.msg || '加载失败')
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const markRead = async (id) => {
  try {
    const res = await api.notice.inboxMarkRead(id)
    if (res.code === 0) {
      await loadUnread()
      await loadList()
    }
  } catch (_) {}
}

const markAllRead = async () => {
  try {
    const res = await api.notice.inboxMarkAllRead()
    if (res.code === 0) {
      await loadUnread()
      await loadList()
    }
  } catch (_) {}
}

const deleteNotice = async (row) => {
  if (!row?.isRead) {
    ElMessage.info('未读通知不能删除，请先标记为已读')
    return
  }
  try {
    const res = await api.notice.inboxDelete(row.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      await loadUnread()
      await loadList()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (_) {}
}

onMounted(async () => {
  await loadUnread()
  await loadList()
})
</script>

<template>
  <div class="system-notice-page">
    <div class="header">
      <h3>系统通知</h3>
      <div class="tools">
        <span class="unread">未读：{{ unreadCount }}</span>
        <el-button type="primary" link @click="markAllRead" :disabled="unreadCount === 0">全部已读</el-button>
      </div>
    </div>
    <el-table :data="notices" v-loading="loading" style="width:100%">
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column prop="title" label="标题" width="200" />
      <el-table-column prop="content" label="内容" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.isRead ? 'info' : 'success'">{{ scope.row.isRead ? '已读' : '未读' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="scope">
          <el-button v-if="!scope.row.isRead" type="primary" link @click="markRead(scope.row.id)">标记已读</el-button>
          <el-button v-else type="danger" link @click="deleteNotice(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
  
</template>

<style scoped>
.system-notice-page {
  background: #fff;
  padding: 16px;
  border-radius: 6px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.unread {
  color: #909399;
  margin-right: 8px;
}
</style>


