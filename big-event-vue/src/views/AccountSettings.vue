<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import request from '@/utils/request'

// 获取路由实例
const route = useRoute()
const router = useRouter()

// 用户基本信息表单
const userForm = reactive({
  username: '张三',
  nickname: '张三',
  realName: '张三',
  email: 'zhangsan@example.com',
  phone: '13800138000',
  userPic: ''
})

// 修改密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 已移除：通知设置（不再使用）

// 表单验证规则
const userFormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 10, message: '昵称长度不能超过10个字符', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ]
}

const passwordFormRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 表单引用
const userFormRef = ref(null)
const passwordFormRef = ref(null)
const avatarUploadRef = ref(null)

// 当前激活的标签页
const activeTab = ref('basic')

// 根据URL参数设置激活的标签页
onMounted(() => {
  // 如果URL中有tab参数，则切换到对应的标签页
  const tabParam = route.query.tab
  if (tabParam && ['basic', 'password'].includes(tabParam)) {
    activeTab.value = tabParam
  }
  
  // 加载用户信息
  loadUserInfo()
})

/**
 * 加载用户信息
 * 从服务器获取最新的用户信息并更新到表单
 */
const loadUserInfo = async () => {
  try {
    // 调用API获取用户信息
    const response = await api.account.me()
    if (response && response.code === 0 && response.data) {
      // 更新表单数据
      userForm.username = response.data.username || ''
      userForm.nickname = response.data.nickname || ''
      userForm.realName = response.data.realname || ''
      userForm.email = response.data.email || ''
      userForm.phone = response.data.phone || ''
      userForm.userPic = response.data.userPic || ''
    } else {
      ElMessage.error(response?.msg || '获取用户信息失败')
    }
  } catch (error) {
    ElMessage.error('获取用户信息失败')
    console.error('获取用户信息失败:', error)
  }
}

/**
 * 更新用户信息
 * 验证表单并调用API更新用户信息
 */
const updateUserInfo = async () => {
  if (!userFormRef.value) return
  
  try {
    await userFormRef.value.validate()
    
    // 更新昵称
    const nicknameResponse = await api.account.updateNickname(userForm.nickname)
    if (nicknameResponse.code !== 0) {
      ElMessage.error(nicknameResponse.message || '昵称更新失败')
      return
    }
    
    // 更新真实姓名
    const realnameResponse = await api.account.updateRealname(userForm.realName)
    if (realnameResponse.code !== 0) {
      ElMessage.error(realnameResponse.message || '真实姓名更新失败')
      return
    }
    
    // 更新邮箱
    const emailResponse = await api.account.updateEmail(userForm.email)
    if (emailResponse.code !== 0) {
      ElMessage.error(emailResponse.message || '邮箱更新失败')
      return
    }
    
    // 更新电话
    const phoneResponse = await api.account.updatePhone(userForm.phone)
    if (phoneResponse.code !== 0) {
      ElMessage.error(phoneResponse.message || '电话更新失败')
      return
    }
    
    ElMessage.success('个人信息更新成功')
    
    // 重新加载用户信息，确保数据同步
    await loadUserInfo()
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

/**
 * 处理头像上传
 * 当用户选择新头像文件时触发
 */
const handleAvatarChange = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  // 验证文件类型
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('只能上传JPG、PNG或GIF格式的图片')
    return
  }
  
  // 验证文件大小（限制为2MB）
  const maxSize = 2 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('图片大小不能超过2MB')
    return
  }
  
  try {
    // 创建FormData对象
    const formData = new FormData()
    formData.append('file', file) // 注意：参数名必须与后端@RequestParam一致
    
    // 临时显示新头像（使用本地文件预览）
    const reader = new FileReader()
    reader.onload = async (e) => {
      // 本地预览使用Base64
      userForm.userPic = e.target.result
      
      try {
        // 调用后端文件上传API
        console.log('开始上传头像...')
        
        // 使用axios实例发送请求，自动带上token
        const result = await request.post('/upload/avatar', formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        })
        
        console.log('上传响应结果:', result)
        
        if (result && result.code === 0) {
          // 头像上传接口已在后端同步更新了用户头像，此处不再进行二次更新，避免重复校验报错
          const fileUrl = result.data
          userForm.userPic = fileUrl
          ElMessage.success('头像上传成功')
          // 同步最新信息（可选）
          await loadUserInfo()
        } else {
          ElMessage.error(result?.msg || '头像上传失败')
          // 恢复原头像
          await loadUserInfo()
        }
      } catch (error) {
        console.error('上传头像失败:', error)
        // 显示更详细的错误信息
        ElMessage.error(`头像上传失败: ${error.message || '未知错误'}`)
        // 恢复原头像
        await loadUserInfo()
      }
    }
    
    reader.readAsDataURL(file)
  } catch (error) {
    console.error('处理头像上传失败:', error)
    ElMessage.error('头像处理失败')
  } finally {
    // 清空文件输入，允许重新选择同一文件
    event.target.value = ''
  }
}

