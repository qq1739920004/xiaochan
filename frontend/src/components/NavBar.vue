<script setup lang="ts">
import { ref, onMounted, computed, inject } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Select, User, Message, Unlock, UserFilled } from '@element-plus/icons-vue'
import api from '../api'

const route = useRoute()

// 注入认证状态
const authState = inject<{
  isAuthenticated: { value: boolean }
  setAuthenticated: () => void
  waitForAuth: () => Promise<void>
}>('authState')!

const currentPage = computed(() => {
  const name = route.name as string
  return name || 'home'
})

// Token dialog state
const dialogVisible = ref(false)
const activeTab = ref('existing')

// Existing token form
const existingFormRef = ref()
const existingForm = ref({ token: '' })
const existingRules = {
  token: [
    { required: true, message: '请输入 Token 值', trigger: 'blur' },
    { min: 1, max: 100, message: 'Token 长度应在 1-100 个字符之间', trigger: 'blur' },
  ],
}
const existingErrorMsg = ref('')
const existingLoading = ref(false)

// Register form
const registerFormRef = ref()
const registerForm = ref({ spt: '', code: '' })
const registerRules = {
  spt: [
    { required: true, message: '请输入 SPT', trigger: 'blur' },
    { min: 1, max: 100, message: 'SPT 长度应在 1-100 个字符之间', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { min: 4, max: 10, message: '验证码长度应在 4-10 个字符之间', trigger: 'blur' },
  ],
}
const sendingCode = ref(false)
const codeBtnDisabled = ref(false)
const codeBtnText = ref('发送验证码')
const codeCountdown = ref(60)
const registerLoading = ref(false)

// Register success dialog
const registerSuccessDialogVisible = ref(false)
const registeredToken = ref('')
const quickAccessUrl = computed(() => {
  const base = window.location.origin + window.location.pathname + window.location.hash.split('?')[0]
  return base + '?token=' + registeredToken.value
})

function extractAndStoreToken() {
  const urlParams = new URLSearchParams(window.location.search)
  const token = urlParams.get('token')
  if (token) {
    localStorage.setItem('token', token)
  }
}

function checkAndShowTokenDialog() {
  const urlParams = new URLSearchParams(window.location.search)
  const urlToken = urlParams.get('token')
  if (urlToken) {
    localStorage.setItem('token', urlToken)
    // 清除 URL 中的 token 参数后刷新页面
    const cleanUrl = window.location.pathname + window.location.hash
    window.history.replaceState({}, '', cleanUrl)
    window.location.reload()
    return
  }
  const storedToken = localStorage.getItem('token')
  if (storedToken) {
    authState?.setAuthenticated()
    return
  }
  dialogVisible.value = true
}

async function handleExistingConfirm() {
  try {
    await existingFormRef.value?.validate()
  } catch {
    return
  }

  const tokenValue = existingForm.value.token.trim()
  if (!tokenValue) {
    existingErrorMsg.value = '请输入 Token 值'
    return
  }

  existingErrorMsg.value = ''
  existingLoading.value = true

  try {
    await api.get('/api/user/getUserInfo', { token: tokenValue })
    existingLoading.value = false
    localStorage.setItem('token', tokenValue)
    dialogVisible.value = false
    // 刷新页面
    window.location.reload()
  } catch (error: unknown) {
    existingLoading.value = false
    existingErrorMsg.value =
      (error instanceof Error ? error.message : '') || 'Token 验证失败，请检查 Token 是否正确'
  }
}

async function handleSendCode() {
  if (!registerForm.value.spt || !registerForm.value.spt.trim()) {
    ElMessage.warning('请先输入 SPT')
    return
  }

  const sptValue = registerForm.value.spt.trim()
  sendingCode.value = true

  try {
    await api.postForm('/api/user/sendSptCode', { spt: sptValue })
    sendingCode.value = false
    ElMessage.success('验证码已发送，请查收')
    startCodeCountdown()
  } catch {
    sendingCode.value = false
  }
}

function startCodeCountdown() {
  codeBtnDisabled.value = true
  codeCountdown.value = 60
  codeBtnText.value = codeCountdown.value + 's 后重试'

  const timer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(timer)
      codeBtnDisabled.value = false
      codeBtnText.value = '发送验证码'
    } else {
      codeBtnText.value = codeCountdown.value + 's 后重试'
    }
  }, 1000)
}

