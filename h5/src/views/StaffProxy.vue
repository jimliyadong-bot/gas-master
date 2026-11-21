<template>
  <div class="app-page">
    <van-nav-bar title="会员码代下单" left-arrow @click-left="$router.push('/staff')" />
    <PageIntro icon="scan" tone="green" eyebrow="ASSISTED ORDER" title="识别会员，协助下单" description="扫码或输入会员码，创建后仍需司机确认并付款。" />
    <div class="steps-strip"><b>01 识别会员</b><i></i><span>02 核对金额</span><i></i><span>03 司机确认</span></div>
    <van-cell-group inset title="司机会员码">
      <van-field v-model.trim="token" label="会员码" placeholder="微信扫码或手动输入32位码" maxlength="32" />
    </van-cell-group>
    <div class="actions">
      <van-button block round type="primary" plain @click="onScan">微信扫一扫</van-button>
      <van-button block round type="primary" :disabled="token.length !== 32" :loading="loading" @click="inspect">识别会员并选枪</van-button>
    </div>

    <template v-if="info">
      <van-cell-group inset title="会员与站点">
        <van-cell title="会员手机号" :value="info.memberPhone" />
        <van-cell title="下单站点" :value="info.stationName" />
      </van-cell-group>
      <van-cell-group inset title="选择枪">
        <van-radio-group v-model="gunId" class="guns">
          <van-radio v-for="g in info.guns" :key="g.gunId" :name="g.gunId" :disabled="!g.enabled">
            {{ g.gasType }} {{ g.gunNo }}号枪（{{ g.unitPrice ? `¥${g.unitPrice}` : '未定价' }}）
          </van-radio>
        </van-radio-group>
      </van-cell-group>
      <van-cell-group inset title="订单金额">
        <van-field v-model="amount" type="number" label="金额" placeholder="请输入金额"><template #right-icon>元</template></van-field>
      </van-cell-group>
      <div class="actions">
        <van-button block round type="primary" :loading="submitting" @click="submit">创建待司机确认订单</van-button>
      </div>
    </template>
    <van-empty v-if="created" :description="`订单 ${created.orderNo} 已创建，等待司机在H5确认并支付`">
      <van-button round type="primary" size="small" @click="$router.replace('/staff')">返回工作台</van-button>
    </van-empty>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { showToast } from 'vant'
import { inspectMemberToken, createProxyOrder } from '../api/h5'
import { scanQrCode } from '../api/scan'

const DECIMAL_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/
const token = ref('')
const inspectedToken = ref('')
const info = ref(null)
const gunId = ref(null)
const amount = ref('')
const loading = ref(false)
const submitting = ref(false)
const created = ref(null)
let inspectSequence = 0

watch(token, () => {
  inspectSequence++
  loading.value = false
  inspectedToken.value = ''
  info.value = null
  gunId.value = null
}, { flush: 'sync' })

async function inspect() {
  const requestedToken = token.value.trim()
  if (requestedToken.length !== 32) return
  const sequence = ++inspectSequence
  inspectedToken.value = ''
  info.value = null
  created.value = null
  loading.value = true
  try {
    const data = (await inspectMemberToken(requestedToken)).data
    if (sequence !== inspectSequence || requestedToken !== token.value) return
    inspectedToken.value = requestedToken
    info.value = data
    gunId.value = (data.guns || []).find(g => g.enabled)?.gunId || null
  } finally {
    if (sequence === inspectSequence) loading.value = false
  }
}
async function onScan() {
  const value = await scanQrCode()
  if (value) { token.value = value.trim(); await inspect() }
}
async function submit() {
  if (!info.value || inspectedToken.value !== token.value) { showToast('会员码已变化，请重新识别'); return }
  if (!gunId.value) { showToast('请选择有当前价格的加气枪'); return }
  const orderAmount = amount.value.trim()
  if (!DECIMAL_PATTERN.test(orderAmount) || !(Number(orderAmount) >= 0.01)) { showToast('金额至少0.01元且最多两位小数'); return }
  const submittedToken = inspectedToken.value
  const submittedInfo = info.value
  submitting.value = true
  try {
    const result = (await createProxyOrder({ token: submittedToken, shiftId: submittedInfo.shiftId, gunId: gunId.value, orderAmount })).data
    if (info.value === submittedInfo) info.value = null
    if (token.value === submittedToken) token.value = ''
    created.value = result
    showToast('已创建，等待司机确认')
  } finally { submitting.value = false }
}
</script>
