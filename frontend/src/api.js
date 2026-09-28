import axios from 'axios'
export const api=axios.create({baseURL:'/api',withCredentials:true,headers:{'Content-Type':'application/json'}})
export function messageOf(error){return error.response?.data?.message||'连接失败，请稍后重试'}
