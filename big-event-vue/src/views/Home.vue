<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// 统一到门面 api
import { useUserStore } from '@/stores/user'
import { removeToken } from '@/utils/auth.js'
import { hasFeaturePermission } from '@/utils/permission.js'
import api from '@/api'
// 统一到新门面 api.notice
import webSocketService from '@/utils/websocket.js'
// 移除：import { useChatStore } from '@/stores/chat.js'
import {
  User,
  Bell,
  Setting,
  OfficeBuilding,
  DataAnalysis,
  SwitchButton,
  ArrowDown,
  Fold,
  Expand,
  HomeFilled,
  ChatDotRound
} from '@element-plus/icons-vue'


// 定义事件
const emit = defineEmits(['logout'])

// 获取当前路由和路由器
const route = useRoute()
const router = useRouter()

// 移除：初始化聊天 Store
// const chatStore = useChatStore()

// 控制侧边栏折叠状态
const isCollapse = ref(false)

// 用户信息
const userStore = useUserStore()
const userInfo = ref({ username: '', nickname: '', realname: '', role: '' })

// 新增：规范化头像URL工具与计算属性
/**
 * 规范化头像URL
 * 函数级注释：
 * - 将后端返回的相对路径补全为以 / 开头的绝对路径（如 uploads/xxx → /uploads/xxx）
 * - 将 Windows 路径分隔符“\\”转换为 URL 友好的“/”
 * - 若为空或无效，返回默认头像 /avatar.jpg
 */
const normalizeAvatar = (url) => {
  if (!url) return '/avatar.jpg'
  let u = String(url).replace(/\\/g, '/')
  if (u.startsWith('http://') || u.startsWith('https://')) return u
  if (u.startsWith('/uploads/')) return u
  if (u.startsWith('uploads/')) return '/' + u
  if (u.startsWith('/avatar')) return u
  return u.startsWith('/') ? u : '/' + u
}

/**
 * 当前用户头像URL（已规范化）
 * 函数级注释：基于 userInfo.userPic 计算出可直接用于 <el-avatar> 的图片地址
 */
const avatarUrl = computed(() => normalizeAvatar(userInfo.value?.userPic))
// 未读公告数量 + 系统通知未读数量
const unreadAnnouncementCount = ref(0)
const unreadSystemNoticeCount = ref(0)

// 当前激活的菜单项，根据路由路径自动更新
const activeMenu = computed(() => route.path)

// 检查用户是否有特定功能权限
const hasPermission = (feature) => {
  return hasFeaturePermission(feature, userInfo.value.role)
}

/**
 * 处理用户登出
 * 函数级注释：
 * - 弹出二次确认，调用后端登出接口（失败也继续本地登出）
 * - 清理本地 token，主动断开 WebSocket 连接
 * - 通过 emit 通知父组件执行路由跳转等逻辑
 * - 注意：不清理 chatStore 的本地持久化，以满足“退出后保留聊天记录”的需求
 */
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    try {
      const result = await api.account.logout()
      ElMessage.success(result.msg || '退出登录成功')
    } catch (apiError) {
      // 接口失败也不阻断登出流程
      ElMessage.warning('登出接口调用失败，但已执行本地登出')
    } finally {
      // 清除 token
      removeToken()
      try { userStore.clear() } catch (_) {}
      // 主动断开 WebSocket 连接，防止登出后仍然接收消息
      try {
        webSocketService.disconnect()
      } catch (_) {}
      // 通知父组件
      emit('logout')
      // 新增：退出后立即跳转到登录页，避免需要手动刷新
      router.replace('/login')
    }
  } catch (error) {
    // 用户取消
    if (error !== 'cancel' && error !== 'close') {
      console.error('登出流程发生错误:', error)
    }
  }
}

/**
 * 切换侧边栏折叠状态
 */
const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}

/**
 * 加载用户信息
 * 函数级注释：
 * - 调用后端接口获取当前登录用户信息
 */