async function handleRegisterConfirm() {
  try {
    await registerFormRef.value?.validate()
  } catch {
    return
  }

  const sptValue = registerForm.value.spt.trim()
  const codeValue = registerForm.value.code.trim()

  if (!sptValue) {
    ElMessage.warning('请输入 SPT')
    return
  }
  if (!codeValue) {
    ElMessage.warning('请输入验证码')
    return
  }

  registerLoading.value = true

  try {
    const response = await api.post('/api/user/register', { spt: sptValue, code: codeValue })
    registerLoading.value = false
    const tokenValue = response.data.data ? response.data.data.token : null
    if (tokenValue) {
      registeredToken.value = tokenValue
      localStorage.setItem('token', tokenValue)
      dialogVisible.value = false
      registerSuccessDialogVisible.value = true
    } else {
      dialogVisible.value = false
      window.location.reload()
    }
  } catch {
    registerLoading.value = false
  }
}

function handleRegisterSuccessConfirm() {
  registerSuccessDialogVisible.value = false
  window.location.reload()
}

function copyQuickAccessUrl() {
   if (navigator.clipboard) {
    navigator.clipboard.writeText(quickAccessUrl.value).then(() => {
      ElMessage.success('链接已复制到剪贴板')
    }).catch(() => {
      fallbackCopy(quickAccessUrl.value)
    })
  } else {
    fallbackCopy(quickAccessUrl.value)
  }
}

function fallbackCopy(name: string) {
  const textarea = document.createElement('textarea')
  textarea.value = name
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    document.execCommand('copy')
    ElMessage.success('门店名称已复制')
  } catch (err) {
    ElMessage.error('复制失败')
  } finally {
    document.body.removeChild(textarea)
  }
}

onMounted(() => {
  extractAndStoreToken()
  checkAndShowTokenDialog()
})
</script>

