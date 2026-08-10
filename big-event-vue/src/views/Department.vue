<script setup>
import { ref, onMounted, computed } from 'vue'
import { Search, Plus, Edit, Delete, ArrowRight, OfficeBuilding } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

// 部门树形结构数据
const departmentTree = ref([])

// 当前选中的部门
const selectedDepartment = ref(null)

// 搜索关键词
const searchKeyword = ref('')

// 部门成员列表
const departmentMembers = ref([])

// 当前登录用户角色，用于权限显示
const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
const userIsSystemAdmin = computed(() => userInfo.role === 'system_admin')

// 加载部门树形结构
const loadDepartmentTree = async () => {
  try {
    const res = await api.org.departmentTree()
    if (res.code === 0 && res.data) {
      departmentTree.value = res.data
    } else {
      ElMessage.warning('获取部门结构失败')
    }
  } catch (error) {
    console.error('获取部门树形结构错误:', error)
    ElMessage.error('获取部门数据失败')
  }
}

// 加载部门成员
const loadDepartmentMembers = async (departmentId) => {
  try {
    console.log('开始加载部门成员，部门ID:', departmentId)
    // 检查用户信息和权限
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    console.log('当前用户信息:', userInfo)
    
    // 放宽前端预校验：交由后端判定，避免本地 userInfo 过期导致误拒
    console.log('当前用户角色:', userInfo.role)
    
    // 优先新门面：/org/department/{id}/users
    let res = await api.org.departmentUsers(departmentId)
    // 兼容：若新门面受限，回退旧接口 /user/byDepartment/{id}
    if (!(res && res.code === 0 && Array.isArray(res.data))) {
      try {
        const legacy = await (await import('@/api/department.js')).then(m => m.getDepartmentUsersService(departmentId))
        if (legacy?.code === 0) {
          res = legacy
        }
      } catch (_) {}
    }
    console.log('部门成员API响应:', res)
    if (res.code === 0 && res.data) {
      // 转换用户数据为成员列表格式
      departmentMembers.value = res.data.map(user => ({
        id: user.id,
        name: user.username,
        realName: user.realname || user.nickname || user.username,
        position: user.position || '未设置',
        phone: user.phone || '未设置',
        email: user.email || '未设置',
        avatar: user.userPic || '/avatar.jpg',
        role: user.role // 添加角色字段
      }))
      console.log('部门成员数据处理完成:', departmentMembers.value)
    } else {
      console.warn('获取部门成员失败:', res)
      if (res.msg && res.msg.includes('权限')) {
        ElMessage.warning(`权限不足: ${res.msg}`)
      } else {
        ElMessage.warning(`获取部门成员失败: ${res.msg || '未知错误'}`)
      }
      departmentMembers.value = []
    }
  } catch (error) {
    console.error('获取部门成员错误:', error)
    // 检查是否是权限相关错误
    if (error.msg && error.msg.includes('权限')) {
      ElMessage.warning(`权限不足: ${error.msg}`)
    } else if (error.response && error.response.status === 403) {
      ElMessage.warning('您没有权限查看该部门的成员')
    } else {
      ElMessage.error(`获取部门成员失败: ${error.message || '未知错误'}`)
    }
    departmentMembers.value = []
  }
}

// 辅助：在树中查找指定ID的部门
const findDeptById = (list, id) => {
  if (!Array.isArray(list)) return null
  for (const d of list) {
    if (d && d.id === id) return d
    const c = findDeptById(d?.children, id)
    if (c) return c
  }
  return null
}

// 选择部门（非系统管理员仅允许查看本部门）
const selectDepartment = (department) => {
  const myDeptId = Number(JSON.parse(localStorage.getItem('userInfo') || '{}').departmentId || 0)
  const isSys = userIsSystemAdmin.value === true
  if (!isSys && department && department.id && Number(department.id) !== myDeptId) {
    ElMessage.warning('您没有权限查看该部门的成员')
    const mine = findDeptById(departmentTree.value, myDeptId)
    selectedDepartment.value = mine || null
    if (myDeptId) {
      loadDepartmentMembers(myDeptId)
    } else {
      departmentMembers.value = []
    }
    return
  }
  selectedDepartment.value = department
  if (department && department.id) {
    console.log('选择部门:', department)
    loadDepartmentMembers(department.id)
  }
}

