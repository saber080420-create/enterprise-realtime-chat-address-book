<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Plus, Edit, Delete, Bell, View, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
// 移除：import { useChatStore } from '@/stores/chat.js'
import api from '@/api'

// 用户信息
const userStore = useUserStore()
const route = useRoute()
// 使用store中的isAdmin属性
const isAdmin = computed(() => userStore.isAdmin)
// 直接从localStorage获取用户信息，用于调试
const userInfoFromStorage = computed(() => {
  try {
    const storedInfo = localStorage.getItem('userInfo')
    if (storedInfo) {
      const parsedInfo = JSON.parse(storedInfo)
      console.log('当前用户角色:', parsedInfo.role)
      return parsedInfo
    }
    return null
  } catch (error) {
    console.error('解析用户信息失败:', error)
    return null
  }
})

// 公告数据
const announcements = ref([])

// 分页参数
const pagination = ref({
  page: 1,
  pageSize: 10,
  total: 0
})

// 加载状态
const loading = ref(false)

// 加载公告列表
const loadAnnouncements = async () => {
  loading.value = true
  try {
    const res = await api.notice.list({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
      type: departmentFilter.value === '全部' ? null : departmentFilter.value
    })
    
    if (res.code === 0) {
      announcements.value = res.data.records.map(item => ({
        id: item.id,
        title: item.title,
        content: item.content,
        publisher: item.publisherName || '未知',
        publisherAvatar: item.publisherAvatar || '/avatar.jpg',
        publishTime: item.publishTime,
        scope: item.type === 'company' ? '全公司' : (item.departmentName || '未知部门'),
        isRead: item.isRead || false,
        readCount: item.readCount || 0,
        totalCount: item.totalCount || 0,
        status: item.status
      }))
      pagination.value.total = res.data.total
    } else {
      ElMessage.error(res.msg || '获取公告列表失败')
    }
  } catch (error) {
    console.error('获取公告列表出错:', error)
    ElMessage.error('获取公告列表失败')
  } finally {
    loading.value = false
  }
}

// 当前选中的公告
const selectedAnnouncement = ref(null)

// 搜索关键词
const searchKeyword = ref('')

// 部门筛选
const departmentFilter = ref('全部')

// 已读/未读筛选
const readStatusFilter = ref('全部')

// 部门列表
const departments = ref(['全部'])
// 部门ID映射表
const departmentMap = ref({})

// 获取部门列表
/**
 * 获取部门列表数据
 * 用于公告筛选和发布时的部门选择
 * @returns {Promise<void>} 无返回值，更新 departments 和 departmentMap 响应式变量
 */
const loadDepartments = async () => {
  try {
    // 导入部门API服务
    const { getAllDepartmentsService } = await import('@/api/department.js')
    const res = await getAllDepartmentsService()
    
    if (res.code === 0 && res.data) {
      // 添加'全部'选项
      departments.value = ['全部']
      
      // 构建部门映射表并添加部门名称到列表
      // 注意：getAllDepartmentsService 返回的字段是 departmentName，不是 name
      res.data.forEach(dept => {
        departments.value.push(dept.departmentName)
        departmentMap.value[dept.departmentName] = dept.id
      })
      
      console.log('部门映射表:', departmentMap.value)
    } else {
      // 如果API调用失败，使用静态数据
      departments.value = ['全部', '技术部', '产品部', '市场部', '人事部', '财务部']
      // 创建模拟的部门ID映射
      departmentMap.value = {
        '技术部': 1,
        '产品部': 2,
        '市场部': 3,
        '人事部': 4,
        '财务部': 5
      }
    }
  } catch (error) {
    console.error('获取部门列表出错:', error)
    // 出错时使用静态数据
    departments.value = ['全部', '技术部', '产品部', '市场部', '人事部', '财务部']
    // 创建模拟的部门ID映射
    departmentMap.value = {
      '技术部': 1,
      '产品部': 2,
      '市场部': 3,
      '人事部': 4,
      '财务部': 5
    }
  }
}