<template>
  <nav class="navbar">
    <router-link to="/" class="navbar-brand">小蚕活动平台</router-link>
    <div class="navbar-nav">
      <router-link to="/" class="nav-link" :class="{ active: currentPage === 'home' }">
        首页
      </router-link>
      <router-link
        to="/location"
        class="nav-link"
        :class="{ active: currentPage === 'location' }"
      >
        地址管理
      </router-link>
      <router-link to="/monitor" class="nav-link" :class="{ active: currentPage === 'monitor' }">
        监控管理
      </router-link>
      <router-link to="/notify-history" class="nav-link" :class="{ active: currentPage === 'notify-history' }">
        通知记录
      </router-link>
      <router-link to="/brand-card-claim" class="nav-link" :class="{ active: currentPage === 'brand-card-claim' }">
        自动领取大牌卷
      </router-link>
    </div>
  </nav>

  <!-- Token dialog -->
  <el-dialog
    v-model="dialogVisible"
    width="480px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    class="token-dialog"
    align-center
  >
    <template #header>
      <div class="dialog-header">
        <div class="dialog-icon">
          <el-icon size="28"><Key /></el-icon>
        </div>
        <h3 class="dialog-title">身份验证</h3>
        <p class="dialog-subtitle">请选择验证方式以继续使用</p>
      </div>
    </template>

    <div class="dialog-body">
      <el-tabs v-model="activeTab" class="custom-tabs">
        <el-tab-pane name="existing">
          <template #label>
            <span class="tab-label">
              <el-icon><Unlock /></el-icon>
              已有 Token
            </span>
          </template>
          <div class="tab-content">
            <p class="tab-desc">如果您已有 Token，请直接输入验证</p>
            <el-form
              :model="existingForm"
              :rules="existingRules"
              ref="existingFormRef"
              @submit.prevent
              class="custom-form"
            >
              <el-form-item prop="token" :error="existingErrorMsg">
                <el-input
                  v-model="existingForm.token"
                  placeholder="请输入您的 Token"
                  clearable
                  size="large"
                  class="custom-input"
                  @keyup.enter="handleExistingConfirm"
                >
                  <template #prefix>
                    <el-icon class="input-icon"><Key /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
            </el-form>
            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              @click="handleExistingConfirm"
              :loading="existingLoading"
            >
              <el-icon v-if="!existingLoading"><Select /></el-icon>
              {{ existingLoading ? '验证中...' : '验证并登录' }}
            </el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane name="register">
          <template #label>
            <span class="tab-label">
              <el-icon><UserFilled /></el-icon>
              新用户注册
            </span>
          </template>
          <div class="tab-content">
            <p class="tab-desc">新用户请完成注册获取 Token</p>
            <el-form
              :model="registerForm"
              :rules="registerRules"
              ref="registerFormRef"
              @submit.prevent
              class="custom-form"
              label-position="top"
            >
              <el-form-item prop="spt" label="SPT">
                <el-input
                  v-model="registerForm.spt"
                  placeholder="请输入您的 SPT"
                  clearable
                  size="large"
                  class="custom-input"
                >
                  <template #prefix>
                    <el-icon class="input-icon"><User /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item prop="code" label="验证码">
                <div class="code-input-group">
                  <el-input
                    v-model="registerForm.code"
                    placeholder="请输入验证码"
                    clearable
                    size="large"
                    class="custom-input"
                    @keyup.enter="handleRegisterConfirm"
                  >
                    <template #prefix>
                      <el-icon class="input-icon"><Message /></el-icon>
                    </template>
                  </el-input>
                  <el-button
                    size="large"
                    class="code-btn"
                    @click="handleSendCode"
                    :loading="sendingCode"
                    :disabled="codeBtnDisabled"
                  >
                    {{ codeBtnText }}
                  </el-button>
                </div>
              </el-form-item>
            </el-form>
            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              @click="handleRegisterConfirm"
              :loading="registerLoading"
            >
              <el-icon v-if="!registerLoading"><Select /></el-icon>
              {{ registerLoading ? '注册中...' : '立即注册' }}
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>

  <!-- Register success dialog -->
  <el-dialog
    v-model="registerSuccessDialogVisible"
    width="520px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    align-center
  >
    <template #header>
      <div class="dialog-header success-header">
        <div class="dialog-icon">
          <el-icon size="28"><Select /></el-icon>
        </div>
        <h3 class="dialog-title">注册成功</h3>
        <p class="dialog-subtitle">请保存以下信息以便后续使用</p>
      </div>
    </template>

    <div class="success-dialog-body">
      <div class="token-display">
        <label class="token-label">您的 Token</label>
        <div class="token-value-box">
          <code class="token-value">{{ registeredToken }}</code>
        </div>
      </div>

      <div class="token-display">
        <label class="token-label">快速访问链接（免登录）</label>
        <div class="token-value-box url-box">
          <code class="token-value url-value">{{ quickAccessUrl }}</code>
          <el-button size="small" class="copy-btn" @click="copyQuickAccessUrl">
            复制链接
          </el-button>
        </div>
        <p class="token-hint">通过此链接访问页面无需登录，请妥善保管</p>
      </div>

      <el-button
        type="primary"
        size="large"
        class="submit-btn"
        @click="handleRegisterSuccessConfirm"
      >
        <el-icon><Select /></el-icon>
        确定
      </el-button>
    </div>
  </el-dialog>
</template>

<style scoped>
.navbar {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 15px 30px;
  margin-bottom: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.18);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.navbar-brand {
  font-size: 24px;
  font-weight: 700;
  color: #2c3e50;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 10px;
}

.navbar-brand:hover {
  color: #667eea;
  text-decoration: none;
}

.navbar-nav {
  display: flex;
  gap: 20px;
  align-items: center;
}

.nav-link {
  color: #666;
  text-decoration: none;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-link:hover {
  color: #667eea;
  background: rgba(102, 126, 234, 0.1);
  text-decoration: none;
  transform: translateY(-1px);
}

.nav-link.active {
  color: #667eea;
  background: rgba(102, 126, 234, 0.15);
  font-weight: 600;
}

/* Dialog Styles */
.token-dialog :deep(.el-dialog) {
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  border: none;
}

.token-dialog :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
}

