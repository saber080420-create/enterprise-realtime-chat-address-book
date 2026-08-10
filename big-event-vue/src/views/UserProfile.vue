<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '@/api'

// 获取路由实例
const router = useRouter()

// 用户信息
const userInfo = ref({
  username: '',
  nickname: '',
  realname: '',
  email: '',
  phone: '',
  department: '',
  role: '',
  position: '',
  lastLogin: '',
  registerDate: ''
})

/**
 * 加载用户信息
 * 从服务器获取最新的用户信息并更新到页面
 */
const loadUserInfo = async () => {
  try {
    // 调用API获取用户信息
    const response = await api.account.me()
    console.log('用户信息响应:', response)
    if (response && response.code === 0 && response.data) {
      userInfo.value = response.data
    } else {
      // 如果API调用失败或返回错误，显示错误信息
      ElMessage.error(response?.msg || '获取用户信息失败')
      console.error('获取用户信息失败:', response)
    }
  } catch (error) {
    ElMessage.error('获取用户信息失败')
    console.error('获取用户信息失败:', error)
  }
}

// 组件挂载时加载用户信息
onMounted(() => {
  loadUserInfo()
})
</script>

<template>
  <div class="user-profile-container">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <h2>个人中心</h2>
          <el-button type="primary" size="small" @click="loadUserInfo">刷新</el-button>
        </div>
      </template>
      
      <div class="profile-content">
        <div class="profile-avatar">
          <el-avatar :size="100" :src="userInfo.userPic || '/avatar.jpg'" />
          <h3>{{ userInfo.nickname || userInfo.username }}</h3>
          <p>{{ userInfo.role }} | {{ userInfo.department }}</p>
        </div>
        
        <el-divider />
        
        <div class="profile-info">
          <el-descriptions title="基本信息" :column="2" border>
            <el-descriptions-item label="账号">{{ userInfo.username }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">{{ userInfo.realname || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ userInfo.nickname || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userInfo.email }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ userInfo.phone }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ userInfo.department }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ userInfo.role }}</el-descriptions-item>
            <el-descriptions-item label="职位">{{ userInfo.position || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">{{ userInfo.lastLogin }}</el-descriptions-item>
            <el-descriptions-item label="注册日期">{{ userInfo.registerDate }}</el-descriptions-item>
          </el-descriptions>
        </div>
        
        <el-divider />
        
        <div class="profile-actions">
          <el-button type="primary" round @click="router.push('/settings')">编辑个人资料</el-button>
          <el-button type="info" round @click="router.push('/settings?tab=password')">修改密码</el-button>
        </div>
      </div>
    </el-card>
    
    <!-- 不再需要修改对话框 -->
  </div>
</template>

<style lang="scss" scoped>
.user-profile-container {
  padding: 20px;
  
  .profile-card {
    max-width: 800px;
    margin: 0 auto;
    
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      
      h2 {
        margin: 0;
        font-size: 18px;
        color: #303133;
      }
    }
    
    .profile-content {
      .profile-avatar {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-bottom: 20px;
        
        h3 {
          margin: 10px 0 5px;
          font-size: 18px;
          color: #303133;
        }
        
        p {
          margin: 0;
          color: #909399;
          font-size: 14px;
        }
      }
      
      .profile-info {
        margin: 20px 0;
        
        .nickname-container {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }
      }
      
      .profile-actions {
        display: flex;
        justify-content: center;
        gap: 20px;
        margin-top: 20px;
      }
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>