const loadUserInfo = async () => {
  try {
    const response = await api.account.me()
    if (response && response.code === 0 && response.data) {
      userInfo.value = response.data
      try { userStore.clear(); userStore.loadUserInfo() } catch (_) {}
    } else {
      console.error('获取用户信息失败:', response)
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

/**
 * 加载未读公告数量
 */
const loadUnreadAnnouncementCount = async () => {
  try {
    const response = await api.notice.unreadCount()
    if (response && response.code === 0) {
      unreadAnnouncementCount.value = response.data || 0
    } else {
      console.error('获取未读公告数量失败:', response)
    }
  } catch (error) {
    console.error('获取未读公告数量失败:', error)
  }
}

// 加载系统通知未读数量
const loadUnreadSystemNoticeCount = async () => {
  try {
    const response = await api.notice.inboxUnreadCount()
    if (response && response.code === 0) {
      unreadSystemNoticeCount.value = response.data || 0
    }
  } catch (error) {
    console.error('获取系统通知未读数量失败:', error)
  }
}

/**
 * 处理公告更新事件：统一刷新未读徽标
 */
const handleAnnouncementUpdate = () => {
  loadUnreadAnnouncementCount()
}

/**
 * 公告消息处理函数（具名回调）
 * 函数级注释：
 * - 处理后端推送的 announcement 消息，仅负责触发未读数刷新与用户提示
 */
const handleAnnouncementMessageHandler = (data) => {
  try {
    console.log('收到公告通知:', data)
    // 移除：chatStore.handleAnnouncementMessage(data)
    loadUnreadAnnouncementCount()
    const announcementEvent = new CustomEvent('announcement-update', {
      detail: { announcement: data, isUpdate: true }
    })
    window.dispatchEvent(announcementEvent)
    ElMessage({
      message: `新公告: ${data.title || '系统公告'}`,
      type: 'success',
      duration: 5000
    })
  } catch (e) {
    console.error('处理公告消息失败:', e)
  }
}

/**
 * 公告撤回处理函数（具名回调）
 * 函数级注释：
 * - 处理后端推送的 announcement_revoke 消息，仅广播事件
 */
const handleAnnouncementRevokeHandler = (data) => {
  try {
    console.log('收到公告撤回通知:', data)
    // 移除：chatStore.handleAnnouncementRevokeMessage(data)
    const revokeEvent = new CustomEvent('announcement-update', {
      detail: { announcement: data, isUpdate: true }
    })
    window.dispatchEvent(revokeEvent)
  } catch (e) {
    console.error('处理公告撤回失败:', e)
  }
}

// 新增：通知消息处理器
/**
 * 通知消息处理函数（具名回调）
 * 函数级注释：
 * - 处理后端推送的 notification 消息，统一进行提示
 * - 若与公告未读有关，触发未读数刷新
 */
const handleNotificationHandler = (data) => {
  try {
    console.log('收到通知:', data)
    if (data && (data.type === 'announcement_update' || data.category === 'announcement')) {
      loadUnreadAnnouncementCount()
    }
    if (data && data.event === 'system_notice') {
      // 系统通知未读数刷新
      loadUnreadSystemNoticeCount()
    }
    const text = data?.title || data?.message || '系统通知'
    ElMessage.info(text)
  } catch (e) {
    console.error('处理通知消息失败:', e)
  }
}

// 移除：所有聊天消息相关处理器（handleChatMessageHandler、handleChatMessageEditHandler、handleChatMessageRecallHandler、handleChatMessageDeleteHandler、handleOfflineMessageSyncHandler）

/**
 * 初始化WebSocket连接
 * 函数级注释：
 * - 建立连接并注册 announcement/announcement_revoke/notification 三类具名回调
 * - 统一处理断开与错误提示
 */
const initWebSocket = async () => {
  try {
    await webSocketService.connect()
    webSocketService.onMessage('announcement', handleAnnouncementMessageHandler)
    webSocketService.onMessage('announcement_revoke', handleAnnouncementRevokeHandler)
    // 移除：聊天消息类事件注册
    // webSocketService.onMessage('chat_message', handleChatMessageHandler)
    // webSocketService.onMessage('chat_message_edit', handleChatMessageEditHandler)
    // webSocketService.onMessage('chat_message_recall', handleChatMessageRecallHandler)
    // webSocketService.onMessage('chat_message_delete', handleChatMessageDeleteHandler)
    // webSocketService.onMessage('offline_message_sync', handleOfflineMessageSyncHandler)
    // 注册通知类消息
    webSocketService.onMessage('notification', handleNotificationHandler)

    webSocketService.onClose(() => {
      console.log('WebSocket连接已关闭')
    })

    webSocketService.onError((error) => {
      console.error('WebSocket连接错误:', error)
      ElMessage.error('WebSocket连接失败，部分实时功能可能不可用')
    })
  } catch (error) {
    console.error('初始化WebSocket失败:', error)
    ElMessage.error('WebSocket连接失败，部分实时功能可能不可用')
  }
}

// 移除：最近会话列表逻辑 loadRecentChats

// 生命周期
onMounted(async () => {
  await loadUserInfo()
  await loadUnreadAnnouncementCount()
  await loadUnreadSystemNoticeCount()
  await initWebSocket()
  // 移除：await loadRecentChats()
  window.addEventListener('announcement-update', handleAnnouncementUpdate)
  window.addEventListener('announcement-read-count-update', handleAnnouncementUpdate)
})

onUnmounted(() => {
  webSocketService.offMessage('announcement', handleAnnouncementMessageHandler)
  webSocketService.offMessage('announcement_revoke', handleAnnouncementRevokeHandler)
  // 移除：chat相关offMessage
  // webSocketService.offMessage('chat_message', handleChatMessageHandler)
  // webSocketService.offMessage('chat_message_edit', handleChatMessageEditHandler)
  // webSocketService.offMessage('chat_message_recall', handleChatMessageRecallHandler)
  // webSocketService.offMessage('chat_message_delete', handleChatMessageDeleteHandler)
  // webSocketService.offMessage('offline_message_sync', handleOfflineMessageSyncHandler)
  // 新增：注销通知类消息监听
  webSocketService.offMessage('notification', handleNotificationHandler)
  webSocketService.disconnect()
  window.removeEventListener('announcement-update', handleAnnouncementUpdate)
  window.removeEventListener('announcement-read-count-update', handleAnnouncementUpdate)
})
</script>

<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <header class="header">
      <div class="left-section">
        <el-button type="text" :icon="isCollapse ? 'Expand' : 'Fold'" @click="toggleSidebar" />
        <div class="logo">
          <img src="@/assets/logo.png" alt="Logo">
          <h1>企业通讯录系统</h1>
        </div>
      </div>

      <div class="right-section">
        <el-tooltip content="系统公告" placement="bottom">
          <div class="notification-icon" @click="router.push({ path: '/announcement' })">
            <el-badge :value="unreadAnnouncementCount" :hidden="unreadAnnouncementCount === 0" class="notification-badge">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </div>
        </el-tooltip>
        <el-tooltip content="系统通知" placement="bottom">
          <div class="notification-icon" @click="router.push({ path: '/system-notice' })">
            <el-badge :value="unreadSystemNoticeCount" :hidden="unreadSystemNoticeCount === 0" class="notification-badge">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </div>
        </el-tooltip>

        <el-dropdown>
          <span class="user-dropdown">
            <el-avatar :size="32" :src="avatarUrl" class="avatar"></el-avatar>
            <span>{{ userInfo.nickname || userInfo.realname || userInfo.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item @click="router.push('/settings')">
                <el-icon><Setting /></el-icon>账号设置
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="main-container">
      <!-- 侧边导航栏 -->
      <el-menu
        class="sidebar"
        :collapse="isCollapse"
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router>

        <el-menu-item index="/welcome">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-menu-item index="/contacts">
          <el-icon><User /></el-icon>
          <template #title>通讯录</template>
        </el-menu-item>

        <!-- 新增：聊天模块入口 -->
        <el-menu-item index="/ai-assistant">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI 助手</template>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>聊天</template>
        </el-menu-item>

        
        <el-menu-item index="/department">
          <el-icon><OfficeBuilding /></el-icon>
          <template #title>部门组织架构</template>
        </el-menu-item>

        <el-menu-item index="/announcement">
          <el-icon><Bell /></el-icon>
          <template #title>系统公告</template>
        </el-menu-item>

        <el-menu-item index="/statistics" v-if="hasPermission('view_all_statistics')">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>数据统计</template>
        </el-menu-item>

        <el-menu-item index="/system" v-if="hasPermission('manage_users')">
          <el-icon><Setting /></el-icon>
          <template #title>系统管理</template>
        </el-menu-item>
      </el-menu>

      <!-- 主内容区域 -->
      <div class="content">
        <!-- 使用路由视图显示子路由组件 -->
        <router-view />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;

  .header {
    height: 60px;
    background-color: #ffffff;
    color: #333;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
    position: relative;
    z-index: 10;

    .left-section {
      display: flex;
      align-items: center;

      .logo {
        display: flex;
        align-items: center;
        margin-left: 15px;

        img {
          height: 36px;
          margin-right: 10px;
        }

        h1 {
          font-size: 18px;
          font-weight: 600;
          margin: 0;
          color: #304156;
        }
      }
    }

    .right-section {
      display: flex;
      align-items: center;
      gap: 20px;

      .notification-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
      }

      .notification-badge {
        cursor: pointer;
        font-size: 20px;
        color: #606266;
      }

      .user-dropdown {
        display: flex;
        align-items: center;
        cursor: pointer;
        padding: 5px 8px;
        border-radius: 4px;
        transition: background-color 0.3s;

        &:hover {
          background-color: #f5f7fa;
        }

        .avatar {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          margin-right: 8px;
          object-fit: cover;
        }

        span {
          margin-right: 5px;
          font-size: 14px;
        }
      }
    }
  }

  .main-container {
    display: flex;
    flex: 1;
    overflow: hidden;

    .sidebar {
      height: calc(100vh - 60px);
      background-color: #304156;
      transition: width 0.3s;
      overflow-y: auto;
      overflow-x: hidden;

      &:not(.el-menu--collapse) {
        width: 210px;
      }

      &.el-menu--collapse {
        width: 64px;
      }
    }

    .content {
      flex: 1;
      padding: 20px;
      overflow-y: auto;
      background-color: #f5f7fa;

      .welcome-content {
        background-color: white;
        border-radius: 4px;
        padding: 30px;
        text-align: center;
        box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
        margin-top: 40px;

        h2 {
          color: #303133;
          margin-bottom: 15px;
        }

        p {
          color: #606266;
        }
      }
    }
  }
}
</style>
