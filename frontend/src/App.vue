<script setup>
import { onMounted } from 'vue';import { useRouter } from 'vue-router';import { auth,loadUser,logout } from './auth'
const router=useRouter();onMounted(loadUser)
async function signout(){await logout();router.push('/')}
</script>
<template><header><RouterLink class="brand" to="/"><span>邻</span> 邻里闲置</RouterLink><nav>
 <RouterLink to="/">逛闲置</RouterLink><RouterLink to="/publish">发布</RouterLink><RouterLink to="/mine">我的</RouterLink>
 <button v-if="auth.user" class="link" @click="signout">退出 {{auth.user.nickname}}</button><RouterLink v-else to="/login">登录</RouterLink>
</nav></header><main><RouterView/></main><nav class="mobile-nav"><RouterLink to="/">逛闲置</RouterLink><RouterLink to="/publish">发布</RouterLink><RouterLink to="/mine">我的</RouterLink></nav></template>