// 过滤后的公告列表
const filteredAnnouncements = computed(() => {
  let result = announcements.value
  
  // 按部门筛选
  if (departmentFilter.value !== '全部') {
    result = result.filter(item => item.scope === departmentFilter.value)
  }
  
  // 按已读/未读筛选
  if (readStatusFilter.value === '已读') {
    result = result.filter(item => item.isRead)
  } else if (readStatusFilter.value === '未读') {
    result = result.filter(item => !item.isRead)
  }
  
  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(item => 
      item.title.toLowerCase().includes(keyword) ||
      item.content.toLowerCase().includes(keyword) ||
      item.publisher.toLowerCase().includes(keyword)
    )
  }
  
  return result
})

// 选择公告
const selectAnnouncement = async (announcement) => {
  loading.value = true
  try {
    // 获取公告详情
    const res = await api.notice.get(announcement.id)
    if (res.code === 0) {
      selectedAnnouncement.value = {
        ...announcement,
        content: res.data.content,
        publisherAvatar: res.data.publisherAvatar || '/avatar.jpg',
        status: res.data.status
      }
      
      // 如果是未读公告，标记为已读
      if (!announcement.isRead) {
        const markRes = await api.notice.markRead(announcement.id)
        if (markRes.code === 0) {
          announcement.isRead = true
          announcement.readCount++
          // 直接通知Home组件更新未读数，无需触发全局事件避免重复弹窗
          const homeUpdateEvent = new CustomEvent('announcement-read-count-update')
          window.dispatchEvent(homeUpdateEvent)

          // 已移除：联动聊天会话未读数（聊天模块已下线）
        }
      }
      
      // 加载已读用户列表
      loadReadUsers(announcement.id)
    } else {
      // 接口返回非0，提示公告不存在
      ElMessage.warning(res.msg || '该公告已不存在或已被撤回')
    }
  } catch (error) {
    console.error('获取公告详情出错:', error)
    // 如果是404或业务异常，统一提示
    ElMessage.warning('该公告已不存在或已被撤回')
  } finally {
    loading.value = false
  }
}

// 已读人员数据
const readUsers = ref([])

