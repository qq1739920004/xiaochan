<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Clock, Refresh, Warning } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import api from '../api'

const authState = inject<{
  isAuthenticated: { value: boolean }
  setAuthenticated: () => void
  waitForAuth: () => Promise<void>
}>('authState')!

const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const claiming = ref(false)
const historyLoading = ref(false)
const xSivirMasked = ref('')
const history = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const lastResult = ref<any>(null)
const accounts = ref<any[]>([])
const selectedAccountId = ref<number | null>(null)
const selectedAccount = computed(() => accounts.value.find(item => item.id === selectedAccountId.value))

const form = reactive({
  silkId: null as number | null,
  xVayne: null as number | null,
  xSivir: '',
  enabled: false,
  cron: '27 29 9 * * ?',
  maxAttempts: 5,
  minIntervalMs: 100,
  maxIntervalMs: 300,
})

const rules = {
  silkId: [{ required: true, message: '请输入 silk_id', trigger: 'blur' }],
  xVayne: [{ required: true, message: '请输入 X-Vayne', trigger: 'blur' }],
  minIntervalMs: [{ required: true, message: '请输入最小间隔', trigger: 'blur' }],
  maxIntervalMs: [{ required: true, message: '请输入最大间隔', trigger: 'blur' }],
}

async function loadConfig() {
  loading.value = true
  try {
    const response = await api.get(selectedAccountId.value
      ? `/api/brand-card/config/${selectedAccountId.value}`
      : '/api/brand-card/config')
    const config = response.data.data
    const account = accounts.value.find(item => item.id === selectedAccountId.value)
    form.silkId = config.silkId ?? account?.silkId ?? null
    form.xVayne = config.xVayne ?? account?.xVayne ?? null
    form.enabled = Boolean(config.enabled)
    form.cron = config.cron || '27 29 9 * * ?'
    form.maxAttempts = 5
    form.minIntervalMs = 100
    form.maxIntervalMs = 300
    xSivirMasked.value = config.xSivirMasked || ''
  } finally {
    loading.value = false
  }
}

async function loadAccounts() {
  const response = await api.get('/api/xiaochan/accounts', { refresh: false })
  accounts.value = response.data.data || []
  if (!selectedAccountId.value && accounts.value.length) selectedAccountId.value = accounts.value[0].id
}

async function changeAccount() {
  await Promise.all([loadConfig(), loadHistory()])
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const response = await api.post(
      selectedAccountId.value ? `/api/brand-card/history/page/${selectedAccountId.value}` : '/api/brand-card/history/page',
      pagination,
    )
    const page = response.data.data
    history.value = page?.records || []
    pagination.total = Number(page?.total || 0)
  } finally {
    historyLoading.value = false
  }
}

async function saveConfig() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (form.minIntervalMs > form.maxIntervalMs) {
    ElMessage.warning('最小请求间隔不能大于最大请求间隔')
    return
  }
  saving.value = true
  try {
    const payload: any = { ...form, maxAttempts: 5, minIntervalMs: 100, maxIntervalMs: 300 }
    if (!payload.xSivir.trim()) {
      delete (payload as Partial<typeof payload>).xSivir
    }
    if (selectedAccountId.value) {
      payload.accountId = selectedAccountId.value
      await api.post(`/api/brand-card/config/${selectedAccountId.value}`, payload)
    } else {
      await api.post('/api/brand-card/config', payload)
    }
    form.xSivir = ''
    ElMessage.success('配置已保存')
    await loadConfig()
  } finally {
    saving.value = false
  }
}