// 更新密码
const updatePassword = async () => {
  if (!passwordFormRef.value) return
  
  try {
    await passwordFormRef.value.validate()
    
    // 调用API更新密码
    const response = await updatePasswordService(
      passwordForm.oldPassword,
      passwordForm.newPassword,
      passwordForm.confirmPassword
    )
    
    if (response.code === 0) {
      // 密码修改成功，提示用户重新登录
      ElMessageBox.alert('密码修改成功，请重新登录', '提示', {
        confirmButtonText: '确定',
        callback: () => {
          // 重置表单
          passwordForm.oldPassword = ''
          passwordForm.newPassword = ''
          passwordForm.confirmPassword = ''
          passwordFormRef.value.resetFields()
          
          // 跳转到登录页
          router.push('/login')
        }
      })
    } else {
      ElMessage.error(response.msg || '密码修改失败')
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}
</script>

<template>
  <div class="account-settings-container">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <h2>账号设置</h2>
        </div>
      </template>
      
      <el-tabs v-model="activeTab" class="settings-tabs">
        <el-tab-pane label="基本信息" name="basic">
          <el-form
            ref="userFormRef"
            :model="userForm"
            :rules="userFormRules"
            label-width="100px"
            class="settings-form"
          >
            <el-form-item label="头像">
              <div class="avatar-uploader-container">
                <el-avatar
                  :size="100"
                  :src="userForm.userPic || '/avatar.jpg'"
                  class="clickable-avatar"
                  @click="() => avatarUploadRef.click()"
                />
                <div class="avatar-upload-tip">点击头像更换</div>
                <input
                  type="file"
                  ref="avatarUploadRef"
                  style="display: none"
                  accept="image/*"
                  @change="handleAvatarChange"
                />
              </div>
            </el-form-item>
            
            <el-form-item label="用户名">
              <el-input v-model="userForm.username" disabled />
              <div class="form-tip">用户名不可修改</div>
            </el-form-item>
            
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="userForm.nickname" maxlength="10" show-word-limit />
              <div class="form-tip">昵称将显示在个人中心和聊天中</div>
            </el-form-item>
            
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="userForm.realName" />
            </el-form-item>
            
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="userForm.email" />
            </el-form-item>
            
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="userForm.phone" />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="updateUserInfo">保存修改</el-button>
              <el-button @click="userFormRef?.resetFields()">重置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        <el-tab-pane label="修改密码" name="password">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordFormRules"
            label-width="100px"
            class="settings-form"
          >
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="updatePassword">修改密码</el-button>
              <el-button @click="passwordFormRef?.resetFields()">重置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        
      </el-tabs>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.account-settings-container {
  padding: 20px;
  
  .settings-card {
    max-width: 800px;
    margin: 0 auto;
    
    .card-header {
      h2 {
        margin: 0;
        font-size: 18px;
        color: #303133;
      }
    }
    
    .settings-tabs {
      .settings-form {
        max-width: 500px;
        margin-top: 20px;
        
        .form-tip {
          font-size: 12px;
          color: #909399;
          margin-top: 5px;
        }
        
        .avatar-uploader-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          margin-bottom: 10px;
          
          .clickable-avatar {
            cursor: pointer;
            transition: all 0.3s;
            border: 2px solid transparent;
            
            &:hover {
              border-color: #409EFF;
              transform: scale(1.05);
            }
          }
          
          .avatar-upload-tip {
            font-size: 12px;
            color: #909399;
            margin-top: 8px;
          }
        }
      }
      
      
    }
  }
}
</style>