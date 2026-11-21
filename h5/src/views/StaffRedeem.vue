<template>
  <div class="app-page">
    <van-nav-bar title="扫码核销" left-arrow @click-left="$router.push('/staff')" />
    <PageIntro icon="scan" tone="green" eyebrow="ORDER VERIFICATION" title="先核对，再确认实际量" description="核对司机订单，加气后按实际加气量完成核销。" />
    <div class="steps-strip"><b>01 核对订单</b><i></i><span>02 实际加气</span><i></i><span>03 完成核销</span></div>
    <van-cell-group inset title="司机核销码">
      <van-field v-model.trim="token" label="动态码" placeholder="微信扫码或手动输入32位码" maxlength="32" />
    </van-cell-group>
    <div class="actions">
      <van-button block round type="primary" plain @click="onScan">微信扫一扫</van-button>
      <van-button block round type="primary" :disabled="token.length !== 32" :loading="loading" @click="inspect">核对订单</van-button>
    </div>

    <template v-if="order">
      <van-cell-group inset title="请与司机核对">
        <van-cell title="订单号" :value="order.orderNo" />
        <van-cell title="站点/枪号" :value="`${order.stationName} / ${order.gunNo}号枪`" />
        <van-cell title="会员手机号" :value="order.memberPhone || '-'" />
        <van-cell title="实付金额" :value="`¥${order.payAmount}`" />
        <van-cell title="预计气量" label="仅供参考，核销填写实际加气量" :value="`${order.estVolume} ${unit}`" />
      </van-cell-group>
      <van-cell-group inset title="实际加气量">
        <van-field v-model="actualVolume" type="number" label="确认量" placeholder="请输入实际加气量">
          <template #right-icon>{{ unit }}</template>
        </van-field>
      </van-cell-group>
      <div class="actions">
        <van-button block round type="primary" :loading="submitting" @click="submit">在线完成核销</van-button>
      </div>
    </template>
    <van-empty v-if="completed" description="核销完成，库存已按FIFO扣减">
      <van-button round type="primary" size="small" @click="$router.replace('/staff')">返回工作台</van-button>
    </van-empty>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { showToast } from 'vant'
import { inspectRedeemToken, redeemOrder } from '../api/h5'
import { scanQrCode } from '../api/scan'
import { confirmDialog } from '../api/request'

const DECIMAL_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/
const token = ref('')
const inspectedToken = ref('')
const order = ref(null)
const actualVolume = ref('')
const loading = ref(false)
const submitting = ref(false)
const completed = ref(false)
let inspectSequence = 0
const unit = computed(() => order.value?.gasType === 'CNG' ? 'm³' : 'kg')

watch(token, () => {
  inspectSequence++
  loading.value = false
  inspectedToken.value = ''
  order.value = null
  actualVolume.value = ''
  completed.value = false
}, { flush: 'sync' })

async function inspect() {
  const requestedToken = token.value.trim()
  if (requestedToken.length !== 32) return
  const sequence = ++inspectSequence
  inspectedToken.value = ''
  order.value = null
  completed.value = false
  loading.value = true
  try {
    const data = (await inspectRedeemToken(requestedToken)).data
    if (sequence !== inspectSequence || requestedToken !== token.value) return
    inspectedToken.value = requestedToken
    order.value = data
    actualVolume.value = String(data.estVolume)
  } finally {
    if (sequence === inspectSequence) loading.value = false
  }
}
async function onScan() {
  const value = await scanQrCode()
  if (value) { token.value = value.trim(); await inspect() }
}
async function submit() {
  if (!order.value || inspectedToken.value !== token.value) { showToast('动态码已变化，请重新核对'); return }
  const volume = actualVolume.value.trim()
  if (!DECIMAL_PATTERN.test(volume) || !(Number(volume) > 0)) { showToast('实际加气量须大于0且最多两位小数'); return }
  if (!await confirmDialog(`确认实际加气量为 ${volume} ${unit.value}？核销后不可重复操作。`)) return
  const submittedToken = inspectedToken.value
  const submittedOrder = order.value
  submitting.value = true
  try {
    await redeemOrder({ token: submittedToken, shiftId: submittedOrder.shiftId, actualVolume: volume })
    if (order.value === submittedOrder) order.value = null
    if (token.value === submittedToken) {
      token.value = ''
      completed.value = true
    }
    showToast('核销成功')
  } finally { submitting.value = false }
}
</script>
