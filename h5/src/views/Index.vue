<template>
  <div class="app-page home-page">
    <header class="home-header">
      <div class="brand"><span class="brand-mark"><AppIcon name="flame" :size="25" /></span><div><strong>加气站</strong><p>GAS STATION · 移动服务</p></div></div>
      <span class="header-label">{{ auth.me ? roleText : '欢迎使用' }}</span>
    </header>
    <section class="home-hero">
      <div class="hero-art"><AppIcon name="station" :size="124" /></div>
      <p class="hero-eyebrow">{{ auth.isStationAdmin ? 'STATION MANAGEMENT' : auth.isStaff ? 'STAFF WORKSPACE' : 'BETTER JOURNEY' }}</p>
      <h1>{{ auth.isStationAdmin ? '本站经营，心中有数' : auth.isStaff ? '每次服务，都有条不紊' : '为每一程，补足能量' }}</h1>
      <p class="hero-description">{{ auth.isStationAdmin ? '采购、补货与库存，清晰掌握。' : auth.isStaff ? '管理当班，核对订单，高效服务。' : '便捷加气，轻松查看每一笔订单。' }}</p>
      <van-button v-if="!auth.loggedIn" block class="hero-action" @click="onAuth">微信授权登录 <AppIcon name="arrow" :size="18" /></van-button>
      <template v-else-if="auth.me">
        <van-button v-if="auth.isMember && !auth.phoneBound" block class="hero-action" @click="$router.push('/bind')">绑定手机号（下单前必做）</van-button>
        <van-button v-else-if="auth.isMember" block class="hero-action" @click="$router.push('/orders')">我的订单 / 下单</van-button>
        <van-button v-else-if="auth.isStationAdmin" block class="hero-action" @click="$router.push('/manager')">本站采购 / 补货 / 库存</van-button>
        <van-button v-else block class="hero-action" @click="$router.push('/staff')">员工工作台</van-button>
      </template>
      <p v-else class="hero-description" role="status">正在读取身份信息…</p>
    </section>
    <section v-if="auth.me" class="identity-card">
      <span class="identity-avatar"><AppIcon :name="auth.isMember ? 'user' : 'staff'" :size="22" /></span>
      <div class="identity-copy"><span>当前身份</span><strong>{{ roleText }}</strong></div>
      <div class="identity-extra"><span>{{ auth.isMember ? '手机号' : '所属站点' }}</span><strong>{{ auth.isMember ? (auth.me.phone || '未绑定') : (auth.me.stationId ? `站点 #${auth.me.stationId}` : '—') }}</strong></div>
    </section>
    <div class="section-heading"><h2>{{ auth.isMember || !auth.me ? '常用服务' : '工作助手' }}</h2><span>让操作更简单</span></div>
    <div v-if="auth.me" class="service-grid">
      <button v-if="auth.isMember" class="service-tile" aria-label="会员码" @click="$router.push('/member-token')"><span class="tile-icon"><AppIcon name="scan" /></span><strong>会员码</strong><small>向加气员出示身份</small></button>
      <button v-if="auth.isMember" class="service-tile" aria-label="优惠券" @click="$router.push('/coupons')"><span class="tile-icon tile-icon--orange"><AppIcon name="ticket" /></span><strong>优惠券</strong><small>查看可用加气优惠</small></button>
      <button v-if="auth.isMember" class="service-tile" aria-label="员工入口" @click="$router.push('/staff/bind')"><span class="tile-icon tile-icon--green"><AppIcon name="staff" /></span><strong>员工入口</strong><small>绑定员工与站管账号</small></button>
      <button v-if="!auth.isMember" class="service-tile" aria-label="班次工作台" @click="$router.push('/staff')"><span class="tile-icon tile-icon--green"><AppIcon name="clock" /></span><strong>班次工作台</strong><small>查看当班与服务操作</small></button>
      <button class="service-tile" aria-label="我的" @click="$router.push('/mine')"><span class="tile-icon tile-icon--purple"><AppIcon name="user" /></span><strong>我的</strong><small>账号信息与身份管理</small></button>
    </div>
    <div v-else class="welcome-note surface"><AppIcon name="shield" :size="28" /><div><strong>一个入口，连接每次服务</strong><p>授权后查看订单、会员码和账号信息。</p></div></div>
    <section class="journey-note" v-if="auth.isMember || !auth.me">
      <div><AppIcon name="station" :size="18" /><strong>加气小指南</strong></div>
      <p>绑定手机号 <span>→</span> 扫描站点码 <span>→</span> 确认并付款</p>
      <small>预计气量仅供参考，以加气员确认的实际量为准。</small>
    </section>
    <p class="home-footer">便捷服务 · 清晰记录</p>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { goAuth } from '../api/request'