async function claimNow() {
  try {
    await ElMessageBox.confirm('将立即向小蚕接口发送一次领取请求。', '手动试领', {
      confirmButtonText: '立即请求',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  claiming.value = true
  try {
    const response = await api.post('/api/brand-card/claim-now', null,
      selectedAccountId.value ? { params: { accountId: selectedAccountId.value } } : undefined)
    lastResult.value = response.data.data
    ElMessage.success('请求已完成')
    await loadHistory()
  } finally {
    claiming.value = false
  }
}

function handlePageChange(pageNum: number) {
  pagination.pageNum = pageNum
  loadHistory()
}

function statusType(item: any) {
  return item.success ? 'success' : item.stopReason === 'SOLD_OUT' ? 'warning' : 'danger'
}

function statusText(item: any) {
  const labels: Record<string, string> = {
    SUCCESS: '领取成功',
    SOLD_OUT: '已抢完',
    ALREADY_CLAIMED: '已领取',
    NEED_VERIFY: '需要验证',
    AUTH_INVALID: '登录态失效',
    BUSINESS_FAILED: '业务失败',
    MAX_ATTEMPTS_REACHED: '达到次数上限',
    TIME_WINDOW_EXPIRED: '执行窗口结束',
  }
  return labels[item.stopReason] || item.stopReason || '未知'
}

onMounted(async () => {
  await authState.waitForAuth()
  await loadAccounts()
  await Promise.all([loadConfig(), loadHistory()])
})
</script>

<template>
  <main v-loading="loading" class="brand-card-page">
    <section class="page-heading">
      <div>
        <p class="eyebrow">VIP RIGHTS</p>
        <h1>自动领取大牌卷</h1>
      </div>
      <div class="run-state">
        <span class="state-dot" :class="{ enabled: form.enabled }"></span>
        {{ form.enabled ? '已启用' : '已停用' }}
      </div>
    </section>

    <section class="claim-grid">
      <div class="config-panel">
        <div class="panel-header">
          <div>
            <h2>领取配置</h2>
            <p>09:29:27.000 至 09:30:01.000 连续请求；凭证同时供监控自动抢单使用</p>
          </div>
          <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
        </div>

        <el-form-item label="执行账号">
          <el-select v-model="selectedAccountId" class="full-width" placeholder="请选择小蚕账号" @change="changeAccount">
            <el-option v-for="account in accounts" :key="account.id" :label="`${account.accountName}（${account.nickname || account.silkId}）`" :value="account.id" />
          </el-select>
          <p class="field-note"><router-link to="/xiaochan-accounts">去账号配置</router-link> 添加或更新小蚕登录态。</p>
          <div v-if="selectedAccount" class="account-summary">
            <span>卡券 {{ selectedAccount.cardTotal ?? 0 }}（有效 {{ selectedAccount.cardActive ?? 0 }}）</span>
            <span>红包 {{ selectedAccount.redpackTotal ?? 0 }}</span>
            <span>{{ selectedAccount.refreshStatus === 'OK' ? '账号信息已同步' : '账号信息待刷新' }}</span>
          </div>
        </el-form-item>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="claim-form">
          <el-form-item label="silk_id" prop="silkId">
            <el-input-number v-model="form.silkId" :min="1" :controls="false" placeholder="126938104" class="full-width" />
          </el-form-item>
          <el-form-item label="X-Vayne（小蚕用户标识）" prop="xVayne">
            <el-input-number v-model="form.xVayne" :min="1" :controls="false" placeholder="1836966" class="full-width" />
            <p class="field-note">从抢单成功请求的请求头中复制 X-Vayne；它不是登录 Token。</p>
          </el-form-item>
          <el-form-item label="X-Sivir（小蚕登录态）" prop="xSivir">
            <el-input v-model="form.xSivir" type="password" show-password :placeholder="xSivirMasked ? '留空则保留 ' + xSivirMasked : '请输入抓包得到的 X-Sivir'" />
            <p v-if="xSivirMasked" class="field-note">已保存：{{ xSivirMasked }}</p>
            <p class="field-note">项目网页登录 Token 不用于小蚕接口；大牌券和自动抢单共用 silk_id、X-Vayne 与 X-Sivir。</p>
          </el-form-item>

          <div class="time-row">
            <div class="time-cell">
              <span>首次请求时间</span>
              <strong><el-icon><Clock /></el-icon> 连续窗口</strong>
            </div>
            <div class="time-cell">
              <span>默认执行</span>
              <strong>09:29:27</strong>
            </div>
          </div>
          <el-form-item label="准备 cron（含秒）">
            <el-input v-model="form.cron" placeholder="27 29 9 * * ?" />
            <p class="field-note">默认 09:29:27.000 开始，持续到 09:30:01.000；每个账号可单独设置。</p>
          </el-form-item>

          <div class="number-grid">
            <el-form-item label="窗口请求上限">
              <el-input model-value="400 次" disabled class="full-width" />
              <p class="field-note">连续窗口内最多 400 次，不因业务响应提前停止。</p>
            </el-form-item>
            <el-form-item label="最小间隔 (ms)" prop="minIntervalMs">
              <el-input-number v-model="form.minIntervalMs" :min="100" :max="100" :controls="false" disabled class="full-width" />
            </el-form-item>
            <el-form-item label="最大间隔 (ms)" prop="maxIntervalMs">
              <el-input-number v-model="form.maxIntervalMs" :min="300" :max="300" :controls="false" disabled class="full-width" />
            </el-form-item>
          </div>
        </el-form>

        <div class="action-row">
          <el-button :icon="Refresh" :loading="saving" @click="saveConfig">保存配置</el-button>
          <el-button type="primary" :icon="Check" :loading="claiming" @click="claimNow">手动试领</el-button>
        </div>
      </div>

      <aside class="result-panel">
        <div class="result-icon"><el-icon><Warning /></el-icon></div>
        <h2>执行规则</h2>
        <dl>
          <div><dt>连续窗口</dt><dd>09:29:27-09:30:01</dd></div>
          <div><dt>最多请求</dt><dd>400 次</dd></div>
          <div><dt>随机间隔</dt><dd>{{ form.minIntervalMs }}-{{ form.maxIntervalMs }}ms</dd></div>
          <div><dt>停止条件</dt><dd>窗口结束或达到次数上限</dd></div>
        </dl>
        <div v-if="lastResult" class="last-result">
          <span>最近手动请求</span>
          <strong>{{ lastResult.resultMessage || statusText(lastResult) }}</strong>
        </div>
      </aside>
    </section>

    <section class="history-section">
      <div class="history-heading">
        <div>
          <p class="eyebrow">RUN LOG</p>
          <h2>执行历史</h2>
        </div>
        <el-button circle :icon="Refresh" aria-label="刷新执行历史" @click="loadHistory" />
      </div>
      <el-table v-loading="historyLoading" :data="history" class="history-table" empty-text="暂无执行记录">
        <el-table-column prop="startTime" label="开始时间" min-width="170" />
        <el-table-column prop="requestCount" label="请求次数" width="100" align="center" />
        <el-table-column label="最终状态" min-width="130">
          <template #default="{ row }"><el-tag :type="statusType(row)" effect="plain">{{ statusText(row) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="resultCode" label="响应码" width="100" align="center" />
        <el-table-column prop="resultMsg" label="响应消息" min-width="240" show-overflow-tooltip />
      </el-table>
      <div class="pagination-row">
        <span>共 {{ pagination.total }} 条</span>
        <el-pagination background layout="prev, pager, next" :current-page="pagination.pageNum" :page-size="pagination.pageSize" :total="pagination.total" @current-change="handlePageChange" />
      </div>
    </section>
  </main>
</template>

<style scoped>
.brand-card-page { color: #202a31; padding: 10px 0 24px; }
.page-heading, .panel-header, .history-heading, .action-row, .pagination-row { display: flex; align-items: center; justify-content: space-between; }
.page-heading { margin: 4px 0 18px; }
.eyebrow { color: #a45e28; font-size: 11px; font-weight: 700; letter-spacing: 1.4px; margin: 0 0 5px; }
h1, h2, p { margin-top: 0; }
h1 { font-size: 28px; line-height: 1.15; margin-bottom: 0; letter-spacing: 0; }
h2 { font-size: 17px; margin-bottom: 0; letter-spacing: 0; }
.run-state { color: #66737c; font-size: 14px; display: flex; align-items: center; gap: 7px; }
.state-dot { width: 8px; height: 8px; border-radius: 50%; background: #a9b0b4; }
.state-dot.enabled { background: #13795b; box-shadow: 0 0 0 4px rgba(19, 121, 91, .12); }
.claim-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(240px, .75fr); gap: 16px; }
.config-panel, .result-panel, .history-section { background: #fff; border: 1px solid #e5e8e8; border-radius: 8px; }
.config-panel { padding: 22px; }
.panel-header { border-bottom: 1px solid #edf0ef; padding-bottom: 16px; margin-bottom: 18px; }
.panel-header p { color: #78848a; font-size: 13px; margin: 5px 0 0; }
.claim-form :deep(.el-form-item__label) { color: #4c5b62; font-weight: 600; padding-bottom: 5px; }
.full-width { width: 100%; }
.field-note { color: #78848a; font-size: 12px; margin: 6px 0 0; }
.account-summary { display: flex; flex-wrap: wrap; gap: 8px 14px; margin: 10px 0 16px; color: #5e6b71; font-size: 12px; }
.time-row { border-top: 1px solid #edf0ef; border-bottom: 1px solid #edf0ef; display: grid; grid-template-columns: 1fr 1fr; margin: 6px 0 18px; }
.time-cell { padding: 14px 0; display: grid; gap: 4px; }
.time-cell + .time-cell { border-left: 1px solid #edf0ef; padding-left: 18px; }
.time-cell span { color: #78848a; font-size: 12px; }
.time-cell strong { color: #253239; font-size: 15px; display: flex; gap: 6px; align-items: center; }
.number-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.action-row { margin-top: 4px; justify-content: flex-end; gap: 10px; }
.result-panel { background: #fbfcfb; padding: 24px 20px; }
.result-icon { color: #a45e28; background: #fff0e4; width: 36px; height: 36px; display: grid; place-items: center; border-radius: 7px; margin-bottom: 18px; }
.result-panel dl { margin: 20px 0; }
.result-panel dl div { border-bottom: 1px solid #e8ecea; padding: 12px 0; display: flex; justify-content: space-between; gap: 10px; }
.result-panel dt { color: #78848a; font-size: 13px; }
.result-panel dd { margin: 0; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; color: #27343b; }
.last-result { border-left: 3px solid #13795b; background: #eff8f4; padding: 11px 12px; display: grid; gap: 3px; }
.last-result span { color: #5a7468; font-size: 12px; }
.last-result strong { font-size: 13px; overflow-wrap: anywhere; }
.history-section { margin-top: 16px; padding: 20px; }
.history-heading { margin-bottom: 14px; }
.history-heading .eyebrow { margin-bottom: 4px; }
.history-table { width: 100%; }
.pagination-row { color: #78848a; font-size: 13px; margin-top: 14px; }
@media (max-width: 768px) {
  .page-heading { align-items: flex-start; gap: 12px; }
  h1 { font-size: 23px; }
  .claim-grid { grid-template-columns: 1fr; }
  .config-panel, .history-section { padding: 16px; }
  .number-grid { grid-template-columns: 1fr; gap: 0; }
  .pagination-row { align-items: flex-start; flex-direction: column; gap: 10px; }
}
</style>
