<template>
  <div class="app-page">
    <van-nav-bar title="采购批次与补货" left-arrow @click-left="$router.push('/manager')" />
    <van-empty v-if="error" image="error" :description="error"><van-button plain :disabled="submitting" @click="initialize">重新加载</van-button></van-empty>
    <template v-if="batch && !error">
      <PageIntro icon="stock" eyebrow="PURCHASE & RECEIPTS" title="每次到货，清晰入账" :description="`${batch.stationName} · ${batch.gasType}采购批次`" compact />
      <section class="surface quota-card"><span class="surface-label">剩余可到货额度</span><strong>{{ formatQuantity(remaining) }}<small>{{ unit }}</small></strong><p>累计到货 {{ batch.receivedQty }} / 采购 {{ batch.qty }} {{ unit }}</p></section>
      <van-cell-group inset title="采购批次">
        <van-cell title="批次号" :value="batch.batchNo" />
        <van-cell title="站点 / 采购商" :value="`${batch.stationName} / ${batch.buyerName}`" />
        <van-cell title="气种 / 采购日期" :value="`${batch.gasType} / ${batch.purchaseDate}`" />
        <van-cell title="采购数量" :value="`${batch.qty} ${unit}`" />
        <van-cell title="累计到货" :value="`${batch.receivedQty} ${unit}`" />
        <van-cell title="剩余可到货额度" :value="`${formatQuantity(remaining)} ${unit}`" />
        <van-cell title="采购单价 / 金额" :value="`¥${batch.unitPrice} / ¥${batch.amount}`" />
        <van-cell title="备注" :value="batch.remark || '-'" />
      </van-cell-group>
      <van-cell-group inset title="本站该气种库存">
        <van-cell title="可用 / 预占" :value="inventory ? `${inventory.available} / ${inventory.reserved} ${unit}` : '尚未入库'" />
        <van-cell v-if="inventory" title="累计实际消耗" :value="`${inventory.consumed} ${unit}`" />
      </van-cell-group>
      <van-notice-bar wrapable text="累计到货不得超过采购数量；销售消耗不会恢复到货额度。超出部分请另建采购批次。" />
      <van-notice-bar v-if="pending" wrapable text="存在结果未确认的补货，内容已锁定。请重试原提交，不要重新录入；服务端保证只入库一次。" />
      <van-notice-bar v-if="savedReceiptId" class="notice-success" role="status" wrapable :text="`补货已成功，入库记录 #${savedReceiptId}。无需后台审核。`" />
      <van-form v-if="ready && (remaining > 0 || pending)" @submit="submit" :disabled="submitting || pending || needsRefresh">
        <van-cell-group inset title="本次补货">
          <van-field v-model="fields.qty" type="number" label="到货数量" :placeholder="`请输入本次实际到货（${unit}）`" :rules="[{ validator: v => positiveDecimal(v, 12, 2), message: '请输入大于0且最多两位小数的数量' }]" />
          <van-field v-model="fields.receivedAt" label="到货时间" :rules="[{ required: true, message: '请选择到货时间' }]">
            <template #input><input v-model="fields.receivedAt" class="time-input" aria-label="到货时间" type="datetime-local" step="1" required :disabled="submitting || pending || needsRefresh" /></template>
          </van-field>
          <van-field v-model="fields.remark" type="textarea" label="备注" maxlength="500" show-word-limit />
        </van-cell-group>
        <div class="actions"><van-button block round type="primary" native-type="submit" :loading="submitting" :disabled="needsRefresh">{{ pending ? '重试原补货提交' : '核对并提交入库' }}</van-button></div>
      </van-form>
      <van-empty v-else-if="ready && !pending" description="本批次已无可到货额度；新的补货请另建批次" />
      <div class="actions"><van-button block plain :loading="loading" @click="refresh">刷新批次与入库记录</van-button></div>
      <van-notice-bar v-if="refreshError" wrapable :text="refreshError" />
      <van-cell-group inset title="分次到货记录">
        <van-cell v-for="receipt in receipts" :key="receipt.receiptId" :title="`${receipt.qty} ${unit} · ${receipt.receivedAt}`"
          :label="`操作人：${receipt.createBy} · 余量 ${receipt.remain} ${unit}${receipt.remark ? ' · ' + receipt.remark : ''}`" />
      </van-cell-group>
      <van-empty v-if="!loading && !receipts.length && !refreshError" description="暂无到货记录" />
      <div class="actions" v-if="total > 10"><van-pagination :model-value="page" :total-items="total" :items-per-page="10" mode="simple" @change="changePage" /></div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import { confirmDialog } from '../api/request'