.token-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.dialog-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 30px 24px;
  text-align: center;
}

.dialog-icon {
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  backdrop-filter: blur(10px);
}

.dialog-icon .el-icon {
  color: #fff;
}

.dialog-title {
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 8px;
}

.dialog-subtitle {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  margin: 0;
}

.dialog-body {
  padding: 24px;
  background: #fff;
}

.custom-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.custom-tabs :deep(.el-tabs__nav-wrap) {
  background: #f5f7fa;
  border-radius: 12px;
  padding: 4px;
}

.custom-tabs :deep(.el-tabs__nav-scroll) {
  padding: 0;
}

.custom-tabs :deep(.el-tabs__nav) {
  border: none;
  width: 100%;
}

.custom-tabs :deep(.el-tabs__item) {
  border: none;
  padding: 12px 20px;
  font-size: 15px;
  font-weight: 500;
  color: #606266;
  transition: all 0.3s ease;
  flex: 1;
  text-align: center;
}

.custom-tabs :deep(.el-tabs__item.is-active) {
  color: #667eea;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.custom-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.tab-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.tab-content {
  padding: 8px 0;
}

.tab-desc {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin: 0 0 24px;
}

.custom-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.custom-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #303133;
  padding-bottom: 8px;
}

.custom-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  padding: 0 15px;
  transition: all 0.3s ease;
}

.custom-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.3) inset;
}

.custom-input :deep(.el-input__inner) {
  height: 44px;
  line-height: 44px;
}

.input-icon {
  color: #c0c4cc;
  font-size: 18px;
}

.code-input-group {
  display: flex;
  gap: 12px;
}

.code-input-group .custom-input {
  flex: 1;
}

.code-btn {
  flex-shrink: 0;
  border-radius: 10px;
  padding: 0 20px;
  font-weight: 500;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: #fff;
  min-width: 110px;
}

.code-btn:hover {
  opacity: 0.9;
}

.code-btn:disabled {
  background: #c0c4cc;
  opacity: 0.6;
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  margin-top: 8px;
}

.submit-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.success-header {
  background: linear-gradient(135deg, #67c23a 0%, #42b883 100%);
}

.success-dialog-body {
  padding: 24px;
}

.token-display {
  margin-bottom: 20px;
}

.token-label {
  display: block;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  font-size: 14px;
}

.token-value-box {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.token-value {
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: #303133;
  word-break: break-all;
  flex: 1;
  user-select: all;
}

.url-box {
  flex-wrap: wrap;
}

.url-value {
  font-size: 13px;
  color: #667eea;
}

.copy-btn {
  flex-shrink: 0;
  border-radius: 6px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: #fff;
}

.copy-btn:hover {
  opacity: 0.9;
}

.token-hint {
  color: #909399;
  font-size: 12px;
  margin: 8px 0 0;
}

@media (max-width: 768px) {
  .navbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
    padding: 14px;
  }

  .navbar-nav {
    width: 100%;
    gap: 6px;
    flex-wrap: wrap;
  }

  .nav-link {
    padding: 6px 10px;
    font-size: 13px;
  }
  .navbar {
    flex-direction: column;
    gap: 10px;
    padding: 12px 16px;
    border-radius: 12px;
    margin-bottom: 12px;
  }

  .navbar-brand {
    font-size: 20px;
  }

  .navbar-nav {
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    width: 100%;
  }

  .nav-link {
    padding: 6px 12px;
    font-size: 14px;
    flex: 1;
    text-align: center;
    justify-content: center;
    min-width: 0;
    white-space: nowrap;
  }

  .token-dialog :deep(.el-dialog) {
    width: 92% !important;
    margin: 16px auto;
    border-radius: 16px;
  }

  .dialog-header {
    padding: 20px 16px;
  }

  .dialog-title {
    font-size: 18px;
  }

  .dialog-subtitle {
    font-size: 13px;
  }

  .dialog-body {
    padding: 16px;
  }

  .code-input-group {
    flex-direction: column;
    gap: 8px;
  }

  .code-btn {
    width: 100%;
    min-width: auto;
  }
}
</style>
