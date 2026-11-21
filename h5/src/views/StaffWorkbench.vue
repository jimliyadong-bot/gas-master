<template>
  <div class="app-page">
    <van-nav-bar title="员工工作台" left-arrow @click-left="$router.push('/index')" />
    <PageIntro icon="staff" tone="green" eyebrow="STAFF WORKSPACE" :title="work.stationName || '服务，从这里开始'" description="核对订单、管理当班，让每次服务清晰有序。" />
    <div v-if="work.role" class="shift-status"><span class="shift-dot" :class="{ 'shift-dot--active': work.myShift }"></span>{{ work.myShift ? '你正在当班' : work.activeShift ? '当前由其他员工当班' : '当前未开班' }}</div>
    <van-cell-group inset>
      <van-cell title="所属站点" :value="work.stationName || '加载中'" />
      <van-cell title="当前账号" :value="work.staffName || '-'" />
      <van-cell v-if="work.role === 'STATION_ADMIN'" title="本站采购与补货（无需当班）" is-link @click="$router.push('/manager')" />
    </van-cell-group>

    <van-cell-group inset title="当前班次" v-if="work.activeShift">
      <van-cell title="当班人" :value="work.activeShift.staffName" />
      <van-cell title="开始时间" :value="work.activeShift.startedAt" />
      <van-cell title="班次编号" :value="String(work.activeShift.shiftId)" />
      <van-cell v-if="!work.activeShift.activeFlag" title="核销数" :value="String(work.activeShift.redeemCount || 0)" />
    </van-cell-group>
    <van-empty v-else description="当前无人当班" image-size="80" />

    <div class="actions" v-if="!work.activeShift">
      <van-button block round type="primary" :loading="operating" @click="onStart">开始当班</van-button>
    </div>
    <div class="actions" v-if="work.myShift">
      <van-button block round type="primary" @click="$router.push('/staff/redeem')">扫码核销</van-button>
      <van-button block round type="primary" plain @click="$router.push('/staff/proxy')">扫会员码代下单</van-button>
      <van-button block round type="warning" plain :loading="operating" @click="onEnd">结束当班并生成汇总</van-button>
    </div>
    <van-notice-bar v-if="work.activeShift && !work.myShift" type="warning"
                    text="他人当班中，核销和代下单不可用；请联系当班人交班。" />

    <template v-if="work.role === 'STATION_ADMIN' && work.activeShift">
      <van-cell-group inset title="站点管理员">
        <van-field v-model="forceReason" rows="2" type="textarea" maxlength="255"
                   label="强制原因" placeholder="忘记交班等原因（必填）" />
      </van-cell-group>
      <div class="actions">
        <van-button block round type="danger" plain :loading="operating" @click="onForceEnd">强制交班</van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { staffWorkbench, startShift, endShift, forceEndShift } from '../api/h5'
import { confirmDialog } from '../api/request'

const work = ref({})
const operating = ref(false)
const forceReason = ref('')

async function load() { work.value = (await staffWorkbench()).data }
async function operate(action) {
  operating.value = true
  try {
    try { await action() }
    catch (error) {
      await load().catch(() => {})
      throw error
    }
    await load()
  } finally { operating.value = false }
}
async function onStart() { await operate(async () => { await startShift(); showToast('已开始当班') }) }
async function onEnd() {
  if (!await confirmDialog('确认结束当班？系统将生成代下单、核销量和金额汇总。')) return
  await operate(async () => {
    const res = await endShift(work.value.activeShift.shiftId)
    const s = res.data
    showToast(`已交班：核销${s.redeemCount}单，金额¥${s.payAmount}`)
  })
}
async function onForceEnd() {
  const reason = forceReason.value.trim()
  if (!reason) { showToast('请填写强制交班原因'); return }
  const shiftId = work.value.activeShift.shiftId
  if (!await confirmDialog('确认强制结束当前员工班次？')) return
  if (!await confirmDialog(`请再次确认强制交班，原因：${reason}`)) return
  await operate(async () => {
    await forceEndShift(shiftId, reason)
    forceReason.value = ''
    showToast('已强制交班')
  })
}
onMounted(load)
</script>

<style scoped>
.shift-status { display: flex; align-items: center; gap: 7px; margin: 0 20px 18px; font-size: 12px; color: #597387; }
.shift-dot { width: 7px; height: 7px; border-radius: 50%; background: #8c9eb2; }
.shift-dot--active { background: #0e9f7e; box-shadow: 0 0 0 4px #0e9f7e12; }
</style>
