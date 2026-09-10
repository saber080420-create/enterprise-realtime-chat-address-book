<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessageBox } from 'element-plus'
import { documentRequest } from '@/api/ai'

defineProps({ disabled: Boolean })
const emit = defineEmits(['source', 'changed'])
const documents = ref([])
const pending = ref(false)
const error = ref('')
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())
onMounted(refresh)

async function refresh() {
  pending.value = true
  error.value = ''
  try { documents.value = await documentRequest('GET', null, null, controller.signal) }
  catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { pending.value = false }
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || pending.value) return
  error.value = ''
  if (!/\.(txt|md)$/i.test(file.name) || file.size > 80000 || file.size === 0) {
    error.value = '请选择 1 至 80000 字节的 UTF-8 TXT/Markdown 文件'; return
  }
  pending.value = true
  try {
    await documentRequest('POST', null, file, controller.signal)
    emit('changed')
    await refresh()
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { pending.value = false }
}

async function remove(document) {
  try { await ElMessageBox.confirm(`将“${document.title}”移出个人知识库？移出后无法通过页面恢复，历史回答不会自动清除。`, '移出文档', {
    confirmButtonText: '确认移出', cancelButtonText: '取消', type: 'warning',
  }) } catch { return }
  pending.value = true
  error.value = ''
  try {
    await documentRequest('DELETE', document.id, null, controller.signal)
    emit('changed')
    await refresh()
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { pending.value = false }
}
</script>

<template>
  <section class="documents" aria-label="个人文档库">
    <div class="actions">
      <strong>个人文档库（{{ documents.length }}/50）</strong>
      <input type="file" accept=".txt,.md" aria-label="上传个人文档" :disabled="pending || disabled" @change="upload" />
      <el-button :disabled="pending || disabled" @click="refresh">刷新文档列表</el-button>
    </div>
    <small>仅自己可见；UTF-8 TXT/Markdown，单篇最多 20000 字符、80000 字节。上传不会调用模型；提问时相关片段会发送给 DeepSeek。请勿上传密钥或敏感资料。</small>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="pending" role="status">正在处理文档…</p>
    <ul v-else-if="documents.length">
      <li v-for="document in documents" :key="document.id">
        <el-button link type="primary" @click="emit('source', { announcementId: document.id, sourceType: 'document' })">{{ document.title }}</el-button>
        <small>{{ document.characters }} 字符</small>
        <el-button link type="danger" :disabled="disabled" @click="remove(document)">移出</el-button>
      </li>
    </ul>
    <p v-else>还没有文档。上传后即可用标题或关键词提问。</p>
  </section>
</template>

<style scoped>
.documents { border: 1px solid #dce2eb; padding: 12px; border-radius: 8px; }
.actions, li { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
input { max-width: 100%; }
ul { max-height: 130px; overflow-y: auto; padding: 0; margin: 8px 0 0; list-style: none; }
li { padding: 4px 0; overflow-wrap: anywhere; }
small { color: #667085; line-height: 1.6; }
</style>
