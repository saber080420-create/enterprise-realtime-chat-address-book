<script setup>
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { aiConfig } from '@/api/ai'
defineProps({ disabled: Boolean })
const emit = defineEmits(['changed'])
const visible = ref(false)
const apiKey = ref('')
const config = ref(null)
const busy = ref(false)
const error = ref('')
const notice = ref('')
async function open() {
  visible.value = true; apiKey.value = ''; error.value = ''; notice.value = ''; config.value = null
  busy.value = true
  try { config.value = await aiConfig() }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function submit(test) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await aiConfig(test ? 'POST' : 'PUT', apiKey.value, test)
    if (test) notice.value = result.message
    else {
      apiKey.value = ''
      emit('changed')
      config.value = await aiConfig()
      notice.value = '已保存，后续请求将使用你的 Key，无需重启后端。'
    }
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function remove() {
  try { await ElMessageBox.confirm('删除后新的 AI 请求将不可用，已开始的请求可能继续完成。', '删除自己的 API Key', { type: 'warning' }) }
  catch { return }
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await aiConfig('DELETE'); apiKey.value = ''; emit('changed')
    config.value = await aiConfig(); notice.value = '已删除保存的 Key。如需彻底吊销，请同时在 DeepSeek 平台操作。'
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>

<template>
  <el-button :disabled="disabled" @click="open">模型配置</el-button>
  <el-dialog v-model="visible" title="我的 DeepSeek 配置" width="min(560px, 92vw)"
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @closed="apiKey = ''">
    <el-alert :closable="false" type="info" title="使用你自己的 API Key，模型费用由你承担。Key 会传到本项目后端并加密保存；后端可解密调用 DeepSeek。请只在可信部署中配置。" />
    <p>服务地址：https://api.deepseek.com（固定）</p>
    <p>模型：{{ config?.model || '由服务器配置' }}</p>
    <p v-if="config">{{ config.configured ? `已配置 · 尾号 ${config.keySuffix}` : config.saved ? '已保存但当前无法解密，请联系管理员或重新保存' : '尚未配置个人 Key' }}</p>
    <el-alert v-if="config && !config.storageReady" :closable="false" type="warning" title="管理员需先配置服务器加密主密钥 AI_CREDENTIAL_MASTER_KEY，才能保存和使用 Key。" />
    <el-input v-model="apiKey" type="password" autocomplete="new-password" :disabled="busy" maxlength="256"
      placeholder="输入新的 API Key（不会回显已保存的完整 Key）" aria-label="DeepSeek API Key" style="margin: 16px 0" />
    <small>测试连接仅校验模型列表接口可访问，不生成文本，不保证余额充足。保存不会自动发起模型请求。</small>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
    <template #footer>
      <el-button :disabled="busy || !config?.saved" type="danger" plain @click="remove">删除 Key</el-button>
      <el-button :disabled="busy || !apiKey" @click="submit(true)">测试连接</el-button>
      <el-button type="primary" :disabled="busy || !apiKey || !config?.storageReady" @click="submit(false)">保存</el-button>
    </template>
  </el-dialog>
</template>