const auth = useAuthStore()
const roleText = ref('')

const ROLE_TEXT = {
  MEMBER: '消费者',
  STAFF: '加气员',
  STATION_ADMIN: '站点管理员'
}

function onAuth() {
  goAuth('/index')
}

onMounted(async () => {
  if (!auth.loggedIn) return
  try {
    await auth.loadMe()
    roleText.value = ROLE_TEXT[auth.me.role] || auth.me.role
  } catch (e) {
    /* 401 已由拦截器跳授权 */
  }
})
</script>

<style scoped>
.home-header { display: flex; align-items: center; justify-content: space-between; padding: 24px 20px 20px; }
.brand { display: flex; align-items: center; gap: 10px; }
.brand-mark { display: grid; place-items: center; width: 40px; height: 44px; background: var(--brand); color: white; border-radius: 13px; }
.brand strong { font-size: 20px; letter-spacing: .05em; }
.brand p { font-size: 8px; letter-spacing: .11em; color: var(--muted); margin-top: 3px; }
.header-label { color: var(--muted); font-size: 11px; }
.home-hero { position: relative; overflow: hidden; margin: 0 16px; padding: 26px 22px 20px; border-radius: 22px; color: white; background: linear-gradient(120deg, #18549d, #2a78d6 75%, #3988dc); box-shadow: 0 10px 24px #2269b824; }
.hero-eyebrow { position: relative; font-size: 9px; letter-spacing: .16em; color: #cee3ff; }
.home-hero h1 { position: relative; max-width: 255px; font-size: 25px; font-weight: 700; line-height: 1.6; margin: 12px 0 6px; letter-spacing: -.04em; }
.hero-description { position: relative; color: #e1eeff; font-size: 12px; line-height: 1.8; }
.hero-art { position: absolute; top: 27px; right: -26px; color: #b5d6ff; opacity: .18; transform: rotate(-12deg); pointer-events: none; }
.hero-action { margin-top: 24px; color: var(--brand-deep); border: 0; background: white; }
.hero-action svg { margin-left: 8px; }
.identity-card { display: flex; align-items: center; gap: 10px; margin: 14px 16px 0; padding: 15px; background: white; border: 1px solid var(--line); border-radius: 15px; }
.identity-avatar { color: #6982a0; width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; background: #edf3fb; }
.identity-copy, .identity-extra { display: flex; flex-direction: column; gap: 5px; }
.identity-card span:not(.identity-avatar) { font-size: 10px; color: var(--muted); }
.identity-card strong { font-size: 12px; font-weight: 600; }
.identity-extra { margin-left: auto; text-align: right; }
.service-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; padding: 0 16px; }
.service-tile { border: 1px solid var(--line); background: white; border-radius: 16px; padding: 18px 15px; text-align: left; color: var(--ink); transition: border-color .15s, transform .15s; }
.service-tile:hover { border-color: #bed3ee; }
.service-tile:active { transform: translateY(1px); }
.tile-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; color: var(--brand); background: #eaf3ff; margin-bottom: 14px; }
.tile-icon--orange { color: #bf733b; background: #fff2e5; }
.tile-icon--green { color: #168568; background: #e7f5ee; }
.tile-icon--purple { color: #7863b4; background: #f0edfb; }
.service-tile strong, .service-tile small { display: block; }
.service-tile strong { font-size: 14px; font-weight: 650; }
.service-tile small { margin-top: 6px; font-size: 10px; color: var(--muted); line-height: 1.5; }
.journey-note { padding: 18px; margin: 20px 16px 0; border: 1px solid #e2ebf5; border-radius: 16px; background: #eef4fb; }
.journey-note > div { display: flex; align-items: center; gap: 8px; color: #46668b; font-size: 12px; }
.journey-note p { font-size: 11px; color: #48617e; margin: 12px 0 8px; }
.journey-note p span { color: #9aaec5; margin: 0 6px; }
.journey-note small { font-size: 10px; color: var(--muted); line-height: 1.8; }
.welcome-note { display: flex; gap: 12px; color: var(--brand); }
.welcome-note strong { color: var(--ink); font-size: 13px; }
.welcome-note p { color: var(--muted); font-size: 12px; margin-top: 7px; line-height: 1.8; }
.home-footer { margin: 22px 0 0; text-align: center; color: #7c8da2; font-size: 10px; letter-spacing: .12em; }
</style>
