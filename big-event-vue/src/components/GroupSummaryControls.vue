<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { groupSummaryRead } from '@/api/ai'
defineProps({ disabled: Boolean })
const emit = defineEmits(['generate'])
const groups = ref([])
const groupId = ref('')
// The database stores local Beijing wall times, independently of browser timezone.
const beijing = date => new Date(date.getTime() + 8 * 3600000).toISOString().slice(0, 16)
const end = ref(beijing(new Date()))
const start = ref(beijing(new Date(Date.now() - 24 * 3600000)))
const error = ref('')
const controller = new AbortController()
onBeforeUnmount(() => controller.abort())
onMounted(async () => {
  try { groups.value = await groupSummaryRead(null, null, controller.signal) }
  catch (e) { if (e.name !== 'AbortError') error.value = e.message }
})
function generate() {
  if (!groupId.value || !start.value || !end.value || start.value >= end.value) { error.value = '请选择群聊和有效起止时间'; return }
  error.value = ''
  emit('generate', { groupId: Number(groupId.value), start: start.value + ':00', end: end.value + ':00' })
}
</script>

<template>
  <section class="summary-controls" aria-label="群聊摘要范围">
    <label>群聊 <select v-model="groupId" :disabled="disabled" aria-label="选择群聊">
      <option value="">请选择</option><option v-for="group in groups" :key="group.id" :value="group.id">{{ group.groupName }}</option>
    </select></label>
    <label>开始 <input v-model="start" type="datetime-local" aria-label="摘要开始时间" :disabled="disabled" /></label>
    <label>结束 <input v-model="end" type="datetime-local" aria-label="摘要结束时间" :disabled="disabled" /></label>
    <el-button :disabled="disabled || !groupId" @click="generate">生成群聊摘要</el-button>
    <small>北京时间，结束时间不包含；单次最多 7 天。最多取最近 100 条有效文本、合计 16000 字符；超过 2000 字符的单条消息跳过。群列表最多 100 个。</small>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </section>
</template>

<style scoped>
.summary-controls { display:flex; gap:12px; flex-wrap:wrap; align-items:center; }
label { display:flex; gap:6px; align-items:center; } select,input { padding:6px; max-width:100%; }
small { color:#667085; width:100%; }
</style>
