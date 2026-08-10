<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { Search, Plus, Edit, Delete, OfficeBuilding, ChatDotRound, Star } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus' 

import api from '@/api'
// 新增：聊天相关 - 跳转到聊天页并打开会话
import { useRouter } from 'vue-router'
// 移除：import { useChatStore } from '@/stores/chat.js'

const router = useRouter()
// 移除：const chatStore = useChatStore()

// 通讯录数据（从后端API获取）
const contacts = ref([])

// 当前用户信息
const currentUserInfo = ref({})

// 系统管理员视图下的筛选项
const allDepartments = ref([])
const selectedDeptId = ref('all') // 'all' | number
const adminOnly = ref(false) // 仅看部门管理员

// 搜索关键词
const searchKeyword = ref('')

// 当前选中的联系人
const selectedContact = ref(null)


// 常用联系人
const frequentContacts = ref([])

// 新增：我的通讯录分组与联系人
const myAbGroups = ref([])
const selectedAbGroupId = ref(null) // null 表示"全部"
const myAbContacts = ref([])

// 新增：分组联系人计数缓存，用于解决计数bug
const groupContactCounts = ref({})

// A方案：在分组选择器中提供"+ 创建新分组…"选项的标识值
const CREATE_GROUP_FLAG = '__CREATE_GROUP__'
// 记录选择器上一次有效的分组值，用于在点击"创建新分组"后回退
const lastSelectedGroupId = ref(null)

// Tab切换：部门通讯录 / 我的通讯录
const activeTab = ref('all') // 'all' | 'mine'

/**
 * 获取当前用户信息
 * 用于确定权限和决定调用哪个API
 */
const loadCurrentUserInfo = async () => {
  try {
    const res = await api.account.me()
    if (res.code === 0 && res.data) {
      currentUserInfo.value = res.data
      console.log('当前用户信息:', currentUserInfo.value)
    } else {
      ElMessage.error(res.message || '获取用户信息失败')
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  }
}

/**
 * 获取通讯录数据
 * 根据用户权限决定调用全部用户还是部门用户接口
 */
const loadContacts = async () => {
  try {
    let res
    
    // 系统管理员可以获取所有用户列表
    if (currentUserInfo.value.role === 'system_admin') {
      res = await api.system.userList()
    } else {
      // 非系统管理员只能查看自己部门的用户
      if (currentUserInfo.value.departmentId) {
        try {
          res = await api.org.departmentUsers(currentUserInfo.value.departmentId)
        } catch (_) {
          res = { code: 1, msg: '权限不足' }
        }
      } else {
        ElMessage.warning('您没有分配部门，无法查看通讯录')
        return
      }
    }
    
    if (res.code === 0 && res.data) {
      // 映射后端字段到前端字段
      contacts.value = res.data.map(user => ({
        id: user.id,
        name: user.realname || user.nickname || user.username, // 优先使用真实姓名
        department: user.department || user.departmentName || '未分配部门',
        departmentId: user.departmentId,
        position: user.position || '暂无职位',
        phone: user.phone || '暂无电话',
        email: user.email || '暂无邮箱',
        avatar: user.userPic || '/avatar.jpg', // 使用默认头像
        role: user.role || ''
      }))
      
      console.log('通讯录数据加载成功:', contacts.value)
    } else {
      ElMessage.error(res.message || '获取通讯录数据失败')
    }
  } catch (error) {
    console.error('获取通讯录数据失败:', error)
    ElMessage.error('获取通讯录数据失败')
  }
}

// 加载全部部门（仅系统管理员使用）
const loadAllDepartments = async () => {
  try {
    if (currentUserInfo.value.role !== 'system_admin') return
    const res = await api.org.allDepartments()
    if (res && res.code === 0 && Array.isArray(res.data)) {
      allDepartments.value = res.data.map(d => ({ id: d.id, name: d.name || d.departmentName || '未命名部门' }))
    }
  } catch (e) { console.warn('加载部门列表失败', e) }
}

// 过滤后的联系人列表
const filteredContacts = computed(() => {
  let result = contacts.value

  // 系统管理员的部门与角色筛选
  if (currentUserInfo.value.role === 'system_admin') {
    if (selectedDeptId.value !== 'all') {
      const d = allDepartments.value.find(x => x.id === selectedDeptId.value)
      const deptId = d?.id
      // 若服务端未返回 departmentId，这里退化为按部门名称匹配（已保留旧逻辑）
      if (typeof deptId === 'number') {
        result = result.filter(c => Number(c.departmentId) === Number(deptId))
      } else if (d?.name) {
        result = result.filter(c => c.department === d.name)
      }
    }
    if (adminOnly.value) {
      // 兼容多种可能返回：role === 'department_admin' 或者 position/roleName 字段含“管理员”
      result = result.filter(c => String(c.role || '').toLowerCase() === 'department_admin' || String(c.position || '').includes('管理员') || String(c.roleName || '').includes('管理员'))
    }
  }
 
  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(contact => 
      contact.name.toLowerCase().includes(keyword) ||
      contact.position.toLowerCase().includes(keyword) ||
      contact.phone.includes(keyword) ||
      contact.email.toLowerCase().includes(keyword)
    )
  }
   
  return result
})