// 加载已读人员列表
const loadReadUsers = async (announcementId) => {
  try {
    const res = await api.notice.readUsers(announcementId)
    if (res.code === 0) {
      const arr = Array.isArray(res.data) ? res.data : []
      readUsers.value = arr.map(u => ({
        id: u.id,
        name: u.name || u.nickname || u.username || '未命名',
        avatar: u.avatar || u.userPic || '/avatar.jpg',
        department: u.departmentName || u.department || '未知部门'
      }))
    } else {
      readUsers.value = []
    }
  } catch (error) {
    console.error('获取已读人员列表出错:', error)
    readUsers.value = []
  }
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 公告表单
const announcementForm = ref({
  title: '',
  content: '',
  type: 'company', // 'company' 或 'department'
  departmentName: null // 改为使用部门名称，方便选择器显示
})

// 公告表单对话框
const dialogVisible = ref(false)
const dialogTitle = ref('发布公告')
const isEdit = ref(false)

// 打开发布公告对话框
const openCreateDialog = () => {
  dialogTitle.value = '发布公告'
  isEdit.value = false
  
  // 获取当前用户信息
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  
  announcementForm.value = {
    title: '',
    content: '',
    type: 'company',
    departmentName: null, // 改为使用部门名称
    status: 'published' // 默认设置为发布状态
    // 移除publisherId，由后端统一设置
  }
  
  console.log('创建公告表单初始化:', JSON.stringify(announcementForm.value))
  dialogVisible.value = true
}

// 打开编辑公告对话框
const openEditDialog = (announcement) => {
  dialogTitle.value = '编辑公告'
  isEdit.value = true
  
  // 如果是部门公告，需要找到对应的部门名称
  let departmentName = null
  if (announcement.scope !== '全公司') {
    // 使用scope作为部门名称（从公告列表中已经获得了部门名称）
    departmentName = announcement.scope
    console.log('编辑公告，部门名称:', departmentName)
  }
  
  // 获取当前用户信息
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  
  announcementForm.value = {
    id: announcement.id,
    title: announcement.title,
    content: announcement.content,
    type: announcement.scope === '全公司' ? 'company' : 'department',
    departmentName: departmentName, // 改为使用部门名称
    status: announcement.status || 'draft' // 使用原公告状态，如果没有则默认为草稿
    // 移除publishTime字段，让后端根据状态变化自动处理
    // 移除publisherId，由后端统一设置
  }
  
  console.log('编辑公告表单初始化:', JSON.stringify(announcementForm.value))
  dialogVisible.value = true
}

// 提交公告表单
const submitAnnouncementForm = async () => {
  try {
    // 获取当前用户信息
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    
    // 创建提交数据，将部门名称转换为部门ID
    const formData = { ...announcementForm.value }
    
    // 处理公告类型和部门ID的关系
    if (formData.type === 'company') {
      // 如果是公司公告，将部门ID设为null
      formData.departmentId = null
      console.log('公司公告，部门ID设为null')
    } else if (formData.type === 'department') {
      // 将部门名称转换为部门ID
      if (formData.departmentName && departmentMap.value[formData.departmentName]) {
        formData.departmentId = departmentMap.value[formData.departmentName]
        console.log('部门公告，部门名称转换为ID:', formData.departmentName, '->', formData.departmentId)
      } else {
        ElMessage.warning('请选择部门')
        return // 阻止提交
      }
    }
    
    // 删除departmentName字段，只发送departmentId到后端
    delete formData.departmentName
    
    // 确保不发送publishTime字段，让后端根据状态变化自动处理
    delete formData.publishTime
    
    console.log('提交公告表单:', JSON.stringify(formData))
    
    if (isEdit.value) {
      // 编辑公告
      console.log('编辑公告数据(提交前):', JSON.stringify(formData))
      const res = await api.notice.update(formData.id, formData)
      if (res.code === 0) {
        ElMessage.success('公告更新成功')
        dialogVisible.value = false
        // 更新成功后重置到第一页，便于看到最新公告
        pagination.value.page = 1
        loadAnnouncements()
      } else {
        ElMessage.error(res.msg || '公告更新失败')
      }
    } else {
      // 创建公告
      // 确保不包含id字段，让后端自动生成
      delete formData.id
      // 创建时默认直接发布
      formData.status = 'published'
      
      console.log('创建公告数据(提交前):', JSON.stringify(formData))
      const res = await api.notice.create(formData)
      if (res.code === 0) {
        ElMessage.success('公告发布成功')
        dialogVisible.value = false
        // 发布成功后重置到第一页，便于看到最新公告
        pagination.value.page = 1
        loadAnnouncements()
      } else {
        ElMessage.error(res.msg || '公告发布失败')
      }
    }
  } catch (error) {
    console.error('提交公告表单出错:', error)
    ElMessage.error('操作失败，请重试')
  }
}

// 撤回公告
const withdrawAnnouncement = (announcement) => {
  ElMessageBox.confirm('确定要撤回该公告吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      // 将状态从'withdrawn'改为'archived'，与后端实体类定义一致
      const res = await api.notice.setStatus(announcement.id, 'archived')
      if (res.code === 0) {
        ElMessage.success('公告已撤回')
        loadAnnouncements()
        if (selectedAnnouncement.value && selectedAnnouncement.value.id === announcement.id) {
          selectedAnnouncement.value.status = 'archived'
        }
      } else {
        ElMessage.error(res.msg || '撤回公告失败')
      }
    } catch (error) {
      console.error('撤回公告出错:', error)
      ElMessage.error('撤回公告失败')
    }
  }).catch(() => {})
}

// 删除公告
const deleteAnnouncement = (announcement) => {
  ElMessageBox.confirm('确定要删除该公告吗？此操作不可恢复！', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'error'
  }).then(async () => {
    try {
      const res = await api.notice.remove(announcement.id)
      if (res.code === 0) {
        ElMessage.success('公告已删除')
        loadAnnouncements()
        if (selectedAnnouncement.value && selectedAnnouncement.value.id === announcement.id) {
          selectedAnnouncement.value = null
        }
      } else {
        ElMessage.error(res.msg || '删除公告失败')
      }
    } catch (error) {
      console.error('删除公告出错:', error)
      ElMessage.error('删除公告失败')
    }
  }).catch(() => {})
}

