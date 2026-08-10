<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import api from '@/api'

// 标签页激活状态
const activeTab = ref('userManagement')

// 用户管理相关数据
const userTableData = ref([])
const userSearchKeyword = ref('')
const userPagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 已移除：系统设置（不再使用）

// 用户表格列定义
const userColumns = [
  { prop: 'id', label: 'ID', width: '80' },
  { prop: 'username', label: '用户名', width: '120' },
  { prop: 'realName', label: '姓名', width: '120' },
  { prop: 'department', label: '部门', width: '120' },
  { prop: 'position', label: '职位', width: '120' }, // 添加职位列
  { prop: 'role', label: '角色', width: '120' },
  { prop: 'email', label: '邮箱', width: '180' },
  { prop: 'phone', label: '电话', width: '150' },
  { prop: 'status', label: '状态', width: '100' },
  { prop: 'lastLogin', label: '最后登录时间', width: '180' },
  { prop: 'operations', label: '操作', width: '200', fixed: 'right' }
]

// 角色选项
const roleOptions = [
  { value: 'department_admin', label: '部门管理员' },
  { value: 'employee', label: '普通员工' }
]

// 部门选项
const departmentOptions = ref([])

// 用户状态选项
const statusOptions = [
  { value: 'active', label: '正常' },
  { value: 'inactive', label: '禁用' },
  { value: 'pending', label: '待审核' }
]

// 用户表单
const userForm = reactive({
  id: '',
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  department: '',
  role: '',
  email: '',
  phone: '',
  status: 'active',
  position: '' // 添加职位字段
})

// 用户表单验证规则
const userFormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== userForm.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  departmentId: [
    { required: true, message: '请选择部门', trigger: 'change' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ]
}

// 用户表单对话框
const userDialogVisible = ref(false)
const userDialogTitle = ref('添加用户')
const userFormRef = ref(null)
// 用户表单字段禁用状态
const userFormDisabled = ref(false)