// 新增：我的通讯录 过滤后的联系人列表（按分组与关键词）
const filteredMyAbContacts = computed(() => {
  // 基础数组兜底，避免 undefined.length 报错
  let result = Array.isArray(myAbContacts.value) ? myAbContacts.value : []

  // 按分组筛选（null 表示全部）
  if (selectedAbGroupId.value !== null && selectedAbGroupId.value !== undefined) {
    result = result.filter(c => c.groupId === selectedAbGroupId.value)
  }

  // 按搜索关键词筛选（与 filteredContacts 保持一致的字段）
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(c => 
      (c.name || '').toLowerCase().includes(keyword) ||
      (c.position || '').toLowerCase().includes(keyword) ||
      String(c.phone || '').includes(keyword) ||
      (c.email || '').toLowerCase().includes(keyword)
    )
  }

  return result
})

// 选择联系人
const selectContact = (contact) => {
  selectedContact.value = contact
}

/**
 * 获取常用联系人列表（从后端）
 * 对应接口：GET /org/contact/frequent?limit={number}
 */
const loadFrequentContacts = async () => {
  try {
    const res = await api.org.frequentList(10)
    if (res.code === 0 && Array.isArray(res.data)) {
      // 兼容后端可能返回的不同字段结构
      frequentContacts.value = res.data.map(item => {
        const user = item?.contact || item?.contactUser || item?.targetUser || item?.user || item
        const id = user?.id ?? item?.contactId ?? item?.id
        const name = user?.realname || user?.nickname || user?.username || user?.name || '未知用户'
        const department = user?.department || user?.departmentName || '未分配部门'
        const avatar = user?.userPic || user?.avatar || '/avatar.jpg'
        return { id, name, department, avatar }
      })
    } else {
      ElMessage.error(res.message || '获取常用联系人失败')
    }
  } catch (error) {
    console.error('获取常用联系人失败:', error)
    ElMessage.error('获取常用联系人失败')
  }
}

/**
 * 从常用联系人中移除
 * 对应接口：DELETE /org/contact/frequent/{contactId}
 * @param {number} contactId - 联系人ID
 */
const removeFromFrequent = async (contactId) => {
  try {
    const res = await api.org.deleteFrequent(contactId)
    if (res.code === 0) {
      ElMessage.success('已从常用联系人中移除')
      await loadFrequentContacts()
    } else {
      ElMessage.error(res.message || '移除常用联系人失败')
    }
  } catch (error) {
    console.error('移除常用联系人失败:', error)
    ElMessage.error('移除常用联系人失败')
  }
}

/**
 * 加载"我的通讯录"分组列表
 */
const loadMyAddressBookGroups = async () => {
  try {
    const res = await api.org.groups()
    if (res.code === 0 && Array.isArray(res.data)) {
      myAbGroups.value = res.data
      console.log('我的通讯录分组加载成功:', myAbGroups.value)
      // 更新分组联系人计数
      await updateGroupContactCounts()
    } else {
      ElMessage.error(res.message || '获取分组列表失败')
    }
  } catch (error) {
    console.error('获取分组列表失败:', error)
    ElMessage.error('获取分组列表失败')
  }
}

/**
 * 加载"我的通讯录"联系人列表（带分组过滤）
 * - 为保证分组计数正确，这里始终加载“全部联系人”，由前端过滤
 */
const loadMyAddressBookContacts = async (groupId = null) => {
  try {
    const res = await api.org.listContacts()
    if (res.code === 0 && Array.isArray(res.data)) {
      myAbContacts.value = res.data.map(item => ({
        abId: item.abId || item.id,
        id: item.id,
        contactId: item.contactId,
        contactType: item.contactType,
        externalId: item.externalId,
        name: item.contactName || item.realname || item.nickname || item.username || item.name || '未知用户',
        department: item.departmentName || item.department || '未分配部门',
        position: item.position || '暂无职位',
        phone: item.phone || item.externalPhone || '暂无电话',
        email: item.email || item.externalEmail || '暂无邮箱',
        avatar: item.contactAvatar || item.userPic || item.avatar || '/avatar.jpg',
        alias: item.alias || '',
        remark: item.remark || '',
        groupId: Number(item.groupId),
        groupName: item.groupName || '默认分组',
        tags: item.alias ? `备注: ${item.alias}` : null
      }))
      console.log('我的通讯录联系人加载成功:', myAbContacts.value)
    } else {
      ElMessage.error(res.message || '获取我的通讯录联系人失败')
    }
  } catch (error) {
    console.error('获取我的通讯录联系人失败:', error)
    ElMessage.error('获取我的通讯录联系人失败')
  }
}

