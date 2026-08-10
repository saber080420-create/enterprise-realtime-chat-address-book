<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { connect, onMessage, offMessage } from '@/utils/websocket'
// 统一改用门面 api.chat
import api from '@/api'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { HISTORY_ENABLED } from '@/config/featureFlags'
import { ChatDotRound, Document, Plus } from '@element-plus/icons-vue'
import { uploadChatImageService, uploadChatFileService } from '@/api/upload'
// 统一使用门面 api.org

/**
 * 文本按指定长度换行工具函数
 * 函数级注释：将文本按指定字符数断行，确保显示固定宽度，防止过长单行和自动换行。
 * @param {string} text - 原始文本内容
 * @param {number} maxLength - 每行最大字符数
 * @returns {string} 换行后的文本
 */
const wrapTextByLength = (text, maxLength = 16) => {
  if (text == null) return ''
  const s = String(text)
  if (!s) return ''
  // 按原有换行分段，逐段再做定长切分
  const paragraphs = s.split(/\r?\n/)
  const lines = []
  for (const p of paragraphs) {
    if (p.length <= maxLength) {
      // 短段原样保留，不额外插入换行
      lines.push(p)
      continue
    }
    // 逐 maxLength 切分，避免把 emoji/代理对切断：使用 Array.from 按 Unicode 码点迭代
    const chars = Array.from(p)
    for (let i = 0; i < chars.length; i += maxLength) {
      lines.push(chars.slice(i, i + maxLength).join(''))
    }
  }
  return lines.join('\n')
}

// 路由与用户
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 目标会话信息（单聊）
const targetUserId = ref(null)
const targetName = ref('')
const targetAvatar = ref('/avatar.jpg')
// 群聊会话状态
const isGroupActive = ref(false)
const targetGroupId = ref(null)
const targetGroupName = ref('')
const targetGroupAvatar = ref('/avatar.jpg')

// 新增：创建群聊弹窗开关（第一步仅放置按钮，弹窗稍后实现）
/**
 * 打开“创建群聊”弹窗
 * 函数级注释：仅切换弹窗显隐状态；表单与提交流程在下一步实现。
 */
const createGroupVisible = ref(false)
const openCreateGroupDialog = () => {
  createGroupVisible.value = true
}
// 消息列表与输入框
const messages = ref([])
const inputText = ref('')
const listRef = ref(null)
const recentChats = ref([])
const loadingRecent = ref(false)
const activeListTab = ref('recent')
const myGroups = ref([])
const loadingGroups = ref(false)
// 历史分页与游标状态（缺失补充）：
// hasMore：是否还有更多历史消息可加载；
// loadingMore：是否正在加载更多；
// cursorTime/cursorId：用于游标分页的时间与ID双锚点，保证稳定排序与去重。
const hasMore = ref(false)
const loadingMore = ref(false)
// 群聊历史偏移量（后端当前仅支持 limit+offset）
const groupOffset = ref(0)
const cursorTime = ref(null)
const cursorId = ref(null)
// 公告会话相关状态
// isAnnouncementActive：是否处于公告“伪会话”视图
// announcementList：公告摘要列表（按发布时间升序，最新在底部）
// announcementLoading：公告列表加载状态
// unreadAnnouncementCount：未读公告数量，用于侧边栏徽标显示
const isAnnouncementActive = ref(false)
const announcementList = ref([])
const announcementLoading = ref(false)
const unreadAnnouncementCount = ref(0)

// 群公告状态
const isGroupAnnouncementActive = ref(false)
const groupAnnouncementLoading = ref(false)
const groupAnnouncements = ref([])

const openGroupAnnouncement = async () => {
  const gid = Number(targetGroupId.value)
  if (!isGroupActive.value || !Number.isInteger(gid)) return
  isAnnouncementActive.value = false
  isGroupAnnouncementActive.value = true
  // 打开群公告时，先判定当前身份是否为群主，用于控制“发布群公告”按钮显隐
  try {
    const roleRes = await api.chat.memberRole(gid)
    if (roleRes && roleRes.code === 0) {
      const role = (roleRes.data || '').toLowerCase()
      isGroupOwner.value = role === 'owner' || role === 'creator' || role === 'owner_admin' || role === 'group_owner'
    } else {
      isGroupOwner.value = false
    }
  } catch (_) {
    isGroupOwner.value = false
  }
  await loadGroupAnnouncements()
}

const exitGroupAnnouncement = () => {
  isGroupAnnouncementActive.value = false
  // 回到群聊主界面
  isGroupActive.value = true
}

// 顶部群公告提醒（长驻，可手动关闭）
const announcementBanner = ref({ visible: false, count: 0 })
const checkGroupAnnouncementUnread = async () => {
  try {
    if (!isGroupActive.value || !Number.isInteger(targetGroupId.value)) return
    const res = await api.chat.announcementUnreadCount(Number(targetGroupId.value))
    if (res && res.code === 0) {
      const c = Number(res.data || 0)
      announcementBanner.value.visible = c > 0
      announcementBanner.value.count = c
    }
  } catch (_) {}
}

// 监听进入群聊或目标群变化，自动刷新群公告提醒
watch([isGroupActive, targetGroupId], async () => {
  if (isGroupActive.value && Number.isInteger(targetGroupId.value) && targetGroupId.value > 0) {
    await checkGroupAnnouncementUnread()
  } else {
    announcementBanner.value.visible = false
  }
})

const loadGroupAnnouncements = async () => {
  if (!Number.isInteger(targetGroupId.value)) return
  groupAnnouncementLoading.value = true
  try {
    const res = await api.chat.listAnnouncements(Number(targetGroupId.value), 20)
    if (res && res.code === 0 && Array.isArray(res.data)) {
      groupAnnouncements.value = res.data.map(a => ({
        id: a.id,
        title: a.title,
        content: a.content,
        summary: extractSummary(a.content, 80),
        publishTime: a.publishTime || a.createTime,
        publisher: a.publisherName || '群主'
      }))
    } else {
      // 非成员或其他错误：给出提示
      const msg = res?.msg || res?.message
      if (msg) ElMessage.error(msg)
      groupAnnouncements.value = []
    }
  } catch (e) {
    console.error('加载群公告失败:', e)
    ElMessage.error('您不是群成员，无法查看群公告')
    groupAnnouncements.value = []
  } finally {
    groupAnnouncementLoading.value = false
  }
}

const publishAnn = ref({ visible: false, title: '', content: '' })
const openPublishGroupAnnouncementDialog = () => {
  if (!isGroupOwner.value) return
  publishAnn.value = { visible: true, title: '', content: '' }
}
const submitPublishGroupAnnouncement = async () => {
  const title = String(publishAnn.value.title || '').trim()
  const content = String(publishAnn.value.content || '').trim()
  if (!title) {
    ElMessage.warning('请填写公告标题')
    return
  }
  if (!content) {
    ElMessage.warning('请填写公告内容')
    return
  }
  try {
    const res = await request.post(`/api/chat/group/${targetGroupId.value}/announcement`, { title, content })
    if (res && res.code === 0) {
      ElMessage.success('发布成功')
      publishAnn.value.visible = false
      await loadGroupAnnouncements()
    } else {
      ElMessage.error(res?.message || '发布失败')
    }
  } catch (e) {
    console.error('发布群公告失败:', e)
    ElMessage.error('发布失败，请稍后重试')
  }
}

const groupAnnDetail = ref({ visible: false, data: null, readers: [], readersVisible: false, readersLoading: false, totalMembers: 0 })
const openGroupAnnouncementDetail = async (a) => {
  groupAnnDetail.value = { visible: true, data: a, readers: [], readersVisible: false, readersLoading: false, totalMembers: 0 }
  // 详情即视为已读
  try { await api.chat.markAnnouncementRead(a.id) } catch (_) {}
  // 拉取已读名单与总人数
  if (isGroupOwner.value) {
    await refreshGroupAnnouncementRead(a.id)
  }
}

const refreshGroupAnnouncementRead = async (announcementId) => {
  try {
    groupAnnDetail.value.readersLoading = true
    const res = await api.chat.listAnnouncementReaders(announcementId)
    if (res && res.code === 0 && Array.isArray(res.data)) {
      groupAnnDetail.value.readers = res.data
    } else {
      groupAnnDetail.value.readers = []
    }
    // 计算群成员总数：优先用已加载的成员，否则请求一次
    if (Array.isArray(groupMembers.value) && groupMembers.value.length > 0) {
      groupAnnDetail.value.totalMembers = groupMembers.value.length
    } else {
      try {
        const listRes = await api.chat.groupMembers(Number(targetGroupId.value))
        if (listRes && listRes.code === 0 && Array.isArray(listRes.data)) {
          groupAnnDetail.value.totalMembers = listRes.data.length
        }
      } catch (_) {}
    }
  } catch (e) {
    console.error('获取群公告已读名单失败:', e)
  } finally {
    groupAnnDetail.value.readersLoading = false
  }
}