// 获取用户列表
const getUserList = async () => {
  try {
    const res = await api.system.userList()
    if (res.code === 0 && res.data) {
      // 处理用户数据，添加显示所需的字段
      const users = res.data.map(user => {
        // 根据角色值获取角色显示名称
        let roleName = '普通员工'
        if (user.role === 'system_admin') roleName = '系统管理员'
        else if (user.role === 'department_admin') roleName = '部门管理员'
        
        // 用户状态
        const statusValue = user.status || 'active'
        const statusName = statusValue === 'active' ? '正常' : '禁用'
        
        return {
          id: user.id,
          username: user.username,
          realName: user.realname || '',
          department: user.department || '',
          departmentValue: user.departmentId || null,
          position: user.position || '', // 添加职位字段
          role: roleName,
          roleValue: user.role,
          email: user.email || '',
          phone: user.phone || '',
          status: statusName,
          statusValue: statusValue,
          lastLogin: user.lastLogin ? new Date(user.lastLogin).toLocaleString() : ''
        }
      })
      
      // 过滤搜索结果
      let filteredUsers = [...users]
      if (userSearchKeyword.value) {
        const keyword = userSearchKeyword.value.toLowerCase()
        filteredUsers = filteredUsers.filter(user => 
          (user.username && user.username.toLowerCase().includes(keyword)) ||
          (user.realName && user.realName.toLowerCase().includes(keyword)) ||
          (user.email && user.email.toLowerCase().includes(keyword)) ||
          (user.department && user.department.toLowerCase().includes(keyword))
        )
      }
      
      // 更新分页信息
      userPagination.total = filteredUsers.length
      
      // 分页处理
      const start = (userPagination.currentPage - 1) * userPagination.pageSize
      const end = start + userPagination.pageSize
      userTableData.value = filteredUsers.slice(start, end)
    } else {
      ElMessage.error(res.msg || '获取用户列表失败')
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error('获取用户列表失败')
  }
}

// 搜索用户
const searchUsers = () => {
  userPagination.currentPage = 1
  getUserList()
}

// 重置搜索
const resetSearch = () => {
  userSearchKeyword.value = ''
  userPagination.currentPage = 1
  getUserList()
}

// 分页变化
const handlePageChange = (page) => {
  userPagination.currentPage = page
  getUserList()
}

// 每页条数变化
const handleSizeChange = (size) => {
  userPagination.pageSize = size
  userPagination.currentPage = 1
  getUserList()
}

// 添加用户功能已移除
// const openAddUserDialog = () => {
//   userDialogTitle.value = '添加用户'
//   resetUserForm()
//   userFormDisabled.value = false
//   userDialogVisible.value = true
// }

// 打开编辑用户对话框
const openEditUserDialog = (row) => {
  userDialogTitle.value = '编辑用户信息'
  resetUserForm()
  
  // 填充表单数据
  userForm.id = row.id
  userForm.role = row.roleValue
  userForm.departmentId = row.departmentValue
  userForm.position = row.position // 填充职位字段
  
  // 编辑模式下不需要密码
  userFormRules.password[0].required = false
  userFormRules.confirmPassword[0].required = false
  
  userDialogVisible.value = true
}

// 重置用户表单
const resetUserForm = () => {
  if (userFormRef.value) {
    userFormRef.value.resetFields()
  }
  
  Object.assign(userForm, {
    id: '',
    username: '',
    password: '',
    confirmPassword: '',
    realName: '',
    department: '',
    departmentId: '',
    role: '',
    email: '',
    phone: '',
    status: 'active',
    position: '' // 重置职位字段
  })
  
  // 重置字段禁用状态
  userFormDisabled.value = false
  
  // 重置密码为必填
  if (userFormRules.password && userFormRules.confirmPassword) {
    userFormRules.password[0].required = true
    userFormRules.confirmPassword[0].required = true
  }
}

// 提交用户表单
const submitUserForm = () => {
  if (!userFormRef.value) return
  
  userFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (userForm.id) {
          // 更新用户角色
          const roleRes = await api.system.updateRole(userForm.id, userForm.role)
          
          if (roleRes.code !== 0) {
            ElMessage.error(roleRes.msg || '用户角色更新失败')
            return
          }
          
          // 更新用户部门
          const departmentRes = await api.system.updateDepartment(userForm.id, userForm.departmentId)
          
          if (departmentRes.code !== 0) {
            ElMessage.error(departmentRes.msg || '用户部门更新失败')
            return
          }
          
          // 更新用户职位
          try {
            console.log('更新职位参数:', userForm.id, userForm.position)
            const positionRes = await api.system.updatePosition(userForm.id, userForm.position)
            console.log('职位更新响应:', positionRes)
            
            if (positionRes.code !== 0) {
              console.error('职位更新失败:', positionRes)
              ElMessage.error(positionRes.msg || '用户职位更新失败')
              return
            }
          } catch (error) {
            console.error('职位更新异常:', error)
            ElMessage.error('职位更新异常: ' + (error.message || error))
            return
          }
          
          ElMessage.success('用户信息更新成功')
          userDialogVisible.value = false
          
          // 直接更新本地用户数据，而不是重新调用getUserList
          const updatedUser = userTableData.value.find(user => user.id === userForm.id)
          if (updatedUser) {
            // 根据角色值获取角色显示名称
            let roleName = '普通员工'
            if (userForm.role === 'department_admin') roleName = '部门管理员'
            
            // 更新角色信息
            updatedUser.role = roleName
            updatedUser.roleValue = userForm.role
            
            // 更新部门信息
            const selectedDepartment = departmentOptions.value.find(dept => dept.value === userForm.departmentId)
            if (selectedDepartment) {
              updatedUser.department = selectedDepartment.label
              updatedUser.departmentValue = userForm.departmentId
            }
            
            // 更新职位信息
            updatedUser.position = userForm.position
          } else {
            // 如果在当前页面找不到用户，则刷新整个列表
            getUserList()
          }
        } else {
          // 添加用户功能已移除
          ElMessage.warning('系统不支持直接添加用户，用户需要通过注册流程')
          userDialogVisible.value = false
        }
      } catch (error) {
        console.error('提交用户表单失败:', error)
        ElMessage.error(error.message || '操作失败，请稍后重试')
      }
    }
  })
}

// 删除用户
const deleteUser = (row) => {
  ElMessageBox.confirm(
    `确定要删除用户 "${row.username}" 吗？`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      const res = await api.system.deleteUser(row.id)
      if (res.code === 0) {
        ElMessage.success('用户删除成功')
        getUserList()
      } else {
        ElMessage.error(res.msg || '用户删除失败')
      }
    } catch (error) {
      console.error('删除用户失败:', error)
      ElMessage.error('操作失败，请稍后重试')
    }
  }).catch(() => {
    // 取消删除
  })
}

