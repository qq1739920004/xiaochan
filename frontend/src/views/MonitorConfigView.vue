<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, View, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import api from '../api'

// 注入认证状态
const authState = inject<{
  isAuthenticated: { value: boolean }
  setAuthenticated: () => void
  waitForAuth: () => Promise<void>
}>('authState')!

const router = useRouter()

const configList = ref<any[]>([])
const loading = ref(false)
const showDetail = ref(false)
const currentDetail = ref<any>(null)

// 移动端检测
const isMobile = ref(false)
function checkMobile() {
  isMobile.value = window.innerWidth <= 768
}

// 运行记录相关
const historyDialogVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref<any[]>([])
const historyConfigId = ref<number | null>(null)
const historyConfigName = ref('')
const historyPagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

// 对话框相关
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const currentEditConfig = ref<any>(null)
const submitLoading = ref(false)
const locationList = ref<any[]>([])
const formRef = ref<FormInstance>()

const weekOptions = [
  { label: '周一', value: '1' },
  { label: '周二', value: '2' },
  { label: '周三', value: '3' },
  { label: '周四', value: '4' },
  { label: '周五', value: '5' },
  { label: '周六', value: '6' },
  { label: '周日', value: '7' },
]

const form = reactive({
  locationId: null as number | null,
  startHour: 8,
  endHour: 22,
  weeks: [] as string[],
  cron: '',
  remindFrequency: 'ONCE',
  minimumPayExtNotifyConfig: {
    minimumPay: 1,
  },
  storeKeywordExtNotifyConfig: {
    keyword: '',
    limitDistance: true,
  },
})

const cronCollapseActive = ref<string[]>([])

const configType = ref<string>('MINIMUM_PAY')

