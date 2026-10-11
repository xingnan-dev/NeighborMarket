import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import MarketView from './views/MarketView.vue'
import LoginView from './views/LoginView.vue'
import PublishView from './views/PublishView.vue'
import DetailView from './views/DetailView.vue'
import MineView from './views/MineView.vue'
import DashboardView from './views/DashboardView.vue'
import { setUnauthorizedHandler } from './api'
import { auth } from './auth'
import './style.css'
const router=createRouter({history:createWebHistory(),routes:[
 {path:'/',component:MarketView},{path:'/login',component:LoginView},{path:'/publish',component:PublishView},
 {path:'/items/:id',component:DetailView},{path:'/mine',component:MineView},{path:'/dashboard',component:DashboardView}
]})
setUnauthorizedHandler(() => {
 auth.user = null
 const current = router.currentRoute.value
 if (current.path !== '/login') router.push({path:'/login',query:{redirect:current.fullPath}})
})
createApp(App).use(router).mount('#app')
