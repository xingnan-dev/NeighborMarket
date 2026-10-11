<script setup>
import {reactive,ref} from 'vue';import {useRoute,useRouter} from 'vue-router';import {api,messageOf} from '../api';import {auth} from '../auth'
const form=reactive({username:'neighbor_a',password:''}),busy=ref(false),error=ref(''),router=useRouter(),route=useRoute()
async function submit(){if(busy.value)return;if(!form.username||!form.password){error.value='请输入账号和密码';return}busy.value=true;error.value='';try{auth.user=(await api.post('/auth/login',form,{skipAuthRedirect:true})).data;const target=typeof route.query.redirect==='string'&&route.query.redirect.startsWith('/')?route.query.redirect:'/';router.push(target)}catch(e){error.value=messageOf(e)}finally{busy.value=false}}
</script>
<template><section class="panel narrow"><p class="eyebrow">演示社区账号</p><h1>欢迎回来</h1><form @submit.prevent="submit"><label>账号<input v-model.trim="form.username" autocomplete="username"></label><label>密码<input v-model="form.password" type="password" autocomplete="current-password"></label><p v-if="error" class="error">{{error}}</p><button :disabled="busy">{{busy?'登录中…':'登录'}}</button></form><p class="hint">演示账号见 README。登录使用 HttpOnly 会话 Cookie，不把凭据存入 localStorage。</p></section></template>