const formRules = {
  locationId: [{ required: true, message: '请选择位置', trigger: 'change' }],
  startHour: [
    {
      validator: (_rule: any, value: number, callback: any) => {
        if (!form.cron && (value === null || value === undefined)) {
          callback(new Error('未填写 cron 时，开始时间必填'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  endHour: [
    {
      validator: (_rule: any, value: number, callback: any) => {
        if (!form.cron && (value === null || value === undefined)) {
          callback(new Error('未填写 cron 时，结束时间必填'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  weeks: [
    {
      validator: (_rule: any, value: string[], callback: any) => {
        if (!form.cron && (!value || value.length === 0)) {
          callback(new Error('未填写 cron 时，请至少选择一天'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  cron: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        const trimmed = value ? value.trim() : ''
        if (!trimmed) {
          if (!form.startHour || !form.endHour || !form.weeks || form.weeks.length === 0) {
            callback(new Error('未填写 cron 时，开始时间、结束时间、运行星期必须填写'))
          } else {
            callback()
          }
        } else {
          const parts = trimmed.split(/\s+/)
          if (parts.length !== 6) {
            callback(new Error('cron 表达式应为 6 位，以空格分隔（含秒），如：0 15 9 * * ?'))
          } else {
            callback()
          }
        }
      },
      trigger: 'blur',
    },
  ],
  'minimumPayExtNotifyConfig.minimumPay': [
    { required: true, message: '请输入最小实付金额', trigger: 'blur' },
  ],
  'storeKeywordExtNotifyConfig.keyword': [
    {
      validator: (_rule: any, value: string, callback: any) => {
        const type = isEdit.value ? currentEditConfig.value?.type : configType.value
        if (type === 'STORE_KEYWORD' && (!value || !value.trim())) {
          callback(new Error('请输入门店关键字'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function getTypeText(type: string) {
  switch (type) {
    case 'STORE_ACTIVITY':
      return '指定门店'
    case 'MINIMUM_PAY':
      return '最小实付'
    case 'STORE_KEYWORD':
      return '门店关键字'
    default:
      return '未知类型'
  }
}

function getTypeTagType(type: string) {
  switch (type) {
    case 'STORE_ACTIVITY':
      return '' as const
    case 'MINIMUM_PAY':
      return 'success' as const
    case 'STORE_KEYWORD':
      return 'warning' as const
    default:
      return 'info' as const
  }
}

function getLocationName(locationId: number) {
  const loc = locationList.value.find((l: any) => l.id === locationId)
  return loc ? loc.name : '未知位置'
}

function formatWeeks(weeks: string) {
  if (!weeks) return ''
  const weekMap: Record<string, string> = {
    '1': '周一',
    '2': '周二',
    '3': '周三',
    '4': '周四',
    '5': '周五',
    '6': '周六',
    '7': '周日',
  }
  return weeks
    .split(',')
    .map((w) => weekMap[w.trim()] || w)
    .join(', ')
}

function getPlatformNameById(type: number) {
  switch (type) {
    case 1:
      return '美团'
    case 2:
      return '饿了么'
    case 3:
      return '京东'
    default:
      return '未知平台'
  }
}

function getFrequencyText(frequency: string) {
  switch (frequency) {
    case 'DAILY':
      return '每日提醒'
    case 'NONE':
      return '不提醒'
    default:
      return '只提醒一次'
  }
}

async function loadConfigList() {
  loading.value = true
  try {
    const response = await api.get('/api/notify/config/list')
    if (response.data.success) {
      configList.value = response.data.data || []
    } else {
      ElMessage.error(response.data.msg || '获取监控配置列表失败')
    }
  } catch {
    ElMessage.error('获取监控配置列表失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

async function loadLocations() {
  try {
    const response = await api.get('/api/location')
    if (response.data.success) {
      locationList.value = response.data.data || []
    } else {
      ElMessage.error(response.data.msg || '获取位置列表失败')
    }
  } catch {
    ElMessage.error('获取位置列表失败，请检查网络连接')
  }
}

function resetForm() {
  formRef.value?.resetFields()
  form.locationId = null
  form.startHour = 8
  form.endHour = 22
  form.weeks = []
  form.cron = ''
  form.remindFrequency = 'ONCE'
  form.minimumPayExtNotifyConfig = { minimumPay: 1 }
  form.storeKeywordExtNotifyConfig = { keyword: '', limitDistance: true }
  cronCollapseActive.value = []
  configType.value = 'MINIMUM_PAY'
  isEdit.value = false
  editingId.value = null
  currentEditConfig.value = null
}

function showAddDialog() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function showEditDialog(config: any) {
  resetForm()
  isEdit.value = true
  editingId.value = config.id
  currentEditConfig.value = config
  form.locationId = config.locationId
  form.startHour = config.startHour ?? 8
  form.endHour = config.endHour ?? 22
  form.weeks = config.weeks ? config.weeks.split(',') : []
  form.cron = config.cron || ''
  form.remindFrequency = config.storeExtNotifyConfig?.remindFrequency || 'ONCE'
  cronCollapseActive.value = form.cron ? ['cron'] : []
  if (config.type === 'MINIMUM_PAY' && config.minimumPayExtNotifyConfig) {
    form.minimumPayExtNotifyConfig.minimumPay = config.minimumPayExtNotifyConfig.minimumPay
  }
  if (config.type === 'STORE_KEYWORD' && config.storeKeywordExtNotifyConfig) {
    form.storeKeywordExtNotifyConfig.keyword = config.storeKeywordExtNotifyConfig.keyword
    form.storeKeywordExtNotifyConfig.limitDistance = config.storeKeywordExtNotifyConfig.limitDistance !== false
  }
  dialogVisible.value = true
}

function submitForm() {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      submitLoading.value = true
      try {
        const trimmedCron = form.cron ? form.cron.trim() : ''
        const requestData: any = {
          locationId: form.locationId,
          cron: trimmedCron || null,
          startHour: trimmedCron ? (form.startHour ?? null) : form.startHour,
          endHour: trimmedCron ? (form.endHour ?? null) : form.endHour,
          weeks: trimmedCron ? (form.weeks.length > 0 ? form.weeks.join(',') : null) : form.weeks.join(','),
        }

        if (isEdit.value) {
          requestData.id = editingId.value
          requestData.type = currentEditConfig.value.type
          if (currentEditConfig.value.type === 'MINIMUM_PAY') {
            requestData.minimumPayExtNotifyConfig = form.minimumPayExtNotifyConfig
          }
          if (currentEditConfig.value.type === 'STORE_ACTIVITY') {
            requestData.storeExtNotifyConfig = {
              ...currentEditConfig.value.storeExtNotifyConfig,
              remindFrequency: form.remindFrequency,
            }
          }
          if (currentEditConfig.value.type === 'STORE_KEYWORD') {
            requestData.storeKeywordExtNotifyConfig = form.storeKeywordExtNotifyConfig
          }
        } else {
          requestData.type = configType.value
          if (configType.value === 'MINIMUM_PAY') {
            requestData.minimumPayExtNotifyConfig = form.minimumPayExtNotifyConfig
          } else if (configType.value === 'STORE_KEYWORD') {
            requestData.storeKeywordExtNotifyConfig = form.storeKeywordExtNotifyConfig
          }
        }

        const response = await api.post('/api/notify/config', requestData)
        if (response.data.success) {
          ElMessage.success(isEdit.value ? '监控配置更新成功' : '监控配置保存成功')
          dialogVisible.value = false
          await loadConfigList()
        } else {
          ElMessage.error(response.data.msg || '保存监控配置失败')
        }
      } catch {
        ElMessage.error('保存监控配置失败，请检查网络连接')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

function deleteConfig(configId: number) {
  ElMessageBox.confirm('确定要删除这个监控配置吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      loading.value = true
      try {
        const response = await api.delete(`/api/notify/config/${configId}`)
        if (response.data.success) {
          ElMessage.success('监控配置删除成功')
          await loadConfigList()
        } else {
          ElMessage.error(response.data.msg || '监控配置删除失败')
        }
      } catch {
        ElMessage.error('删除监控配置失败，请检查网络连接')
      } finally {
        loading.value = false
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

async function toggleStatus(config: any) {
  const targetStatus = config.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  const actionText = targetStatus === 'ENABLE' ? '启用' : '停用'
  try {
    const response = await api.put(`/api/notify/config/${config.id}/status`, null, {
      params: { status: targetStatus },
    })
    if (response.data.success) {
      ElMessage.success(`${actionText}成功`)
      await loadConfigList()
    } else {
      ElMessage.error(response.data.msg || `${actionText}失败`)
    }
  } catch {
    ElMessage.error(`${actionText}失败，请检查网络连接`)
  }
}

async function showExecHistory(config: any) {
  historyConfigId.value = config.id
  historyConfigName.value = getLocationName(config.locationId)
  historyPagination.pageNum = 1
  historyPagination.total = 0
  historyList.value = []
  historyDialogVisible.value = true
  await loadExecHistory()
}

async function loadExecHistory() {
  historyLoading.value = true
  try {
    const response = await api.post('/api/task-exec-history/page', {
      notifyConfigId: historyConfigId.value,
      pageNum: historyPagination.pageNum,
      pageSize: historyPagination.pageSize,
    })
    if (response.data.success) {
      const pageData = response.data.data
      historyList.value = pageData?.records || []
      historyPagination.total = pageData?.total || 0
    } else {
      ElMessage.error(response.data.msg || '获取运行记录失败')
    }
  } catch {
    ElMessage.error('获取运行记录失败，请检查网络连接')
  } finally {
    historyLoading.value = false
  }
}

function handleHistoryPageChange(page: number) {
  historyPagination.pageNum = page
  loadExecHistory()
}

function formatDateTime(dateStr: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const y = date.getFullYear()
  const m = (date.getMonth() + 1).toString().padStart(2, '0')
  const d = date.getDate().toString().padStart(2, '0')
  const h = date.getHours().toString().padStart(2, '0')
  const min = date.getMinutes().toString().padStart(2, '0')
  const s = date.getSeconds().toString().padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}:${s}`
}

function viewDetail(config: any) {
  currentDetail.value = config
  showDetail.value = true
}

function handleRecord(config: any) {
  const storeInfo = config.storeExtNotifyConfig?.storeInfo
  if (!storeInfo) return
  router.push({
    path: '/store-inventory',
    query: {
      uniqueId: storeInfo.uniqId,
      name: storeInfo.name,
    },
  })
}

function backToList() {
  showDetail.value = false
  currentDetail.value = null
}

function refreshList() {
  loadConfigList()
}

function handleCardCommand(command: string, config: any) {
  switch (command) {
    case 'detail':
      viewDetail(config)
      break
    case 'edit':
      showEditDialog(config)
      break
    case 'delete':
      deleteConfig(config.id)
      break
  }
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  await authState?.waitForAuth()
  loadConfigList()
  loadLocations()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<template>
  <div>
    <!-- 配置列表 -->
    <el-card
      v-if="!showDetail"
      class="mt-4"
      style="border-radius: 12px; border: none; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05)"
      v-loading="loading"
    >
      <template #header>
        <div class="card-header">
          <span>监控配置管理</span>
          <div style="display: flex; gap: 10px">
            <el-button class="add-address-btn" @click="showAddDialog"> 新增配置 </el-button>
            <el-button class="add-address-btn" @click="refreshList" :disabled="loading">
              刷新列表
            </el-button>
          </div>
        </div>
      </template>

      <div v-if="configList.length === 0" class="empty-state">
        <el-empty description="暂无监控配置">
          <el-button class="add-address-btn" @click="refreshList"> 刷新列表 </el-button>
        </el-empty>
      </div>

      <div v-else class="config-grid mt-4">
        <el-card
          v-for="(config, index) in configList"
          :key="config.id"
          class="address-card"
          :style="{ animationDelay: index * 0.1 + 's' }"
        >
          <div>
            <div class="address-header">
              <div class="address-name">{{ getLocationName(config.locationId) }}</div>
              <div style="display: flex; gap: 6px; margin-left: auto; align-items: center;">
                <el-tag
                  :type="config.status === 'ENABLE' ? 'success' : 'info'"
                  size="small"
                >
                  {{ config.status === 'ENABLE' ? '启用中' : '已停用' }}
                </el-tag>
                <el-tag :type="getTypeTagType(config.type)" size="small">
                  {{ getTypeText(config.type) }}
                </el-tag>
              </div>
            </div>

            <div class="notify-info">
              <p v-if="!config.cron" class="info-item">
                <span>运行时间：{{ config.startHour }}:00 - {{ config.endHour }}:00</span>
              </p>
              <p v-if="!config.cron" class="info-item">
                <span>运行星期：{{ formatWeeks(config.weeks) }}</span>
              </p>
              <p v-if="config.cron" class="info-item">
                <span>cron：{{ config.cron }}</span>
              </p>
              <p
                v-if="config.type === 'MINIMUM_PAY' && config.minimumPayExtNotifyConfig"
                class="info-item"
              >
                <span
                  >最小实付：{{ config.minimumPayExtNotifyConfig.minimumPay }}元</span
                >
              </p>
              <p
                v-if="config.type === 'STORE_KEYWORD' && config.storeKeywordExtNotifyConfig"
                class="info-item"
              >
                <span>关键字：{{ config.storeKeywordExtNotifyConfig.keyword }}</span>
              </p>
              <p
                v-if="config.type === 'STORE_KEYWORD' && config.storeKeywordExtNotifyConfig"
                class="info-item"
              >
                <span>限距离制：{{ config.storeKeywordExtNotifyConfig.limitDistance !== false ? '开启（≤3500米）' : '关闭' }}</span>
              </p>
              <template
                v-if="
                  config.type === 'STORE_ACTIVITY' &&
                  config.storeExtNotifyConfig?.storeInfo
                "
              >
                <p class="info-item">
                  <span
                    >门店：{{ config.storeExtNotifyConfig.storeInfo.name }}（{{ getPlatformNameById(config.storeExtNotifyConfig.storeInfo.type) }}）</span
                  >
                </p>
                <p class="info-item">
                  <span>距离：{{ config.storeExtNotifyConfig.storeInfo.distance }}</span>
                </p>
                <p class="info-item">
                  <span>提醒频率：{{ getFrequencyText(config.storeExtNotifyConfig.remindFrequency) }}</span>
                </p>
              </template>
            </div>

            <div class="operation-buttons">
              <el-button
                size="small"
                :class="config.status === 'ENABLE' ? 'disable-btn' : 'enable-btn'"
                @click="toggleStatus(config)"
                :disabled="loading"
              >
                {{ config.status === 'ENABLE' ? '停用' : '启用' }}
              </el-button>
              <el-button
                size="small"
                class="history-btn"
                @click="showExecHistory(config)"
                :disabled="loading"
              >
                运行记录
              </el-button>
              <el-button
                v-if="config.type === 'STORE_ACTIVITY' && config.storeExtNotifyConfig?.storeInfo"
                size="small"
                class="record-btn"
                @click="handleRecord(config)"
              >
                记录
              </el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleCardCommand(cmd, config)">
                <el-button size="small" class="more-btn" :disabled="loading">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">
                      <el-icon><View /></el-icon>查看详情
                    </el-dropdown-item>
                    <el-dropdown-item command="edit">
                      <el-icon><Edit /></el-icon>编辑
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span style="color: #ff4d4f"><el-icon><Delete /></el-icon>删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </el-card>
      </div>
    </el-card>

    <!-- 详情视图 -->
    <el-card
      v-if="showDetail"
      class="mt-4"
      style="border-radius: 12px; border: none; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05)"
    >
      <template #header>
        <div class="card-header">
          <span>监控配置详情</span>
          <el-button type="primary" link @click="backToList"> 返回列表 </el-button>
        </div>
      </template>

      <div v-if="currentDetail" class="detail-content">
        <div class="detail-section">
          <h3>基本信息</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>配置ID：</label>
              <span>{{ currentDetail.id }}</span>
            </div>
            <div class="detail-item">
              <label>类型：</label>
              <span>{{ getTypeText(currentDetail.type) }}</span>
            </div>
            <div class="detail-item">
              <label>位置：</label>
              <span>{{ getLocationName(currentDetail.locationId) }}</span>
            </div>
            <div v-if="!currentDetail.cron" class="detail-item">
              <label>运行时间：</label>
              <span>{{ currentDetail.startHour }}:00 - {{ currentDetail.endHour }}:00</span>
            </div>
            <div v-if="!currentDetail.cron" class="detail-item">
              <label>运行星期：</label>
              <span>{{ formatWeeks(currentDetail.weeks) }}</span>
            </div>
            <div v-if="currentDetail.cron" class="detail-item">
              <label>cron 表达式：</label>
              <span>{{ currentDetail.cron }}</span>
            </div>
          </div>
        </div>

        <div
          class="detail-section"
          v-if="
            currentDetail.type === 'STORE_ACTIVITY' &&
            currentDetail.storeExtNotifyConfig?.storeInfo
          "
        >
          <h3>门店配置</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>门店名称：</label>
              <span>{{ currentDetail.storeExtNotifyConfig.storeInfo.name }}</span>
            </div>
            <div class="detail-item">
              <label>门店ID：</label>
              <span>{{ currentDetail.storeExtNotifyConfig.storeInfo.storeId }}</span>
            </div>
            <div class="detail-item">
              <label>门店平台：</label>
              <span>{{
                getPlatformNameById(currentDetail.storeExtNotifyConfig.storeInfo.type)
              }}</span>
            </div>
            <div class="detail-item">
              <label>距离：</label>
              <span>{{ currentDetail.storeExtNotifyConfig.storeInfo.distance }}</span>
            </div>
            <div class="detail-item">
              <label>提醒频率：</label>
              <span>{{ getFrequencyText(currentDetail.storeExtNotifyConfig.remindFrequency) }}</span>
            </div>
          </div>
        </div>

        <div
          class="detail-section"
          v-if="currentDetail.type === 'MINIMUM_PAY' && currentDetail.minimumPayExtNotifyConfig"
        >
          <h3>最小实付配置</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>最小实付金额：</label>
              <span>{{ currentDetail.minimumPayExtNotifyConfig.minimumPay }}元</span>
            </div>
          </div>
        </div>

        <div
          class="detail-section"
          v-if="currentDetail.type === 'STORE_KEYWORD' && currentDetail.storeKeywordExtNotifyConfig"
        >
          <h3>关键字配置</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>门店关键字：</label>
              <span>{{ currentDetail.storeKeywordExtNotifyConfig.keyword }}</span>
            </div>
            <div class="detail-item">
              <label>限距离制：</label>
              <span>{{ currentDetail.storeKeywordExtNotifyConfig.limitDistance !== false ? '开启（≤3500米）' : '关闭' }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      :title="isEdit ? '编辑监控配置' : '新增监控配置'"
      v-model="dialogVisible"
      width="500px"
      class="monitor-dialog"
      @close="resetForm"
      :close-on-click-modal="false"
      style="margin-top: 5vh"
    >
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="110px">
        <!-- 编辑模式显示只读信息 -->
        <template v-if="isEdit && currentEditConfig">
          <el-form-item label="类型">
            <span>{{ getTypeText(currentEditConfig.type) }}</span>
          </el-form-item>
          <el-form-item label="位置">
            <span>{{ getLocationName(currentEditConfig.locationId) }}</span>
          </el-form-item>
        </template>

        <!-- 新增模式选择位置 -->
        <template v-if="!isEdit">
          <el-form-item label="位置" prop="locationId">
            <el-select v-model="form.locationId" placeholder="请选择位置" style="width: 100%">
              <el-option
                v-for="loc in locationList"
                :key="loc.id"
                :label="loc.name"
                :value="loc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="监控类型">
            <el-select v-model="configType" style="width: 100%">
              <el-option label="最小实付" value="MINIMUM_PAY" />
              <el-option label="门店关键字" value="STORE_KEYWORD" />
            </el-select>
          </el-form-item>
        </template>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startHour">
              <el-input-number
                v-model="form.startHour"
                :min="0"
                :max="23"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endHour">
              <el-input-number
                v-model="form.endHour"
                :min="0"
                :max="23"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="运行星期" prop="weeks">
          <el-checkbox-group v-model="form.weeks">
            <el-checkbox
              v-for="opt in weekOptions"
              :key="opt.value"
              :label="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item prop="cron">
          <el-collapse v-model="cronCollapseActive" style="width: 100%">
            <el-collapse-item title="自定义 cron 表达式（高级）" name="cron">
              <el-input
                v-model="form.cron"
                placeholder="如：0 15 9 * * ?（6位，含秒）"
                clearable
                style="width: 100%"
              />
              <p class="cron-tip">填写后将完全按 cron 执行，无需设置开始/结束时间和运行星期。</p>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>

        <el-form-item
          v-if="isEdit && currentEditConfig?.type === 'STORE_ACTIVITY'"
          label="提醒频率"
        >
          <el-radio-group v-model="form.remindFrequency">
            <el-radio label="ONCE" value="ONCE">只提醒一次</el-radio>
            <el-radio label="DAILY" value="DAILY">每日提醒</el-radio>
            <el-radio label="NONE" value="NONE">不提醒</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 最小实付金额：新增且类型为 MINIMUM_PAY 时显示，编辑时仅 MINIMUM_PAY 类型显示 -->
        <template
          v-if="!isEdit ? configType === 'MINIMUM_PAY' : currentEditConfig?.type === 'MINIMUM_PAY'"
        >
          <el-form-item label="最小实付" prop="minimumPayExtNotifyConfig.minimumPay">
            <el-input-number
              v-model="form.minimumPayExtNotifyConfig.minimumPay"
              :min="1"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </template>

        <!-- 门店关键字：新增且类型为 STORE_KEYWORD 时显示，编辑时仅 STORE_KEYWORD 类型显示 -->
        <template
          v-if="!isEdit ? configType === 'STORE_KEYWORD' : currentEditConfig?.type === 'STORE_KEYWORD'"
        >
          <el-form-item label="门店关键字" prop="storeKeywordExtNotifyConfig.keyword">
            <el-input
              v-model="form.storeKeywordExtNotifyConfig.keyword"
              placeholder="请输入门店关键字，如：麦当劳"
              clearable
            />
          </el-form-item>
          <el-form-item label="限距离制">
            <el-checkbox v-model="form.storeKeywordExtNotifyConfig.limitDistance">
              开启后仅推送距离 3500 米以内的门店
            </el-checkbox>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="submitForm" :loading="submitLoading">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 运行记录对话框 -->
    <el-dialog
      :title="`运行记录 - ${historyConfigName}`"
      v-model="historyDialogVisible"
      :width="isMobile ? 'calc(100% - 32px)' : '900px'"
      class="history-dialog"
      style="margin-top: 5vh"
    >
      <div v-loading="historyLoading">
        <!-- PC端表格 -->
        <div class="history-table-wrapper">
          <el-table :data="historyList" style="width: 100%" empty-text="暂无运行记录">
            <el-table-column label="开始时间" min-width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column label="结束时间" min-width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="类型" min-width="90">
              <template #default="{ row }">
                <el-tag :type="getTypeTagType(row.notifyType)" size="small">
                  {{ getTypeText(row.notifyType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="通知门店数" min-width="100" align="center">
              <template #default="{ row }">
                {{ row.notifyStoreCount ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.remark || '-' }}
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- H5端卡片列表 -->
        <div class="history-card-list">
          <div v-if="historyList.length === 0" class="history-empty">
            暂无运行记录
          </div>
          <div
            v-for="item in historyList"
            :key="item.id"
            class="history-card-item"
          >
            <div class="history-card-header">
              <span class="history-status" :class="item.success ? 'status-success' : 'status-fail'">
                {{ item.success ? '成功' : '失败' }}
              </span>
              <el-tag :type="getTypeTagType(item.notifyType)" size="small">
                {{ getTypeText(item.notifyType) }}
              </el-tag>
            </div>
            <div class="history-card-body">
              <p><span class="label">开始：</span>{{ formatDateTime(item.startTime) }}</p>
              <p><span class="label">结束：</span>{{ formatDateTime(item.endTime) }}</p>
              <p><span class="label">通知门店：</span>{{ item.notifyStoreCount ?? '-' }} 家</p>
              <p v-if="item.remark"><span class="label">备注：</span>{{ item.remark }}</p>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="history-pagination" v-if="historyPagination.total > 0">
          <el-pagination
            v-model:current-page="historyPagination.pageNum"
            :page-size="historyPagination.pageSize"
            :total="historyPagination.total"
            layout="total, prev, pager, next"
            small
            @current-change="handleHistoryPageChange"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
// ============================================
// Card header
// ============================================
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  span {
    font-size: 16px;
    font-weight: 600;
    color: #333;
  }
}

// ============================================
// Grid layout
// ============================================
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(640px, 1fr));
  gap: 20px;

  @media screen and (min-width: 2640px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media screen and (min-width: 1980px) and (max-width: 2639px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media screen and (min-width: 1320px) and (max-width: 1979px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media screen and (max-width: 1319px) {
    grid-template-columns: 1fr;
  }
}

// ============================================
// Card styles
// ============================================
.address-card {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  border-radius: 12px;
  border: none;
  overflow: hidden;
  animation: fadeIn 0.5s ease forwards;

  &:hover {
    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.08), 0 6px 6px rgba(0, 0, 0, 0.05);
    transform: translateY(-4px);
  }
}

.address-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.address-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

// ============================================
// Notify info
// ============================================
.notify-info {
  margin-bottom: 15px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}

// ============================================
// Buttons
// ============================================
.operation-buttons {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
  margin-top: 15px;
}

.history-btn {
  background-color: #f0f5ff !important;
  color: #722ed1 !important;
  border-color: #d3adf7 !important;
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.25s ease;

  &:hover {
    background-color: #f9f0ff !important;
    border-color: #b37feb !important;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(114, 46, 209, 0.15);
  }
}

.record-btn {
  background-color: #fff7e6 !important;
  color: #d97706 !important;
  border-color: #fcd34d !important;
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.25s ease;

  &:hover {
    background-color: #fef3c7 !important;
    border-color: #fbbf24 !important;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(217, 119, 6, 0.15);
  }
}

.enable-btn {
  background-color: #f6ffed !important;
  color: #52c41a !important;
  border-color: #b7eb8f !important;
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.25s ease;

  &:hover {
    background-color: #d9f7be !important;
    border-color: #95de64 !important;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(82, 196, 26, 0.15);
  }
}

.disable-btn {
  background-color: #fff7e6 !important;
  color: #fa8c16 !important;
  border-color: #ffd591 !important;
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.25s ease;

  &:hover {
    background-color: #ffe7ba !important;
    border-color: #ffc069 !important;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(250, 140, 22, 0.15);
  }
}

.more-btn {
  background-color: #fafafa !important;
  color: #666 !important;
  border-color: #e0e0e0 !important;
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.25s ease;

  &:hover {
    background-color: #f5f5f5 !important;
    color: #333 !important;
    border-color: #d0d0d0 !important;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }

  .el-icon--right {
    margin-left: 4px;
    font-size: 12px;
    transition: transform 0.2s;
  }
}

.add-address-btn {
  background-color: #87ceeb !important;
  color: #fff !important;
  border-color: #87ceeb !important;

  &:hover {
    background-color: #6bb6e6 !important;
    transform: translateY(-2px);
  }
}

// ============================================
// Empty state
// ============================================
.empty-state {
  text-align: center;
  padding: 40px 20px;
  background-color: #fff;
  border-radius: 8px;
  margin-top: 20px;
}

// ============================================
// Detail view
// ============================================
.detail-content {
  padding: 20px;
}

.detail-section {
  margin-bottom: 30px;

  h3 {
    font-size: 16px;
    font-weight: 600;
    color: #333;
    margin-bottom: 15px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 15px;
}

.detail-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;

  label {
    font-weight: 500;
    color: #555;
    min-width: 120px;
    margin-right: 10px;
  }

  span {
    color: #333;
    flex: 1;
  }
}

// ============================================
// Dialog footer
// ============================================
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

// ============================================
// Utility
// ============================================
.mt-4 {
  margin-top: 16px;
}

// ============================================
// H5 / Mobile responsive
// ============================================
@media screen and (max-width: 768px) {
  // 卡片头部
  .card-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;

    span {
      font-size: 18px;
    }

    div {
      width: 100%;
      display: flex;
      gap: 8px;
    }
  }

  .add-address-btn {
    flex: 1;
    font-size: 14px !important;
    padding: 10px 0 !important;
  }

  // 卡片网格
  .config-grid {
    gap: 16px;
  }

  // 卡片内容
  .address-card {
    :deep(.el-card__body) {
      padding: 16px;
    }
  }

  .address-header {
    margin-bottom: 14px;
  }

  .address-name {
    font-size: 17px;
  }

  // 信息条目
  .info-item {
    font-size: 15px;
    margin-bottom: 10px;
    line-height: 1.7;
  }

  // 操作按钮 - 增大触控区域
  .operation-buttons {
    gap: 10px;
    margin-top: 16px;
    justify-content: flex-end;

    .el-button {
      font-size: 14px !important;
      padding: 8px 16px !important;
      height: auto !important;
      min-height: 36px;
    }
  }

  // 详情视图
  .detail-content {
    padding: 12px 4px;
  }

  .detail-section h3 {
    font-size: 17px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .detail-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    padding: 12px 0;
    font-size: 15px;

    label {
      min-width: auto;
      margin-right: 0;
      font-size: 14px;
      color: #888;
    }

    span {
      font-size: 15px;
    }
  }

  // 对话框
  .dialog-footer {
    .el-button {
      flex: 1;
      font-size: 15px !important;
      padding: 12px 0 !important;
      height: auto !important;
      min-height: 42px;
    }
  }
}

// 对话框移动端适配（需要穿透 scoped）
.monitor-dialog {
  :deep(.el-dialog) {
    @media screen and (max-width: 768px) {
      width: 92% !important;
      margin: 16px auto !important;
    }
  }

  :deep(.el-dialog__body) {
    @media screen and (max-width: 768px) {
      padding: 16px 12px;
    }
  }

  // 表单移动端适配
  :deep(.el-form) {
    @media screen and (max-width: 768px) {
      .el-form-item__label {
        font-size: 14px;
      }

      .el-form-item {
        margin-bottom: 20px;
      }

      .el-input-number {
        .el-input__inner {
          font-size: 15px;
        }
      }

      .el-select {
        .el-input__inner {
          font-size: 15px;
        }
      }

      .el-checkbox-group {
        display: flex;
        flex-wrap: wrap;
        gap: 8px 4px;

        .el-checkbox {
          margin-right: 0;
          padding: 6px 10px;
          border: 1px solid #dcdfe6;
          border-radius: 6px;

          &.is-checked {
            border-color: #409eff;
            background-color: #ecf5ff;
          }
        }
      }

      // 时间输入行在小屏幕上垂直排列
      .el-row {
        .el-col {
          max-width: 100%;
          flex: 0 0 100%;

          &:first-child {
            margin-bottom: 4px;
          }
        }
      }
    }
  }
}

// 运行记录对话框适配
.history-dialog {
  :deep(.el-dialog) {
    @media screen and (max-width: 768px) {
      width: calc(100% - 32px) !important;
      max-width: 100vw !important;
      margin: 16px auto !important;
    }
  }

  :deep(.el-dialog__body) {
    @media screen and (max-width: 768px) {
      padding: 12px 8px;
    }
  }
}

// ============================================
// History dialog content
// ============================================
.history-table-wrapper {
  display: block;
}

.history-card-list {
  display: none;
  width: 100%;
  box-sizing: border-box;
}

.history-empty {
  text-align: center;
  padding: 40px 20px;
  color: #999;
  font-size: 14px;
}

.history-card-item {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 12px;
  transition: box-shadow 0.2s;
  max-width: 100%;
  box-sizing: border-box;
  overflow-wrap: break-word;
  word-break: break-word;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.history-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.history-status {
  font-size: 13px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;

  &.status-success {
    background-color: #f6ffed;
    color: #52c41a;
  }

  &.status-fail {
    background-color: #fff2f0;
    color: #ff4d4f;
  }
}

.history-card-body {
  p {
    margin: 6px 0;
    font-size: 13px;
    color: #555;
    line-height: 1.6;
  }

  .label {
    color: #999;
    font-size: 12px;
  }
}

.history-pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

@media screen and (max-width: 768px) {
  .history-table-wrapper {
    display: none;
  }

  .history-card-list {
    display: block;
  }

  .history-pagination {
    :deep(.el-pagination) {
      flex-wrap: wrap;
      justify-content: center;
    }
  }
}

.cron-tip {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
  line-height: 1.5;
}

// ============================================
// Animations
// ============================================
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