const withdrawGroupAnn = async (a) => {
  try {
    await ElMessageBox.confirm('撤回后所有成员将无法查看该公告，确定撤回吗？', '撤回群公告', { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' })
    const res = await api.chat.withdrawAnnouncement(Number(targetGroupId.value), a.id)
    if (res && res.code === 0) {
      ElMessage.success('已撤回')
      await loadGroupAnnouncements()
    } else {
      ElMessage.error(res?.msg || res?.message || '撤回失败')
    }
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('撤回群公告异常:', e)
      ElMessage.error('撤回失败')
    }
  }
}

// 侧边栏数据源：将“公告”伪会话置顶拼接到 recent 列表前
const sidebarChats = computed(() => {
  const announcementItem = {
    chat_type: 'announcement',
    name: '系统公告',
    last_content: '系统公告通知',
    last_time: Date.now(),
    unread_count: unreadAnnouncementCount.value
  }
  return [announcementItem, ...recentChats.value]
})

/**
 * 加载未读公告数量
 * 函数级注释：调用 /announcement/read-status/unread-count，刷新侧边栏“公告”徽标显示。
 */
const loadUnreadAnnouncementCount = async () => {
  try {
    const res = await api.notice.unreadCount()
    if (res && (res.code === 0 || res.success)) {
      unreadAnnouncementCount.value = Number(res.data || 0)
    }
  } catch (e) {
    console.warn('获取未读公告数量失败:', e)
  }
}

/**
 * 提取公告摘要
 * 函数级注释：从公告全文中抽取首段/前若干字符作为摘要，避免列表中展示过长内容。
 * @param {string} content - 公告全文内容
 * @param {number} [maxLen=80] - 摘要最大长度
 * @returns {string} 摘要文本
 */
const extractSummary = (content, maxLen = 80) => {
  if (!content) return ''
  const firstLine = String(content).split(/\r?\n/).find(s => s && s.trim()) || String(content)
  const text = firstLine.replace(/\s+/g, ' ').trim()
  return text.length > maxLen ? text.slice(0, maxLen) + '…' : text
}

/**
 * 加载公告列表（用于聊天页的公告会话视图）
 * 函数级注释：
 * - 请求公告分页接口第一页，pageSize=20；
 * - 将结果按 publishTime 升序（旧->新，最新在底部）并映射出摘要、已读状态与发布人；
 * - 不在此处标记已读，保持与公告页一致的已读逻辑（进入详情后标记）。
 */
const loadAnnouncementsForChat = async () => {
  announcementLoading.value = true
  try {
    const res = await api.notice.list({ page: 1, pageSize: 20 })
    if (res && res.code === 0 && res.data && Array.isArray(res.data.records)) {
      const records = res.data.records
        .map(it => ({
          id: it.id,
          title: it.title,
          content: it.content,
          summary: extractSummary(it.content, 80),
          publishTime: it.publishTime || it.createTime,
          isRead: !!it.isRead,
          scope: it.type === 'company' ? '全公司' : (it.departmentName || '未知部门'),
          // 新增：发布人字段，优先使用后端返回的 publisherName
          publisher: it.publisherName || '未知'
        }))
        .sort((a, b) => toTimestamp(a.publishTime) - toTimestamp(b.publishTime))
      announcementList.value = records
    }
  } catch (e) {
    console.error('加载公告列表失败:', e)
  } finally {
    announcementLoading.value = false
  }
}

/**
 * 公告相关事件处理
 * 函数级注释：
 * - 响应 Home.vue 广播的 announcement-update / announcement-read-count-update 事件；
 * - 刷新未读数，若当前处于公告视图则刷新列表。
 */
const handleAnnouncementEvent = () => {
  loadUnreadAnnouncementCount()
  if (isAnnouncementActive.value) {
    loadAnnouncementsForChat()
  }
}

/**
 * 加载最近会话列表
 * 函数级注释：
 * - 调用后端 /chat/message/recent 接口，获取最近联系的会话摘要（单聊/群聊）。
 * - 当前仅支持单聊渲染，群聊数据将展示为禁用项或忽略。
 * - 会在进入页面、切换会话、接收新消息时触发刷新。
 */
const loadRecentChats = async () => {
  loadingRecent.value = true
  try {
    const res = await api.chat.recent(50)
    if (res.code === 0 && Array.isArray(res.data)) {
      recentChats.value = dedupeRecent(res.data)
      // 若当前从外部进入，仅携带 userId，则用最近会话中记录的 name/avatar 进行回填，避免URL暴露
      if (isValidTarget()) {
        const item = recentChats.value.find(x => x.chat_type === 'single' && Number(x.contact_id) === Number(targetUserId.value))
        if (item) {
          if (!targetName.value) targetName.value = item.name || `用户${targetUserId.value}`
          if (!targetAvatar.value || targetAvatar.value === '/avatar.jpg') targetAvatar.value = item.avatar || '/avatar.jpg'
        }
      }
    }
  } catch (e) {
    console.error('加载最近会话失败:', e)
  } finally {
    loadingRecent.value = false
  }
}

// 删除最近会话（前端本地移除）
const deleteRecent = (item) => {
  try {
    const isSame = (a, b) => a.chat_type === b.chat_type && (
      (a.chat_type === 'single' && Number(a.contact_id) === Number(b.contact_id)) ||
      (a.chat_type === 'group' && Number(a.group_id) === Number(b.group_id)) ||
      (a.chat_type === 'announcement' && b.chat_type === 'announcement')
    )
    const list = Array.isArray(recentChats.value) ? recentChats.value : []
    recentChats.value = list.filter(rc => !isSame(rc, item))
  } catch (e) {
    console.warn('删除最近会话失败:', e)
  }
}

const loadMyGroups = async () => {
  loadingGroups.value = true
  try {
    const res = await api.chat.myGroups()
    if (res && res.code === 0 && Array.isArray(res.data)) {
      myGroups.value = res.data
    } else {
      myGroups.value = []
    }
  } catch (e) {
    console.error('加载我的群聊失败:', e)
  } finally {
    loadingGroups.value = false
  }
}

/**
 * 在“消息撤回”事件发生时，修补左侧最近会话预览
 * 函数级注释：
 * - 目的：后端的 recent 接口可能仍返回撤回前的 last_content，前端本地修补以隐藏原文
 * - 输入：WS 推送的数据 data，其中应包含 senderId/receiverId/chatType
 * - 行为：
 *   1) 仅处理单聊；计算对端ID作为 recent key（single-contact_id）
 *   2) 命中后将 last_content 改为“你撤回了一条消息/对方撤回了一条消息”，并更新时间 last_time
 *   3) 重新按最后时间降序排序 recent 列表
 */
const patchRecentOnRecall = (data) => {
  try {
    if (!data) return
    const me = userStore.userInfo?.id
    if (!me) return

    if (data.chatType === 'single') {
      const peerId = data.senderId === me ? data.receiverId : data.senderId
      const idx = recentChats.value.findIndex(x => x.chat_type === 'single' && Number(x.contact_id) === Number(peerId))
      if (idx === -1) return
      const tip = data.senderId === me ? '你撤回了一条消息' : '对方撤回了一条消息'
      const t = data.updateTime || data.editTime || Date.now()
      const item = { ...recentChats.value[idx], last_content: tip, last_time: t }
      recentChats.value.splice(idx, 1, item)
      // 重新排序：最近在前
      recentChats.value = [...recentChats.value].sort((a, b) => (toTimestamp(b.last_time) - toTimestamp(a.last_time)))
      return
    }

    if (data.chatType === 'group') {
      const idx = recentChats.value.findIndex(x => x.chat_type === 'group' && Number(x.group_id) === Number(data.groupId))
      if (idx === -1) return
      const tip = (Number(data.senderId) === Number(me))
        ? '你撤回了一条消息'
        : `${data.senderName || ('用户' + data.senderId)}撤回了一条消息`
      const t = data.updateTime || data.editTime || Date.now()
      const item = { ...recentChats.value[idx], last_content: tip, last_time: t }
      recentChats.value.splice(idx, 1, item)
      // 重新排序：最近在前
      recentChats.value = [...recentChats.value].sort((a, b) => (toTimestamp(b.last_time) - toTimestamp(a.last_time)))
      return
    }
  } catch (err) {
    console.warn('patchRecentOnRecall error:', err)
  }
}

/**
 * 规范化最近会话预览文案
 * 函数级注释：
 * - 若 last_content 是图片/文件上传的 JSON 元数据（如包含 mime/size/url 等字段），则返回【图片】或【文件】；
 * - 对于普通文本，保持原样；对于空值返回空字符串；
 * - 该函数仅用于左侧最近会话列表的预览展示，不影响实际消息内容。
 * @param {any} content 原始最近消息内容（可能是纯文本，也可能是JSON字符串）
 * @returns {string} 规范化后的预览文案
 */
const normalizeRecentPreview = (content) => {
  if (content == null) return ''
  if (typeof content !== 'string') return String(content ?? '')
  const s = content.trim()
  if (!s) return ''
  // 尝试解析 JSON：图片/文件消息的 content 存储为JSON元数据
  if (s.startsWith('{') && s.endsWith('}')) {
    try {
      const obj = JSON.parse(s)
      const mime = typeof obj?.mime === 'string' ? obj.mime.toLowerCase() : ''
      if (mime) {
        return mime.startsWith('image/') ? '【图片】' : '【文件】'
      }
      // 兜底：若包含典型图片元数据字段（宽高/缩略图），判定为图片
      if ((typeof obj.width === 'number' && typeof obj.height === 'number') || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(obj?.name || obj?.fileName || obj?.originalName || '')) {
        return '【图片】'
      }
      // 若包含 size/name 等典型文件字段，判定为文件
      if (typeof obj.size === 'number' || obj.name || obj.fileName || obj.originalName) {
        return '【文件】'
      }
      // 其余未知 JSON — 显示“文件”以免泄露元数据
      return '【文件】'
    } catch (_) {
      // 非严格JSON或解析失败：回退为原文
      return s
    }
  }
  // 普通文本
  return s
}

/**
 * 去重最近会话
 * 函数级注释：
 * - 以 chat_type + contact_id/group_id 作为去重key
 * - 对重复项：选取 last_time 最新的一条作为显示基准；unread_count 做求和；name/avatar 采用最新条的非空值回填
 * - 最终按最后时间降序排序
 */
const dedupeRecent = (list = []) => {
  const map = new Map()
  for (const raw of list) {
    const item = { ...raw }
    // 预处理最近会话预览文案
    item.last_content = normalizeRecentPreview(item.last_content)
    const isSingle = item.chat_type === 'single'
    const key = isSingle ? `single-${item.contact_id}` : `group-${item.group_id}`
    const ts = toTimestamp(item.last_time)
    if (!map.has(key)) {
      map.set(key, { ...item, _ts: ts, unread_count: Number(item.unread_count) || 0 })
      continue
    }
    const prev = map.get(key)
    // 重复会话的未读数以“最大值”为准，避免某些接口重复项导致未读翻倍
    const maxUnread = Math.max(Number(prev.unread_count) || 0, Number(item.unread_count) || 0)
    if (ts >= prev._ts) {
      map.set(key, {
        ...item,
        // 新记录覆盖时同样保持预览文案为规范化后的内容
        last_content: normalizeRecentPreview(item.last_content),
        _ts: ts,
        unread_count: maxUnread,
        name: item.name || prev.name,
        avatar: item.avatar || prev.avatar
      })
    } else {
      prev.unread_count = maxUnread
      if (!prev.name && item.name) prev.name = item.name
      if (!prev.avatar && item.avatar) prev.avatar = item.avatar
      map.set(key, prev)
    }
  }
  const arr = Array.from(map.values())
  arr.sort((a, b) => (b._ts || 0) - (a._ts || 0))
  arr.forEach(o => delete o._ts)
  return arr
}

/**
 * 判断当前会话目标ID是否有效
 * 函数级注释：要求为正整数，避免将 null/undefined/NaN/字符串"null" 传给后端
 * @returns {boolean}
 */
const isValidTarget = () => Number.isInteger(targetUserId.value) && targetUserId.value > 0

/**
 * 将多种时间字段解析为毫秒时间戳
 * 函数级注释：
 * - 兼容 number、纯数字字符串（毫秒）、常规 ISO 字符串（含/不含 T）、回退 0
 * - 用于对历史消息进行稳定排序，保证最新消息在底部
 * @param {number|string|undefined|null} val - 待解析的时间值
 * @returns {number} 以毫秒为单位的时间戳
 */
const toTimestamp = (val) => {
  try {
    if (val == null) return 0
    if (typeof val === 'number') return val
    const s = String(val).trim()
    if (!s) return 0
    if (/^\d+$/.test(s)) return Number(s)
    // 统一分隔符并插入T，兼容 'YYYY/MM/DD HH:mm[:ss[.SSS]]'、'YYYY-MM-DD HH:mm[:ss[.SSS]]'
    const uniform = s.replace(/\//g, '-')
    const withT = uniform.includes('T') ? uniform : uniform.replace(' ', 'T')
    const ms = Date.parse(withT)
    if (Number.isFinite(ms)) return ms
    // 兜底：直接使用 new Date 尝试解析
    const ms2 = new Date(s).getTime()
    return Number.isFinite(ms2) ? ms2 : 0
  } catch (_) {
    return 0
  }
}

/**
 * 格式化时间展示
 * 函数级注释：
 * - 今日显示 HH:mm，非今日显示 MM-DD。
 * @param {string|number|Date} t
 * @returns {string}
 */
const formatTime = (t) => {
  const ms = toTimestamp(t)
  if (!ms) return ''
  const d = new Date(ms)
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const isSameDay = d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  return isSameDay ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 格式化完整日期时间（公告场景）
 * 函数级注释：
 * - 输入可为 number/string/Date，内部统一用 toTimestamp 解析；
 * - 返回格式：YYYY-MM-DD HH:mm；当解析失败时返回短横线“—”；
 * - 专用于公告发布时间展示，避免“仅小时或月日”的歧义。
 * @param {string|number|Date} t
 * @returns {string}
 */
const formatDateTime = (t) => {
  const ms = toTimestamp(t)
  if (!ms) return '—'
  const d = new Date(ms)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 滚动到底部
 * 函数级注释：在消息变更后保持视图滚动到最新消息位置，提升聊天体验。
 */
const scrollToBottom = async () => {
  await nextTick()
  const el = listRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

/**
 * 解析路由参数并初始化会话信息
 * 函数级注释：从 query 中读取 userId/name/avatar，用于展示与过滤消息。
 * @returns {boolean} 是否初始化成功；当缺少 userId 时返回 false，但不再跳转或报错。
 */
const initFromRoute = () => {
  // 优先：从 sessionStorage 恢复当前单聊对象，避免地址栏暴露
  try {
    const saved = sessionStorage.getItem('activeChat')
    if (saved) {
      const data = JSON.parse(saved)
      const uidFromState = Number(data?.userId)
      if (uidFromState && !Number.isNaN(uidFromState)) {
        targetUserId.value = uidFromState
        // 直接使用存储的 name 与 avatar 进行首屏渲染，避免空白
        targetName.value = data?.name || ''
        targetAvatar.value = data?.avatar || '/avatar.jpg'
        return true
      }
    }
  } catch (_) {}

  const raw = route.query.userId
  const uid = Number(raw)
  if (!uid || Number.isNaN(uid)) {
    // 允许无会话进入聊天页，此时仅显示左侧会话列表
    targetUserId.value = null
    targetName.value = ''
    targetAvatar.value = '/avatar.jpg'
    return false
  }
  targetUserId.value = uid
  // 不再从 URL 读取 name/avatar，统一从最近会话或接口回填
  targetName.value = ''
  targetAvatar.value = '/avatar.jpg'
  return true
}

/**
 * 连接 WebSocket 并注册消息监听
 * 函数级注释：
 * - 监听 chat_message/chat_message_edit/chat_message_recall/chat_message_delete 四种事件。
 * - 仅处理与当前会话相关的消息，避免串台。
 */
const setupWebSocket = async () => {
  try {
    await connect()
  } catch (e) {
    console.error('WebSocket 连接失败:', e)
  }

  const handleIncoming = (data, type) => {
    /**
     * 函数级注释：统一处理单聊与群聊的 WS 推送。
     * - 单聊：仅当消息与当前打开的单聊对象有关时，才更新右侧消息区；否则刷新最近会话以更新未读。
     * - 群聊：仅当当前处于该群会话，且该消息的接收者是当前用户（扇出副本）时，更新右侧消息区；否则刷新最近会话。
     */
    const me = userStore.userInfo?.id
    if (!data || !me) {
      if (type === 'chat_message' || type === 'chat_message_recall' || type === 'chat_message_delete') loadRecentChats()
      return
    }

    // 单聊处理
    if (data.chatType === 'single') {
      const isRelated = (data.senderId === targetUserId.value && data.receiverId === me) ||
                        (data.senderId === me && data.receiverId === targetUserId.value)
      if (!isRelated) {
        if (type === 'chat_message') {
          loadRecentChats()
        } else if (type === 'chat_message_recall') {
          patchRecentOnRecall(data)
        } else if (type === 'chat_message_delete') {
          loadRecentChats()
        }
        return
      }

      if (type === 'chat_message') {
        messages.value.push(data)
        scrollToBottom()
        loadRecentChats()
      } else if (type === 'chat_message_edit') {
        const idx = messages.value.findIndex(m => m.id === data.id)
        if (idx !== -1) {
          messages.value[idx] = { ...messages.value[idx], ...data }
        }
      } else if (type === 'chat_message_recall') {
        // 优先按 originMessageId 匹配（群聊复制场景），否则回退到 id
        const match = (m) => (data?.originMessageId != null
          ? Number(m.originMessageId) === Number(data.originMessageId)
          : Number(m.id) === Number(data.id))
        messages.value = messages.value.map(m => (match(m) ? { ...m, ...data, isRecalled: true, content: '' } : m))
        // 单聊 recent 修补
        patchRecentOnRecall(data)
      } else if (type === 'chat_message_delete') {
        const idx = messages.value.findIndex(m => m.id === data.id)
        if (idx !== -1) {
          messages.value.splice(idx, 1)
        }
        loadRecentChats()
      }
      return
    }

    // 群聊处理
  if (data.chatType === 'group') {
      // 说明：群聊普通消息为群内广播，不再校验 receiverId；只要当前会话是该群即可接收
      const sameGroup = isGroupActive.value && Number(targetGroupId.value) === Number(data.groupId)
      const inThisGroup = sameGroup
      if (!inThisGroup) {
        if (type === 'chat_message') {
          loadRecentChats()
        } else if (type === 'chat_message_recall') {
          patchRecentOnRecall(data)
        } else if (type === 'chat_message_delete') {
          loadRecentChats()
        }
        return
      }

      if (type === 'chat_message') {
        messages.value.push(data)
        scrollToBottom()
        loadRecentChats()
      } else if (type === 'chat_message_edit') {
        const idx = messages.value.findIndex(m => m.id === data.id)
        if (idx !== -1) {
          messages.value[idx] = { ...messages.value[idx], ...data }
        }
      } else if (type === 'chat_message_recall') {
        // 群聊：优先按 originMessageId 批量匹配（写时扇出副本场景）；无 origin 时回退到 id
        if (data?.originMessageId != null) {
          messages.value = messages.value.map(m => (Number(m.originMessageId) === Number(data.originMessageId)
            ? { ...m, ...data, isRecalled: true, content: '' }
            : m))
        } else {
          const idx = messages.value.findIndex(m => Number(m.id) === Number(data.id))
          if (idx !== -1) {
            messages.value[idx] = { ...messages.value[idx], ...data, isRecalled: true, content: '' }
          }
        }
        // 群聊撤回：修补最近会话预览，不再重新加载以免覆盖修补结果
        patchRecentOnRecall(data)
      } else if (type === 'chat_message_delete') {
        const idx = messages.value.findIndex(m => m.id === data.id)
        if (idx !== -1) {
          messages.value.splice(idx, 1)
        }
        loadRecentChats()
      }
      return
    }

    // 群管理事件通知：notification
    if (type === 'notification' && data && data.event) {
      const e = String(data.event)
      if (e === 'group_disbanded') {
        if (isGroupActive.value && Number(data.groupId) === Number(targetGroupId.value)) {
          ElMessage.warning('群聊已被解散')
          isGroupActive.value = false
          membersDrawerVisible.value = false
          messages.value = []
        }
        // 从“我的群聊”中移除；保留最近会话以便回看历史
        myGroups.value = myGroups.value.filter(g => Number(g.id) !== Number(data.groupId))
        loadMyGroups()
        return
      }
      if (e === 'group_member_removed') {
        // 我被移出：若当前在该群，退回列表
        const me = userStore.userInfo?.id
        if (Number(data.targetUserId) === Number(me)) {
          if (isGroupActive.value && Number(data.groupId) === Number(targetGroupId.value)) {
            ElMessage.warning('你已被移出该群聊')
            isGroupActive.value = false
            membersDrawerVisible.value = false
            messages.value = []
          }
          // 本地从“我的群聊”移除；保留最近会话以便回看历史
          myGroups.value = myGroups.value.filter(g => Number(g.id) !== Number(data.groupId))
          loadMyGroups()
          return
        }
        // 其他成员收到：显示系统提示行（入当前会话）
        if (isGroupActive.value && Number(data.groupId) === Number(targetGroupId.value)) {
          const name = data.targetName || `用户${data.targetUserId}`
          messages.value.push({ id: `sys-${Date.now()}`, sysTip: true, sysText: `${name} 被移出群聊` })
          ElMessage.info(`${name} 被移出群聊`)
        }
        return
      }
      if (e === 'group_member_left') {
        if (isGroupActive.value && Number(data.groupId) === Number(targetGroupId.value)) {
          const name = data.targetName || `用户${data.targetUserId}`
          messages.value.push({ id: `sys-${Date.now()}`, sysTip: true, sysText: `${name} 退出了群聊` })
          ElMessage.info(`${name} 退出了群聊`)
        }
        // 若是我自己（在其他端退出），同步刷新我的群聊列表
        if (Number(data.targetUserId) === Number(userStore.userInfo?.id)) {
          myGroups.value = myGroups.value.filter(g => Number(g.id) !== Number(data.groupId))
          loadMyGroups()
        }
        return
      }
      if (e === 'group_member_joined') {
        if (isGroupActive.value && Number(data.groupId) === Number(targetGroupId.value)) {
          const name = data.targetName || `用户${data.targetUserId}`
          messages.value.push({ id: `sys-${Date.now()}`, sysTip: true, sysText: `${name} 已进入群聊` })
          ElMessage.success(`${name} 已进入群聊`)
        }
        // 如果是我被拉入群聊，刷新“我的群聊”列表
        if (Number(data.targetUserId) === Number(userStore.userInfo?.id)) {
          loadMyGroups()
        }
        loadRecentChats()
        return
      }
    }

    // 其他类型：仅刷新最近会话
    if (type === 'chat_message' || type === 'chat_message_recall' || type === 'chat_message_delete') loadRecentChats()
  }

  onMessage('chat_message', handleIncoming)
  onMessage('chat_message_edit', handleIncoming)
  onMessage('chat_message_recall', handleIncoming)
  onMessage('chat_message_delete', handleIncoming)
  onMessage('read_ack', (data) => {
    try {
      // 仅处理当前单聊会话的回执：对端是 targetUserId，且我是发送者
      const me = userStore.userInfo?.id
      if (!me || !isValidTarget() || isGroupActive.value) return
      const related = data && ((data.senderId === me && data.receiverId === targetUserId.value) || (data.messageId && messages.value.some(m => m.id === data.messageId && m.senderId === me)))
      if (!related) return
      // 将我方发送的消息标记为已读（若有 messageId 则精确标记，否则批量）
      if (data.messageId) {
        const idx = messages.value.findIndex(m => Number(m.id) === Number(data.messageId))
        if (idx !== -1) {
          messages.value[idx] = { ...messages.value[idx], isRead: true, readTime: data.readTime || Date.now() }
        }
      } else {
        messages.value = messages.value.map(m => (m.senderId === me ? { ...m, isRead: true, readTime: data.readTime || Date.now() } : m))
      }
    } catch (e) { console.error('处理已读回执失败', e) }
  })
  onMessage('notification', handleIncoming)

  // 卸载时清理
  onBeforeUnmount(() => {
    offMessage('chat_message', handleIncoming)
    offMessage('chat_message_edit', handleIncoming)
    offMessage('chat_message_recall', handleIncoming)
    offMessage('chat_message_delete', handleIncoming)
    offMessage('notification', handleIncoming)
  })
}

/**
 * 加载单聊历史
 * 函数级注释：当 HISTORY_ENABLED=false 或目标ID无效时跳过；
 * 加载后进行“时间升序”排序，确保最新消息显示在底部。
 */
const loadHistory = async () => {
  if (!HISTORY_ENABLED) return
  if (!isValidTarget()) return
  try {
    const res = await api.chat.singleHistory(targetUserId.value, 15, 0)
    if (res.code === 0) {
      const list = Array.isArray(res.data) ? res.data : []
      // 升序：旧 -> 新，保证最新在底部
      list.sort((a, b) => {
        const ta = toTimestamp(a.createTime || a.timestamp || a.time)
        const tb = toTimestamp(b.createTime || b.timestamp || b.time)
        return ta - tb
      })
      // 历史消息加载：升序排序后，屏蔽已撤回消息的内容，并统一打上 isRecalled 标记
      messages.value = list.map(m => {
        if (m && (m.isRecalled || m.status === 'recalled')) {
          return { ...m, isRecalled: true, content: '' }
        }
        return m
      })
      // 初始化游标（指向当前已加载最旧一条）与是否还有更多
      hasMore.value = list.length >= 15
      const first = messages.value[0]
      if (first) {
        cursorTime.value = first.createTime || first.timestamp || first.time || null
        cursorId.value = first.id || null
      } else {
        cursorTime.value = null
        cursorId.value = null
        hasMore.value = false
      }
      await scrollToBottom()
    }
  } catch (e) {
    console.error('加载历史消息失败:', e)
  }
}

/**
 * 加载更多历史（游标分页）
 * 函数级注释：
 * - 基于当前 cursorTime/cursorId 作为游标，从后端拉取更早消息并插入列表顶部；
 * - 仅在 HISTORY_ENABLED 且会话有效时工作；
 * - 根据返回数量更新 hasMore；自动去重避免因并发导致的重复。
 */
const loadMoreHistory = async () => {
  if (!HISTORY_ENABLED) return
  if (!isValidTarget()) return
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    const limit = 15
    const params = {
      limit,
      beforeTime: cursorTime.value ? toTimestamp(cursorTime.value) : undefined,
      beforeId: cursorId.value || undefined
    }
    const res = await api.chat.singleHistory(targetUserId.value, params.limit, 0)
    if (res.code === 0) {
      let arr = Array.isArray(res.data) ? res.data : []
      // 后端倒序返回，转为升序以便插入到顶部
      arr.sort((a, b) => {
        const ta = toTimestamp(a.createTime || a.timestamp || a.time)
        const tb = toTimestamp(b.createTime || b.timestamp || b.time)
        return ta - tb
      })
      // 去重（按id），并打上撤回标记隐藏内容
      const existingIds = new Set(messages.value.map(m => m.id))
      arr = arr
        .filter(m => m && !existingIds.has(m.id))
        .map(m => (m && (m.isRecalled || m.status === 'recalled') ? { ...m, isRecalled: true, content: '' } : m))
      if (arr.length > 0) {
        const el = listRef.value
        const prevHeight = el ? el.scrollHeight : 0
        messages.value = [...arr, ...messages.value]
        // 更新游标为最新插入的最旧一条
        const first = messages.value[0]
        cursorTime.value = first?.createTime || first?.timestamp || first?.time || null
        cursorId.value = first?.id || null
        // 保持滚动位置稳定
        await nextTick()
        if (el) {
          const newHeight = el.scrollHeight
          el.scrollTop = newHeight - prevHeight + el.scrollTop
        }
      }
      if (arr.length < limit) {
        hasMore.value = false
      }
    }
  } catch (e) {
    console.error('加载更多失败:', e)
  } finally {
    loadingMore.value = false
  }
}

/**
 * 批量标记当前会话未读为已读
 * 函数级注释：进入会话或切换会话后调用，将对端发给我的所有未读设为已读。
 */
const markCurrentChatAsRead = async () => {
  const me = userStore.userInfo?.id
  if (!me || !isValidTarget()) return
  try {
    await api.chat.markBatch({ senderId: targetUserId.value })
    // 已读后刷新最近会话未读数
    loadRecentChats()
  } catch (e) {
    console.error('批量已读失败:', e)
  }
}

 /**
 * 发送文本消息
 * 函数级注释：调用后端发送接口，并将返回的消息本地回显；后端不会通过WS回推给发送者本人。
 */
const sendText = async () => {
  const content = inputText.value.trim()
  if (!content) return
  const me = userStore.userInfo?.id
  const inGroup = isGroupActive.value && Number.isInteger(targetGroupId.value) && targetGroupId.value > 0
  const inSingle = isValidTarget()
  if (!me || (!inSingle && !inGroup)) {
    ElMessage.info('请先在左侧选择会话')
    return
  }
  try {
    const payload = inGroup
      ? { groupId: targetGroupId.value, chatType: 'group', messageType: 'text', content }
      : { receiverId: targetUserId.value, chatType: 'single', messageType: 'text', content }
    const res = await api.chat.send(payload)
    if (res.code === 0 && res.data) {
      messages.value.push({ ...res.data, senderId: me })
      inputText.value = ''
      await scrollToBottom()
      // 发送成功后刷新最近会话列表（更新预览/时间）
      loadRecentChats()
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    console.error('发送消息失败:', e)
    ElMessage.error('发送失败')
  }
}

// 新增：加载群聊历史
// 函数级注释：
// - 当 HISTORY_ENABLED 为 true 且当前处于群聊视图时，调用 getGroupHistoryService 拉取该群的最新消息；
// - 将结果按时间升序（旧->新）排序；若消息被撤回，打上 isRecalled 并清空内容；
// - 加载完成后滚动到底部，保证最新消息可见。
const loadGroupHistory = async () => {
  if (!HISTORY_ENABLED) return
  if (!isGroupActive.value || !Number.isInteger(targetGroupId.value) || targetGroupId.value <= 0) return
  try {
    const limit = 15
    const res = await api.chat.groupHistory(targetGroupId.value, limit, 0)
    if (res && res.code === 0) {
      const list = Array.isArray(res.data) ? res.data : []
      list.sort((a, b) => {
        const ta = toTimestamp(a.createTime || a.timestamp || a.time)
        const tb = toTimestamp(b.createTime || b.timestamp || b.time)
        return ta - tb
      })
      messages.value = list.map(m => (m && (m.isRecalled || m.status === 'recalled') ? { ...m, isRecalled: true, content: '' } : m))
      // 初始化群聊分页状态
      hasMore.value = list.length >= limit
      groupOffset.value = list.length
      await scrollToBottom()
    }
  } catch (e) {
    console.error('加载群聊历史失败:', e)
  }
}

// 新增：批量将当前群聊消息设为已读
// 函数级注释：
// - 进入群聊或加载群聊历史后调用；
// - 调用 markMessagesAsReadBatchService({ groupId })，成功后刷新左侧最近会话未读数。
const markGroupAsRead = async () => {
  const gid = Number(targetGroupId.value)
  if (!gid) return
  try {
    await api.chat.markBatch({ groupId: gid })
    loadRecentChats()
  } catch (e) {
    console.error('群聊批量已读失败:', e)
  }
}

// 群聊“更多”菜单与成员抽屉（第一步：UI壳体与占位方法）
const membersDrawerVisible = ref(false)
const isGroupOwner = ref(false)
const membersLoading = ref(false)
const memberKeyword = ref('')
const groupMembers = ref([])
const filteredMembers = computed(() => {
  const k = memberKeyword.value.trim().toLowerCase()
  if (!k) return groupMembers.value
  return groupMembers.value.filter(m => {
    const name = (m.nickname || m.realname || m.username || '').toLowerCase()
    return name.includes(k) || String(m.id).includes(k)
  })
})
// 邀请弹窗：联系人多选
const inviteVisible = ref(false)
const inviteLoading = ref(false)
const inviteOptions = ref([])
const inviteSelected = ref([])
const openInviteDialog = async () => {
  inviteVisible.value = true
  inviteSelected.value = []
  await loadInviteContacts()
}
const loadInviteContacts = async () => {
  inviteLoading.value = true
  try {
    const res = await api.org.listContacts()
    if (res && (res.code === 0 || res.success) && Array.isArray(res.data)) {
      // 过滤已在群内成员
      const existingIds = new Set(groupMembers.value.map(m => Number(m.id)))
      inviteOptions.value = res.data
        .filter(x => Number.isInteger(Number(x.contactId)) && !existingIds.has(Number(x.contactId)))
        .map(x => ({ value: Number(x.contactId), label: x.contactName || `用户${x.contactId}` }))
    } else {
      inviteOptions.value = []
    }
  } catch (e) {
    console.error('加载可邀请联系人失败:', e)
    inviteOptions.value = []
  } finally {
    inviteLoading.value = false
  }
}
const submitInvite = async () => {
  const gid = Number(targetGroupId.value)
  if (!gid || inviteSelected.value.length === 0) {
    ElMessage.info('请选择要邀请的联系人')
    return
  }
  try {
    const res = await api.chat.addMembers(gid, inviteSelected.value)
    if (res && res.code === 0) {
      ElMessage.success('已发送邀请并加入群聊')
      inviteVisible.value = false
      // 刷新成员列表
      await loadGroupRoleAndMembers()
    }
  } catch (e) {
    console.error('邀请成员失败:', e)
  }
}
const loadGroupRoleAndMembers = async () => {
  const gid = Number(targetGroupId.value)
  if (!gid) return
  try {
    membersLoading.value = true
    const [roleRes, listRes] = await Promise.all([
      api.chat.memberRole(gid),
      api.chat.groupMembers(gid)
    ])
    if (roleRes && roleRes.code === 0) {
      const role = (roleRes.data || '').toLowerCase()
      isGroupOwner.value = role === 'owner' || role === 'creator' || role === 'owner_admin' || role === 'group_owner'
    } else {
      isGroupOwner.value = false
    }
    if (listRes && listRes.code === 0 && Array.isArray(listRes.data)) {
      groupMembers.value = listRes.data
    } else {
      groupMembers.value = []
    }
  } catch (e) {
    console.error('加载群成员失败:', e)
    ElMessage.error('获取群成员失败')
  } finally {
    membersLoading.value = false
  }
}
const openMembersDrawer = async () => {
  if (!isGroupActive.value) return
  membersDrawerVisible.value = true
  // 确保使用当前登录用户的最新信息（避免缓存错位）
  try { await userStore.loadUserInfo() } catch (_) {}
  // 打开即加载角色与成员
  loadGroupRoleAndMembers()
}
const confirmDisbandGroup = async () => {
  try {
    await ElMessageBox.confirm('解散后所有成员将被移出且聊天记录不再可见，确定解散该群吗？', '解散群聊', {
      type: 'warning',
      confirmButtonText: '解散',
      cancelButtonText: '取消'
    })
    const gid = Number(targetGroupId.value)
    const res = await api.chat.deleteGroup(gid)
    if (res && res.code === 0) {
      ElMessage.success('已解散群聊')
      membersDrawerVisible.value = false
      // 关闭当前群视图并刷新最近会话
      isGroupActive.value = false
      messages.value = []
      // 本地从“我的群聊”移除并刷新
      myGroups.value = myGroups.value.filter(g => Number(g.id) !== Number(targetGroupId.value))
      loadRecentChats()
      loadMyGroups()
    }
  } catch (_) {}
}
const confirmLeaveGroup = async () => {
  try {
    await ElMessageBox.confirm('退出后将不再接收该群消息，确定退出吗？', '退出群聊', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    const gid = Number(targetGroupId.value)
    const res = await api.chat.leaveGroup(gid)
    if (res && res.code === 0) {
      ElMessage.success('已退出群聊')
      membersDrawerVisible.value = false
      isGroupActive.value = false
      messages.value = []
      // 本地从“我的群聊”移除并刷新
      myGroups.value = myGroups.value.filter(g => Number(g.id) !== Number(targetGroupId.value))
      loadRecentChats()
      loadMyGroups()
    }
  } catch (_) {}
}

const removeMember = async (userId, nick) => {
  if (!Number(targetGroupId.value)) return
  if (userId === userStore.userInfo?.id) return
  try {
    await ElMessageBox.confirm(`确定将“${nick || ('用户' + userId)}”移出该群吗？`, '移出群聊', {
      type: 'warning', confirmButtonText: '移出', cancelButtonText: '取消'
    })
    const res = await api.chat.removeMember(Number(targetGroupId.value), Number(userId))
    if (res && res.code === 0) {
      ElMessage.success('已移出群聊')
      // 本地移除并刷新
      groupMembers.value = groupMembers.value.filter(m => Number(m.id) !== Number(userId))
      loadRecentChats()
    }
  } catch (_) {}
}

/**
 * 加载更多群聊历史（偏移量分页）
 * 函数级注释：
 * - 参照单聊的“加载更多”逻辑，但由于后端未提供游标接口，这里使用 limit+offset 的分页方式；
 * - 将新获取的更早消息按时间升序插入到列表顶部，保持滚动位置不跳变；
 * - 自动去重（按 id）；当返回数量小于 limit 时，判定无更多可加载。
 */
const loadMoreGroupHistory = async () => {
  if (!HISTORY_ENABLED) return
  if (!isGroupActive.value || !Number.isInteger(targetGroupId.value) || targetGroupId.value <= 0) return
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    const limit = 15
    const res = await api.chat.groupHistory(targetGroupId.value, limit, groupOffset.value)
    if (res && res.code === 0) {
      let arr = Array.isArray(res.data) ? res.data : []
      // 保持升序（旧->新），便于插入顶部
      arr.sort((a, b) => {
        const ta = toTimestamp(a.createTime || a.timestamp || a.time)
        const tb = toTimestamp(b.createTime || b.timestamp || b.time)
        return ta - tb
      })
      // 去重并处理撤回
      const existingIds = new Set(messages.value.map(m => m.id))
      arr = arr
        .filter(m => m && !existingIds.has(m.id))
        .map(m => (m && (m.isRecalled || m.status === 'recalled') ? { ...m, isRecalled: true, content: '' } : m))
      if (arr.length > 0) {
        const el = listRef.value
        const prevHeight = el ? el.scrollHeight : 0
        messages.value = [...arr, ...messages.value]
        groupOffset.value += arr.length
        await nextTick()
        if (el) {
          const newHeight = el.scrollHeight
          el.scrollTop = newHeight - prevHeight + el.scrollTop
        }
      }
      if (arr.length < limit) {
        hasMore.value = false
      }
    }
  } catch (e) {
    console.error('加载更多群聊历史失败:', e)
  } finally {
    loadingMore.value = false
  }
}

/**
 * 打开指定最近会话
 * 函数级注释：
 * - 单聊：跳转到 /chat?userId=contact_id&name=...&avatar=...
 * - 群聊：设置群聊状态，加载群聊历史，并批量设为已读。
 * @param {Object} item - 最近会话条目
 */
const openConversation = (item) => {
  if (!item) return
  if (item.chat_type === 'announcement') {
    isAnnouncementActive.value = true
    isGroupAnnouncementActive.value = false
    isGroupActive.value = false
    // 退出单聊目标
    targetUserId.value = null
    targetName.value = '系统公告'
    targetAvatar.value = '/avatar.jpg'
    loadAnnouncementsForChat()
    // 切换时滚动顶部
    nextTick(() => { listRef.value?.scrollTo?.({ top: 0 }) })
  } else if (item.chat_type === 'single' && item.contact_id) {
    // 从公告或其他状态切回单聊
    isAnnouncementActive.value = false
    isGroupAnnouncementActive.value = false
    isGroupActive.value = false
    const current = Number(route.query.userId)
    // 记录当前单聊对象到 sessionStorage，避免放到地址栏，并带上名称与头像用于首屏渲染
    try { sessionStorage.setItem('activeChat', JSON.stringify({ userId: Number(item.contact_id), name: item.name || '', avatar: item.avatar || '/avatar.jpg' })) } catch (_) {}
    if (current === Number(item.contact_id)) {
      // 路由未发生变化（例如：此前在同一会话，期间进入过公告）
      // 直接手动初始化目标会话并加载历史，避免 watcher 不触发导致右侧不更新
      targetUserId.value = item.contact_id
      targetName.value = item.name || ''
      targetAvatar.value = item.avatar || '/avatar.jpg'
      messages.value = []
      // 加载历史并将消息设为已读，同时刷新最近会话预览/未读
      loadHistory().then(() => markCurrentChatAsRead())
      loadRecentChats()
    } else {
      // 正常切换：地址仅为 /chat，不携带任何 query
      if (route.path === '/chat') {
        // 若已在聊天页，手动根据状态初始化
        const ok = initFromRoute()
        messages.value = []
        if (ok) {
          loadHistory().then(() => markCurrentChatAsRead())
        }
        loadRecentChats()
        checkGroupAnnouncementUnread()
      } else {
        router.push({ path: '/chat' })
      }
    }
  } else if (item.chat_type === 'group' && item.group_id) {
    // 进入群聊视图
    isAnnouncementActive.value = false
    isGroupAnnouncementActive.value = false
    isGroupActive.value = true
    targetGroupId.value = Number(item.group_id)
    targetGroupName.value = item.name || '群聊'
    targetGroupAvatar.value = item.avatar || '/avatar.jpg'
    // 清理单聊目标，避免误判 isValidTarget()
    targetUserId.value = null
    messages.value = []
    // 重置群聊分页/游标状态
    hasMore.value = false
    loadingMore.value = false
    groupOffset.value = 0
    cursorTime.value = null
    cursorId.value = null
    // 加载群聊历史并批量已读，刷新最近会话；返回一个 Promise 以便外部可在加载完成后执行后续逻辑
    const p = loadGroupHistory().then(() => { return markGroupAsRead() }).catch(() => {})
    loadRecentChats()
    checkGroupAnnouncementUnread()
    return p
  } else {
    ElMessage.info('无法打开该会话')
  }
}

/**
 * 编辑消息
 * 函数级注释：仅允许编辑“我发送”的消息；成功后本地同步内容与编辑标记。
 * @param {Object} msg - 待编辑的消息对象
 */
const handleEditMessage = async (msg) => {
  if (!msg?.id) return
  if (msg.isRecalled) {
    ElMessage.info('该消息已撤回，无法编辑')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('编辑消息内容', '编辑消息', {
      inputValue: msg.content,
      inputPlaceholder: '请输入新的消息内容',
      inputValidator: (val) => !!(val && val.trim()) || '内容不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const res = await api.chat.edit(msg.id, value.trim())
    if (res.code === 0 && res.data) {
      const idx = messages.value.findIndex(m => m.id === msg.id)
      if (idx !== -1) {
        messages.value[idx] = { ...messages.value[idx], ...res.data, isEdited: true }
      }
      ElMessage.success('已保存修改')
    }
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    console.error('编辑消息失败:', e)
    ElMessage.error('编辑失败')
  }
}

/**
 * 撤回消息
 * 函数级注释：仅允许撤回“我发送”的消息；成功后本地同步撤回标记。
 * - 单聊：同时修补最近会话预览（避免后端 last_content 延迟），使用 patchRecentOnRecall。
 * - 群聊：直接刷新最近会话列表（无需单聊式局部修补）。
 * @param {Object} msg - 待撤回的消息对象
 */
const handleRecallMessage = async (msg) => {
  if (!msg?.id) return
  try {
    await ElMessageBox.confirm('确定要撤回这条消息吗？', '撤回消息', { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' })
    const res = await api.chat.recall(msg.id)
    if (res.code === 0 && res.data) {
      // 群聊本地优先按 originMessageId 批量标记撤回；无 origin 回退到 id；单聊按 id
      const hasOrigin = msg?.originMessageId != null
      if (isGroupActive.value && hasOrigin) {
        messages.value = messages.value.map(m => (Number(m.originMessageId) === Number(msg.originMessageId)
          ? { ...m, ...res.data, isRecalled: true, content: '' }
          : m))
      } else {
        const idx = messages.value.findIndex(m => Number(m.id) === Number(msg.id))
        if (idx !== -1) {
          messages.value[idx] = { ...messages.value[idx], ...res.data, isRecalled: true, content: '' }
        }
      }
      const me = userStore.userInfo?.id
      if (isGroupActive.value) {
        // 群聊：撤回成功后修补左侧最近会话预览提示，避免后端 recent 延迟
        patchRecentOnRecall({
          chatType: 'group',
          senderId: me,
          senderName: userStore.userInfo?.nickName || userStore.userInfo?.username || '',
          groupId: targetGroupId.value,
          updateTime: res.data?.updateTime || res.data?.editTime || Date.now()
        })
      } else {
        // 单聊：撤回成功后修补左侧最近会话预览提示
        patchRecentOnRecall({
          chatType: 'single',
          senderId: me,
          receiverId: targetUserId.value,
          updateTime: res.data?.updateTime || res.data?.editTime || Date.now()
        })
      }
      ElMessage.success('已撤回')
    }
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    console.error('撤回消息失败:', e)
    ElMessage.error('撤回失败')
  }
}

/**
 * 删除消息
 * 函数级注释：仅允许删除“我发送”的消息；成功后将该消息从本地列表移除。
 * @param {Object} msg - 待删除的消息对象
 */
const handleDeleteMessage = async (msg) => {
  if (!msg?.id) return
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除该消息吗？', '删除消息', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    const res = await api.chat.delete(msg.id)
    if (res.code === 0 && res.data) {
      const idx = messages.value.findIndex(m => m.id === msg.id)
      if (idx !== -1) messages.value.splice(idx, 1)
      // 刷新左侧最近会话，以便立即反映最新一条内容/排序
      loadRecentChats()
      ElMessage.success('已删除')
    }
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    console.error('删除消息失败:', e)
    ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  // 进入聊天页时强制刷新一次用户信息，避免跨账号残留
  try { await userStore.loadUserInfo() } catch (_) {}
  const ok = initFromRoute()
  await setupWebSocket()
  await loadRecentChats()
  await loadMyGroups()
  await loadUnreadAnnouncementCount()
  // 监听“删除公告会话”事件：清空公告视图消息
  window.addEventListener('announcement-conversation-deleted', () => {
    if (isAnnouncementActive.value) {
      messages.value = []
      // 重新拉取未读数量，避免铃铛显示
      loadUnreadAnnouncementCount()
    }
  })
  // 监听公告更新与已读数变更事件
  window.addEventListener('announcement-update', handleAnnouncementEvent)
  window.addEventListener('announcement-read-count-update', handleAnnouncementEvent)
  if (ok) {
    await loadHistory()
    await markCurrentChatAsRead()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('announcement-update', handleAnnouncementEvent)
  window.removeEventListener('announcement-read-count-update', handleAnnouncementEvent)
  window.removeEventListener('announcement-conversation-deleted', () => {})
})
watch(() => route.path, async () => {
  if (route.path !== '/chat') return
  // 切换到聊天页时退出公告视图并从本地状态恢复会话
  isAnnouncementActive.value = false
  const ok = initFromRoute()
  messages.value = []
  if (ok) {
    await loadHistory()
    await markCurrentChatAsRead()
  }
  loadRecentChats()
});

// 上传相关的响应式状态与引用
const uploading = ref(false)
const uploadPercent = ref(0)
const imageInputRef = ref(null)
const fileInputRef = ref(null)
let uploadAbortController = null

/**
 * 规范化附件 URL
 * 函数级注释：
 * - 将后端返回的路径中的反斜杠替换为正斜杠；
 * - 对于相对路径（如 uploads/... 或 /uploads/...），确保以 "/uploads" 开头并以 "/" 起始，使其可被 http://localhost:5173/ 代理到后端；
 * - 对于 http/https 开头的绝对地址，原样返回。
 * @param {string} url 原始 URL
 * @returns {string} 规范化后的 URL
 */
const normalizeAttachmentUrl = (url) => {
  if (!url) return ''
  let u = String(url).replace(/\\/g, '/').trim()
  if (/^https?:\/\//i.test(u)) return u
  if (!u.startsWith('/')) u = '/' + u
  if (!u.startsWith('/uploads/')) {
    // 兼容返回形如 /chat/images/xxx 的相对路径，此处仅在非 /uploads 开头时直返
    // 如需强制前缀，可在后端统一返回 /uploads 前缀
    return u
  }
  return u
}

// 图片预览（双击放大）
const imageViewer = ref({ visible: false, list: [], index: 0 })
const collectImageUrls = () => {
  try {
    return messages.value
      .filter(m => !m?.isRecalled && m?.messageType === 'image')
      .map(m => {
        try { return normalizeAttachmentUrl(JSON.parse(m.content)?.url) } catch (_) { return '' }
      })
      .filter(u => !!u)
  } catch (_) { return [] }
}
const openImageViewer = (url) => {
  const list = collectImageUrls()
  const idx = list.findIndex(u => u === url)
  imageViewer.value = { visible: true, list, index: idx >= 0 ? idx : 0 }
}

/**
 * 选择图片
 * 函数级注释：
 * - 在有效单聊会话下触发隐藏的 input[type=file]；
 * - 公告视图或未选择会话时给出提示。
 */
const chooseImage = () => {
  if (isAnnouncementActive.value) {
    ElMessage.info('公告会话不可发送消息')
    return
  }
  const inGroup = isGroupActive.value && Number.isInteger(targetGroupId.value) && targetGroupId.value > 0
  if (!isValidTarget() && !inGroup) {
    ElMessage.info('请先在左侧选择会话')
    return
  }
  imageInputRef.value && imageInputRef.value.click()
}

/**
 * 选择文件
 * 函数级注释：逻辑同 chooseImage。
 */
const chooseFile = () => {
  if (isAnnouncementActive.value) {
    ElMessage.info('公告会话不可发送消息')
    return
  }
  const inGroup = isGroupActive.value && Number.isInteger(targetGroupId.value) && targetGroupId.value > 0
  if (!isValidTarget() && !inGroup) {
    ElMessage.info('请先在左侧选择会话')
    return
  }
  fileInputRef.value && fileInputRef.value.click()
}

/**
 * 处理图片选择并上传
 * 函数级注释：
 * - 前端快速校验类型与大小（≤5MB）；
 * - 使用 uploadChatImageService 以 multipart/form-data 上传，支持进度与取消；
 * - 上传成功后构造 messageType='image' 的消息发送给后端；
 * - 成功后本地回显并刷新最近会话；失败给出提示；
 * - finally 中重置上传状态，清空 input 以允许重复选择同一文件。
 * @param {Event} e change 事件
 */
const handleImageChange = async (e) => {
  const input = e?.target
  const file = input?.files?.[0]
  // 允许重复选择相同文件
  if (input) input.value = ''
  if (!file) return
  if (!file.type || !file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  const maxMB = 5
  if (file.size > maxMB * 1024 * 1024) {
    ElMessage.error(`图片大小不能超过${maxMB}MB`)
    return
  }
  try {
    uploadAbortController = new AbortController()
    uploading.value = true
    uploadPercent.value = 0
    const res = await uploadChatImageService(file, {
      onProgress: (p) => { uploadPercent.value = Math.round(p) },
      signal: uploadAbortController.signal
    })
    if (res && res.code === 0 && res.data) {
      const meta = res.data
      const inGroup = isGroupActive.value && Number.isInteger(targetGroupId.value) && targetGroupId.value > 0
      const payload = inGroup
        ? { groupId: targetGroupId.value, chatType: 'group', messageType: 'image', content: JSON.stringify(meta) }
        : { receiverId: targetUserId.value, chatType: 'single', messageType: 'image', content: JSON.stringify(meta) }
      const sres = await sendChatMessageService(payload)
      if (sres && sres.code === 0 && sres.data) {
        messages.value.push({ ...sres.data, senderId: userStore.userInfo?.id })
        await scrollToBottom()
        loadRecentChats()
      } else {
        ElMessage.error(sres?.message || '发送图片消息失败')
      }
    } else {
      ElMessage.error(res?.message || '上传图片失败')
    }
  } catch (err) {
    // 取消上传使用 AbortController，axios 会抛出 CanceledError
    if (err && (err.name === 'CanceledError' || String(err.message || '').toLowerCase().includes('canceled'))) {
      // 静默处理
    } else {
      console.error('图片上传失败：', err)
      ElMessage.error('上传失败')
    }
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    uploadAbortController = null
  }
}

/**
 * 处理文件选择并上传
 * 函数级注释：
 * - 前端快速校验大小（≤20MB）；
 * - 使用 uploadChatFileService 上传，支持进度与取消；
 * - 上传成功后发送 messageType='file' 的消息；
 * - 成功后本地回显并刷新最近会话；异常处理与图片一致。
 * @param {Event} e change 事件
 */
const handleFileChange = async (e) => {
  const input = e?.target
  const file = input?.files?.[0]
  if (input) input.value = ''
  if (!file) return
  const maxMB = 20
  if (file.size > maxMB * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过${maxMB}MB`)
    return
  }
  try {
    uploadAbortController = new AbortController()
    uploading.value = true
    uploadPercent.value = 0
    const res = await uploadChatFileService(file, {
      onProgress: (p) => { uploadPercent.value = Math.round(p) },
      signal: uploadAbortController.signal
    })
    if (res && res.code === 0 && res.data) {
      const meta = res.data
      const inGroup = isGroupActive.value && Number.isInteger(targetGroupId.value) && targetGroupId.value > 0
      const payload = inGroup
        ? { groupId: targetGroupId.value, chatType: 'group', messageType: 'file', content: JSON.stringify(meta) }
        : { receiverId: targetUserId.value, chatType: 'single', messageType: 'file', content: JSON.stringify(meta) }
      const sres = await sendChatMessageService(payload)
      if (sres && sres.code === 0 && sres.data) {
        messages.value.push({ ...sres.data, senderId: userStore.userInfo?.id })
        await scrollToBottom()
        loadRecentChats()
      } else {
        ElMessage.error(sres?.message || '发送文件消息失败')
      }
    } else {
      ElMessage.error(res?.message || '上传文件失败')
    }
  } catch (err) {
    if (err && (err.name === 'CanceledError' || String(err.message || '').toLowerCase().includes('canceled'))) {
      // 静默处理
    } else {
      console.error('文件上传失败：', err)
      ElMessage.error('上传失败')
    }
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    uploadAbortController = null
  }
}

/**
 * 取消当前上传
 * 函数级注释：
 * - 若存在进行中的上传，调用 AbortController.abort() 终止；
 * - UI 状态重置在各自 finally 中处理，这里只负责触发取消。
 */
const cancelUpload = () => {
  if (uploadAbortController) {
    try { uploadAbortController.abort() } catch (_) {}
  }
}

/**
 * 创建群聊表单模型
 * 函数级注释：用于收集群名称、描述与选中成员ID列表；在弹窗打开时重置，关闭时清空。
 */
const createGroupForm = ref({ groupName: '', description: '', memberIds: [] })

/**
 * 创建群聊表单校验规则
 * 函数级注释：
 * - 群名称：必填，长度1-20；
 * - 成员：至少选择2人；
 */
const createGroupRules = {
  groupName: [
    { required: true, message: '请输入群名称', trigger: 'blur' },
    { min: 1, max: 20, message: '群名称长度为1-20个字符', trigger: 'blur' }
  ],
  memberIds: [
    {
      validator: (_rule, value, callback) => {
        if (!Array.isArray(value) || value.length < 2) return callback(new Error('请至少选择2位成员'))
        callback()
      },
      trigger: 'change'
    }
  ]
}

/**
 * 联系人选项与加载状态
 * 函数级注释：弹窗打开时拉取我的通讯录联系人，并映射为 { value: contactId, label: contactName } 供多选。
 */
const contactOptions = ref([])
const contactsLoading = ref(false)
const createGroupFormRef = ref(null)

/**
 * 在弹窗打开时加载联系人列表
 * 函数级注释：调用 getAddressBookContactsService 获取通讯录数据；失败时提示错误。
 */
const loadCreateGroupContacts = async () => {
  contactsLoading.value = true
  try {
    const res = await api.org.listContacts()
    if (res && (res.code === 0 || res.success) && Array.isArray(res.data)) {
      contactOptions.value = res.data
        .filter(x => Number.isInteger(Number(x.contactId)))
        .map(x => ({ value: Number(x.contactId), label: x.contactName || `用户${x.contactId}` }))
    } else {
      ElMessage.error(res?.message || '获取联系人失败')
    }
  } catch (e) {
    console.error('加载联系人失败:', e)
    ElMessage.error('获取联系人失败')
  } finally {
    contactsLoading.value = false
  }
}

/**
 * 打开创建群聊弹窗时的钩子
 * 函数级注释：重置表单并加载联系人选项。
 */
const onCreateGroupDialogOpen = () => {
  createGroupForm.value = { groupName: '', description: '', memberIds: [] }
  contactOptions.value = []
  loadCreateGroupContacts()
}

/**
 * 创建群聊弹窗关闭后的钩子
 * 函数级注释：清理表单与校验状态，避免下次打开残留。
 */
const onCreateGroupDialogClosed = () => {
  createGroupFormRef.value?.clearValidate?.()
}

/**
 * 提交“创建群聊”
 * 函数级注释：
 * - 先进行表单校验；
 * - 调用 createChatGroupService({ groupName, description }, memberIds) 创建群；
 * - 成功后关闭弹窗、提示成功并刷新最近会话；
 * - 可选：自动切换到新群（如需）。
 */
const handleCreateGroupSubmit = async () => {
  try {
    await createGroupFormRef.value?.validate?.()
    const payload = { groupName: createGroupForm.value.groupName.trim(), description: (createGroupForm.value.description || '').trim() }
    const memberIds = createGroupForm.value.memberIds
    const res = await createChatGroupService(payload, memberIds)
    if (res && (res.code === 0 || res.success)) {
      ElMessage.success('创建群聊成功')
      createGroupVisible.value = false
      const g = res.data || {}
      // 自动切换到新群聊会话并加载历史
      if (g && g.id) {
        const p = openConversation({ chat_type: 'group', group_id: g.id, name: g.groupName, avatar: g.groupAvatar })
        ElMessage.info('已为你打开新群聊')
        // 等待群聊历史加载与已读完成后，自动发送一条群欢迎消息
        try {
          await (p || Promise.resolve())
          const me = userStore.userInfo?.id
          if (me) {
            // 发送一条系统欢迎语（以普通文本消息发送）
            const welcome = `欢迎加入「${g.groupName || '新群聊'}」\n可以开始聊天啦～`
            // sendChatMessageService 支持群聊：需传 groupId、chatType:'group'
            const payload = { groupId: g.id, chatType: 'group', messageType: 'text', content: welcome }
            const sendRes = await sendChatMessageService(payload)
            if (!(sendRes && sendRes.code === 0)) {
              console.warn('自动发送欢迎消息失败:', sendRes?.message)
            }
          }
        } catch (e) {
          console.warn('等待群聊初始化后发送欢迎消息失败:', e)
        }
      }
      await loadRecentChats()
    } else {
      ElMessage.error(res?.message || '创建群聊失败')
    }
  } catch (err) {
    // 可能是校验错误或接口抛错
    if (err && err.message) return
    console.error('创建群聊异常:', err)
    ElMessage.error('创建群聊失败')
  }
}
</script>

<template>
  <div class="chat-layout">
    <!-- 左侧：最近会话/我的群聊 -->
    <div class="sidebar">
      <div class="sidebar-header tabs">
        <div class="tab" :class="{active: activeListTab==='recent'}" @click="activeListTab='recent'">最近会话</div>
        <div class="tab" :class="{active: activeListTab==='groups'}" @click="activeListTab='groups'">我的群聊</div>
        <div class="header-actions">
          <el-tooltip effect="dark" content="创建群聊" placement="bottom">
            <el-button size="small" circle @click="openCreateGroupDialog">
              <el-icon><Plus /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
      <div v-show="activeListTab==='recent'" class="recent-list" v-loading="loadingRecent">
        <div
          v-for="item in sidebarChats"
          :key="item.chat_type==='announcement' ? 'announcement' : `${item.chat_type}-${item.contact_id || item.group_id}`"
          class="recent-item"
          :class="{ active: (item.chat_type==='announcement' && isAnnouncementActive) || (item.chat_type==='single' && item.contact_id === targetUserId) }"
          @click="openConversation(item)"
        >
          <el-avatar :size="32" :src="item.chat_type==='single' ? (normalizeAttachmentUrl(item.avatar) || '/avatar.jpg') : '/avatar.jpg'">
            {{ (item.name || '群').charAt(0) }}
          </el-avatar>
          <div class="recent-main">
            <div class="top-row">
              <div class="name">{{ item.name || (item.chat_type==='group' ? '群聊' : '用户') }}</div>
              <div class="time">{{ formatTime(item.last_time) }}</div>
            </div>
            <div class="bottom-row">
              <div class="preview">{{ item.last_content || ' ' }}</div>
              <el-badge v-if="(item.chat_type==='announcement' ? unreadAnnouncementCount : (item.unread_count || 0)) > 0" :value="item.chat_type==='announcement' ? unreadAnnouncementCount : item.unread_count" class="unread" />
            </div>
          </div>
          <div class="item-ops" @click.stop>
            <el-dropdown trigger="click">
              <span class="ops-trigger" style="padding:0 4px;">•••</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="deleteRecent(item)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
        <div v-if="!loadingRecent && sidebarChats.length <= 1" class="empty">暂无最近会话</div>
       </div>

      <div v-show="activeListTab==='groups'" class="recent-list" v-loading="loadingGroups">
        <div v-for="g in myGroups" :key="g.id" class="recent-item" @click="openConversation({ chat_type:'group', group_id: g.id, name: g.groupName, avatar: g.groupAvatar })">
          <el-avatar :size="32" :src="normalizeAttachmentUrl(g.groupAvatar) || '/avatar.jpg'">群</el-avatar>
          <div class="recent-main">
            <div class="top-row">
              <div class="name">{{ g.groupName || '群聊' }}</div>
            </div>
            <div class="bottom-row">
              <div class="preview">{{ g.description || '群聊' }}</div>
            </div>
          </div>
        </div>
        <div v-if="!loadingGroups && myGroups.length === 0" class="empty">暂无群聊</div>
      </div>
     </div>

    <!-- 右侧：聊天窗口 -->
    <div class="chat-page">
      <div class="chat-header">
        <div class="peer" v-if="isAnnouncementActive">
          <el-avatar :src="'/avatar.jpg'" :size="36">公</el-avatar>
          <div class="peer-info">
            <div class="name">系统公告</div>
            <div class="desc">系统公告会话</div>
          </div>
        </div>
        <div class="peer" v-else-if="isGroupActive">
          <el-avatar :src="normalizeAttachmentUrl(targetGroupAvatar) || '/avatar.jpg'" :size="36">群</el-avatar>
          <div class="peer-info">
            <div class="name">{{ targetGroupName }}</div>
            <div class="desc">群聊</div>
          </div>
        </div>
        <div class="peer" v-else-if="isValidTarget()">
          <el-avatar :src="normalizeAttachmentUrl(targetAvatar) || '/avatar.jpg'" :size="36">{{ targetName?.charAt?.(0) }}</el-avatar>
          <div class="peer-info">
            <div class="name">{{ targetName }}</div>
            <div class="desc">单聊</div>
          </div>
        </div>
        <div class="peer" v-else>
          <div class="peer-info">
            <div class="name">请选择左侧会话开始聊天</div>
            <div class="desc">支持从通讯录发起或从最近会话进入</div>
          </div>
        </div>
        <div class="actions" v-if="isGroupActive">
          <el-dropdown trigger="click">
            <span class="ops-trigger" style="cursor:pointer;user-select:none;">•••</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openGroupAnnouncement">群公告</el-dropdown-item>
                <el-dropdown-item @click="openMembersDrawer">查看成员</el-dropdown-item>
                <el-dropdown-item v-if="isGroupOwner" divided @click="confirmDisbandGroup">
                  <span style="color:#f56c6c;">解散群聊</span>
                </el-dropdown-item>
                <el-dropdown-item v-else divided @click="confirmLeaveGroup">
                  <span style="color:#f56c6c;">退出群聊</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="isGroupAnnouncementActive" size="small" style="margin-left:8px;" @click="exitGroupAnnouncement">返回</el-button>
        </div>
        <div class="actions" v-else>
          <el-button size="small" @click="router.push('/contacts')">返回通讯录</el-button>
        </div>
      </div>

      <!-- 群公告提醒条（长驻，用户可手动关闭） -->
      <div v-if="isGroupActive && announcementBanner.visible" class="ann-banner">
        <span>有 {{ announcementBanner.count }} 条未读群公告</span>
        <div class="ops">
          <el-button size="small" type="primary" text @click="openGroupAnnouncement">查看</el-button>
          <el-button size="small" text @click="announcementBanner.visible=false">知道了</el-button>
        </div>
      </div>

      <div class="chat-body" ref="listRef">
        <template v-if="isAnnouncementActive">
          <!-- 保持公告块不变 -->
          <div class="announcement-list" v-loading="announcementLoading">
            <div v-if="announcementList.length === 0 && !announcementLoading" class="empty">暂无公告</div>
            <div v-for="a in announcementList" :key="a.id" class="msg announcement">
              <div class="msg-main">
                <!-- 顶部：标题（未读高亮；左侧“标题：”标签） -->
                <div class="msg-header">
                  <div class="msg-title" :class="{ unread: !a.isRead }">
                    <span class="label">标题：</span>
                    <span class="title-text">{{ a.title }}</span>
                  </div>
                </div>
                <!-- 中部：摘要（两行省略；左侧“摘要：”标签；仅对正文做省略） -->
                <div class="msg-content">
                  <div class="summary">
                    <span class="label">摘要：</span>
                    <span class="summary-text" :title="a.summary">{{ a.summary }}</span>
                  </div>
                </div>
                <!-- 底部：左侧时间与发布人；右侧按钮 -->
                <div class="msg-footer">
                  <div class="left-meta">
                    <span class="time">发布时间：{{ formatDateTime(a.publishTime) }}</span>
                    <span class="dot">·</span>
                    <span class="publisher">发布人：{{ a.publisher }}</span>
                  </div>
                  <div class="msg-actions">
                    <el-button type="primary" size="small" @click="router.push({ path: '/announcement', query: { id: a.id, fromChat: 1 } })">查看详情</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
        
        <!-- 群公告视图（复用公告样式与卡片渲染） -->
        <template v-else-if="isGroupAnnouncementActive">
          <div class="announcement-list" v-loading="groupAnnouncementLoading">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
              <div style="font-weight:600;">群公告</div>
              <el-button v-if="isGroupOwner" type="primary" size="small" @click="openPublishGroupAnnouncementDialog">发布群公告</el-button>
            </div>
            <div v-if="groupAnnouncements.length === 0 && !groupAnnouncementLoading" class="empty">暂无群公告</div>
            <div v-for="a in groupAnnouncements" :key="a.id" class="msg announcement">
              <div class="msg-main">
                <div class="msg-header">
                  <div class="msg-title">
                    <span class="label">标题：</span>
                    <span class="title-text">{{ a.title }}</span>
                  </div>
                </div>
                <div class="msg-content">
                  <div class="summary">
                    <span class="label">摘要：</span>
                    <span class="summary-text" :title="a.summary">{{ a.summary }}</span>
                  </div>
                </div>
                <div class="msg-footer">
                  <div class="left-meta">
                    <span class="time">发布时间：{{ formatDateTime(a.publishTime) }}</span>
                    <span class="dot">·</span>
                    <span class="publisher">发布人：{{ a.publisher }}</span>
                  </div>
                  <div class="msg-actions">
                    <el-button type="primary" size="small" @click="openGroupAnnouncementDetail(a)">查看详情</el-button>
                    <el-button v-if="isGroupOwner" type="danger" size="small" @click="withdrawGroupAnn(a)" style="margin-left:8px;">撤回</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="isGroupActive">
          <!-- 群聊历史：支持“加载更多”与“没有更多”提示 -->
          <div class="load-more" v-if="HISTORY_ENABLED && hasMore">
            <el-button size="small" :loading="loadingMore" @click="loadMoreGroupHistory">加载更多</el-button>
          </div>
          <div class="load-more no-more" v-else-if="HISTORY_ENABLED && messages.length > 0">没有更多消息了</div>
          <div v-for="msg in messages" :key="msg.id || msg.createTime" class="msg" :class="{ me: !msg.isRecalled && msg.senderId === userStore.userInfo?.id }">
            <!-- 群聊系统提示行 -->
            <template v-if="msg.sysTip">
              <div class="sys-tip">{{ msg.sysText || '系统提示' }}</div>
            </template>
            <template v-else>
            <template v-if="msg.isRecalled">
               <div class="sys-tip">{{ msg.senderId === userStore.userInfo?.id ? '你撤回了一条消息' : ((msg.senderName || ('用户' + msg.senderId)) + '撤回了一条消息') }}</div>
              </template>
            <template v-else>
              <!-- 自己发送的群聊消息 -->
              <template v-if="msg.senderId === userStore.userInfo?.id">
                <el-avatar :src="normalizeAttachmentUrl(userStore.userInfo?.userPic) || '/avatar.jpg'" :size="28">我</el-avatar>
                <div class="bubble">
                  <template v-if="msg.messageType === 'image'">
                    <img :src="normalizeAttachmentUrl(JSON.parse(msg.content).url)" class="img-attach" @dblclick="openImageViewer(normalizeAttachmentUrl(JSON.parse(msg.content).url))" />
                  </template>
                  <template v-else-if="msg.messageType === 'file'">
                    <a class="file-attach" :href="normalizeAttachmentUrl(JSON.parse(msg.content).url)" target="_blank" :download="JSON.parse(msg.content).name">
                      <el-icon style="margin-right:6px;"><Document /></el-icon>
                      <span class="name">{{ JSON.parse(msg.content).name }}</span>
                      <span class="size">{{ (JSON.parse(msg.content).size/1024).toFixed(1) }}KB</span>
                    </a>
                  </template>
                  <template v-else>
                    <div class="content">{{ wrapTextByLength(msg.content, 16) }}</div>
                  </template>
                  <div class="meta"><span v-if="msg.isEdited" class="edited">已编辑</span></div>
                </div>
                <!-- 群聊：自己的消息操作菜单（撤回/删除） -->
                <el-dropdown v-if="!msg.isRecalled" class="msg-ops" trigger="click">
                  <span class="ops-trigger">•••</span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="handleRecallMessage(msg)">撤回</el-dropdown-item>
                      <el-dropdown-item divided @click="handleDeleteMessage(msg)"><span style="color:#f56c6c;">删除</span></el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
              <!-- 他人发送的群聊消息 -->
              <template v-else>
                <el-avatar :src="normalizeAttachmentUrl(msg.senderAvatar) || '/avatar.jpg'" :size="28">{{ (msg.senderName || '群')?.charAt?.(0) || '群' }}</el-avatar>
                <div class="msg-right">
                  <div class="sender-name">{{ msg.senderName || ('用户' + msg.senderId) }}</div>
                  <div class="bubble">
                    <template v-if="msg.messageType === 'image'">
                      <img :src="normalizeAttachmentUrl(JSON.parse(msg.content).url)" class="img-attach" @dblclick="openImageViewer(normalizeAttachmentUrl(JSON.parse(msg.content).url))" />
                    </template>
                    <template v-else-if="msg.messageType === 'file'">
                      <a class="file-attach" :href="normalizeAttachmentUrl(JSON.parse(msg.content).url)" target="_blank" :download="JSON.parse(msg.content).name">
                        <el-icon style="margin-right:6px;"><Document /></el-icon>
                        <span class="name">{{ JSON.parse(msg.content).name }}</span>
                        <span class="size">{{ (JSON.parse(msg.content).size/1024).toFixed(1) }}KB</span>
                      </a>
                    </template>
                    <template v-else>
                      <div class="content">{{ wrapTextByLength(msg.content, 16) }}</div>
                    </template>
                    <div class="meta"><span v-if="msg.isEdited" class="edited">已编辑</span></div>
                  </div>
                </div>
              </template>
            </template>
            <!-- 关闭外层 v-else 包裹 -->
            </template>
          </div>
          <div v-if="messages.length === 0" class="placeholder">暂无群聊消息</div>
        </template>
        <template v-else-if="isValidTarget()">
          <!-- 单聊渲染：保持原有实现 -->
          <div class="load-more" v-if="HISTORY_ENABLED && hasMore">
  <el-button
    size="small"
    :loading="loadingMore"
    @click="isGroupActive ? loadMoreGroupHistory() : loadMoreHistory()"
  >加载更多</el-button>
</div>
          <div class="load-more no-more" v-else-if="HISTORY_ENABLED && messages.length > 0">没有更多消息了</div>
 
           <div v-for="msg in messages" :key="msg.id || msg.createTime" class="msg" :class="{ me: !msg.isRecalled && msg.senderId === userStore.userInfo?.id }">
             <!-- 撤回消息：以系统提示形式居中显示，不露出原内容 -->
             <template v-if="msg.isRecalled">
              <div class="sys-tip">{{ msg.senderId === userStore.userInfo?.id ? '你撤回了一条消息' : ((msg.senderName || ('用户' + msg.senderId)) + '撤回了一条消息') }}</div>
            </template>
             <!-- 普通消息渲染 -->
            <template v-else>
              <!-- 自己发送的消息：头像在右侧（通过 row-reverse + DOM 顺序） -->
              <template v-if="msg.senderId === userStore.userInfo?.id">
                <el-avatar :src="normalizeAttachmentUrl(userStore.userInfo?.userPic) || '/avatar.jpg'" :size="28">我</el-avatar>
                <div class="bubble">
                  <template v-if="msg.messageType === 'image'">
                    <img :src="normalizeAttachmentUrl(JSON.parse(msg.content).url)" class="img-attach" @dblclick="openImageViewer(normalizeAttachmentUrl(JSON.parse(msg.content).url))" />
                  </template>
                  <template v-else-if="msg.messageType === 'file'">
                    <a class="file-attach" :href="normalizeAttachmentUrl(JSON.parse(msg.content).url)" target="_blank" :download="JSON.parse(msg.content).name">
                      <el-icon style="margin-right:6px;"><Document /></el-icon>
                      <span class="name">{{ JSON.parse(msg.content).name }}</span>
                      <span class="size">{{ (JSON.parse(msg.content).size/1024).toFixed(1) }}KB</span>
                    </a>
                  </template>
                  <template v-else>
                    <div class="content">{{ wrapTextByLength(msg.content, 16) }}</div>
                  </template>
                  <div class="meta">
                    <span v-if="msg.isEdited" class="edited">已编辑</span>
                    <span v-if="!isGroupActive && msg.senderId === userStore.userInfo?.id && msg.isRead" class="read-flag">已读</span>
                  </div>
                </div>
                <!-- 自己消息的操作菜单 -->
                <el-dropdown v-if="!msg.isRecalled" class="msg-ops" trigger="click">
                  <span class="ops-trigger">•••</span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="handleEditMessage(msg)">编辑</el-dropdown-item>
                      <el-dropdown-item @click="handleRecallMessage(msg)">撤回</el-dropdown-item>
                      <el-dropdown-item divided @click="handleDeleteMessage(msg)"><span style="color:#f56c6c;">删除</span></el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>

              <!-- 对方发送的消息：头像在左侧保持不变 -->
              <template v-else>
                <el-avatar :src="normalizeAttachmentUrl(targetAvatar) || '/avatar.jpg'" :size="28">{{ targetName?.charAt?.(0) }}</el-avatar>
                <div class="bubble">
                  <template v-if="msg.messageType === 'image'">
                    <img :src="normalizeAttachmentUrl(JSON.parse(msg.content).url)" class="img-attach" @dblclick="openImageViewer(normalizeAttachmentUrl(JSON.parse(msg.content).url))" />
                  </template>
                  <template v-else-if="msg.messageType === 'file'">
                    <a class="file-attach" :href="normalizeAttachmentUrl(JSON.parse(msg.content).url)" target="_blank" :download="JSON.parse(msg.content).name">
                      <el-icon style="margin-right:6px;"><Document /></el-icon>
                      <span class="name">{{ JSON.parse(msg.content).name }}</span>
                      <span class="size">{{ (JSON.parse(msg.content).size/1024).toFixed(1) }}KB</span>
                    </a>
                  </template>
                  <template v-else>
                    <div class="content">{{ wrapTextByLength(msg.content, 16) }}</div>
                  </template>
                  <div class="meta">
                    <span v-if="msg.isEdited" class="edited">已编辑</span>
                  </div>
                </div>
              </template>
            </template>
          </div>
        </template>
        <template v-else>
          <div class="placeholder">
            <el-icon style="font-size:48px;color:#c0c4cc;margin-bottom:12px;"><ChatDotRound /></el-icon>
            <div>从左侧选择一个会话，或前往通讯录选择联系人发起聊天</div>
          </div>
        </template>
      </div>

      <template v-if="isGroupActive">
        <!-- 群聊输入区：与单聊一致，支持文本/图片/文件发送 -->
        <div class="chat-input">
          <el-input v-model="inputText" placeholder="按 Enter 发送" @keyup.enter="sendText" clearable />
          <div class="attach-buttons">
            <el-button @click="chooseImage" :disabled="uploading">图片</el-button>
            <el-button @click="chooseFile" :disabled="uploading">文件</el-button>
          </div>
          <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="handleImageChange" />
          <input ref="fileInputRef" type="file" style="display:none" @change="handleFileChange" />
          <el-button type="primary" @click="sendText">发送</el-button>
        </div>
        <div class="upload-progress" v-if="uploading">
          <el-progress :percentage="uploadPercent" style="flex:1;" />
          <el-button type="danger" size="small" @click="cancelUpload">取消上传</el-button>
        </div>
      </template>
      <template v-else-if="isValidTarget()">
        <!-- 单聊输入区：保持原有实现 -->
        <div class="chat-input">
          <el-input v-model="inputText" placeholder="按 Enter 发送" @keyup.enter="sendText" clearable />
          <div class="attach-buttons">
            <el-button @click="chooseImage" :disabled="uploading">图片</el-button>
            <el-button @click="chooseFile" :disabled="uploading">文件</el-button>
          </div>
          <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="handleImageChange" />
          <input ref="fileInputRef" type="file" style="display:none" @change="handleFileChange" />
          <el-button type="primary" @click="sendText">发送</el-button>
        </div>
        <div class="upload-progress" v-if="uploading">
          <el-progress :percentage="uploadPercent" style="flex:1;" />
          <el-button type="danger" size="small" @click="cancelUpload">取消上传</el-button>
        </div>
      </template>
      
       <div class="chat-input disabled" v-else-if="isAnnouncementActive || isGroupAnnouncementActive">
        <el-input disabled placeholder="公告会话不可发送消息，请点击列表项查看详情" />
        <el-button disabled>发送</el-button>
      </div>
       <div class="chat-input disabled" v-else>
         <el-input disabled placeholder="请选择左侧会话后输入消息" />
         <el-button disabled>发送</el-button>
       </div>
    </div>
  </div>

  <!-- 图片查看器（双击图片打开） -->
  <el-image-viewer
    v-if="imageViewer.visible"
    :url-list="imageViewer.list"
    :initial-index="imageViewer.index"
    @close="imageViewer.visible=false"
  />

  <!-- 群公告详情弹窗 -->
  <el-dialog v-model="groupAnnDetail.visible" :title="groupAnnDetail.data?.title || '群公告'" width="560px">
    <div style="white-space:pre-wrap;line-height:1.6; margin-bottom:12px;">{{ groupAnnDetail.data?.content }}</div>
    <div v-if="isGroupOwner" style="display:flex; justify-content:space-between; align-items:center;">
      <div style="color:#909399;">已读 {{ groupAnnDetail.readers.length }}/{{ groupAnnDetail.totalMembers || '-' }}</div>
      <div><el-button size="small" @click="groupAnnDetail.readersVisible = true">查看已读名单</el-button></div>
    </div>
    <template #footer>
      <el-button @click="groupAnnDetail.visible=false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 已读名单弹窗 -->
  <el-dialog v-model="groupAnnDetail.readersVisible" title="已读名单" width="420px">
    <div v-loading="groupAnnDetail.readersLoading">
      <div v-if="groupAnnDetail.readers.length === 0" style="text-align:center;color:#c0c4cc;">暂无已读成员</div>
      <div v-for="u in groupAnnDetail.readers" :key="u.id" style="display:flex;align-items:center;gap:10px;padding:6px 0;border-bottom:1px solid #f2f2f2;">
        <el-avatar :size="28" :src="normalizeAttachmentUrl(u.avatar)||'/avatar.jpg'">{{ (u.name||'用').charAt(0) }}</el-avatar>
        <div style="font-size:14px;">{{ u.name }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="groupAnnDetail.readersVisible=false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 发布群公告弹窗（标题+内容一次填写） -->
  <el-dialog v-model="publishAnn.visible" title="发布群公告" width="520px">
    <el-form label-width="68px">
      <el-form-item label="标题">
        <el-input v-model="publishAnn.title" placeholder="请输入公告标题" />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="publishAnn.content" type="textarea" :rows="6" placeholder="请输入群公告内容（支持多行）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="publishAnn.visible=false">取消</el-button>
      <el-button type="primary" @click="submitPublishGroupAnnouncement">发布</el-button>
    </template>
  </el-dialog>

  <!-- 创建群聊弹窗（表单 + 联系人加载） -->
  <el-dialog v-model="createGroupVisible" title="创建群聊" width="520px" @open="onCreateGroupDialogOpen" @closed="onCreateGroupDialogClosed">
    <el-form ref="createGroupFormRef" :model="createGroupForm" :rules="createGroupRules" label-width="88px">
      <el-form-item label="群名称" prop="groupName">
        <el-input v-model="createGroupForm.groupName" maxlength="20" show-word-limit placeholder="请输入群名称（1-20个字符）" />
      </el-form-item>
      <el-form-item label="群描述">
        <el-input v-model="createGroupForm.description" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="可填写群用途或说明（最多100字）" />
      </el-form-item>
      <el-form-item label="群成员" prop="memberIds">
        <el-select v-model="createGroupForm.memberIds" multiple filterable clearable :loading="contactsLoading" placeholder="请选择群成员（至少2人）" style="width:100%;">
          <el-option v-for="c in contactOptions" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <el-button text type="primary" style="margin-left:8px;" :loading="contactsLoading" @click="loadCreateGroupContacts">刷新</el-button>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createGroupVisible=false">取消</el-button>
      <el-button type="primary" @click="handleCreateGroupSubmit">创建</el-button>
    </template>
  </el-dialog>

  <!-- 群成员抽屉（占位：第二步接入数据与操作） -->
  <el-drawer v-model="membersDrawerVisible" title="群成员" size="380px">
    <div style="margin-bottom:8px; display:flex; gap:8px; align-items:center;">
      <el-input v-model="memberKeyword" placeholder="搜索成员昵称/用户名" size="small" clearable style="flex:1;" />
      <el-button size="small" @click="loadGroupRoleAndMembers" :loading="membersLoading">刷新</el-button>
    </div>
    <div v-loading="membersLoading">
      <div v-if="filteredMembers.length === 0" style="color:#c0c4cc; text-align:center; padding:12px 0;">暂无成员</div>
      <div v-for="m in filteredMembers" :key="m.id" style="display:flex; align-items:center; justify-content:space-between; padding:8px 6px; border-bottom:1px solid #f2f2f2;">
        <div style="display:flex; align-items:center; gap:10px;">
          <el-avatar :size="28" :src="normalizeAttachmentUrl(m.userPic) || '/avatar.jpg'">{{ (m.nickname || m.realname || m.username || '用').charAt(0) }}</el-avatar>
          <div>
            <div style="font-weight:600;">{{ m.nickname || m.realname || m.username || ('用户' + m.id) }}</div>
            <div style="font-size:12px; color:#909399;">ID: {{ m.id }}</div>
          </div>
        </div>
        <div>
          <el-button v-if="isGroupOwner && m.id !== userStore.userInfo?.id" type="danger" text @click="removeMember(m.id, m.nickname || m.realname || m.username)">移出</el-button>
          <el-tag v-else-if="m.id === userStore.userInfo?.id" size="small">我</el-tag>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="openInviteDialog">邀请成员</el-button>
      <el-button v-if="isGroupOwner" type="danger" @click="confirmDisbandGroup">解散群聊</el-button>
      <el-button v-else type="danger" @click="confirmLeaveGroup">退出群聊</el-button>
    </template>
  </el-drawer>

  <!-- 邀请成员弹窗 -->
  <el-dialog v-model="inviteVisible" title="邀请成员加入群聊" width="520px" @open="loadInviteContacts">
    <div>
      <el-select v-model="inviteSelected" multiple filterable clearable :loading="inviteLoading" placeholder="请选择联系人" style="width:100%">
        <el-option v-for="opt in inviteOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </div>
    <template #footer>
      <el-button @click="inviteVisible=false">取消</el-button>
      <el-button type="primary" :loading="inviteLoading" @click="submitInvite">邀请</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 布局 */
.chat-layout { display: flex; height: 100%; background: #f6f7f9; }
.sidebar { width: 280px; background: #ffffff; border-right: 1px solid #eee; display: flex; flex-direction: column; }
.sidebar-header { height: 48px; display: flex; align-items: center; padding: 0 12px; font-weight: 600; border-bottom: 1px solid #eee; }
.sidebar-header.tabs { gap: 12px; }
.sidebar-header .tab { padding: 6px 8px; border-radius: 6px; cursor: pointer; color: #606266; }
.sidebar-header .tab.active { background: #e8f4ff; color: #303133; }
.recent-list { flex: 1; overflow-y: auto; padding: 8px; }
.recent-item { display: flex; gap: 10px; padding: 8px; border-radius: 8px; cursor: pointer; align-items: center; }
.recent-item:hover { background: #f5f7fa; }
.recent-item.active { background: #e8f4ff; }
.recent-item .item-ops { margin-left: auto; color: #909399; }
.recent-main { flex: 1; min-width: 0; }
.top-row { display: flex; justify-content: space-between; align-items: center; }
.name { font-weight: 600; color: #303133; }
.time { font-size: 12px; color: #909399; }
.bottom-row { display: flex; justify-content: space-between; align-items: center; gap: 6px; }
.preview { flex: 1; min-width: 0; font-size: 13px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.unread .el-badge__content { background: #f56c6c; }
.empty { text-align: center; color: #c0c4cc; padding: 24px 0; }

/* 右侧聊天区原有样式 */
.chat-page { display: flex; flex-direction: column; flex: 1; height: 100%; }
.chat-header { height: 56px; background: #fff; border-bottom: 1px solid #eee; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; }
/* 顶部公告提醒条 */
.ann-banner { background:#fff7e6; border:1px solid #ffe7ba; color:#ad6800; margin:8px 16px 0; padding:6px 10px; border-radius:6px; display:flex; justify-content:space-between; align-items:center; }
.ann-banner .ops .el-button { margin-left:6px; }
.peer { display: flex; align-items: center; gap: 10px; }
.peer-info .name { font-weight: 600; }
.peer-info .desc { font-size: 12px; color: #909399; }
.chat-body { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.msg { display: flex; align-items: flex-end; gap: 8px; }
.msg.me { flex-direction: row-reverse; }
.bubble { max-width: 60%; background: #fff; padding: 8px 12px; border-radius: 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
.msg.me .bubble { background: #e8f4ff; }
/* 新增：群聊他人消息昵称位置调整 */
.msg-right { display: flex; flex-direction: column; align-items: flex-start; }
.sender-name { font-size: 12px; color: #909399; margin-left: 2px; margin-bottom: 2px; line-height: 1; }
.content { white-space: pre-wrap; word-break: break-word; overflow-wrap: break-word; }
.meta { font-size: 11px; color: #a0a0a0; margin-top: 4px; display: flex; gap: 8px; }
.edited { color: #67c23a; }
.recalled { color: #f56c6c; }
.sys-tip { align-self: center; background: #f0f2f5; color: #909399; font-size: 12px; padding: 4px 8px; border-radius: 4px; margin: 6px 0; }
 .chat-input { background: #fff; border-top: 1px solid #eee; padding: 10px; display: flex; gap: 10px; }
.chat-input.disabled { opacity: 0.7; }
.msg-ops { color: #909399; cursor: pointer; }
.msg .msg-ops { margin: 0 4px; }
.msg-ops .ops-trigger { padding: 0 4px; user-select: none; }
.placeholder { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #909399; }

/***** 新增：加载更多样式 *****/
.load-more { text-align: center; color: #909399; font-size: 13px; padding: 6px 0; }
.load-more .el-button { --el-button-size: 24px; }
.load-more.no-more { color: #c0c4cc; }
/* 公告视图样式：卡片布局（标题上、摘要中、时间/发布人与按钮下） */
.announcement-list .msg { display: flex; margin-bottom: 12px; }
.announcement-list .msg:last-child { margin-bottom: 0; }
.announcement-list .msg .msg-main { width: 420px; max-width: 70%; background: #fff; padding: 10px 12px; border-radius: 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
.announcement-list .msg .msg-header { margin-bottom: 6px; }
.announcement-list .msg .msg-title { font-weight: 600; color: #303133; white-space: normal; word-break: break-word; display: flex; align-items: flex-start; }
.announcement-list .msg .msg-title.unread { color: #f56c6c; }
.announcement-list .msg .msg-title .label { color: #909399; font-weight: 500; margin-right: 4px; flex: 0 0 auto; }
.announcement-list .msg .msg-title .title-text { flex: 1 1 auto; }

.announcement-list .msg .msg-content { margin-bottom: 8px; }
.announcement-list .msg .summary { color: #606266; white-space: normal; word-break: break-word; line-height: 1.6; display: flex; align-items: flex-start; }
.announcement-list .msg .summary .label { color: #909399; margin-right: 4px; flex: 0 0 auto; }
.announcement-list .msg .summary .summary-text { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; text-overflow: ellipsis; max-height: calc(1.6em * 2); white-space: normal; word-break: break-word; flex: 1 1 auto; }
.announcement-list .msg .msg-footer { display: flex; justify-content: space-between; align-items: center; }
.announcement-list .msg .left-meta { font-size: 12px; color: #909399; display: inline-flex; align-items: center; gap: 6px; }
.announcement-list .msg .left-meta .time { white-space: nowrap; }
.announcement-list .msg .left-meta .dot { color: #c0c4cc; }
.announcement-list .msg .msg-actions { text-align: right; }
.announcement-list .msg .msg-meta { font-size: 12px; color: #909399; margin-bottom: 6px; }
.announcement-list .msg .msg-meta .time { white-space: nowrap; }
.img-attach { max-width: 220px; border-radius: 6px; cursor: zoom-in; display:block; }
.file-attach { display: inline-flex; align-items: center; gap: 6px; color: #409eff; text-decoration: none; }
.file-attach .name { max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.file-attach .size { color: #909399; font-size: 12px; margin-left: 6px; }
.attach-buttons { display:flex; align-items:center; gap:8px; }
.upload-progress { display:flex; align-items:center; gap:10px; background:#fff; border-top:1px solid #eee; padding:8px 10px; }
/* 调整最近会话头部：标题-按钮两端对齐 */
.sidebar-header { justify-content: space-between; }
</style>