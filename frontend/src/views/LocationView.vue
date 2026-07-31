<script setup lang="ts">
import { ref, reactive, onMounted, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import api from '../api'

// 注入认证状态
const authState = inject<{
  isAuthenticated: { value: boolean }
  setAuthenticated: () => void
  waitForAuth: () => Promise<void>
}>('authState')!

const addressList = ref<any[]>([])
const regionOptions = ref<any[]>([])
const regionProps = { value: 'code', label: 'name', children: 'child' }

const formRef = ref<FormInstance>()
const form = reactive({
  id: null as string | null,
  name: '',
  address: '',
  region: [] as string[],
  cityCode: null as number | null,
  addressKeyword: '',
  selectedAddress: null as any,
})

const rules = {
  region: [{ required: true, message: '请选择所在地区', trigger: 'change' }],
  selectedAddress: [{ required: true, message: '请选择详细地址', trigger: 'change' }],
}

const isAdding = ref(false)
const isEditing = ref(false)
const loading = ref(false)

async function loadAddressList() {
  loading.value = true
  try {
    const response = await api.get('/api/location')
    if (response.data.success) {
      addressList.value = response.data.data || []
    } else {
      ElMessage.error(response.data.msg || '获取地址列表失败')
    }
  } catch {
    ElMessage.error('获取地址列表失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

async function loadRegionOptions() {
  try {
    const response = await api.get('/api/location/cityCode')
    if (response.data.success) {
      regionOptions.value = response.data.data || []
    } else {
      ElMessage.error('获取地区数据失败')
    }
  } catch {
    ElMessage.error('获取地区数据失败，请检查网络连接')
  }
}

function startAddAddress() {
  isAdding.value = true
  isEditing.value = false
  resetForm()
}

function startEditAddress(address: any) {
  isEditing.value = true
  isAdding.value = false
  form.id = address.id
  form.name = address.name
  form.address = address.address
  form.region = []
  form.cityCode = address.cityCode
  form.addressKeyword = ''
  form.selectedAddress = null
}

async function queryAddress(queryString: string, callback: (results: any[]) => void) {
  if (!form.cityCode || !queryString.trim()) {
    callback([])
    return
  }

  try {
    const response = await api.get('/api/location/searchAddress', {
      keyword: queryString.trim(),
      cityCode: form.cityCode,
    })
    if (response.data.success) {
      const addresses = (response.data.data || []).map((item: any) => ({
        value: item.title,
        title: item.title,
        address: item.address,
        latitude: item.latitude,
        longitude: item.longitude,
        cityCode: item.cityCode,
        province: item.province,
        city: item.city,
        district: item.district,
      }))
      callback(addresses)
    } else {
      callback([])
    }
  } catch {
    callback([])
  }
}

function cancelOperation() {
  isAdding.value = false
  isEditing.value = false
  resetForm()
}

function resetForm() {
  form.id = null
  form.name = ''
  form.address = ''
  form.region = []
  form.cityCode = null
  form.addressKeyword = ''
  form.selectedAddress = null
  formRef.value?.resetFields()
}

function handleRegionChange(values: string[]) {
  if (values && values.length > 0) {
    form.cityCode = parseInt(values[values.length - 1] ?? '0')
  } else {
    form.cityCode = null
    form.selectedAddress = null
    form.addressKeyword = ''
  }
}

function handleAddressSelect(item: any) {
  form.selectedAddress = item
}

async function submitForm() {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      const submitData = {
        name: form.selectedAddress.title,
        address: form.selectedAddress.address,
        cityCode: form.cityCode,
        latitude: form.selectedAddress.latitude,
        longitude: form.selectedAddress.longitude,
      }

      loading.value = true
      try {
        const response = await api.post('/api/location', submitData)
        if (response.data.success) {
          ElMessage.success('地址新增成功')
          await loadAddressList()
          isAdding.value = false
          resetForm()
        } else {
          ElMessage.error(response.data.msg || '地址新增失败')
        }
      } catch {
        ElMessage.error('新增地址失败，请检查网络连接')
      } finally {
        loading.value = false
      }
    }
  })
}


function deleteAddress(id: string, index: number) {
  ElMessageBox.confirm('确定要删除这个地址吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      loading.value = true
      try {
        const response = await api.delete(`/api/location/${id}`)
        if (response.data.success) {
          ElMessage.success('地址删除成功')
          await loadAddressList()
        } else {
          ElMessage.error(response.data.msg || '地址删除失败')
        }
      } catch {
        ElMessage.error('删除地址失败，请检查网络连接')
      } finally {
        loading.value = false
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

onMounted(async () => {
  // 等待认证完成
  await authState?.waitForAuth()
  loadAddressList()
  loadRegionOptions()
})
</script>

<template>
  <div>
    <!-- Address list -->
    <el-card
      v-if="!isAdding && !isEditing"
      class="address-list-card mt-4"
      v-loading="loading"
    >
      <template #header>
        <div class="card-header">
          <span>已保存的地址</span>
          <el-button class="add-address-btn" @click="startAddAddress" :disabled="loading">
            新增地址
          </el-button>
        </div>
      </template>

      <div v-if="addressList.length === 0" class="empty-state">
        <el-empty description="暂无保存的地址">
          <el-button class="add-address-btn" @click="startAddAddress"> 新增第一个地址 </el-button>
        </el-empty>
      </div>

      <div v-else class="address-grid mt-4">
        <el-card
          v-for="(address, index) in addressList"
          :key="address.id"
          class="address-card"
          :style="{ animationDelay: index * 0.1 + 's' }"
        >
          <div>
            <div class="address-header">
              <div class="address-name">{{ address.name }}</div>
            </div>

            <p class="address-main">{{ address.address }}</p>

            <div class="operation-buttons">
              <el-button
                class="delete-btn"
                @click="deleteAddress(address.id, index)"
                :disabled="loading"
              >
                删除
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </el-card>

    <!-- Add address form -->
    <el-card
      v-if="isAdding"
      class="address-form-card mt-4"
    >
      <template #header>
        <div class="card-header">
          <span>新增地址</span>
          <el-button type="primary" link @click="cancelOperation"> 取消 </el-button>
        </div>
      </template>

      <el-form :model="form" ref="formRef" :rules="rules" label-width="100px" class="mt-4">
        <el-form-item label="所在地区" prop="region">
          <el-cascader
            v-model="form.region"
            :options="regionOptions"
            :props="regionProps"
            clearable
            placeholder="请选择省/市/区"
            @change="handleRegionChange"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="详细地址" prop="selectedAddress" v-if="form.cityCode">
          <el-autocomplete
            v-model="form.addressKeyword"
            :fetch-suggestions="queryAddress"
            placeholder="请输入详细地址"
            @select="handleAddressSelect"
            value-key="value"
            clearable
            style="width: 100%"
          >
            <template #default="{ item }">
              <div style="line-height: 1.5">
                <div style="font-weight: 500; color: #333">{{ item.title }}</div>
                <div style="font-size: 12px; color: #666; margin-top: 2px">
                  {{ item.address }}
                </div>
              </div>
            </template>
          </el-autocomplete>

          <div v-if="form.selectedAddress" class="selected-address">
            <p class="title">{{ form.selectedAddress.title }}</p>
            <p class="detail">{{ form.selectedAddress.address }}</p>
          </div>
        </el-form-item>

        <div class="form-actions">
          <el-button @click="cancelOperation" :disabled="loading">取消</el-button>
          <el-button type="primary" @click="submitForm" :loading="loading"> 确定 </el-button>
        </div>
      </el-form>
    </el-card>

  </div>
</template>

<style lang="scss" scoped>
// ============================================
// Card containers
// ============================================
.address-list-card,
.address-form-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

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
.address-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
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
  align-items: center;
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

.address-main {
  color: #666;
  line-height: 1.6;
  margin-bottom: 10px;
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

.edit-btn {
  background-color: #f0f7ff !important;
  color: #1890ff !important;
  border-color: #bbd7ff !important;

  &:hover {
    background-color: #e6f7ff !important;
    transform: translateY(-2px);
  }
}

.delete-btn {
  background-color: #fff2f0 !important;
  color: #ff4d4f !important;
  border-color: #ffccc7 !important;
  padding: 8px 20px !important;
  font-size: 14px !important;

  &:hover {
    background-color: #fff1f0 !important;
    transform: translateY(-2px);
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
// Selected address preview
// ============================================
.selected-address {
  margin-top: 10px;
  padding: 12px 15px;
  background-color: #f5f7fa;
  border-radius: 6px;
  border-left: 3px solid #36b37e;

  .title {
    font-weight: 500;
    margin-bottom: 5px;
    color: #333;
  }

  .detail {
    font-size: 13px;
    color: #666;
  }
}

// ============================================
// Form actions
// ============================================
.form-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid #f0f0f0;
}

// ============================================
// Utility
// ============================================
.mt-4 {
  margin-top: 16px;
}

// ============================================
// Responsive
// ============================================
@media screen and (max-width: 768px) {
  .address-list-card,
  .address-form-card {
    border-radius: 10px;

    :deep(.el-card__header) {
      padding: 14px 16px;
    }

    :deep(.el-card__body) {
      padding: 14px 16px;
    }
  }

  .card-header {
    span {
      font-size: 15px;
    }
  }

  .address-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .address-card {
    :deep(.el-card__body) {
      padding: 14px;
    }
  }

  .address-name {
    font-size: 15px;
  }

  .address-main {
    font-size: 13px;
    margin-bottom: 8px;
  }

  .operation-buttons {
    flex-direction: row;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 10px;

    .el-button {
      width: auto;
      min-width: 60px;
    }
  }

  // Form mobile adaptation
  :deep(.el-form) {
    .el-form-item {
      flex-direction: column;
      align-items: stretch;

      .el-form-item__label {
        text-align: left;
        padding-bottom: 4px;
        width: auto !important;
      }

      .el-form-item__content {
        margin-left: 0 !important;
      }
    }
  }

  .form-actions {
    flex-direction: column;
    gap: 8px;
    padding-top: 12px;

    .el-button {
      width: 100%;
    }
  }

  .selected-address {
    padding: 10px 12px;

    .title {
      font-size: 14px;
    }

    .detail {
      font-size: 12px;
    }
  }

  .empty-state {
    padding: 30px 16px;
  }
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
