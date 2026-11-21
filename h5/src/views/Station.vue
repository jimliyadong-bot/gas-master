<template>
  <div class="app-page">
    <van-nav-bar :title="station.stationName || '加气下单'" left-arrow @click-left="$router.back()" />
    <div v-if="error" class="pad">
      <van-empty image="error" :description="error" />
    </div>
    <template v-else>
      <PageIntro icon="station" eyebrow="REFUEL YOUR JOURNEY" title="选好枪号，轻松加气" description="请先核对所在站点与枪号，再填写加气金额。" />
      <div class="steps-strip"><b>01 选择枪号</b><i></i><span>02 输入金额</span><i></i><span>03 确认付款</span></div>
      <van-cell-group inset title="选择枪">
        <van-radio-group v-model="gunId" direction="horizontal" class="guns" @change="onGunChange">
          <van-radio v-for="g in station.guns" :key="g.gunId" :name="g.gunId" :disabled="!g.unitPrice">
            {{ g.gasType }} {{ g.gunNo }} 号枪
            <span class="price">{{ g.unitPrice ? `¥${g.unitPrice}/${g.gasType === 'CNG' ? 'm³' : 'kg'}` : '未定价' }}</span>
          </van-radio>
        </van-radio-group>
      </van-cell-group>

      <van-cell-group inset title="输入金额">
        <van-field v-model="amount" type="number" label="金额" placeholder="请输入加气金额（元）"
                   @input="debouncedPreview">
          <template #right-icon><span>元</span></template>
        </van-field>
      </van-cell-group>

      <div v-if="previewLoading" class="preview-loading" role="status"><van-loading size="18">正在核算金额…</van-loading></div>
      <div v-if="preview" class="preview">
        <van-cell-group inset>
          <van-cell title="单价" :value="`¥${preview.unitPrice}`" />
          <van-cell title="预计气量" :value="`${preview.estVolume} ${preview.gasType === 'CNG' ? 'm³' : 'kg'}`" />
          <van-cell title="优惠券" :value="couponText" :is-link="preview.coupons.length > 0" @click="showCoupons = preview.coupons.length > 0" />
          <van-cell title="优惠" :value="`-¥${preview.discountAmount}`" />
          <van-cell title="应付" :value="`¥${preview.payAmount}`" value-class="amount" />
        </van-cell-group>
        <div class="tip">预计气量按原价计算，仅供参考；每单最多使用一张优惠券</div>
      </div>

      <van-popup v-model:show="showCoupons" position="bottom" round>
        <van-nav-bar title="选择优惠券" />
        <van-cell-group>
          <van-cell title="不使用优惠券" @click="chooseCoupon(null)">
            <template #right-icon><van-icon v-if="!selectedCouponId" name="success" color="#1989fa" /></template>
          </van-cell>
          <van-cell v-for="coupon in preview?.coupons || []" :key="coupon.couponId"
                    :title="`${coupon.templateName} ¥${coupon.faceValue}`"
                    :label="`${Number(coupon.thresholdAmount) > 0 ? `满${coupon.thresholdAmount}元 · ` : ''}${coupon.expireAt}到期`"
                    @click="chooseCoupon(coupon.couponId)">
            <template #right-icon><van-icon v-if="selectedCouponId === coupon.couponId" name="success" color="#1989fa" /></template>
          </van-cell>
        </van-cell-group>
      </van-popup>

      <div class="pad">
        <van-button round block type="primary" :disabled="!canSubmit" :loading="submitting" @click="onSubmit">
          提交订单
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import { stationEntry, previewOrder, createOrder, payOrder } from '../api/h5'

const DECIMAL_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/
const route = useRoute()
const router = useRouter()
const station = ref({})
const gunId = ref(null)
const amount = ref('')
const selectedCouponId = ref(null)
const submissionId = ref(newSubmissionId())
const showCoupons = ref(false)
const preview = ref(null)
const previewLoading = ref(false)
const error = ref('')
const submitting = ref(false)
let previewTimer = null
let previewSequence = 0

const canSubmit = computed(() => preview.value && !previewLoading.value && !submitting.value
  && preview.value.gunId === gunId.value && preview.value.couponId === selectedCouponId.value
  && Number(preview.value.orderAmount) === Number(amount.value))
const couponText = computed(() => {
  if (!selectedCouponId.value) return preview.value?.coupons?.length ? '不使用（可选择）' : '暂无可用'
  const coupon = preview.value?.coupons?.find(c => c.couponId === selectedCouponId.value)
  return coupon ? `${coupon.templateName} -¥${coupon.faceValue}` : '已选择一张'
})

function newSubmissionId() {
  return window.crypto?.randomUUID?.() || `gas_${Date.now()}_${Math.random().toString(36).slice(2, 12)}`
}
function resetSubmissionId() { submissionId.value = newSubmissionId() }
function debouncedPreview() {
  resetSubmissionId()
  preview.value = null
  previewSequence++
  previewLoading.value = false
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(doPreview, 400)
}
function onGunChange() {
  resetSubmissionId()
  selectedCouponId.value = null
  preview.value = null
  doPreview()
}
function chooseCoupon(couponId) {
  resetSubmissionId()
  selectedCouponId.value = couponId
  showCoupons.value = false
  doPreview()
}

async function doPreview() {
  const sequence = ++previewSequence
  const currentGun = gunId.value
  const orderAmount = amount.value.trim()
  const couponId = selectedCouponId.value
  if (!currentGun || !DECIMAL_PATTERN.test(orderAmount) || !(Number(orderAmount) >= 0.01)) {
    preview.value = null
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  try {
    const res = await previewOrder(currentGun, orderAmount, couponId)
    if (sequence !== previewSequence) return
    preview.value = res.data
  } catch (e) {
    if (sequence !== previewSequence) return
    preview.value = null
    const message = e.response?.data?.msg || e.message || ''
    if (couponId && /优惠券|未达门槛|折后金额/.test(message)) {
      selectedCouponId.value = null
      resetSubmissionId()
      await doPreview()
    }
  } finally {
    if (sequence === previewSequence) previewLoading.value = false
  }
}

async function onSubmit() {
  if (!canSubmit.value) return
  const currentGun = gunId.value
  const orderAmount = amount.value.trim()
  const couponId = selectedCouponId.value
  const requestId = submissionId.value
  submitting.value = true
  try {
    const created = await createOrder(currentGun, orderAmount, couponId, requestId)
    let pay
    try { pay = await payOrder(created.data.orderNo) }
    catch (error) {
      router.replace(`/order/${created.data.orderNo}`)
      return
    }
    if (pay.data.payUrl) {
      window.location.href = pay.data.payUrl
      return
    }
    showToast('订单已创建，请前往订单页完成支付')
    router.replace(`/order/${created.data.orderNo}`)
  } catch (e) {
    await doPreview().catch(() => {})
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const res = await stationEntry(route.params.code)
    station.value = res.data
    const first = (res.data.guns || []).find(g => g.unitPrice)
    if (first) gunId.value = first.gunId
  } catch (e) {
    error.value = e.message || '站点码无效'
  }
})

onUnmounted(() => {
  previewSequence++
  if (previewTimer) clearTimeout(previewTimer)
})
</script>

<style scoped>
.preview { margin-top: 18px; }
.preview-loading { padding: 20px; text-align: center; }
:deep(.van-cell__value.amount) { font-size: 24px; font-weight: 700; }
</style>
