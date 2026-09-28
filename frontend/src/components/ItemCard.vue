<script setup>
import {freshnessLabel,isConfirmationStale,formatShanghai} from '../time'
defineProps({item:Object})
function price(i){if(i.tradeType==='FREE')return '免费送';if(i.tradeType==='NEGOTIABLE')return '随便给';return `¥${Number(i.price).toFixed(2)}`}
</script>
<template><RouterLink class="card" :to="`/items/${item.id}`"><img :src="item.imageUrl" alt="物品占位图"><div class="card-body"><div class="meta-row"><small>{{item.category}} · {{item.publisher.nickname}} · {{({AVAILABLE:'可领取',RESERVED:'待交接',COMPLETED:'已送出'})[item.status]}}</small><span class="freshness">{{freshnessLabel(item.publishedAt)}}</span></div><h3>{{item.title}}</h3><p>{{item.description}}</p><strong>{{price(item)}}</strong><p class="confirmed">最近确认：{{formatShanghai(item.lastConfirmedAt)}}</p><p v-if="isConfirmationStale(item.lastConfirmedAt)" class="stale">状态较久未更新，建议先询问</p></div></RouterLink></template>