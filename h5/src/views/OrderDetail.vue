<template>
  <div class="app-page">
    <van-nav-bar title="订单详情" left-arrow @click-left="$router.back()" />
    <template v-if="order">
      <PageIntro icon="order" eyebrow="ORDER DETAILS" :title="statusText(order.status)" :description="`${order.stationName} · ${order.gasType} · ${order.gunNo}号枪`" compact />
      <section class="surface amount-summary"><span>应付 / 实付金额</span><strong class="amount"><small>¥</small>{{ order.payAmount }}</strong><p>原价 ¥{{ order.orderAmount }} <span>·</span> 优惠 ¥{{ order.discountAmount || 0 }}</p></section>
      <van-notice-bar v-if="order.status === 'WAIT_CONFIRM'" type="warning"
                      text="加气员已为您代下单，请核对站点、枪号和金额；确认后才进入支付。" />
      <!-- 待使用：核销码 -->
      <div v-if="order.status === 'PAID'" class="token-card">
        <div class="token-symbol"><AppIcon name="shield" :size="30" /></div>
        <div class="token-title">请向加气员出示核销码</div>
        <div class="token-code">{{ token ? token.token : '···· 生成中 ····' }}</div>
        <div class="token-countdown">
          <van-count-down :key="countdownKey" :time="countdownMs" format="ss 秒后刷新" @finish="onCountdownFinish" />
        </div>
      </div>

      <van-cell-group inset title="订单">
        <van-cell title="订单号" :value="order.orderNo" />
        <van-cell title="状态" :value="statusText(order.status)" />
        <van-cell title="站点" :value="`${order.stationName} ${order.gasType} ${order.gunNo}号枪`" />
        <van-cell title="原价金额" :value="`¥${order.orderAmount}`" />
        <van-cell title="优惠金额" :value="`-¥${order.discountAmount || 0}`" />
        <van-cell title="应付/实付" :value="`¥${order.payAmount}`" value-class="amount" />
        <van-cell v-if="order.estVolume" title="预计气量" label="仅供参考，以确认实际量为准"
                  :value="`${order.estVolume} ${order.gasType === 'CNG' ? 'm³' : 'kg'}`" />
        <van-cell v-if="order.paidAt" title="支付时间" :value="order.paidAt" />
        <van-cell v-if="order.expireAt && order.status === 'WAIT_PAY'" title="支付截止" :value="order.expireAt" />
        <van-cell v-if="order.closeReason" title="关闭原因" :value="order.closeReason" />
        <van-cell v-if="order.exceptionNote" title="异常说明" :value="order.exceptionNote" />
      </van-cell-group>

      <div class="pad" v-if="order.status === 'WAIT_CONFIRM'">
        <van-button round block type="primary" :loading="paying" @click="onConfirm">确认订单并去支付</van-button>
        <van-button round block plain type="danger" :disabled="paying" @click="onReject">拒绝代下单</van-button>
      </div>
      <div class="pad" v-if="order.status === 'WAIT_PAY'">
        <van-button round block type="primary" :loading="paying" @click="onPay">继续支付</van-button>
      </div>
      <div class="pad" v-if="order.status === 'PAID'">
        <van-button round block plain type="primary" @click="refreshToken">刷新核销码</van-button>
      </div>

      <van-cell-group v-if="order.status === 'WAIT_CONFIRM'" inset title="优惠券">
        <van-cell title="每单最多一张" :value="couponText" :is-link="coupons.length > 0" @click="showCoupons = coupons.length > 0" />
        <div class="coupon-tip">确认时由服务端重新校验并计算优惠，确认后不可换券</div>
      </van-cell-group>
      <van-popup v-model:show="showCoupons" position="bottom" round>
        <van-nav-bar title="选择优惠券" />
        <van-cell-group>
          <van-cell title="不使用优惠券" @click="selectedCouponId = null; showCoupons = false">
            <template #right-icon><van-icon v-if="!selectedCouponId" name="success" color="#1989fa" /></template>
          </van-cell>
          <van-cell v-for="coupon in coupons" :key="coupon.couponId"
                    :title="`${coupon.templateName} ¥${coupon.faceValue}`"
                    :label="`${Number(coupon.thresholdAmount) > 0 ? `满${coupon.thresholdAmount}元 · ` : ''}${coupon.expireAt}到期`"
                    @click="selectedCouponId = coupon.couponId; showCoupons = false">
            <template #right-icon><van-icon v-if="selectedCouponId === coupon.couponId" name="success" color="#1989fa" /></template>
          </van-cell>
        </van-cell-group>
      </van-popup>
    </template>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { orderDetail, payOrder, redeemToken, confirmProxyOrder, rejectProxyOrder, availableCoupons } from '../api/h5'
import { confirmDialog } from '../api/request'

const route = useRoute()
const order = ref(null)
const token = ref(null)
const coupons = ref([])
const selectedCouponId = ref(null)
const showCoupons = ref(false)
const remainSeconds = ref(0)
const countdownKey = ref(0)
const paying = ref(false)
let pollTimer = null
let orderRequestSequence = 0
let tokenRequestSequence = 0
let couponRequestSequence = 0
let unmounted = false

