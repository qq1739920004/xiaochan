<script setup lang="ts">
import { inject, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Refresh, User } from '@element-plus/icons-vue'
import api from '../api'

const authState = inject<{ waitForAuth: () => Promise<void> }>('authState')!
const accounts = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const refreshingId = ref<number | null>(null)
const form = reactive({ accountName: '', silkId: null as number | null, xVayne: null as number | null, xSivir: '', enabled: true })

async function loadAccounts(refresh = true) {
  loading.value = true
  try {
    const response = await api.get('/api/xiaochan/accounts', { refresh })
    accounts.value = response.data.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { accountName: '', silkId: null, xVayne: null, xSivir: '', enabled: true })
  dialogVisible.value = true
}

function openEdit(account: any) {
  editingId.value = account.id
  Object.assign(form, { accountName: account.accountName, silkId: account.silkId, xVayne: account.xVayne, xSivir: '', enabled: account.enabled !== false })
  dialogVisible.value = true
}

async function saveAccount() {
  if (!form.accountName.trim() || !form.silkId || !form.xVayne) {
    ElMessage.warning('请填写账号名称、silk_id 和 X-Vayne')
    return
  }
  if (!editingId.value && !form.xSivir.trim()) {
    ElMessage.warning('首次添加账号必须填写 X-Sivir')
    return
  }
  saving.value = true
  try {
    const payload: any = {
      accountName: form.accountName.trim(),
      silkId: Number(form.silkId),
      xVayne: Number(form.xVayne),
      enabled: Boolean(form.enabled),
    }
    if (form.xSivir.trim()) payload.xSivir = form.xSivir.trim()
    if (editingId.value) await api.put(`/api/xiaochan/accounts/${editingId.value}`, payload)
    else await api.post('/api/xiaochan/accounts', payload)
    dialogVisible.value = false
    ElMessage.success('账号已保存')
    await loadAccounts(true)
  } finally {
    saving.value = false
  }
}

async function refreshAccount(account: any) {
  refreshingId.value = account.id
  try {
    await api.post(`/api/xiaochan/accounts/${account.id}/refresh`)
    ElMessage.success('账号信息已刷新')
    await loadAccounts(false)
  } finally {
    refreshingId.value = null
  }
}

async function disableAccount(account: any) {
  try {
    await ElMessageBox.confirm(`确定停用账号“${account.accountName}”吗？`, '停用账号', { type: 'warning' })
    await api.delete(`/api/xiaochan/accounts/${account.id}`)
    ElMessage.success('账号已停用')
    await loadAccounts(false)
  } catch {
    // 用户取消
  }
}

function statusText(account: any) {
  if (account.refreshStatus === 'OK') return '已同步'
  if (account.refreshStatus === 'FAILED') return '刷新失败'
  return '未同步'
}

onMounted(async () => {
  await authState.waitForAuth()
  await loadAccounts(true)
})
</script>