// 部门表单对话框
const departmentDialogVisible = ref(false)
const departmentFormTitle = ref('添加部门')
const departmentForm = ref({
  id: null,
  name: '',
  parentId: null,
  description: ''
})
const departmentFormRules = {
  name: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { min: 2, max: 20, message: '部门名称长度为2-20个字符', trigger: 'blur' }
  ]
}
const departmentFormRef = ref(null)

// 打开添加部门对话框
const openAddDepartmentDialog = (parentId = null) => {
  departmentFormTitle.value = '添加部门'
  departmentForm.value = {
    id: null,
    name: '',
    parentId: parentId,
    description: ''
  }
  departmentDialogVisible.value = true
}

// 打开编辑部门对话框
const openEditDepartmentDialog = (department) => {
  departmentFormTitle.value = '编辑部门'
  departmentForm.value = {
    id: department.id,
    name: department.name,
    parentId: department.parentId,
    description: department.description || ''
  }
  departmentDialogVisible.value = true
}

// 提交部门表单
const submitDepartmentForm = async () => {
  if (!departmentFormRef.value) return
  
  await departmentFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        // 创建一个新的对象，将name字段转换为departmentName字段
        const departmentData = {
          ...departmentForm.value,
          departmentName: departmentForm.value.name
        }
        
        let res
        if (departmentForm.value.id) {
          // 更新部门
          res = await api.org.updateDepartment(
            departmentForm.value.id,
            departmentData
          )
        } else {
          // 添加部门
          res = await api.org.addDepartment(departmentData)
        }
        
        if (res.code === 0) {
          ElMessage.success(departmentForm.value.id ? '部门更新成功' : '部门添加成功')
          departmentDialogVisible.value = false
          // 重新加载部门树
          loadDepartmentTree()
        } else {
          ElMessage.error(res.msg || '操作失败')
        }
      } catch (error) {
        console.error('部门操作错误:', error)
        ElMessage.error('操作失败，请重试')
      }
    }
  })
}