const countdownMs = computed(() => remainSeconds.value * 1000)
const couponText = computed(() => {
  if (!selectedCouponId.value) return coupons.value.length ? '不使用（可选择）' : '暂无可用'
  const coupon = coupons.value.find(c => c.couponId === selectedCouponId.value)
  return coupon ? `${coupon.templateName} -¥${coupon.faceValue}` : '已选择一张'
})

const STATUS_TEXT = {
  WAIT_CONFIRM: '待司机确认',
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

async function loadOrder() {
  const sequence = ++orderRequestSequence
  const orderNo = String(route.params.orderNo)
  const res = await orderDetail(orderNo)
  if (unmounted || sequence !== orderRequestSequence) return
  order.value = res.data
  if (order.value.status === 'PAID') {
    await refreshToken()
  } else {
    tokenRequestSequence++
    token.value = null
    remainSeconds.value = 0
    countdownKey.value++
  }
  if (order.value.status === 'WAIT_CONFIRM') {
    await loadAvailableCoupons()
  } else {
    couponRequestSequence++
    coupons.value = []
    selectedCouponId.value = null
  }
}

async function loadAvailableCoupons() {
  const sequence = ++couponRequestSequence
  const orderNo = String(route.params.orderNo)
  try {
    const res = await availableCoupons(orderNo)
    if (unmounted || sequence !== couponRequestSequence || order.value?.status !== 'WAIT_CONFIRM') return
    coupons.value = res.data || []
    if (!coupons.value.some(c => c.couponId === selectedCouponId.value)) selectedCouponId.value = null
  } catch (e) {
    if (sequence === couponRequestSequence) {
      coupons.value = []
      selectedCouponId.value = null
    }
  }
}

async function refreshToken() {
  if (order.value?.status !== 'PAID') return
  const sequence = ++tokenRequestSequence
  const orderNo = String(route.params.orderNo)
  try {
    const res = await redeemToken(orderNo)
    if (unmounted || sequence !== tokenRequestSequence || order.value?.status !== 'PAID') return
    token.value = res.data
    remainSeconds.value = res.data.remainSeconds
    countdownKey.value++
  } catch (e) {
    if (sequence !== tokenRequestSequence || unmounted) return
    token.value = null
    remainSeconds.value = 0
    countdownKey.value++
  }
}

function onCountdownFinish() {
  if (remainSeconds.value > 0) refreshToken()
}

async function onConfirm() {
  const couponId = selectedCouponId.value
  paying.value = true
  try {
    let confirmed
    try { confirmed = await confirmProxyOrder(route.params.orderNo, couponId) }
    catch (error) {
      await loadOrder().catch(() => {})
      throw error
    }
    order.value = confirmed.data
    coupons.value = []
    selectedCouponId.value = null
    if (!await confirmDialog(`原价 ¥${order.value.orderAmount}，优惠 ¥${order.value.discountAmount}，确认支付 ¥${order.value.payAmount}？`)) {
      await loadOrder()
      return
    }
    let res
    try { res = await payOrder(route.params.orderNo) }
    catch (error) {
      await loadOrder().catch(() => {})
      throw error
    }
    if (res.data.payUrl) {
      window.location.href = res.data.payUrl
      return
    }
    await loadOrder()
  } finally {
    paying.value = false
  }
}

async function onReject() {
  if (!await confirmDialog('确认拒绝这笔代下单？订单将关闭且不会扣款。')) return
  paying.value = true
  try {
    try { await rejectProxyOrder(route.params.orderNo) }
    finally { await loadOrder().catch(() => {}) }
  } finally {
    paying.value = false
  }
}

async function onPay() {
  paying.value = true
  try {
    const res = await payOrder(route.params.orderNo)
    if (res.data.payUrl) {
      window.location.href = res.data.payUrl
      return
    }
    await loadOrder()
  } finally {
    paying.value = false
  }
}

async function pollWaitPay() {
  if (unmounted || order.value?.status !== 'WAIT_PAY') return
  try { await loadOrder() } catch (e) { /* 保留当前页面，下轮继续 */ }
  if (!unmounted && order.value?.status === 'WAIT_PAY') pollTimer = setTimeout(pollWaitPay, 3000)
}

onMounted(async () => {
  await loadOrder()
  // 待支付订单：支付完成跳回后串行轮询，避免请求重叠
  if (order.value?.status === 'WAIT_PAY') pollTimer = setTimeout(pollWaitPay, 3000)
})

onUnmounted(() => {
  unmounted = true
  orderRequestSequence++
  tokenRequestSequence++
  couponRequestSequence++
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<style scoped>
.amount-summary { text-align: center; padding: 24px 18px; }
.amount-summary > span { font-size: 12px; color: var(--muted); }
.amount-summary > strong { display: block; font-size: 36px; letter-spacing: -.04em; margin: 8px 0 12px; overflow-wrap: anywhere; }
.amount-summary small { font-size: 20px; margin-right: 4px; }
.amount-summary p { color: var(--muted); font-size: 11px; line-height: 1.7; }
.amount-summary p span { margin: 0 6px; }
</style>
