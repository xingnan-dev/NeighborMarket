import { reactive } from 'vue'
import { api } from './api'
export const auth=reactive({user:null,ready:false})
export async function loadUser(){try{auth.user=(await api.get('/auth/me')).data}catch{auth.user=null}finally{auth.ready=true}}
export async function logout(){await api.post('/auth/logout');auth.user=null}
