<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { auth, loadUser, logout } from './auth'

const router = useRouter()
onMounted(loadUser)

async function signout() {
  await logout()
  router.push('/')
}
</script>

<template>
  <header class="site-header">
    <RouterLink class="brand" to="/" aria-label="邻里闲置首页">
      <span class="brand-mark" aria-hidden="true">邻</span>
      <strong class="brand-name">邻里闲置</strong>
    </RouterLink>
    <nav class="desktop-nav" aria-label="主导航">
      <RouterLink to="/">逛闲置</RouterLink>
      <RouterLink to="/publish">发布</RouterLink>
      <RouterLink to="/dashboard">社区数据</RouterLink>
      <RouterLink to="/mine">我的</RouterLink>
      <button v-if="auth.user" class="link" type="button" @click="signout">退出 {{ auth.user.nickname }}</button>
      <RouterLink v-else to="/login">登录</RouterLink>
    </nav>
  </header>
  <main><RouterView /></main>
  <nav class="mobile-nav" aria-label="移动端主导航">
    <RouterLink to="/">逛闲置</RouterLink>
    <RouterLink to="/publish">发布</RouterLink>
    <RouterLink to="/mine">我的</RouterLink>
  </nav>
</template>