// 添加联系人弹窗 - 状态
const addDialogVisible = ref(false)
const addDialogLoading = ref(false)
const addForm = ref({
  // 内部联系人模式
  addMode: 'internal', // 'internal' | 'external'
  contactId: null,
  userKeyword: '',
  // 公共关系字段
  groupId: null,
  alias: '',
  remark: '',
  // 外部联系人字段
  externalName: '',
  externalPhone: '',
  externalEmail: '',
  externalOrg: '',
  externalPosition: ''
})

// 编辑联系人弹窗状态
const editDialogVisible = ref(false)
const editDialogLoading = ref(false)
const editForm = ref({
  id: null,
  groupId: null,
  alias: '',
  remark: ''
})

// 编辑弹窗中用于"+ 创建新分组…"的回退值
const lastSelectedEditGroupId = ref(null)

/**
 * 打开编辑联系人弹窗
 */
const openEditDialog = async (contact) => {
  if (!contact || (!contact.contactId && !contact.abId && !contact.id)) return
  editForm.value = {
    id: contact.abId || contact.id,
    abId: contact.abId || contact.id,
    contactId: contact.contactId || null,
    groupId: contact.groupId,
    alias: contact.alias || '',
    remark: contact.remark || ''
  }
  lastSelectedEditGroupId.value = contact.groupId
  editDialogVisible.value = true
  try {
    await loadMyAddressBookGroups()
  } catch (e) {
    console.error('打开编辑联系人弹窗失败', e)
  }
}

/**
 * 编辑弹窗分组选择变化处理
 */
const handleEditGroupSelectChange = async (value) => {
  if (value === CREATE_GROUP_FLAG) {
    editForm.value.groupId = lastSelectedEditGroupId.value
    try {
      const { value: groupName } = await ElMessageBox.prompt('请输入新分组名称', '创建分组', {
        confirmButtonText: '创建',
        cancelButtonText: '取消',
        inputPattern: /^.{1,20}$/,
        inputErrorMessage: '分组名称长度应为1-20个字符'
      })
      const res = await api.org.createGroup(groupName)
      if (res.code === 0) {
        ElMessage.success('创建分组成功')
        await loadMyAddressBookGroups()
        editForm.value.groupId = res.data?.id || res.data
        lastSelectedEditGroupId.value = editForm.value.groupId
      } else {
        ElMessage.error(res.message || '创建分组失败')
      }
    } catch (err) {
      // 用户取消创建
    }
  } else {
    lastSelectedEditGroupId.value = value
  }
}

/**
 * 确认编辑联系人
 */
const confirmEditContact = async () => {
  if (!editForm.value.contactId && !editForm.value.abId && !editForm.value.id) {
    ElMessage.error('缺少必要参数')
    return
  }
  
  editDialogLoading.value = true
  try {
    let res
    const payload = {
      groupId: editForm.value.groupId,
      alias: editForm.value.alias || null,
      remark: editForm.value.remark || null
    }
    if (editForm.value.contactId) {
      res = await api.org.editContact(editForm.value.contactId, payload)
    } else {
      const abId = editForm.value.abId || editForm.value.id
      res = await api.org.editContactByAbId(abId, payload)
    }
    
    if (res.code === 0) {
      ElMessage.success('编辑成功')
      editDialogVisible.value = false
      await loadMyAddressBookContacts(null)
      await updateGroupContactCounts()
    } else {
      ElMessage.error(res.message || '编辑失败')
    }
  } catch (e) {
    console.error('编辑联系人失败', e)
    ElMessage.error('编辑联系人失败')
  } finally {
    editDialogLoading.value = false
  }
}

/**
 * 删除"我的通讯录"中的联系人
 */