<template>
  <main class="account-page" v-loading="loading">
    <header class="page-header">
      <div>
        <p class="eyebrow">XIAOCAN ACCOUNTS</p>
        <h1>小蚕账号配置</h1>
        <p class="subtitle">大牌券和监控自动抢单共用账号登录态，X-Sivir 仅掩码展示。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadAccounts(true)">刷新全部</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增账号</el-button>
      </div>
    </header>

    <section v-if="accounts.length" class="account-grid">
      <article v-for="account in accounts" :key="account.id" class="account-card">
        <div class="card-top">
          <div class="account-title"><el-icon><User /></el-icon><strong>{{ account.accountName }}</strong></div>
          <el-tag :type="account.enabled ? 'success' : 'info'" effect="plain">{{ account.enabled ? '启用' : '停用' }}</el-tag>
        </div>
        <div class="identity-grid">
          <span>昵称</span><strong>{{ account.nickname || '未同步' }}</strong>
          <span>silk_id</span><strong>{{ account.silkId }}</strong>
          <span>用户编号</span><strong>{{ account.upstreamUserId || '未同步' }}</strong>
          <span>会员</span><strong>{{ account.vipLevel || '普通账号' }}</strong>
          <span>手机号</span><strong>{{ account.phoneMasked || '未同步' }}</strong>
          <span>X-Sivir</span><strong>{{ account.xSivirMasked || '未配置' }}</strong>
        </div>
        <div class="stats-row">
          <div><b>{{ account.cardTotal ?? 0 }}</b><span>卡券</span></div>
          <div><b>{{ account.cardActive ?? 0 }}</b><span>有效卡券</span></div>
          <div><b>{{ account.cardExpired ?? 0 }}</b><span>过期卡券</span></div>
          <div><b>{{ account.redpackTotal ?? 0 }}</b><span>红包券</span></div>
          <div><b>{{ account.meituanRedpackTotal ?? 0 }}</b><span>美团红包</span></div>
          <div><b>{{ account.elemeRedpackTotal ?? 0 }}</b><span>饿了么红包</span></div>
          <div><b>{{ account.platformRedpackTotal ?? 0 }}</b><span>平台红包</span></div>
        </div>
        <div class="card-footer">
          <span :class="account.refreshStatus === 'OK' ? 'status-ok' : 'status-muted'">{{ statusText(account) }}</span>
          <span>{{ account.lastRefreshTime || '尚未刷新' }}</span>
          <div class="card-actions">
            <el-button link type="primary" :icon="Refresh" :loading="refreshingId === account.id" @click="refreshAccount(account)">刷新</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(account)">编辑</el-button>
            <el-button link type="danger" @click="disableAccount(account)" :disabled="!account.enabled">停用</el-button>
          </div>
        </div>
        <p v-if="account.lastRefreshError" class="error-text">{{ account.lastRefreshError }}</p>
      </article>
    </section>
    <el-empty v-else description="暂无小蚕账号，请先新增账号" />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑小蚕账号' : '新增小蚕账号'" width="640px">
      <el-form label-position="top" class="account-form">
        <el-form-item label="账号名称"><el-input v-model="form.accountName" placeholder="例如：主账号" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="silk_id"><el-input-number v-model="form.silkId" :min="1" :controls="false" class="full-width" /></el-form-item>
          <el-form-item label="X-Vayne"><el-input-number v-model="form.xVayne" :min="1" :controls="false" class="full-width" /></el-form-item>
        </div>
        <el-form-item label="X-Sivir（留空保留原值）"><el-input v-model="form.xSivir" type="password" show-password placeholder="请输入抓包得到的 X-Sivir" /></el-form-item>
        <el-form-item label="账号状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAccount">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<style scoped>
.account-page { color: #202a31; padding: 8px 0 28px; }
.page-header, .header-actions, .card-top, .card-footer, .card-actions { display: flex; align-items: center; }
.page-header { justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.header-actions { gap: 10px; }
.eyebrow { color: #a45e28; font-size: 11px; font-weight: 700; letter-spacing: 1.4px; margin: 0 0 5px; }
h1 { margin: 0; font-size: 28px; letter-spacing: 0; }
.subtitle { color: #78848a; margin: 8px 0 0; font-size: 13px; }
.account-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(460px, 1fr)); gap: 16px; }
.account-card { background: #fff; border: 1px solid #e5e8e8; border-radius: 8px; padding: 20px; }
.card-top { justify-content: space-between; border-bottom: 1px solid #edf0ef; padding-bottom: 14px; }
.account-title { display: flex; align-items: center; gap: 8px; font-size: 18px; }
.identity-grid { display: grid; grid-template-columns: 90px 1fr 90px 1fr; gap: 10px 14px; padding: 16px 0; font-size: 13px; }
.identity-grid span { color: #78848a; }.identity-grid strong { overflow-wrap: anywhere; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); border-top: 1px solid #edf0ef; border-bottom: 1px solid #edf0ef; padding: 14px 0; gap: 8px; }
.stats-row div { display: grid; gap: 4px; text-align: center; }.stats-row b { font-size: 20px; }.stats-row span { color: #78848a; font-size: 12px; }
.card-footer { flex-wrap: wrap; gap: 10px; padding-top: 14px; color: #78848a; font-size: 12px; }.card-actions { margin-left: auto; gap: 2px; }.status-ok { color: #13795b; }.status-muted { color: #a45e28; }.error-text { color: #c2413a; margin: 10px 0 0; font-size: 12px; overflow-wrap: anywhere; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }.full-width { width: 100%; }
@media (max-width: 720px) { .page-header { align-items: flex-start; flex-direction: column; }.header-actions { width: 100%; }.header-actions .el-button { flex: 1; }.account-grid { grid-template-columns: 1fr; }.identity-grid { grid-template-columns: 80px 1fr; }.stats-row { grid-template-columns: repeat(2, 1fr); }.form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
