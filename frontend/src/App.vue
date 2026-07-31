<script setup lang="ts">
import { ref, provide, readonly } from 'vue'
import NavBar from './components/NavBar.vue'

// 认证状态管理
const isAuthenticated = ref(false)
let authResolve: () => void
const authPromise = new Promise<void>((resolve) => {
  authResolve = resolve
})

// 提供给子组件的方法
const setAuthenticated = () => {
  isAuthenticated.value = true
  authResolve()
}

// 等待认证完成
const waitForAuth = () => authPromise

// 提供给所有子组件
provide('authState', {
  isAuthenticated: readonly(isAuthenticated),
  setAuthenticated,
  waitForAuth,
})
</script>

<template>
  <div class="container">
    <NavBar />
    <router-view v-slot="{ Component }">
      <keep-alive include="HomeView">
        <component :is="Component" />
      </keep-alive>
    </router-view>
  </div>
</template>

<style scoped>
.container {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px;
}

@media screen and (max-width: 768px) {
  .container {
    padding: 10px;
  }
}
</style>
