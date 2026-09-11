<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getAiStatus, streamAiChat, cancelAiChat, getAnnouncementSource, knowledgeIndex, documentRequest, groupSummaryRead } from '@/api/ai'
import KnowledgeDocuments from '@/components/KnowledgeDocuments.vue'
import DocumentVectorIndex from '@/components/DocumentVectorIndex.vue'
import GroupSummaryControls from '@/components/GroupSummaryControls.vue'
import AiModelConfig from '@/components/AiModelConfig.vue'

const status = ref(null)
const messages = ref([])
const input = ref('')
const busy = ref(false)
const error = ref('')
const transcript = ref(null)
const mode = ref('general')
const documentMode = computed(() => mode.value === 'documents' || mode.value === 'documents-vector')
let summaryOptions = {}
function generateSummary(options) { summaryOptions = options; input.value = `总结群聊 #${options.groupId}（${options.start} 至 ${options.end}，北京时间）`; send() }
const sourceDialog = ref(false)
const sourceDetail = ref(null)
const sourceError = ref('')
let sourceController
const indexJob = ref(null)
const indexStarting = ref(false)
let pollTimer
let disposed = false
let controller
const stopping = ref(false)
let ready
let resolveReady
let stopTask
const statusController = new AbortController()

async function refreshStatus() {
  try { status.value = await getAiStatus(statusController.signal); error.value = '' }
  catch (e) { if (e.name !== 'AbortError') error.value = e.message }
}

onMounted(async () => {
  try {
    status.value = await getAiStatus(statusController.signal)
    if (status.value.vectorEnabled) await pollIndex()
  }
  catch (e) { if (e.name !== 'AbortError') error.value = e.message }
})
onBeforeUnmount(() => { disposed = true; clearTimeout(pollTimer); if (busy.value) stop(); statusController.abort(); sourceController?.abort() })