// 删除部门
const handleDeleteDepartment = (department) => {
  ElMessageBox.confirm(
    `确定要删除部门 "${department.name}" 吗？删除后不可恢复，且会同时删除其下所有子部门。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const res = await api.org.deleteDepartment(department.id)
      if (res.code === 0) {
        ElMessage.success('部门删除成功')
        // 如果删除的是当前选中的部门，清空选中
        if (selectedDepartment.value && selectedDepartment.value.id === department.id) {
          selectedDepartment.value = null
        }
        // 重新加载部门树
        loadDepartmentTree()
      } else {
        ElMessage.error(res.msg || '删除失败')
      }
    } catch (error) {
      console.error('删除部门错误:', error)
      ElMessage.error('删除失败，请重试')
    }
  }).catch(() => {
    // 取消删除
  })
}

// 默认展开的节点
const defaultExpandedKeys = ref([1])

// 是否为管理员（用于控制编辑权限）— 仅系统管理员视为管理员
const isAdmin = computed(() => userIsSystemAdmin.value === true)

// 过滤部门树
const filterNode = (value, data) => {
  if (!value) return true
  return data.departmentName.includes(value)
}

// 扁平化部门树，用于下拉选择框
const flattenedDepartments = computed(() => {
  const result = [];
  
  // 递归函数，用于扁平化部门树
  const flatten = (departments, level = 0) => {
    if (!departments || !departments.length) return;
    
    departments.forEach(dept => {
      // 添加当前部门，并在名称前添加缩进，以便在下拉框中显示层级关系
      result.push({
        ...dept,
        departmentName: '　'.repeat(level) + (level > 0 ? '└ ' : '') + dept.departmentName
      });
      
      // 递归处理子部门
      if (dept.children && dept.children.length) {
        flatten(dept.children, level + 1);
      }
    });
  };
  
  flatten(departmentTree.value);
  return result;
})

// 在组件挂载时加载部门数据
onMounted(() => {
  loadDepartmentTree()
})

// 成员对话框
const memberDialogVisible = ref(false)
const editingMember = ref(null)
const newDepartmentId = ref(null)

// 添加成员对话框
const addMemberDialogVisible = ref(false)
const availableUsers = ref([])
const selectedUserId = ref(null)

// 打开添加成员对话框
const openAddMemberDialog = async () => {
  if (!selectedDepartment.value || !selectedDepartment.value.id) {
    ElMessage.warning('请先选择一个部门')
    return
  }
  
  try {
    // 获取所有用户列表
    const res = await api.system.userList()
    if (res.code === 0 && res.data) {
      // 过滤掉已经在当前部门的用户
      const currentDepartmentUserIds = departmentMembers.value.map(member => member.id)
      availableUsers.value = res.data.filter(user => 
        !currentDepartmentUserIds.includes(user.id) && 
        (!user.departmentId || user.departmentId !== selectedDepartment.value.id)
      )
      
      if (availableUsers.value.length === 0) {
        ElMessage.info('没有可添加的用户')
        return
      }
      
      selectedUserId.value = null
      addMemberDialogVisible.value = true
    } else {
      ElMessage.error(res.msg || '获取用户列表失败')
    }
  } catch (error) {
    console.error('获取用户列表错误:', error)
    ElMessage.error('获取用户列表失败，请重试')
  }
}

// 提交添加成员
const submitAddMember = async () => {
  if (!selectedUserId.value) {
    ElMessage.warning('请选择要添加的成员')
    return
  }
  
  try {
    // 调用更新用户部门的API
    const res = await api.system.updateDepartment(selectedUserId.value, selectedDepartment.value.id)
    if (res.code === 0) {
      ElMessage.success('成员添加成功')
      addMemberDialogVisible.value = false
      // 重新加载部门成员
      if (selectedDepartment.value && selectedDepartment.value.id) {
        loadDepartmentMembers(selectedDepartment.value.id)
      }
    } else {
      ElMessage.error(res.msg || '添加失败')
    }
  } catch (error) {
    console.error('添加成员错误:', error)
    ElMessage.error('添加失败，请重试')
  }
}

// 处理编辑成员
const handleEditMember = (member) => {
  // 打开编辑成员对话框
  editingMember.value = member
  newDepartmentId.value = null
  memberDialogVisible.value = true
}

// 编辑成员部门（仅系统管理员可见入口），添加二次确认
const submitMemberEdit = async () => {
  if (!userIsSystemAdmin.value) {
    ElMessage.warning('仅系统管理员可更改成员部门')
    return
  }
  if (!editingMember.value || !newDepartmentId.value) {
    ElMessage.warning('请选择新部门')
    return
  }

  const currentDeptName = selectedDepartment.value ? selectedDepartment.value.departmentName : '当前部门'
  const targetDept = flattenedDepartments.value.find(d => d.id === newDepartmentId.value)
  const targetDeptName = targetDept ? targetDept.departmentName.trim() : '新部门'

  try {
    await ElMessageBox.confirm(
      `此员工属于 ${currentDeptName}，确定要修改至 ${targetDeptName} 吗？`,
      '确认修改部门',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (_) {
    return
  }

  try {
    const res = await api.system.updateDepartment(editingMember.value.id, newDepartmentId.value)
    if (res.code === 0) {
      ElMessage.success('部门已更新')
      memberDialogVisible.value = false
      if (selectedDepartment.value && selectedDepartment.value.id) {
        loadDepartmentMembers(selectedDepartment.value.id)
      }
    } else {
      ElMessage.error(res.msg || '更新失败')
    }
  } catch (e) {
    console.error('更新成员部门失败:', e)
    ElMessage.error('更新失败，请稍后重试')
  }
}

// 处理移出部门
const handleDeleteMember = (member) => {
  ElMessageBox.confirm(
    `确定将成员 "${member.realName}" 移出当前部门吗？`,
    '移出确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    api.system.removeFromDepartment(member.id)
      .then(res => {
        if (res.code === 0) {
          ElMessage.success('已将成员移出部门')
          if (selectedDepartment.value && selectedDepartment.value.id) {
            loadDepartmentMembers(selectedDepartment.value.id)
          }
        } else {
          ElMessage.error(res.msg || '操作失败')
        }
      })
      .catch(error => {
        console.error('移出部门错误:', error)
        ElMessage.error('操作失败，请重试')
      })
  }).catch(() => {
    // 取消
  })
}

// 获取部门管理员
const getDepartmentAdmin = () => {
  if (!departmentMembers.value || departmentMembers.value.length === 0) {
    return '未指定'
  }
  const adminUser = departmentMembers.value.find(member => member.role === 'department_admin')
  return adminUser ? adminUser.realName : '未指定'
}
</script>

<template>
  <div class="department-container">
    <!-- 左侧部门树 -->
    <div class="department-tree-panel">
      <div class="panel-header">
        <h3>部门结构</h3>
        <el-button v-if="isAdmin" type="primary" size="small" :icon="Plus" @click="openAddDepartmentDialog()">添加部门</el-button>
      </div>
      
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索部门"
          :prefix-icon="Search"
          clearable
        />
      </div>
      
      <div class="tree-container">
        <el-tree
          :data="departmentTree"
          :props="{
            children: 'children',
            label: 'departmentName'
          }"
          :default-expanded-keys="defaultExpandedKeys"
          :filter-node-method="filterNode"
          node-key="id"
          highlight-current
          @node-click="selectDepartment"
        >
          <template #default="{ node, data }">
            <div class="custom-tree-node">
              <div class="node-content">
                <span class="label">{{ node.label }}</span>
                <span class="count">({{ data.employeeCount || 0 }}人)</span>
              </div>
              
              <div class="node-actions" v-if="isAdmin">
                <el-button type="danger" size="small" text :icon="Delete" @click.stop="handleDeleteDepartment(data)"></el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>
    </div>
    
    <!-- 右侧部门详情 -->
    <div class="department-details-panel" v-if="selectedDepartment">
      <div class="panel-header">
        <h3>{{ selectedDepartment.departmentName }}</h3>
        <div class="header-actions" v-if="isAdmin">
          <el-button type="primary" size="small" @click="openAddMemberDialog">添加成员</el-button>
          <el-button type="primary" size="small" plain :icon="Edit" @click="openEditDepartmentDialog(selectedDepartment)">编辑部门</el-button>
        </div>
      </div>
      
      <div class="department-info">
        <div class="info-item">
          <div class="label">部门名称</div>
          <div class="value">{{ selectedDepartment.departmentName }}</div>
        </div>
        <div class="info-item">
          <div class="label">部门管理员</div>
          <div class="value">{{ getDepartmentAdmin() }}</div>
        </div>
        <div class="info-item">
          <div class="label">成员数量</div>
          <div class="value">{{ selectedDepartment.employeeCount }}人</div>
        </div>
        <div class="info-item" v-if="selectedDepartment.description">
          <div class="label">部门描述</div>
          <div class="value">{{ selectedDepartment.description }}</div>
        </div>
      </div>
      
      <div class="members-section">
        <h4>部门成员</h4>
        <el-table :data="departmentMembers" style="width: 100%" v-loading="!departmentMembers.length">
          <el-table-column label="" width="60">
            <template #default="scope">
              <el-avatar :src="scope.row.avatar" :size="32"></el-avatar>
            </template>
          </el-table-column>
          <el-table-column prop="realName" label="姓名" width="120"></el-table-column>
          <el-table-column prop="position" label="职位"></el-table-column>
          <el-table-column prop="phone" label="电话"></el-table-column>
          <el-table-column prop="email" label="邮箱"></el-table-column>
          <el-table-column label="操作" width="120" v-if="isAdmin">
            <template #default="scope">
              <el-button v-if="userIsSystemAdmin" type="primary" size="small" text :icon="Edit" @click="handleEditMember(scope.row)">
              </el-button>
              <el-button type="danger" size="small" text :icon="Delete" @click="handleDeleteMember(scope.row)">
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <el-empty v-if="departmentMembers.length === 0" description="暂无成员" />
      </div>
    </div>
    
    <!-- 未选择部门时的提示 -->
    <div class="department-details-panel empty-panel" v-else>
      <el-empty description="请选择一个部门查看详情">
        <template #image>
          <el-icon style="font-size: 60px"><OfficeBuilding /></el-icon>
        </template>
      </el-empty>
    </div>
    
    <!-- 部门表单对话框 -->
    <el-dialog
      v-model="departmentDialogVisible"
      :title="departmentFormTitle"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="departmentFormRef"
        :model="departmentForm"
        :rules="departmentFormRules"
        label-width="80px"
      >
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="departmentForm.name" placeholder="请输入部门名称" />
        </el-form-item>
        
        <el-form-item label="上级部门">
          <el-select
            v-model="departmentForm.parentId"
            placeholder="请选择上级部门"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="dept in departmentTree"
              :key="dept.id"
              :label="dept.departmentName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="部门描述">
          <el-input
            v-model="departmentForm.description"
            type="textarea"
            placeholder="请输入部门描述"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="departmentDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitDepartmentForm">确定</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 成员编辑对话框 -->
    <el-dialog
      v-model="memberDialogVisible"
      title="更改成员部门"
      width="500px"
      destroy-on-close
    >
      <div v-if="editingMember">
        <p>成员: {{ editingMember.realName }}</p>
        <p>当前部门: {{ selectedDepartment ? selectedDepartment.departmentName : '未知' }}</p>
        
        <el-form label-width="80px">
          <el-form-item label="新部门">
            <el-select
              v-model="newDepartmentId"
              placeholder="请选择新部门"
              style="width: 100%"
            >
              <el-option
                v-for="dept in flattenedDepartments"
                :key="dept.id"
                :label="dept.departmentName"
                :value="dept.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="memberDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitMemberEdit">确定</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 添加成员对话框 -->
    <el-dialog
      v-model="addMemberDialogVisible"
      title="添加成员"
      width="500px"
      destroy-on-close
    >
      <div v-if="selectedDepartment">
        <p>当前部门: {{ selectedDepartment.departmentName }}</p>
        
        <el-form label-width="80px">
          <el-form-item label="选择成员">
            <el-select
              v-model="selectedUserId"
              placeholder="请选择要添加的成员"
              style="width: 100%"
            >
              <el-option
                v-for="user in availableUsers"
                :key="user.id"
                :label="user.realname || user.nickname || user.username"
                :value="user.id"
              >
                <div style="display: flex; align-items: center;">
                  <el-avatar :size="24" :src="user.userPic || '/avatar.jpg'" style="margin-right: 8px;"></el-avatar>
                  <span>{{ user.realname || user.nickname || user.username }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="addMemberDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAddMember">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.department-container {
  display: flex;
  height: 100%;
  
  .department-tree-panel {
    width: 300px;
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
    }
    
    .search-bar {
      padding: 15px;
      border-bottom: 1px solid #e6e6e6;
    }
    
    .tree-container {
      flex: 1;
      padding: 15px;
      overflow-y: auto;
      
      .custom-tree-node {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        padding-right: 8px;
        
        .node-content {
          display: flex;
          align-items: center;
          
          .label {
            font-weight: 500;
          }
          
          .count {
            margin-left: 5px;
            font-size: 12px;
            color: #909399;
          }
        }
        
        .node-actions {
          display: none;
        }
      }
      
      .el-tree-node:hover .node-actions {
        display: flex;
      }
    }
  }
  
  .department-details-panel {
    flex: 1;
    padding: 20px;
    display: flex;
    flex-direction: column;
    
    &.empty-panel {
      justify-content: center;
      align-items: center;
    }
    
    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      
      h3 {
        margin: 0;
        font-size: 18px;
      }
      
      .header-actions {
        display: flex;
        gap: 10px;
      }
    }
    
    .department-info {
      background-color: #f5f7fa;
      border-radius: 4px;
      padding: 15px;
      margin-bottom: 20px;
      
      .info-item {
        display: flex;
        margin-bottom: 10px;
        
        &:last-child {
          margin-bottom: 0;
        }
        
        .label {
          width: 100px;
          color: #909399;
        }
        
        .value {
          flex: 1;
          color: #303133;
          font-weight: 500;
        }
      }
    }
    
    .members-section {
      flex: 1;
      display: flex;
      flex-direction: column;
      
      h4 {
        margin: 0 0 15px 0;
        font-size: 16px;
      }
    }
  }
}
</style>