// 删除“我的公告会话”（仅限已读）
const deleteMyAnnouncementConversation = (announcement) => {
  if (!announcement?.isRead) {
    ElMessage.info('未读公告不能删除，请先阅读')
    return
  }
  ElMessageBox.confirm('确定从我的会话中删除该公告吗？这不会影响公告本身。', '删除会话', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await api.notice.deleteRead ? await api.notice.deleteRead(announcement.id) : await Promise.resolve({ code: 1 })
      if (res.code === 0) {
        ElMessage.success('已从我的会话中删除该公告')
        // 从列表移除或刷新状态
        announcements.value = announcements.value.filter(a => a.id !== announcement.id)
        if (selectedAnnouncement.value && selectedAnnouncement.value.id === announcement.id) {
          selectedAnnouncement.value = null
        }
        // 通知聊天页同步清空公告视图，并更新未读计数
        window.dispatchEvent(new CustomEvent('announcement-conversation-deleted', { detail: { announcementId: announcement.id } }))
      } else {
        ElMessage.error(res.msg || '删除失败')
      }
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// 监听筛选条件变化，重新加载数据
const handleFilterChange = () => {
  pagination.value.page = 1
  loadAnnouncements()
}

// 监听公告更新事件，自动刷新公告列表
const handleAnnouncementUpdate = (event) => {
  const detail = event.detail
  const incoming = detail && (detail.announcement || detail)
  const isUpdate = detail && detail.isUpdate
  console.log('收到公告更新事件:', incoming)
  
  // 如果收到的数据中包含id，尝试在本地列表中查找相同ID
  if (incoming && incoming.id) {
    const idx = announcements.value.findIndex(a => a.id === incoming.id)
    if (idx !== -1) {
      // 本地覆盖：更新标题/内容/范围等字段
      const oldItem = announcements.value[idx]
      const updatedItem = {
        ...oldItem,
        title: incoming.title ?? oldItem.title,
        content: incoming.content ?? oldItem.content,
        publishTime: incoming.publishTime ?? oldItem.publishTime,
        status: incoming.status ?? oldItem.status,
        scope: incoming.type === 'company' ? '全公司' : (incoming.departmentName || oldItem.scope),
        // 服务器已将阅读状态重置为未读，这里同步前端标记
        isRead: false
      }
      announcements.value.splice(idx, 1, updatedItem)
      
      // 如果当前选中的是该公告，同步详情展示并提示“已更新”
      if (selectedAnnouncement.value && selectedAnnouncement.value.id === incoming.id) {
        selectedAnnouncement.value = {
          ...selectedAnnouncement.value,
          title: updatedItem.title,
          content: updatedItem.content,
          publishTime: updatedItem.publishTime,
          status: updatedItem.status,
          scope: updatedItem.scope,
          isRead: false
        }
        ElMessage.success('该公告已更新')
      } else {
        ElMessage.success('公告已更新')
      }
      
      // 同步触发未读筛选/徽标等刷新（可选：不强制刷新整个列表）
      // 这里保留轻量逻辑，仅在必要时刷新计数
      // 可在 Home.vue 已触发未读数刷新，这里无需重复
      return
    }
  }
  
  // 如果本地未找到该公告或没有id，回退到原有刷新逻辑
  setTimeout(() => {
    loadAnnouncements()
    if (selectedAnnouncement.value) {
      selectAnnouncement(selectedAnnouncement.value)
    }
    ElMessage.success('公告列表已更新')
  }, 800)
}

// 组件挂载时加载部门和公告数据
onMounted(async () => {
  await loadDepartments()
  await loadAnnouncements()
  // 确保用户信息已加载
  userStore.loadUserInfo()
  
  // 移除自动清除未读的行为：必须点击列表中的公告才记为已读
  
  // 如果带有公告id参数，则仅定位但不自动标记为已读，提示用户点击左侧列表
  const targetId = route.query && route.query.id ? Number(route.query.id) : null
  const fromChat = route.query && route.query.fromChat ? true : false
  if (targetId) {
    const target = announcements.value.find(a => a.id === targetId)
    if (target) {
      // 如果是从聊天页跳转过来，自动选中该公告并标记为已读
      if (fromChat) {
        await selectAnnouncement(target)
        ElMessage.success('已自动为您打开该公告并标记为已读')
      } else {
        // 不是从聊天页跳转，仅提示用户点击查看
        ElMessage.info('请在左侧列表中点击该公告以查看详情并标记为已读')
      }
    } else if (fromChat) {
      // 来自聊天页且列表中找不到该公告（可能已撤回/删除）
      ElMessage.warning('该公告已不存在或已被撤回')
    }
  }
  
  // 添加公告更新事件监听
  window.addEventListener('announcement-update', handleAnnouncementUpdate)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('announcement-update', handleAnnouncementUpdate)
})
</script>

<template>
  <div class="announcement-container">
    <!-- 左侧公告列表 -->
    <div class="announcements-panel">
      <div class="panel-header">
        <h3>系统公告</h3>
        <div class="button-group">
          <el-button v-if="isAdmin" type="primary" size="small" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>发布公告
          </el-button>
        </div>
      </div>
      
      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索系统公告"
          prefix-icon="Search"
          clearable
          @input="handleFilterChange"
          class="search-input">
        </el-input>
        
        <div class="filter-options">
          <el-select v-model="departmentFilter" placeholder="部门" size="small" @change="handleFilterChange">
            <el-option
              v-for="dept in departments"
              :key="dept"
              :label="dept"
              :value="dept">
            </el-option>
          </el-select>
          
          <el-select v-model="readStatusFilter" placeholder="状态" size="small" @change="handleFilterChange">
            <el-option label="全部" value="全部"></el-option>
            <el-option label="已读" value="已读"></el-option>
            <el-option label="未读" value="未读"></el-option>
          </el-select>
        </div>
      </div>
      
      <div class="announcements-list" v-loading="loading">
        <div 
          v-for="announcement in filteredAnnouncements" 
          :key="announcement.id"
          class="announcement-item"
          :class="{ 
            'selected': selectedAnnouncement && selectedAnnouncement.id === announcement.id,
            'unread': !announcement.isRead,
            'withdrawn': announcement.status === 'withdrawn'
          }"
          @click="selectAnnouncement(announcement)">
          <div class="announcement-icon">
            <el-icon v-if="!announcement.isRead"><Bell /></el-icon>
            <el-icon v-else><View /></el-icon>
          </div>
          <div class="announcement-info">
            <div class="announcement-title">{{ announcement.title }}</div>
            <div class="announcement-meta">
              <span class="publisher">{{ announcement.publisher }}</span>
              <span class="time">{{ formatDate(announcement.publishTime) }}</span>
              <span v-if="announcement.status === 'withdrawn'" class="withdrawn-tag">已撤回</span>
            </div>
          </div>
          <div class="announcement-badge" v-if="announcement.scope !== '全公司'">
            {{ announcement.scope }}
          </div>
          <div class="item-actions">
            <!-- 仅已读才允许删除“我的会话”，并且不冒泡到整行点击 -->
            <el-tooltip v-if="announcement.isRead" content="删除我的会话" placement="left">
              <el-icon class="delete-icon" @click.stop="deleteMyAnnouncementConversation(announcement)"><Delete /></el-icon>
            </el-tooltip>
          </div>
        </div>
        
        <!-- 空状态 -->
        <el-empty v-if="filteredAnnouncements.length === 0 && !loading" description="暂无系统公告"></el-empty>
        
        <!-- 分页 -->
        <div class="pagination-container" v-if="pagination.total > 0">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="pagination.total"
            @size-change="loadAnnouncements"
            @current-change="loadAnnouncements">
          </el-pagination>
        </div>
      </div>
    </div>
    
    <!-- 右侧公告详情 -->
    <div class="announcement-details-panel" v-if="selectedAnnouncement" v-loading="loading">
      <div class="announcement-header">
        <h2>{{ selectedAnnouncement.title }}</h2>
        <div class="announcement-meta">
          <div class="publisher-info">
            <el-avatar :src="selectedAnnouncement.publisherAvatar" :size="24"></el-avatar>
            <span>{{ selectedAnnouncement.publisher }}</span>
          </div>
          <div class="publish-time">{{ formatDate(selectedAnnouncement.publishTime) }}</div>
          <div class="announcement-scope">发布范围：{{ selectedAnnouncement.scope }}</div>
          <div v-if="selectedAnnouncement.status === 'withdrawn'" class="status-tag withdrawn">已撤回</div>
        </div>
        <div class="announcement-actions" v-if="isAdmin && selectedAnnouncement.status !== 'withdrawn'">
          <el-button type="primary" size="small" @click="openEditDialog(selectedAnnouncement)">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
          <el-button type="danger" size="small" @click="withdrawAnnouncement(selectedAnnouncement)">
            <el-icon><Delete /></el-icon>撤回
          </el-button>
        </div>
        <div class="announcement-actions" v-else>
          <!-- 普通用户：删除“我的公告会话”（需已读）；管理员撤回后也可删除公告本身 -->
          <el-button v-if="isAdmin && selectedAnnouncement.status === 'withdrawn'" type="danger" size="small" @click="deleteAnnouncement(selectedAnnouncement)">
            <el-icon><Delete /></el-icon>删除公告
          </el-button>
          <el-button type="danger" size="small" @click="deleteMyAnnouncementConversation(selectedAnnouncement)">
            <el-icon><Delete /></el-icon>删除我的会话
          </el-button>
        </div>
      </div>
      
      <div class="announcement-content">
        <pre>{{ selectedAnnouncement.content }}</pre>
      </div>
      
      <div class="read-status-section">
        <div class="read-status-header">
          <div class="read-count">
            已读 {{ selectedAnnouncement.readCount }}/{{ selectedAnnouncement.totalCount }}
            <el-progress 
              :percentage="Math.round((selectedAnnouncement.readCount / (selectedAnnouncement.totalCount || 1)) * 100)" 
              :stroke-width="8" 
              :show-text="false">
            </el-progress>
          </div>
        </div>
        
        <div class="users-list">
          <el-empty v-if="readUsers.length === 0" description="暂无已读人员" :image-size="60"></el-empty>
          <el-tooltip 
            v-for="user in readUsers" 
            :key="user.id"
            :content="user.name + ' - ' + user.department"
            placement="top">
            <el-avatar :src="user.avatar" :size="32" class="user-avatar">
              {{ (user.name || '用').charAt(0) }}
            </el-avatar>
          </el-tooltip>
        </div>
      </div>
    </div>
    
    <!-- 未选择公告时的提示 -->
    <div class="announcement-details-panel empty-panel" v-else>
      <el-empty description="请选择一条系统公告查看详情">
        <template #image>
          <el-icon style="font-size: 60px"><Bell /></el-icon>
        </template>
      </el-empty>
    </div>
    
    <!-- 公告表单对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="50%"
      :close-on-click-modal="false">
      <el-form :model="announcementForm" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="announcementForm.title" placeholder="请输入公告标题"></el-input>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="announcementForm.content"
            type="textarea"
            :rows="10"
            placeholder="请输入公告内容"></el-input>
        </el-form-item>
        <el-form-item label="发布范围" required>
          <el-radio-group v-model="announcementForm.type">
            <el-radio label="company">全公司</el-radio>
            <el-radio label="department">指定部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择部门" v-if="announcementForm.type === 'department'">
          <el-select v-model="announcementForm.departmentName" placeholder="请选择部门" clearable filterable>
            <el-option
              v-for="(dept, index) in departments.filter(d => d !== '全部')"
              :key="dept"
              :label="dept"
              :value="dept">
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAnnouncementForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.announcement-container {
  display: flex;
  height: 100%;
  
  .announcements-panel {
    width: 350px;
    border-right: 1px solid #e6e6e6;
    display: flex;
    flex-direction: column;
    
    .panel-header {
      padding: 15px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid #e6e6e6;
      
      h3 {
        margin: 0;
        font-size: 16px;
      }
      
      .button-group {
        display: flex;
        gap: 10px;
      }
    }
    
    .filter-bar {
      padding: 15px;
      border-bottom: 1px solid #e6e6e6;
      
      .search-input {
        margin-bottom: 10px;
      }
      
      .filter-options {
        display: flex;
        gap: 10px;
      }
    }
    
    .announcements-list {
      flex: 1;
      overflow-y: auto;
      position: relative;
      padding-bottom: 50px; /* 为分页留出空间 */
      
      .announcement-item {
        display: flex;
        align-items: center;
        padding: 15px;
        cursor: pointer;
        transition: background-color 0.3s;
        border-bottom: 1px solid #f0f0f0;
        position: relative;
        
        &:hover {
          background-color: #f5f7fa;
        }
        
        &.selected {
          background-color: #ecf5ff;
        }
        
        &.unread {
          .announcement-icon {
            color: #f56c6c;
          }
          
          .announcement-title {
            font-weight: 600;
            color: #303133;
          }
        }
        
        &.withdrawn {
          opacity: 0.7;
          
          .announcement-title {
            text-decoration: line-through;
            color: #909399;
          }
          
          .withdrawn-tag {
            color: #f56c6c;
            margin-left: 5px;
          }
        }
        
        .announcement-icon {
          font-size: 18px;
          margin-right: 12px;
          color: #909399;
        }
        
        .announcement-info {
          flex: 1;
          min-width: 0;
          
          .announcement-title {
            font-size: 15px;
            margin-bottom: 5px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
          
          .announcement-meta {
            display: flex;
            font-size: 12px;
            color: #909399;
            
            .publisher {
              margin-right: 10px;
            }
          }
        }
        
        .announcement-badge {
          background-color: #409eff;
          color: white;
          padding: 2px 8px;
          border-radius: 10px;
          font-size: 12px;
          margin-left: 10px;
        }

        .item-actions {
          margin-left: auto;
          display: flex;
          align-items: center;
        }
        .delete-icon {
          color: #c0c4cc;
          cursor: pointer;
          transition: color .2s;
        }
        .delete-icon:hover {
          color: #f56c6c;
        }
      }
      
      .pagination-container {
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        padding: 10px;
        background-color: #fff;
        border-top: 1px solid #e6e6e6;
        display: flex;
        justify-content: center;
      }
    }
  }
  
  .announcement-details-panel {
    flex: 1;
    padding: 20px;
    display: flex;
    flex-direction: column;
    overflow-y: auto;
    
    &.empty-panel {
      justify-content: center;
      align-items: center;
    }
    
    .announcement-header {
      margin-bottom: 20px;
      
      h2 {
        margin: 0 0 15px 0;
        font-size: 22px;
      }
      
      .announcement-meta {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 15px;
        margin-bottom: 15px;
        color: #606266;
        font-size: 14px;
        
        .publisher-info {
          display: flex;
          align-items: center;
          
          span {
            margin-left: 8px;
          }
        }
        
        .status-tag {
          padding: 2px 8px;
          border-radius: 4px;
          font-size: 12px;
          
          &.withdrawn {
            background-color: #fef0f0;
            color: #f56c6c;
          }
        }
      }
      
      .announcement-actions {
        display: flex;
        gap: 10px;
        margin-top: 15px;
      }
    }
    
    .announcement-content {
      background-color: #f5f7fa;
      padding: 20px;
      border-radius: 4px;
      margin-bottom: 20px;
      flex: 1;
      
      pre {
        white-space: pre-wrap;
        font-family: inherit;
        margin: 0;
        line-height: 1.6;
      }
    }
    
    .read-status-section {
      border-top: 1px solid #e6e6e6;
      padding-top: 20px;
      
      .read-status-header {
        margin-bottom: 15px;
        
        .read-count {
          font-size: 14px;
          color: #606266;
          margin-bottom: 10px;
        }
      }
      
      .users-list {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        min-height: 60px; /* 为空状态留出空间 */
        
        .user-avatar {
          cursor: pointer;
          transition: transform 0.2s;
          
          &:hover {
            transform: scale(1.1);
          }
        }
      }
    }
  }
}
</style>