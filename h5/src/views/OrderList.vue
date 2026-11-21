<template>
  <div class="app-page">
    <van-nav-bar title="我的订单" left-arrow @click-left="$router.back()" />
    <PageIntro icon="order" eyebrow="MY ORDERS" title="每笔加气，都有记录" description="查看支付进度与历史订单，下拉即可刷新。" compact />
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load">
        <RouterLink v-for="o in orders" :key="o.orderNo" :to="`/order/${o.orderNo}`" class="order-card">
          <div class="order-heading"><strong>{{ o.stationName || '加气站' }}</strong><span :class="['status', 'st-' + o.status]">{{ statusText(o.status) }}</span></div>
          <div class="order-body"><div><span :class="o.gasType === 'CNG' ? 'gas-tag-cng' : 'gas-tag-lng'">{{ o.gasType }}</span><span class="gun-label">{{ o.gunNo }}号枪</span><p v-if="o.estVolume">预计 {{ o.estVolume }} {{ o.gasType === 'CNG' ? 'm³' : 'kg' }} · 仅供参考</p></div><div class="order-price"><small>应付 / 实付</small><strong class="amount">¥{{ o.payAmount }}</strong></div></div>
          <div class="order-footer"><time>{{ o.createTime }}</time><span>查看详情 <AppIcon name="arrow" :size="14" /></span></div>
        </RouterLink>
        <van-empty v-if="finished && !orders.length" description="暂无订单" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { ref } from 'vue'
import { myOrders } from '../api/h5'

const orders = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const page = ref(0)
let requestGeneration = 0

const STATUS_TEXT = {
  WAIT_CONFIRM: '待确认',
  WAIT_PAY: '待支付',
  PAID: '待使用',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}
function statusText(s) {
  return STATUS_TEXT[s] || s
}

async function load() {
  if (refreshing.value) return
  const generation = requestGeneration
  const nextPage = page.value + 1
  try {
    const res = await myOrders(nextPage)
    if (generation !== requestGeneration) return
    const rows = res.rows || []
    orders.value.push(...rows)
    page.value = nextPage
    finished.value = rows.length < 10
  } finally {
    if (generation === requestGeneration) loading.value = false
  }
}

async function onRefresh() {
  const generation = ++requestGeneration
  page.value = 0
  orders.value = []
  finished.value = false
  loading.value = true
  try {
    const res = await myOrders(1)
    if (generation !== requestGeneration) return
    const rows = res.rows || []
    orders.value = rows
    page.value = 1
    finished.value = rows.length < 10
  } finally {
    if (generation === requestGeneration) {
      loading.value = false
      refreshing.value = false
    }
  }
}
</script>

<style scoped>
.order-card { display: block; margin: 0 16px 14px; padding: 16px; border: 1px solid var(--line); border-radius: 16px; background: white; color: var(--ink); text-decoration: none; }
.order-heading, .order-body, .order-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.order-heading strong { min-width: 0; font-size: 14px; overflow-wrap: anywhere; }
.status { flex-shrink: 0; padding: 4px 7px; font-size: 10px; border-radius: 6px; background: #f0f3f7; color: var(--muted); }
.st-WAIT_PAY, .st-WAIT_CONFIRM { color: #a9681c; background: #fff3df; }
.st-PAID { color: #138365; background: #e5f5ee; }
.st-REFUNDING, .st-REFUNDED { color: #8a61ab; background: #f2ecf9; }
.order-body { padding: 20px 0; }
.order-body > div { min-width: 0; }
.gun-label { font-size: 12px; margin-left: 7px; color: #5a718b; }
.order-body p { color: var(--muted); font-size: 10px; line-height: 1.7; margin-top: 10px; }
.order-price { text-align: right; }
.order-price small, .order-price strong { display: block; }
.order-price small { font-size: 10px; color: var(--muted); margin-bottom: 6px; }
.order-price strong { font-size: 24px; letter-spacing: -.04em; overflow-wrap: anywhere; }
.order-footer { border-top: 1px solid var(--line); padding-top: 12px; font-size: 10px; color: var(--muted); }
.order-footer span { display: flex; align-items: center; gap: 5px; color: var(--brand-deep); }
</style>