const handleDeleteContact = async (contact) => {
  if (!contact || (!contact.contactId && !contact.abId && !contact.id)) return
  try {
    await ElMessageBox.confirm(
      `确认从我的通讯录中移除 "${contact.name}" 吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    let res
    if (contact.contactId) {
      res = await api.org.deleteContact(contact.contactId)
    } else {
      const abId = contact.abId || contact.id
      res = await api.org.deleteContactByAbId(abId)
    }
    if (res.code === 0) {
      ElMessage.success('已删除')
      await loadMyAddressBookContacts(null)
      await updateGroupContactCounts()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (err) {
    if (err !== 'cancel') {
      console.error('删除联系人异常：', err)
    }
  }
}

// 用户搜索相关
const candidateUsers = ref([])
const searchLoading = ref(false)

/** 重置添加联系人表单 */
const resetAddForm = () => {
  addForm.value = {
    addMode: 'internal',
    contactId: null,
    userKeyword: '',
    groupId: null,
    alias: '',
    remark: '',
    externalName: '',
    externalPhone: '',
    externalEmail: '',
    externalOrg: '',
    externalPosition: ''
  }
  candidateUsers.value = []
  searchLoading.value = false
}

/** 触发用户搜索 */
const triggerSearchUsers = async () => {
  if (addForm.value.addMode !== 'internal') {
    candidateUsers.value = []
    return
  }
  const keyword = addForm.value.userKeyword?.trim()
  if (!keyword) {
    candidateUsers.value = []
    return
  }
  searchLoading.value = true
  try {
    let res
    try {
      res = await api.org.searchUsers(keyword)
    } catch (_) {
      res = { code: 1, data: [] }
    }
    if (res.code === 0 && Array.isArray(res.data)) {
      candidateUsers.value = res.data
    } else {
      candidateUsers.value = []
    }
  } catch (e) {
    console.error('搜索用户失败', e)
    candidateUsers.value = []
  } finally {
    searchLoading.value = false
  }
}

// 监听搜索关键词变化，防抖搜索
watch(() => addForm.value.userKeyword, () => {
  clearTimeout(triggerSearchUsers._timer)
  triggerSearchUsers._timer = setTimeout(triggerSearchUsers, 300)
}, { immediate: false })

/** 打开添加联系人弹窗 */
const openAddDialog = async () => {
  addDialogVisible.value = true
  resetAddForm()
  try {
    await loadMyAddressBookGroups()
  } catch (e) {
    console.error('打开添加联系人弹窗失败', e)
  }
}

/** 分组选择变化 */
const handleGroupSelectChange = async (value) => {
  if (value === CREATE_GROUP_FLAG) {
    addForm.value.groupId = lastSelectedGroupId.value
    try {
      const { value: groupName } = await ElMessageBox.prompt('请输入新分组名称', '创建分组', {
        confirmButtonText: '创建',
        cancelButtonText: '取消',
        inputPattern: /^.{1,20}$/,
        inputErrorMessage: '分组名称长度应为1-20个字符'
      })
      const res = await api.org.createGroup(groupName)
      if (res.code === 0) {
        ElMessage.success('创建分组成功')
        await loadMyAddressBookGroups()
        addForm.value.groupId = res.data?.id || res.data
        lastSelectedGroupId.value = addForm.value.groupId
      } else {
        ElMessage.error(res.message || '创建分组失败')
      }
    } catch (err) {}
  } else {
    lastSelectedGroupId.value = value
  }
}

// 新增：添加联系人确认按钮禁用逻辑
const addConfirmDisabled = computed(() => {
  if (addForm.value.addMode === 'internal') {
    return !addForm.value.contactId
  }
  return !(addForm.value.externalPhone && addForm.value.externalPhone.trim())
})

/** 确认添加联系人到我的通讯录 */
const confirmAddContact = async () => {
  addDialogLoading.value = true
  try {
    if (addForm.value.addMode === 'internal') {
      if (!addForm.value.contactId) {
        ElMessage.warning('请选择一个联系人')
        return
      }
      const res = await api.org.addContact(
        addForm.value.contactId,
        addForm.value.groupId ?? null,
        addForm.value.alias || '',
        addForm.value.remark || ''
      )
      if (res.code === 0) {
        ElMessage.success('添加成功')
      } else {
        ElMessage.error(res.message || '添加失败')
        return
      }
    } else {
      const phone = (addForm.value.externalPhone || '').trim()
      if (!phone) {
        ElMessage.warning('请填写联系电话')
        return
      }
      const payload = {
        name: (addForm.value.externalName || '').trim() || undefined,
        phone: phone,
        email: (addForm.value.externalEmail || '').trim() || undefined,
        company: (addForm.value.externalOrg || '').trim() || undefined,
        position: (addForm.value.externalPosition || '').trim() || undefined,
        groupId: addForm.value.groupId ?? null,
        alias: addForm.value.alias || '',
        remark: addForm.value.remark || ''
      }
      const res = await api.org.addExternal(payload)
      if (res.code === 0) {
        ElMessage.success('添加成功')
      } else {
        ElMessage.error(res.message || '添加失败')
        return
      }
    }

    addDialogVisible.value = false
    await loadMyAddressBookContacts(null)
    resetAddForm()
    await updateGroupContactCounts()
  } catch (e) {
    console.error('添加联系人失败', e)
    ElMessage.error('添加失败')
  } finally {
    addDialogLoading.value = false
  }
}

// 组件挂载时加载数据
onMounted(async () => {
  await loadCurrentUserInfo()
  await loadFrequentContacts()
  await loadMyAddressBookGroups()
  await loadMyAddressBookContacts(null)
  await loadContacts()
  await loadAllDepartments()
  await updateGroupContactCounts()
})

/** 删除分组 */
const handleDeleteGroup = async (group) => {
  if (!group || !group.id) return
  if (group.isDefault === true || group.isDefault === 1) {
    ElMessage.warning('默认分组不允许删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除分组 "${group.groupName}" 吗？\n该分组下的联系人将自动迁移到"默认分组"。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    const res = await api.org.deleteGroup(group.id)
    if (res.code === 0) {
      ElMessage.success('分组删除成功，联系人已迁移到默认分组')
      if (selectedAbGroupId.value === group.id) {
        selectedAbGroupId.value = null
      }
      await loadMyAddressBookGroups()
      await loadMyAddressBookContacts(null)
    } else {
      ElMessage.error(res.message || '删除分组失败')
    }
  } catch (err) {
    if (err !== 'cancel') {
      console.error('删除分组异常:', err)
    }
  }
}

/** 重命名分组 */
const handleRenameGroup = async (group) => {
  if (!group || !group.id) return
  if (group.isDefault === true || group.isDefault === 1) {
    ElMessage.warning('默认分组不允许重命名')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入新的分组名称（1-20个字符）', '重命名分组', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入新的分组名称',
      inputPattern: /^.{1,20}$/,
      inputErrorMessage: '分组名称长度应为1-20个字符',
      inputValue: group.groupName || ''
    })
    const newName = (value || '').trim()
    if (!newName) { ElMessage.error('分组名称不能为空'); return }
    if (newName.length > 20) { ElMessage.error('分组名称长度不能超过20个字符'); return }
    if (newName === group.groupName) { ElMessage.success('分组名称未变化'); return }
    const res = await api.org.renameGroup(group.id, newName)
    if (res.code === 0) {
      ElMessage.success('分组重命名成功')
      await loadMyAddressBookGroups()
      await updateGroupContactCounts()
    } else {
      ElMessage.error(res.message || '分组重命名失败')
    }
  } catch (err) {
    if (err !== 'cancel') {
      console.error('分组重命名异常:', err)
    }
  }
}

/** 添加到常用联系人 */
const addToFrequent = async (contact) => {
  if (!contact || !contact.id) {
    ElMessage.warning('联系人信息不完整')
    return
  }
  const existingContact = frequentContacts.value.find(fc => fc.id === contact.id)
  if (existingContact) { ElMessage.warning('该联系人已在常用联系人中'); return }
  try {
    const res = await api.org.favorite(contact.id, true)
    if (res.code === 0) {
      ElMessage.success('已添加到常用联系人')
      await loadFrequentContacts()
    } else {
      ElMessage.error(res.message || '添加到常用联系人失败')
    }
  } catch (error) {
    console.error('添加到常用联系人失败:', error)
    ElMessage.error('添加到常用联系人失败')
  }
}

// 新增：更新分组联系人计数方法
/**
 * 更新分组联系人计数
 * - 统计总数与各分组联系人数量
 * - 依赖 myAbContacts 和 myAbGroups
 */
const updateGroupContactCounts = () => {
  const counts = {}
  const list = Array.isArray(myAbContacts.value) ? myAbContacts.value : []
  counts['__TOTAL__'] = list.length
  if (Array.isArray(myAbGroups.value)) {
    myAbGroups.value.forEach(g => {
      const gid = g?.id
      counts[gid] = list.filter(c => c.groupId === gid).length
    })
  }
  groupContactCounts.value = counts
}

// 新增：分组选择处理
const handleSelectAbGroup = (groupId) => { selectedAbGroupId.value = groupId }

/** 从通讯录点击联系人头像时启动私聊会话 */
const startChatWithContact = (contact) => {
  if (!contact) { ElMessage.error('未找到联系人信息，无法发起聊天'); return }
  let targetUserId = null
  if (typeof contact.contactId === 'number' && !Number.isNaN(contact.contactId)) {
    targetUserId = contact.contactId
  } else if ('contactType' in contact) {
    ElMessage.info('外部联系人暂不支持聊天')
    return
  } else if (typeof contact.id === 'number' && !Number.isNaN(contact.id)) {
    targetUserId = contact.id
  }
  if (!targetUserId) { ElMessage.error('未找到有效的内部联系人的ID，无法发起聊天'); return }
  let displayName = contact.name
  if (!displayName || displayName.trim() === '') {
    displayName = contact.realname || contact.nickname || contact.username || `用户${targetUserId}`
  }
  const avatar = contact.avatar || contact.contactAvatar || contact.userPic || '/avatar.jpg'
  try { sessionStorage.setItem('activeChat', JSON.stringify({ userId: Number(targetUserId), name: displayName, avatar })) } catch (_) {}
  router.push({ name: 'Chat', query: { userId: String(targetUserId) } })
}
</script>

<template>
  <div class="contacts-container">
    <!-- 头部搜索区域 -->
    <div class="search-header">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索联系人姓名、职位、电话..."
        prefix-icon="Search"
        size="large"
        clearable
        class="search-input"
      />
    </div>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab" class="contacts-tabs">
      <el-tab-pane label="部门通讯录" name="all">
        <div class="contacts-main">
          <!-- 左侧边栏 -->
          <div class="sidebar">
            <!-- 常用联系人 -->
            <div class="frequent-section">
              <h3><el-icon><Star /></el-icon> 常用联系人</h3>
              <div class="frequent-list">
                <div 
                  v-for="contact in frequentContacts" 
                  :key="contact.id" 
                  class="frequent-item"
                  @click="selectContact(contact)"
                >
                  <el-avatar :src="contact.avatar" :size="32">
                    {{ contact.name.charAt(0) }}
                  </el-avatar>
                  <span class="name">{{ contact.name }}</span>
                  <el-button 
                    type="text" 
                    size="small" 
                    @click.stop="removeFromFrequent(contact.id)"
                    class="remove-btn"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>

          </div>

          <!-- 右侧内容区域 -->
          <div class="content">
            <!-- 系统管理员的筛选条 -->
            <div v-if="currentUserInfo.role==='system_admin'" class="filter-bar">
              <el-select v-model="selectedDeptId" size="small" style="width: 220px; margin-right: 10px;" :clearable="false">
                <el-option :value="'all'" label="全部部门" />
                <el-option v-for="d in allDepartments" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
              <el-checkbox v-model="adminOnly" label="只看部门管理员" />
            </div>
            <!-- 联系人列表 -->
            <div class="contacts-list">
              <div 
                v-for="contact in filteredContacts" 
                :key="contact.id" 
                class="contact-card"
                :class="{ active: selectedContact?.id === contact.id }"
                @click="selectContact(contact)"
              >
                <el-avatar :src="contact.avatar" :size="48">
                  {{ contact.name.charAt(0) }}
                </el-avatar>
                <div class="contact-info">
                  <h4>{{ contact.name }}</h4>
                  <p class="department">{{ contact.department }}</p>
                  <p class="position">{{ contact.position }}</p>
                  <div class="contact-details">
                    <span class="phone">📞 {{ contact.phone }}</span>
                    <span class="email">✉️ {{ contact.email }}</span>
                  </div>
                </div>
                <div class="contact-actions">
                  <el-button 
                    type="primary" 
                    size="small" 
                    @click.stop="addToFrequent(contact)"
                  >
                    <el-icon><Star /></el-icon>
                    常用
                  </el-button>
                  <el-button 
                    type="success" 
                    size="small" 
                    @click.stop="startChatWithContact(contact)"
                  >
                    <el-icon><ChatDotRound /></el-icon>
                    聊天
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的通讯录" name="mine">
        <div class="my-addressbook-main">
          <!-- 左侧分组列表 -->
          <div class="group-sidebar">
            <h3>分组管理</h3>
            <div class="group-list">
              <div 
                class="group-item"
                :class="{ active: selectedAbGroupId === null }"
                @click="handleSelectAbGroup(null)"
              >
                <span>全部联系人</span>
                <span class="count">{{ groupContactCounts['__TOTAL__'] || 0 }}</span>
              </div>
              <div 
                v-for="group in myAbGroups" 
                :key="group.id" 
                class="group-item"
                :class="{ active: selectedAbGroupId === group.id }"
                @click="handleSelectAbGroup(group.id)"
              >
                <span>{{ group.groupName }}</span>
                <div class="group-right">
                  <span class="count">{{ groupContactCounts[group.id] || 0 }}</span>
                  <el-button
                    v-if="!(group.isDefault === true || group.isDefault === 1)"
                    class="rename-btn"
                    type="text"
                    size="small"
                    @click.stop="handleRenameGroup(group)"
                    :title="'重命名分组'"
                  >
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button
                    v-if="!(group.isDefault === true || group.isDefault === 1)"
                    class="delete-btn"
                    type="text"
                    size="small"
                    @click.stop="handleDeleteGroup(group)"
                    :title="'删除分组'"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧联系人列表 -->
          <div class="addressbook-content">
            <div class="addressbook-header">
              <h3>
                {{ selectedAbGroupId === null ? '全部联系人' : myAbGroups.find(g => g.id === selectedAbGroupId)?.groupName || '未知分组' }}
                ({{ filteredMyAbContacts.length }})
              </h3>
              <el-button type="primary" size="small" @click="openAddDialog">
                <el-icon><Plus /></el-icon>
                添加联系人
              </el-button>
            </div>
            
            <div class="addressbook-list">
              <div 
                v-for="contact in filteredMyAbContacts" 
                :key="contact.id" 
                class="addressbook-card"
                @click="selectContact(contact)"
              >
                <el-avatar :src="contact.avatar" :size="48">
                  {{ contact.name.charAt(0) }}
                </el-avatar>
                <div class="contact-info">
                  <h4>{{ contact.name }}</h4>
                  <p class="department">{{ contact.department }}</p>
                  <p class="position">{{ contact.position }}</p>
                  <div class="contact-details">
                    <span class="phone">📞 {{ contact.phone }}</span>
                    <span class="email">✉️ {{ contact.email }}</span>
                  </div>
                  <div v-if="contact.tags" class="tags">
                    <el-tag size="small" type="info">{{ contact.tags }}</el-tag>
                  </div>
                </div>
                <div class="contact-actions">
                  <el-button type="text" size="small" @click.stop="openEditDialog(contact)">
                    <el-icon><Edit /></el-icon>
                    编辑
                  </el-button>
                  <el-button type="text" size="small" @click.stop="handleDeleteContact(contact)">
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-button>
                  <el-button type="text" size="small" @click.stop="startChatWithContact(contact)">
                    <el-icon><ChatDotRound /></el-icon>
                    聊天
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加联系人弹窗 -->
    <el-dialog
      v-model="addDialogVisible"
      title="添加联系人到我的通讯录"
      width="720px"
      :close-on-click-modal="false"
    >
      <div v-loading="addDialogLoading">
        <div class="add-dialog" :class="{ 'single-column': addForm.addMode === 'external' }">
          <div class="left" v-if="addForm.addMode === 'internal'">
            <el-input
              v-model="addForm.userKeyword"
              placeholder="搜索姓名/邮箱/电话"
              clearable
            />
            <div class="candidate-list" v-loading="searchLoading">
              <div
                v-for="u in candidateUsers"
                :key="u.id"
                class="candidate-item"
                :class="{ active: addForm.contactId === u.id }"
                @click="addForm.contactId = u.id"
              >
                <el-avatar :src="u.userPic || '/avatar.jpg'" :size="32">
                  {{ (u.realname || u.nickname || u.username || '用').charAt(0) }}
                </el-avatar>
                <div class="info">
                  <div class="name">{{ u.realname || u.nickname || u.username }}</div>
                  <div class="meta">{{ u.department || u.departmentName || '未分配部门' }}</div>
                  <div class="meta">{{ u.position || '暂无职位' }}</div>
                </div>
              </div>
              <div v-if="!candidateUsers.length && addForm.userKeyword && !searchLoading" class="empty-tip">未找到匹配用户</div>
              <div v-if="!addForm.userKeyword && !searchLoading" class="empty-tip">请输入关键词开始搜索</div>
            </div>
          </div>
          <div class="right">
            <el-form :model="addForm" label-width="90px">
              <el-form-item label="添加方式">
                <el-radio-group v-model="addForm.addMode">
                  <el-radio-button label="internal">企业内搜索</el-radio-button>
                  <el-radio-button label="external">新增外部联系人</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="addForm.addMode === 'internal'">
                <el-form-item label="选择分组">
                  <el-select v-model="addForm.groupId" placeholder="不选择则进入默认分组" clearable @change="handleGroupSelectChange">
                    <el-option
                      v-for="g in myAbGroups"
                      :key="g.id"
                      :label="g.groupName"
                      :value="g.id"
                    />
                    <el-option :label="'+ 创建新分组…'" :value="CREATE_GROUP_FLAG" />
                  </el-select>
                </el-form-item>
                <el-form-item label="备注名">
                  <el-input v-model="addForm.alias" maxlength="50" show-word-limit />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input type="textarea" v-model="addForm.remark" maxlength="200" show-word-limit />
                </el-form-item>
              </template>
              <template v-else>
                <!-- 外部联系人模式下隐藏左侧搜索区，右侧表单自动占满宽度 -->
                <el-form-item label="姓名">
                  <el-input v-model="addForm.externalName" maxlength="50" placeholder="请输入姓名" />
                </el-form-item>
                <el-form-item label="电话">
                  <el-input v-model="addForm.externalPhone" maxlength="20" placeholder="请输入电话号码" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="addForm.externalEmail" maxlength="100" placeholder="可选，便于后续联系" />
                </el-form-item>
                <el-form-item label="公司/部门">
                  <el-input v-model="addForm.externalOrg" maxlength="100" placeholder="可选" />
                </el-form-item>
                <el-form-item label="职位">
                  <el-input v-model="addForm.externalPosition" maxlength="50" placeholder="可选" />
                </el-form-item>
                <el-form-item label="选择分组">
                  <el-select v-model="addForm.groupId" placeholder="不选择则进入默认分组" clearable @change="handleGroupSelectChange">
                    <el-option
                      v-for="g in myAbGroups"
                      :key="g.id"
                      :label="g.groupName"
                      :value="g.id"
                    />
                    <el-option :label="'+ 创建新分组…'" :value="CREATE_GROUP_FLAG" />
                  </el-select>
                </el-form-item>
                <el-form-item label="备注名">
                  <el-input v-model="addForm.alias" maxlength="50" show-word-limit />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input type="textarea" v-model="addForm.remark" maxlength="200" show-word-limit />
                </el-form-item>
              </template>
            </el-form>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="addDialogLoading" :disabled="addConfirmDisabled" @click="confirmAddContact">确 定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑联系人弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑联系人"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-loading="editDialogLoading">
        <el-form :model="editForm" label-width="90px">
          <el-form-item label="调整分组">
            <el-select v-model="editForm.groupId" placeholder="不选择则进入默认分组" clearable @change="handleEditGroupSelectChange">
              <el-option
                v-for="g in myAbGroups"
                :key="g.id"
                :label="g.groupName"
                :value="g.id"
              />
              <el-option :label="'+ 创建新分组…'" :value="CREATE_GROUP_FLAG" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注名">
            <el-input v-model="editForm.alias" maxlength="50" show-word-limit />
          </el-form-item>
          <el-form-item label="备注">
            <el-input type="textarea" v-model="editForm.remark" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="editDialogLoading" @click="confirmEditContact">保 存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.contacts-container {
  padding: 20px;
  height: 100vh;
  overflow: hidden;
}

.search-header {
  margin-bottom: 20px;
}

.search-input {
  max-width: 400px;
}

.contacts-tabs {
  height: calc(100vh - 120px);
}

/* 部门通讯录样式 */
.contacts-main {
  display: flex;
  height: 100%;
  gap: 20px;
}

.sidebar {
  width: 300px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  overflow-y: auto;
}

.frequent-section {
  margin-bottom: 30px;
}

.frequent-section h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 15px;
  color: #303133;
  font-size: 16px;
}

.frequent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.frequent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.frequent-item:hover {
  background-color: #e6f7ff;
}

.frequent-item .name {
  flex: 1;
  font-size: 14px;
}

.remove-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.frequent-item:hover .remove-btn {
  opacity: 1;
}

.content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0; /* 允许子元素使用剩余高度 */
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.contacts-list {
  flex: 1;
  min-height: 0; /* 与父flex配合，避免被压缩导致最后一行被遮挡 */
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
  padding: 10px 10px 16px; /* 底部增加些许内边距，避免贴边裁剪阴影/文本 */
}

.contact-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.2s;
}

.contact-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.contact-card.active {
  border: 2px solid #409eff;
}

.contact-info {
  flex: 1;
}

.contact-info h4 {
  margin: 0 0 5px;
  color: #303133;
  font-size: 16px;
}

.contact-info p {
  margin: 3px 0;
  color: #606266;
  font-size: 14px;
}

.contact-details {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.contact-details span {
  font-size: 12px;
  color: #909399;
}

.contact-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 我的通讯录样式 */
.my-addressbook-main {
  display: flex;
  height: 100%;
  gap: 20px;
}

.group-sidebar {
  width: 250px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
}

.group-sidebar h3 {
  margin-bottom: 15px;
  color: #303133;
  font-size: 16px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.group-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 15px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.group-item:hover {
  background-color: #e6f7ff;
}

.group-item.active {
  background-color: #409eff;
  color: white;
}

.group-item .rename-btn {
  opacity: 0;
  transition: opacity 0.15s ease;
  color: #409eff;
}

.group-item:hover .rename-btn {
  opacity: 1;
}

.group-item.active .rename-btn {
  color: #fff;
}

.group-item.active .rename-btn:hover {
  color: #e6f2ff;
}

.group-item.active .count {
  background: rgba(255, 255, 255, 0.3);
}

.addressbook-content {
  flex: 1;
  overflow: hidden;
}

.addressbook-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.addressbook-header h3 {
  margin: 0;
  color: #303133;
}

.addressbook-list {
  height: calc(100% - 60px);
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
  padding: 10px;
}

.addressbook-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.2s;
}

.addressbook-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.tags {
  margin-top: 8px;
}

.add-dialog {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.add-dialog.single-column {
  grid-template-columns: 1fr;
}
.add-dialog .candidate-list {
  margin-top: 10px;
  max-height: 380px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.candidate-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>