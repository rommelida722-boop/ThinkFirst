<script setup>
import { ref, computed, onMounted } from 'vue'
import { apiKeyApi, request } from '@/lib/auth.js'
const builtin = ['DeepSeek', '通义千问（北京）', '通义千问（新加坡）']
const available = ref([...builtin]), supported = ref([]), connections = ref([])
const legacyKey = ref(null), provider = ref('DeepSeek'), key = ref('')
const loading = ref(true), busy = ref(false), reachable = ref(false), modern = ref(false)
const error = ref(''), message = ref('')
const supportedSelection = computed(() => reachable.value && (modern.value ? supported.value.includes(provider.value) : provider.value === 'DeepSeek'))
const savedSelection = computed(() => connections.value.some(item => item.provider === provider.value) || provider.value === 'DeepSeek' && legacyKey.value?.hasApiKey)
async function load() {
  loading.value = true
  error.value = ''
  reachable.value = false
  try {
    // 即使新接口暂不可用，也保留厂商表单；绝不将千问密钥发往旧DeepSeek接口。
    const data = await request('/user/models')
    connections.value = Array.isArray(data?.connections) ? data.connections : []
    supported.value = Array.isArray(data?.available) ? data.available : []
    available.value = [...new Set([...builtin, ...supported.value])]
    modern.value = supported.value.includes('DeepSeek')
    const preferred = connections.value.find(item => item.preferred)
    if (preferred) provider.value = preferred.provider
    reachable.value = true
  } catch (e) {
    if (e.status === 404) {
      modern.value = false
      connections.value = []
    } else {
      error.value = e.message || '连接失败，请重试'
    }
  }
  try {
    legacyKey.value = await apiKeyApi.get()
    // 旧版接口仍可正常保存DeepSeek，不能因为新版接口缺失而显示空白卡片。
    if (!modern.value && !error.value) reachable.value = true
  } catch (e) {
    if (!reachable.value) error.value = e.message || '设置读取失败，请重试'
  } finally {
    loading.value = false
  }
}
onMounted(load)
async function save() {
  if (!supportedSelection.value || !key.value.trim() || busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try {
    if (modern.value) await request('/user/models', { method: 'PUT', body: { provider: provider.value, apiKey: key.value.trim() } })
    else await apiKeyApi.save(key.value.trim())
    key.value = ''
    await load()
    message.value = '密钥已加密保存'
  } catch (e) { error.value = e.message || '保存失败，请重试' }
  finally { busy.value = false }
}
async function selectSaved() {
  if (!supportedSelection.value || busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try {
    if (modern.value) await request('/user/models/' + encodeURIComponent(provider.value) + '/select', { method: 'POST' })
    message.value = '已使用' + provider.value
    await load()
  } catch (e) { error.value = e.message || '切换失败，请重试' }
  finally { busy.value = false }
}
async function remove(name, legacy = false) {
  busy.value = true; error.value = ''; message.value = ''
  try {
    if (legacy) await apiKeyApi.remove()
    else await request('/user/models/' + encodeURIComponent(name), { method: 'DELETE' })
    await load()
  } catch (e) { error.value = e.message || '删除失败，请重试' }
  finally { busy.value = false }
}
</script>
<template>
  <section class="model-settings">
    <p v-if="loading" role="status">正在读取配置…</p>
    <form @submit.prevent="save">
      <label for="model-provider">厂商</label>
      <select id="model-provider" v-model="provider" :disabled="busy || loading"><option v-for="name in available" :key="name">{{ name }}</option></select>
      <p v-if="!loading && reachable && !supportedSelection" class="v2-auth-error">当前服务版本尚不支持该厂商，需要更新后端。</p>
      <p v-if="provider.includes('千问')" class="region-hint">密钥需与所选地域一致。</p>
      <label for="model-key">API Key</label>
      <input id="model-key" v-model="key" type="password" autocomplete="off" placeholder="粘贴该厂商的 API Key" :disabled="busy || loading" />
      <button v-if="!key.trim() && savedSelection" type="button" class="btn btn-primary" :disabled="busy || loading || !supportedSelection" @click="selectSaved">使用已保存的密钥</button>
      <button v-else class="btn btn-primary" :disabled="busy || loading || !supportedSelection || !key.trim()">{{ busy ? '保存中…' : '加密保存' }}</button>
    </form>
    <div v-if="legacyKey?.hasApiKey" class="saved-key">DeepSeek · {{ legacyKey.apiKeyMask }}<button class="btn btn-quiet" :disabled="busy" @click="remove('DeepSeek', true)">删除</button></div>
    <div v-for="item in connections" :key="item.id" class="saved-key">{{ item.provider }} · {{ item.mask }} {{ item.preferred ? '（优先使用）' : '（备用）' }}<button class="btn btn-quiet" :disabled="busy" @click="remove(item.provider)">删除</button></div>
    <p v-if="error" class="v2-auth-error" role="alert">{{ error }} <button type="button" class="btn btn-quiet" :disabled="busy || loading" @click="load">重试</button></p>
    <p v-if="message" class="v2-auth-ok" role="status">{{ message }}</p>
  </section>
</template>
<style scoped>
.model-settings{margin-top:24px}.model-settings form{display:grid;gap:12px}.model-settings input,.model-settings select{width:100%;font:inherit;padding:12px;border:1px solid var(--line);border-radius:8px}.model-settings label{font-weight:600}.saved-key{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:16px}.region-hint{font-size:15px;color:var(--ink-2)}
</style>