import { stationProfile, stationBatch, stationReceipts, receiveStationBatch } from '../api/station'
import { createStationIdentity } from '../utils/station-identity'
import { createStationDraft, localDateTime, positiveDecimal, quantityUnits, remainingQuantity, formatQuantity } from '../utils/station-draft'
const batchId = String(useRoute().params.batchId)
const batch = ref(null), inventory = ref(null), receipts = ref([]), total = ref(0), page = ref(1)
const emptyFields = () => ({ qty: '', receivedAt: localDateTime(), remark: '' })
const fields = ref(emptyFields())
const ready = ref(false), error = ref(''), refreshError = ref(''), loading = ref(false)
const submitting = ref(false), pending = ref(false), needsRefresh = ref(false), savedReceiptId = ref(null)
const unit = computed(() => batch.value?.gasType === 'CNG' ? 'm³' : 'kg')
const remaining = ref(0n)
let draft, identity, alive = true, listSequence = 0, refreshSequence = 0
const storageChanged = event => identity?.storageChanged(event)
async function loadReceipts(targetPage = 1) {
  const sequence = ++listSequence
  const result = await stationReceipts(batchId, targetPage, identity)
  if (!alive || sequence !== listSequence) return
  receipts.value = result.data.rows; total.value = result.data.total; page.value = targetPage
}
async function changePage(targetPage) {
  try { await loadReceipts(targetPage); if (alive) refreshError.value = '' }
  catch { if (alive) refreshError.value = '入库记录加载失败，请重试；当前页未改变' }
}
async function refresh() {
  const sequence = ++refreshSequence
  loading.value = true; refreshError.value = ''
  try {
    const result = await stationBatch(batchId, identity)
    if (!alive || sequence !== refreshSequence) return false
    remaining.value = remainingQuantity(result.data.batch.qty, result.data.batch.receivedQty)
    batch.value = result.data.batch; inventory.value = result.data.inventory
    await loadReceipts(1)
    if (!alive || sequence !== refreshSequence) return false
    needsRefresh.value = false
    return true
  } catch (e) {
    if (alive && sequence === refreshSequence) { refreshError.value = '数据刷新失败，请重试；不要因刷新失败重复补货'; needsRefresh.value = true }
    return false
  } finally { if (alive && sequence === refreshSequence) loading.value = false }
}
async function initialize() {
  if (submitting.value) return
  error.value = ''; ready.value = false; pending.value = false; savedReceiptId.value = null
  fields.value = emptyFields(); listSequence++; refreshSequence++
  let session
  try {
    session = createStationIdentity(window.localStorage, e => {
      if (alive && identity === session) { error.value = e.message; ready.value = false }
    })
    identity = session
    const { data: me } = await stationProfile(session)
    if (!alive || identity !== session) return
    if (me?.role !== 'STATION_ADMIN') throw new Error('仅站点管理员可使用本站采购功能')
    if (!await refresh()) throw new Error('批次加载失败或无权访问，请重试')
    if (!alive || identity !== session) return
    session.assertCurrent()
    draft = createStationDraft(`gas:station-receipt:${me.userId}:${me.stationId}:${batchId}`, fields.value, window.sessionStorage)
    const saved = draft.read()
    if (saved) { fields.value = saved.fields; pending.value = saved.pending }
    ready.value = true
  } catch (e) { if (alive && (!session || identity === session)) error.value = error.value || e.message || '加载失败' }
}
async function submit() {
  if (submitting.value || !ready.value || needsRefresh.value) return
  const session = identity, activeDraft = draft
  const values = { ...fields.value }
  // 结果未确认时必须允许重试，即使上次成功已用完额度，也应由幂等接口返回原记录。
  if (!pending.value && quantityUnits(values.qty) > remaining.value) { showToast('累计到货不能超过采购量，超出部分请另建批次'); return }
  submitting.value = true
  try {
    session.assertCurrent()
    if (!await confirmDialog(`站点：${batch.value.stationName}\n批次：${batch.value.batchNo}\n本次补货：${values.qty} ${unit.value}\n到货时间：${values.receivedAt.replace('T', ' ')}\n本人确认提交后直接增加本站库存，无需他人审核。`)) return
    if (!alive || identity !== session) return
    session.assertCurrent()
    let saved
    try { saved = activeDraft.begin(values); pending.value = true }
    catch (e) { error.value = `提交凭据保存失败，未发送请求：${e.message}`; return }
    let result
    try {
      const time = saved.fields.receivedAt.replace('T', ' ')
      result = await receiveStationBatch(batchId, { ...saved.fields, receivedAt: time.length === 16 ? `${time}:00` : time, requestId: saved.requestId }, session)
    } catch (e) {
      if (!alive || identity !== session) return
      try { pending.value = activeDraft.failed(saved, e).pending }
      catch { error.value = '无法保存提交状态，请刷新核查；原提交凭据已保留' }
      return
    }
    if (!alive || identity !== session) return
    session.assertCurrent()
    if (result?.code !== 0 || !result?.data?.receiptId) throw new Error('未取得有效入库结果，请刷新后重试原提交')
    try { activeDraft.complete() }
    catch { error.value = `入库 #${result.data.receiptId} 已成功，本地凭据清理失败；请刷新核查原提交` }
    if (!alive) return
    savedReceiptId.value = result.data.receiptId
    pending.value = false; needsRefresh.value = true
    fields.value = { qty: '', receivedAt: localDateTime(), remark: '' }
    showToast('补货入库成功')
    await refresh()
  } catch (e) { if (alive && identity === session) error.value = e.message || '提交状态待核查，请保留原凭据' }
  finally { if (alive) submitting.value = false }
}
onMounted(() => { window.addEventListener('storage', storageChanged); initialize() })
onUnmounted(() => { alive = false; listSequence++; refreshSequence++; window.removeEventListener('storage', storageChanged) })
</script>

<style scoped>
.quota-card { padding: 22px; background: linear-gradient(120deg, #edf5ff, white); border-color: #dbe7f6; }
.quota-card > strong { display: block; color: var(--brand-deep); font-size: 32px; letter-spacing: -.04em; margin: 8px 0 12px; overflow-wrap: anywhere; }
.quota-card small { font-size: 13px; font-weight: 400; margin-left: 6px; }
.quota-card p { color: var(--muted); font-size: 11px; line-height: 1.8; }
.time-input { width: 100%; max-width: 100%; min-width: 0; min-height: 30px; border: 0; background: transparent; font: inherit; font-size: 14px; color: inherit; }
</style>