async function pollIndex() {
  try {
    indexJob.value = await knowledgeIndex('GET', statusController.signal)
    if (!disposed && indexJob.value.state === 'running') pollTimer = setTimeout(pollIndex, 2000)
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
}

async function syncIndex() {
  error.value = ''
  indexStarting.value = true
  clearTimeout(pollTimer)
  try {
    indexJob.value = await knowledgeIndex('POST', statusController.signal)
    if (!disposed) pollTimer = setTimeout(pollIndex, 1000)
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { indexStarting.value = false }
}

async function openSource(source) {
  sourceController?.abort()
  sourceController = new AbortController()
  sourceDetail.value = null
  sourceError.value = ''
  sourceDialog.value = true
  try { sourceDetail.value = source.sourceType === 'group-message'
    ? await groupSummaryRead(source.groupId, source.id, sourceController.signal)
    : source.sourceType === 'document'
    ? await documentRequest('GET', source.announcementId, null, sourceController.signal)
    : await getAnnouncementSource(source.announcementId, sourceController.signal) }
  catch (e) { if (e.name !== 'AbortError') sourceError.value = e.message }
}

function changeMode() {
  messages.value = []
  error.value = ''
}

function stop() {
  if (!busy.value || stopping.value) return
  stopping.value = true
  const currentController = controller
  stopTask = (async () => {
    try {
      // Wait for the server to register this generation before cancelling it.
      const requestId = await ready
      if (requestId) await cancelAiChat(requestId)
    } catch (e) { error.value = e.message }
    finally { currentController.abort() }
  })()
}

async function send() {
  const prompt = input.value.trim()
  if (!prompt || busy.value || !status.value?.configured || status.value?.protocolVersion !== 2) return
  const history = []
  // Only successful complete pairs are context; interrupted answers must not become facts.
  for (let i = 0; i + 1 < messages.value.length; i += 2) {
    if (mode.value === 'general' && messages.value[i + 1].state === 'done' && messages.value[i + 1].content.length <= 4000) {
      history.push(...messages.value.slice(i, i + 2))
    }
  }
  history.push({ role: 'user', content: prompt })
  while (history.length > 3  || history.reduce((n, m) => n + m.content.length, 0) > 16000) history.splice(0, 2)
  messages.value.push({ role: 'user', content: prompt }, { role: 'assistant', content: '', state: 'generating' })
  const answer = messages.value[messages.value.length - 1]
  input.value = ''
  error.value = ''
  busy.value = true
  controller = new AbortController()
  stopping.value = false
  stopTask = null
  ready = new Promise(resolve => { resolveReady = resolve })
  try {
    await streamAiChat(history.map(({ role, content }) => ({ role, content })), controller.signal, (event, data) => {
      if (event === 'ready') resolveReady(data.requestId)
      if (event === 'sources') { answer.sources = data.items; answer.retrieval = data }
      if (event === 'summary') answer.summary = data
      if (event === 'delta') answer.content += data.text
      if (event === 'done') {
        answer.state = data.finishReason === 'stop' ? 'done' : 'truncated'
        answer.stats = data
      }
      if (event === 'error') throw new Error(data.message)
      nextTick(() => { if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight })
    }, mode.value, summaryOptions)
  } catch (e) {
    answer.state = stopping.value || e.name === 'AbortError' ? 'stopped' : 'error'
    if (!stopping.value && e.name !== 'AbortError') error.value = e.message
  } finally {
    resolveReady(null)
    await stopTask
    busy.value = false
    stopping.value = false
    controller = null
  }
}

function retry() {
  const last = messages.value[messages.value.length - 2]
  if (last) { input.value = last.content; messages.value.splice(-2); send() }
}
const stateLabel = { generating: '正在生成…', stopped: '已停止，回答可能不完整', error: '生成失败', truncated: '达到输出限制，回答可能不完整' }
</script>

<template>
  <section class="ai-assistant">
    <header>
      <div><h1>AI 助手</h1><p>协助整理思路、撰写文本和解答问题</p></div>
      <div><AiModelConfig :disabled="busy" @changed="refreshStatus" />
      <el-button :disabled="busy || !messages.length" @click="messages = []; error = ''">新对话</el-button></div>
    </header>
    <el-radio-group v-model="mode" :disabled="busy" @change="changeMode" aria-label="问答模式">
      <el-radio-button label="general">通用问答</el-radio-button>
      <el-radio-button label="announcements">公告问答</el-radio-button>
      <el-radio-button label="announcements-vector" :disabled="!status?.vectorEnabled">公告·语义检索</el-radio-button>
      <el-radio-button label="documents" :disabled="!status?.documentsEnabled">个人文档问答</el-radio-button>
      <el-radio-button label="documents-vector" :disabled="!status?.documentVectorEnabled">文档·语义检索</el-radio-button>
      <el-radio-button label="group-summary" :disabled="!status?.groupSummaryEnabled">群聊摘要</el-radio-button>
    </el-radio-group>
    <el-alert :closable="false" type="info" :title="mode === 'group-summary'
      ? '仅总结你当前有权查看的群消息副本；所选文本将发送给 DeepSeek。请勿选择敏感聊天。摘要完整校验后展示；行动项仅供核对，不自动创建任务或通知他人。'
      : mode === 'documents-vector'
      ? '文档语义检索使用本机 BGE-M3 + PGVector，仅读取自己的有效文档。上传后请同步索引；问题和命中片段会发送给 DeepSeek，每次独立检索。'
      : mode === 'documents'
      ? '个人文档问答使用关键词检索，仅读取你上传的有效文档。每次独立检索，请写出完整问题；也可切换文档语义检索。'
      : mode !== 'general'
      ? '公告问答使用你有权查看的已发布公告；问题和检索片段将发送给 DeepSeek。每次独立检索，请写出完整问题。'
      : '通用问答不读取企业资料。对话会发送给 DeepSeek，请勿输入密钥或敏感资料；刷新页面后对话清空。'" />
    <el-alert v-if="status && status.protocolVersion !== 2" :closable="false" type="warning" title="后端仍是旧版本。请重启后端后刷新页面，加载公告问答和取消生成接口。" />
    <small v-if="status && !status.documentsEnabled">个人文档功能需要新版后端及文档表迁移，升级后请刷新页面。</small>
    <KnowledgeDocuments v-if="documentMode" :disabled="busy" @source="openSource" />
    <DocumentVectorIndex v-if="mode === 'documents-vector'" :disabled="busy" />
    <GroupSummaryControls v-if="mode === 'group-summary'" :disabled="busy || !status?.configured" @generate="generateSummary" />
    <small v-if="status && !status.groupSummaryEnabled">群聊摘要需要新版后端，重启后请刷新页面。</small>
    <small v-if="status?.documentsEnabled && status?.vectorEnabled && !status?.documentVectorEnabled">文档语义检索需要新版后端，重启后请刷新页面。</small>
    <div v-if="mode === 'announcements-vector'" class="index-panel">
      <el-button :disabled="busy || indexStarting || indexJob?.state === 'running'" @click="syncIndex">同步可见公告索引</el-button>
      <small>{{ indexJob?.message || '首次使用、公告变更后请同步。仅处理当前可见的最新 200 篇公告。' }}</small>
      <small v-if="indexJob?.total != null">进度 {{ indexJob.processed }}/{{ indexJob.total }} · 复用 {{ indexJob.reused }} 篇</small>
    </div>
    <small v-if="status && !status.vectorEnabled">语义检索未启用：需要本机 Ollama、BGE-M3、PGVector，并设置 AI_RAG_ENABLED=true 后重启。</small>
    <el-alert v-if="status && !status.configured" :closable="false" type="warning" title="请点击右上角“模型配置”，保存你自己的 DeepSeek API Key。" />
    <div ref="transcript" class="transcript" role="log" aria-label="AI 对话记录" aria-live="polite">
      <div v-if="!messages.length" class="empty"><h2>{{ mode === 'group-summary' ? '选择群聊与时间范围生成摘要' : documentMode ? '从个人文档中寻找依据' : mode !== 'general' ? '从企业公告中寻找依据' : '有什么需要一起梳理的？' }}</h2>
        <p>{{ mode === 'group-summary' ? '摘要保留原消息引用，负责人和截止时间未明确时不猜测。' : documentMode ? '先上传文档；语义模式需同步索引，之后可尝试不同措辞提问。' : mode !== 'general' ? '例如：年假申请需要提前多久？也可切换语义检索，尝试不同说法。' : '例如：帮我拟一份项目周报提纲，包含进展、风险和下周计划。' }}</p>
      </div>
      <article v-for="(message, index) in messages" :key="index" :class="message.role">
        <strong>{{ message.role === 'user' ? '你' : 'AI 助手' }}</strong>
        <div class="content">{{ message.content }}</div>
        <div v-if="message.summary" class="sources">
          <small>纳入 {{ message.summary.sources.length }} 条文本消息。{{ message.summary.limited ? '范围受限：部分消息因条数或长度限制未纳入。' : '' }}</small>
          <h3>摘要要点</h3>
          <p v-if="!message.summary.points.length">暂无要点</p>
          <div v-for="(point, p) in message.summary.points" :key="p" class="source-card">
            <p>{{ point.text }}</p>
            <el-button v-for="id in point.evidenceIds" :key="id" link type="primary" @click="openSource({ sourceType: 'group-message', groupId: message.summary.groupId, id })">消息 #{{ id }}</el-button>
          </div>
          <h3>行动项（待人工核对）</h3>
          <p v-if="!message.summary.actions.length">未提取到明确行动项</p>
          <div v-for="(action, a) in message.summary.actions" :key="a" class="source-card">
            <p>{{ action.task }}</p><small>负责人原文：{{ action.owner || '未明确' }} · 截止时间原文：{{ action.deadline || '未明确' }}</small>
            <el-button v-for="id in action.evidenceIds" :key="id" link type="primary" @click="openSource({ sourceType: 'group-message', groupId: message.summary.groupId, id })">消息 #{{ id }}</el-button>
          </div>
        </div>
        <small v-if="message.state !== 'done'">{{ stateLabel[message.state] }}</small>
        <small v-if="message.stats">耗时 {{ (message.stats.elapsedMs / 1000).toFixed(1) }} 秒 · Token {{ message.stats.usage?.total_tokens ?? '未返回' }}</small>
        <div v-if="message.retrieval" class="sources">
          <small>{{ message.retrieval.retrieval === 'vector-v1' ? message.retrieval.embeddingModel + ' + PGVector · 有效索引' : '关键词检索 · 检查' }} {{ message.retrieval.scannedDocuments }} 篇{{ message.retrieval.sourceType === 'document' ? '个人文档' : '公告' }} · {{ message.retrieval.retrievalMs }} 毫秒</small>
          <small>{{ message.retrieval.sourceType === 'document' ? '范围：自己的最多 50 篇有效文档；每篇最多 20000 字符。' : '范围：最新 200 篇可见公告，每篇前 20000 字符。' }}提供最相关的至多 4 个片段。{{ message.retrieval.limited ? '检索范围受限，可能有资料未纳入。' : '' }}</small>
          <strong v-if="message.sources?.length">检索参考（请核对原文）</strong>
          <div v-for="source in message.sources" :key="source.number" class="source-card">
            <el-button link type="primary" @click="openSource(source)">[{{ source.number }}] {{ source.title }}</el-button>
            <p>{{ source.excerpt }}</p>
          </div>
        </div>
      </article>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="composer">
      <el-input v-if="mode !== 'group-summary'" v-model="input" type="textarea" :rows="3" maxlength="4000" show-word-limit placeholder="输入问题，Ctrl + Enter 发送" aria-label="输入问题" @keydown.ctrl.enter.prevent="send" />
      <footer><small>{{ status?.model || '正在检查配置…' }} · {{ mode !== 'general' ? '每次独立检索资料' : '自动携带最近完整对话' }}</small><div>
        <el-button v-if="!busy && ['error', 'stopped', 'truncated'].includes(messages.at(-1)?.state)" @click="retry">重试</el-button>
        <el-button v-if="busy" :disabled="stopping" @click="stop">{{ stopping ? '正在停止…' : '停止生成' }}</el-button>
        <el-button v-else-if="mode !== 'group-summary'" type="primary" :disabled="!input.trim() || !status?.configured || status.protocolVersion !== 2" @click="send">发送</el-button>
      </div></footer>
    </div>
    <el-dialog v-model="sourceDialog" title="资料来源" width="min(720px, 92vw)" @closed="sourceController?.abort()">
      <el-alert v-if="sourceError" :title="sourceError" type="error" :closable="false" />
      <template v-else-if="sourceDetail">
        <h2>{{ sourceDetail.title }}</h2><p class="source-content">{{ sourceDetail.content }}</p><small>{{ sourceDetail.notice }}</small>
      </template>
      <p v-else>正在校验权限并读取来源…</p>
    </el-dialog>
  </section>
</template>

<style scoped>
.ai-assistant { max-width: 1000px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; height: calc(100vh - 150px); min-height: 520px; }
header, footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h1 { margin: 0; font-size: 24px; } header p, small { color: #667085; } header p { margin: 8px 0 0; }
.transcript { flex: 1; min-height: 120px; overflow-y: auto; padding: 8px; }
.empty { text-align: center; padding: 40px 12px; color: #667085; }
article { padding: 16px 20px; border-radius: 12px; margin-bottom: 16px; background: #f5f7fa; }
article.user { background: #edf5ff; margin-left: 8%; }
.content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.75; margin-top: 8px; }
article small { display: block; margin-top: 8px; } footer { margin-top: 10px; }
.sources { margin-top: 16px; border-top: 1px solid #dce2eb; padding-top: 8px; }
.source-card { margin-top: 10px; padding: 10px; background: white; border-radius: 6px; }
.source-card p { font-size: 13px; color: #475467; line-height: 1.6; overflow-wrap: anywhere; }
.source-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.index-panel { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
@media (max-width: 600px) { footer { flex-wrap: wrap; } .ai-assistant { min-height: 600px; } }
</style>
