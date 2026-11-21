<template>
  <div class="app-page">
    <van-nav-bar title="本站采购与库存" left-arrow @click-left="$router.push('/index')" />
    <PageIntro icon="stock" eyebrow="STATION INVENTORY" :title="overview?.stationName || '本站采购与库存'" :description="overview ? `采购商 · ${overview.buyerName || '—'}` : '独立库存，分项记账，掌握本站补货进度。'" compact />
    <van-notice-bar wrapable :scrollable="false" text="本站独立库存；CNG 与 LNG 分开记账。补货由本人确认提交，无需他人审核。" />
    <div class="actions"><van-button block round type="primary" aria-label="新建采购批次" icon="plus" :disabled="!overview" @click="$router.push('/manager/batches/new')">新建采购批次</van-button></div>
    <section v-for="pool in overview?.inventory || []" :key="pool.poolId" class="stock-card" :class="{ 'stock-card--lng': pool.gasType === 'LNG' }">
      <div class="stock-heading"><span :class="pool.gasType === 'CNG' ? 'gas-tag-cng' : 'gas-tag-lng'">{{ pool.gasType }}</span><span>本站库存 · {{ unit(pool.gasType) }}</span><AppIcon name="stock" :size="20" /></div>
      <div class="stock-main"><span>可用库存</span><strong>{{ pool.available }}<small>{{ unit(pool.gasType) }}</small></strong></div>
      <div class="stock-metrics"><div><span>累计入库</span><strong>{{ pool.totalIn }}</strong></div><div><span>当前预占</span><strong>{{ pool.reserved }}</strong></div><div><span>实际消耗</span><strong>{{ pool.consumed }}</strong></div><div><span>净调整</span><strong>{{ pool.adjusted }}</strong></div></div>
    </section>
    <van-empty v-if="overview && !overview.inventory.length" description="尚无入库记录，建立采购批次并补货后生成库存" />
    <div class="actions">
      <van-button block round plain @click="$router.push('/staff')">查看员工班次工作台</van-button>
    </div>
    <van-cell-group inset title="本站采购批次">
      <van-field v-model="batchNo" label="批次号" placeholder="可输入部分批次号" maxlength="32" />
      <van-field label="气种">
        <template #input><van-radio-group v-model="gasType" direction="horizontal"><van-radio name="">全部</van-radio><van-radio name="CNG">CNG</van-radio><van-radio name="LNG">LNG</van-radio></van-radio-group></template>
      </van-field>
    </van-cell-group>
    <div class="actions"><van-button block plain type="primary" :loading="loading" @click="load(1)">查询 / 刷新</van-button></div>
    <van-empty v-if="error" image="error" :description="error" />
    <div v-else class="batch-list">
      <RouterLink v-for="batch in rows" :key="batch.batchId" :to="`/manager/batches/${batch.batchId}`" class="batch-card">
        <div class="batch-heading"><strong>{{ batch.batchNo }}</strong><AppIcon name="arrow" :size="17" /></div>
        <div class="batch-meta"><span :class="batch.gasType === 'CNG' ? 'gas-tag-cng' : 'gas-tag-lng'">{{ batch.gasType }}</span><span>{{ batch.purchaseDate }}</span><strong>¥{{ batch.amount }}</strong></div>
        <p>已到货 <b>{{ batch.receivedQty }}</b> / 采购 {{ batch.qty }} {{ unit(batch.gasType) }}</p>
      </RouterLink>
    </div>
    <van-empty v-if="!loading && !error && !rows.length" description="暂无符合条件的采购批次" />
    <div class="actions" v-if="total > 10"><van-pagination :model-value="page" :total-items="total" :items-per-page="10" mode="simple" @change="load" /></div>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { onMounted, onUnmounted, ref } from 'vue'
import { stationOverview, stationBatches } from '../api/station'
const overview = ref(null), rows = ref([]), total = ref(0), page = ref(1)
const gasType = ref(''), batchNo = ref(''), loading = ref(false), error = ref('')
let sequence = 0
const unit = gas => gas === 'CNG' ? 'm³' : 'kg'
async function load(targetPage = 1) {
  const request = ++sequence
  loading.value = true
  error.value = ''
  try {
    const [info, batches] = await Promise.all([stationOverview(), stationBatches({ page: targetPage, size: 10, gasType: gasType.value, batchNo: batchNo.value.trim() })])
    if (request !== sequence) return
    overview.value = info.data
    rows.value = batches.data.rows
    total.value = batches.data.total
    page.value = targetPage
  } catch (e) {
    if (request === sequence) { error.value = e.message || '加载失败，请重试'; overview.value = null; rows.value = [] }
  } finally { if (request === sequence) loading.value = false }
}
onMounted(() => load())
onUnmounted(() => { sequence++ })
</script>

<style scoped>
.stock-card { margin: 16px; padding: 18px; border: 1px solid #d6eae4; border-radius: 18px; background: linear-gradient(135deg, #f4fcf8, white 65%); }
.stock-card--lng { border-color: #e2ddf3; background: linear-gradient(135deg, #f7f5ff, white 65%); }
.stock-heading { display: flex; align-items: center; gap: 8px; color: #67877b; font-size: 10px; }
.stock-heading svg { margin-left: auto; }
.stock-main { margin: 20px 0; }
.stock-main > span { color: var(--muted); font-size: 11px; }
.stock-main strong { display: block; margin-top: 7px; color: #176f59; font-size: 30px; line-height: 1.3; letter-spacing: -.04em; overflow-wrap: anywhere; }
.stock-card--lng .stock-main strong { color: #6551a5; }
.stock-main small { font-size: 12px; font-weight: 400; margin-left: 6px; letter-spacing: 0; }
.stock-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 20px; padding-top: 16px; border-top: 1px solid #e2eee9; }
.stock-metrics span { display: block; color: var(--muted); font-size: 10px; }
.stock-metrics strong { display: block; margin-top: 5px; color: #40596d; font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.batch-list { padding: 0 16px; }
.batch-card { display: block; margin-bottom: 12px; padding: 16px; color: var(--ink); background: white; border: 1px solid var(--line); border-radius: 15px; text-decoration: none; }
.batch-heading { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.batch-heading strong { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.batch-heading svg { color: var(--muted); }
.batch-meta { display: flex; align-items: center; gap: 8px; margin-top: 14px; font-size: 10px; color: var(--muted); flex-wrap: wrap; }
.batch-meta strong { margin-left: auto; color: var(--ink); font-size: 15px; overflow-wrap: anywhere; }
.batch-card p { font-size: 11px; line-height: 1.8; margin-top: 13px; color: var(--muted); }
.batch-card b { color: var(--brand-deep); }
</style>