// 更改用户状态
const changeUserStatus = (row) => {
  const newStatus = row.statusValue === 'active' ? 'inactive' : 'active'
  const statusText = newStatus === 'active' ? '启用' : '禁用'
  
  ElMessageBox.confirm(
    `确定要${statusText}用户 "${row.username}" 吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      const res = await api.system.updateStatus(row.id, newStatus)
      if (res.code === 0) {
        row.statusValue = newStatus
        row.status = newStatus === 'active' ? '正常' : '禁用'
        ElMessage.success(`用户${statusText}成功`)
      } else {
        ElMessage.error(res.msg || `用户${statusText}失败`)
      }
    } catch (error) {
      console.error('更改用户状态失败:', error)
      ElMessage.error('操作失败，请稍后重试')
    }
  }).catch(() => {
    // 取消操作
  })
}

// 已移除：系统设置保存/重置逻辑

// 获取部门列表
const getDepartmentList = async () => {
  try {
    const res = await api.org.allDepartments()
    if (res.code === 0 && res.data) {
      departmentOptions.value = res.data.map(dept => ({
        value: dept.id,
        label: dept.departmentName
      }))
    } else {
      ElMessage.error(res.msg || '获取部门列表失败')
    }
  } catch (error) {
    console.error('获取部门列表失败:', error)
    ElMessage.error('获取部门列表失败')
  }
}

// 初始化
onMounted(() => {
  getUserList()
  getDepartmentList()
})
</script>

<template>
  <div class="system-management-container">
    <el-tabs v-model="activeTab" class="system-tabs">
      <el-tab-pane label="用户管理" name="userManagement">
        <div class="user-management-container">
          <div class="user-management-header">
            <div class="search-box">
              <el-input
                v-model="userSearchKeyword"
                placeholder="搜索用户名、姓名、邮箱或部门"
                clearable
                @keyup.enter="searchUsers"
              >
                <template #append>
                  <el-button @click="searchUsers">
                    <el-icon><Search /></el-icon>
                  </el-button>
                </template>
              </el-input>
              <el-button @click="resetSearch">重置</el-button>
            </div>
          </div>
          
          <el-table
            :data="userTableData"
            border
            style="width: 100%"
            max-height="calc(100vh - 250px)"
          >
            <el-table-column
              v-for="column in userColumns"
              :key="column.prop"
              :prop="column.prop"
              :label="column.label"
              :width="column.width"
              :fixed="column.fixed"
            >
              <template #default="scope" v-if="column.prop === 'status'">
                <el-tag
                  :type="scope.row.statusValue === 'active' ? 'success' : scope.row.statusValue === 'inactive' ? 'danger' : 'warning'"
                >
                  {{ scope.row.status }}
                </el-tag>
              </template>
              
              <template #default="scope" v-if="column.prop === 'operations'">
                <el-button
                  type="primary"
                  size="small"
                  @click="openEditUserDialog(scope.row)"
                >
                  编辑角色
                </el-button>
                <el-button
                  size="small"
                  :type="scope.row.statusValue === 'active' ? 'danger' : 'success'"
                  @click="changeUserStatus(scope.row)"
                >
                  {{ scope.row.statusValue === 'active' ? '禁用' : '启用' }}
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  @click="deleteUser(scope.row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="userPagination.currentPage"
              v-model:page-size="userPagination.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="userPagination.total"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </el-tab-pane>
      
      
    </el-tabs>
    
    <!-- 用户表单对话框 -->
    <el-dialog
      v-model="userDialogVisible"
      :title="userDialogTitle"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userFormRules"
        label-width="100px"
      >
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" placeholder="请选择角色">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" prop="departmentId">
          <el-select v-model="userForm.departmentId" placeholder="请选择部门">
            <el-option
              v-for="item in departmentOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="职位" prop="position">
          <el-input v-model="userForm.position" placeholder="请输入职位名称"></el-input>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="userDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitUserForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.system-management-container {
  padding: 20px;
  height: 100%;
  
  .system-tabs {
    height: 100%;
    display: flex;
    flex-direction: column;
    
    :deep(.el-tabs__content) {
      flex: 1;
      overflow: auto;
    }
  }
  
  .user-management-container {
    .user-management-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 20px;
      
      .search-box {
        display: flex;
        gap: 10px;
        width: 400px;
      }
    }
    
    .pagination-container {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
  }
  
  
  
  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 5px;
  }
}
</style>