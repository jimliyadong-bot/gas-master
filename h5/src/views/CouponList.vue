<template>
  <div class="app-page">
    <van-nav-bar title="我的优惠券" left-arrow @click-left="$router.back()" />
    <PageIntro icon="ticket" tone="orange" eyebrow="MY COUPONS" title="让每次加气，多一份实惠" description="优惠不叠加，每笔订单最多使用一张。" compact />
    <van-tabs v-model:active="active" @change="load">
      <van-tab title="未使用" />
      <van-tab title="已使用" />
      <van-tab title="已过期" />
    </van-tabs>
    <van-list :loading="loading" :finished="true">
      <van-cell-group v-for="coupon in coupons" :key="coupon.couponId" inset class="coupon-card" :class="{ 'coupon-card--inactive': active !== 0 }">
        <div class="coupon-heading"><div class="coupon-value"><small>¥</small>{{ coupon.faceValue }}<span>加气优惠券</span></div><div class="coupon-copy"><span class="pill">{{ active === 0 ? '未使用' : active === 1 ? '已使用' : '已过期' }}</span><h2>{{ coupon.templateName }}</h2><p>{{ coupon.buyerName }}</p></div></div>
        <van-cell title="使用门槛" :value="Number(coupon.thresholdAmount) > 0 ? `满${coupon.thresholdAmount}元可用` : '无门槛'" />
        <van-cell title="有效期至" :value="coupon.expireAt" />
        <van-cell v-if="coupon.orderNo" title="使用订单" :value="coupon.orderNo" is-link @click="$router.push(`/order/${coupon.orderNo}`)" />
        <van-cell v-if="coupon.usedAt" title="锁券/使用时间" :value="coupon.usedAt" />
      </van-cell-group>
    </van-list>
    <van-empty v-if="loaded && !loading && !loadError && coupons.length === 0" description="暂无此类优惠券" />
    <van-empty v-if="loadError && !loading" description="优惠券加载失败">
      <van-button round type="primary" size="small" @click="load">重新加载</van-button>
    </van-empty>
    <div v-if="active === 0" class="tip">每单最多使用一张；可用范围以订单所属采购商和金额门槛为准。</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { myCoupons } from '../api/h5'

const active = ref(0)
const coupons = ref([])
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)
let requestSequence = 0
const statuses = ['UNUSED', 'USED', 'EXPIRED']

async function load() {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = false
  coupons.value = []
  try {
    const res = await myCoupons(statuses[active.value])
    if (sequence === requestSequence) coupons.value = res.data || []
  } catch (e) {
    if (sequence === requestSequence) loadError.value = true
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
      loaded.value = true
    }
  }
}
onMounted(load)
</script>

<style scoped>
.coupon-card { margin-top: 12px; border: 1px solid #f0e4d9; }
.coupon-heading { display: flex; gap: 16px; align-items: center; padding: 20px 16px; background: linear-gradient(110deg, #fff4e6, #fffaf4); border-bottom: 1px dashed #e8d9c7; }
.coupon-value { min-width: 86px; max-width: 44%; color: #bd642e; font-size: 30px; font-weight: 750; letter-spacing: -.05em; overflow-wrap: anywhere; }
.coupon-value small { font-size: 16px; margin-right: 3px; }
.coupon-value span { display: block; color: #92795f; font-size: 10px; font-weight: 400; letter-spacing: .04em; margin-top: 5px; }
.coupon-copy { min-width: 0; }
.coupon-copy h2 { font-size: 14px; margin: 8px 0 5px; overflow-wrap: anywhere; }
.coupon-copy p { font-size: 11px; color: var(--muted); }
.coupon-copy .pill { background: #fff; color: #ad652f; font-size: 10px; }
.coupon-card--inactive { border-color: var(--line); }
.coupon-card--inactive .coupon-heading { background: #f0f3f7; border-color: #d6deea; }
.coupon-card--inactive .coupon-value, .coupon-card--inactive .pill { color: #6c7a8b; }
</style>
