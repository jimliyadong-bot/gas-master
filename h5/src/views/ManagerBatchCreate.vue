<template>
  <div class="app-page">
    <van-nav-bar title="新建采购批次" left-arrow @click-left="$router.push('/manager')" />
    <PageIntro icon="order" eyebrow="NEW PURCHASE" title="记录一笔本站采购" description="先建立采购批次，再按实际到货分次补货。" />
    <div class="steps-strip"><b>01 录入采购</b><i></i><span>02 本人确认</span><i></i><span>03 登记到货</span></div>
    <van-notice-bar wrapable :scrollable="false" text="建立批次不增加库存；请在批次下登记实际补货。累计到货不得超过采购数量。" />
    <van-empty v-if="error" image="error" :description="error"><van-button plain :disabled="submitting" @click="initialize">重新加载</van-button></van-empty>
    <template v-else-if="ready">
      <van-cell-group inset><van-cell title="所属站点" :value="stationName" /></van-cell-group>
      <van-notice-bar v-if="pending" wrapable text="上一笔提交结果尚未确认。内容已锁定，请重试原提交；不会重复建立批次。" />
      <van-form @submit="submit" :disabled="submitting || pending || !!completedId">
        <van-cell-group inset title="采购信息">
          <van-field label="气种"><template #input><van-radio-group v-model="fields.gasType" direction="horizontal" :disabled="submitting || pending || !!completedId"><van-radio name="CNG">CNG</van-radio><van-radio name="LNG">LNG</van-radio></van-radio-group></template></van-field>
          <van-field v-model="fields.purchaseDate" label="采购日期" type="date" :rules="[{ required: true, message: '请选择采购日期' }]" />
          <van-field v-model="fields.qty" label="采购数量" type="number" :placeholder="`数量（${unit}），最多两位小数`" :rules="[{ validator: v => positiveDecimal(v, 12, 2), message: '请输入大于0且最多两位小数的数量' }]" />
          <van-field v-model="fields.unitPrice" label="采购单价" type="number" placeholder="最多四位小数" :rules="[{ validator: v => positiveDecimal(v, 6, 4), message: '请输入有效采购单价' }]" />
          <van-cell title="采购金额" :value="amount ? `¥${amount}` : '请填写有效数量及单价'" :value-class="amount ? 'amount' : ''" />
          <van-field v-model="fields.remark" label="备注" type="textarea" maxlength="500" show-word-limit />
        </van-cell-group>
        <div class="actions"><van-button v-if="!completedId" block round type="primary" native-type="submit" :loading="submitting">{{ pending ? '重试原提交' : '核对并建立批次' }}</van-button></div>
      </van-form>
      <van-empty v-if="completedId" description="采购批次已建立，尚未入库"><van-button type="primary" @click="$router.replace(`/manager/batches/${completedId}`)">查看批次并补货</van-button></van-empty>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showToast } from 'vant'
import { confirmDialog } from '../api/request'
import { stationProfile, stationOverview, createStationBatch } from '../api/station'
import { createStationIdentity } from '../utils/station-identity'
import { createStationDraft, localDateTime, positiveDecimal, purchaseAmount } from '../utils/station-draft'
const emptyFields = () => ({ gasType: 'CNG', purchaseDate: localDateTime().slice(0, 10), qty: '', unitPrice: '', remark: '' })
const fields = ref(emptyFields())
const ready = ref(false), error = ref(''), submitting = ref(false), pending = ref(false), completedId = ref(null), stationName = ref('')
const unit = computed(() => fields.value.gasType === 'CNG' ? 'm³' : 'kg')
const amount = computed(() => purchaseAmount(fields.value.qty, fields.value.unitPrice))
let draft, identity, alive = true
const storageChanged = event => identity?.storageChanged(event)
async function initialize() {
  if (submitting.value) return
  error.value = ''; ready.value = false; pending.value = false; completedId.value = null
  fields.value = emptyFields()
  let session
  try {
    session = createStationIdentity(window.localStorage, e => {
      if (alive && identity === session) { error.value = e.message; ready.value = false }
    })
    identity = session
    const { data: me } = await stationProfile(session)
    if (me?.role !== 'STATION_ADMIN') throw new Error('仅站点管理员可使用本站采购功能')
    const info = await stationOverview(session)
    if (!alive || identity !== session) return
    session.assertCurrent()
    stationName.value = info.data.stationName
    draft = createStationDraft(`gas:station-batch:${me.userId}:${me.stationId}`, fields.value, window.sessionStorage)
    const saved = draft.read()
    if (saved) { fields.value = saved.fields; pending.value = saved.pending }
    ready.value = true
  } catch (e) { if (alive && (!session || identity === session)) error.value = e.message || '加载失败' }
}
async function submit() {
  if (submitting.value || !ready.value || completedId.value) return
  const session = identity, activeDraft = draft
  const values = { ...fields.value }
  const total = purchaseAmount(values.qty, values.unitPrice)
  if (!total) { showToast('采购金额须为0.01至9999999999.99元'); return }
  submitting.value = true
  try {
    session.assertCurrent()
    if (!await confirmDialog(`站点：${stationName.value}\n气种：${values.gasType}\n采购量：${values.qty} ${unit.value}\n采购单价：¥${values.unitPrice}\n采购金额：¥${total}\n确认建立批次？此操作不增加库存。`)) return
    if (!alive || identity !== session) return
    session.assertCurrent()
    let saved
    try { saved = activeDraft.begin(values); pending.value = true }
    catch (e) { error.value = `无法保存提交凭据，未发送请求：${e.message}`; return }
    let result
    try { result = await createStationBatch({ ...saved.fields, requestId: saved.requestId }, session) }
    catch (e) {
      if (!alive || identity !== session) return
      try { pending.value = activeDraft.failed(saved, e).pending }
      catch { error.value = '无法保存提交状态，请刷新核查；原提交凭据已保留' }
      return
    }
    if (!alive || identity !== session) return
    session.assertCurrent()
    if (result?.code !== 0 || !result?.data?.batchId) throw new Error('未取得有效批次结果，请刷新后重试原提交')
    // 已有服务端成功结果，不能把后续本地清理失败显示成建单失败。
    try { activeDraft.complete() } catch { showToast('批次已建立，本地提交凭据清理失败；再次进入时请核查原提交') }
    if (alive) { completedId.value = result.data.batchId; pending.value = false; showToast('采购批次已建立') }
  } catch (e) { if (alive && identity === session) error.value = e.message || '提交状态待核查，请保留原凭据' }
  finally { if (alive) submitting.value = false }
}
onMounted(() => { window.addEventListener('storage', storageChanged); initialize() })
onUnmounted(() => { alive = false; window.removeEventListener('storage', storageChanged) })
</script>

<style scoped>
:deep(.van-cell__value.amount) { font-size: 21px; font-weight: 700; }
</style>
