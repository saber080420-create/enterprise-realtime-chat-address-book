<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { documentRequest } from '@/api/ai'

defineProps({ disabled: Boolean })
const job = ref(null)
const starting = ref(false)
const error = ref('')
const controller = new AbortController()
let timer
let disposed = false
onMounted(poll)
onBeforeUnmount(() => { disposed = true; clearTimeout(timer); controller.abort() })
async function poll() {
  try {
    job.value = await documentRequest('GET', 'index', null, controller.signal)
    if (!disposed && job.value.state === 'running') timer = setTimeout(poll, 2000)
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
}
async function sync() {
  error.value = ''; starting.value = true; clearTimeout(timer)
  try {
    job.value = await documentRequest('POST', 'index', null, controller.signal)
    if (!disposed) timer = setTimeout(poll, 1000)
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { starting.value = false }
}
</script>

<template>
  <div class="document-index">
    <el-button :disabled="disabled || starting || job?.state === 'running'" @click="sync">同步个人文档索引</el-button>
    <small>{{ job?.message || '首次使用或新增文档后请同步；只处理自己的最多 50 篇有效文档。' }}</small>
    <small v-if="job?.total != null">进度 {{ job.processed }}/{{ job.total }} · 复用 {{ job.reused }} 篇 · 跳过变更/移出 {{ job.skipped }} 篇</small>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </div>
</template>

<style scoped>
.document-index { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
small { color: #667085; }
</style>
