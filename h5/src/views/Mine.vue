<template>
  <div class="app-page">
    <van-nav-bar title="我的" left-arrow @click-left="$router.back()" />
    <div v-if="auth.me" class="profile-content">
      <section class="profile-hero">
        <span class="profile-avatar"><AppIcon :name="auth.isMember ? 'user' : 'staff'" :size="32" /></span>
        <div><p class="eyebrow">MY ACCOUNT</p><h1>{{ roleText }}</h1><p>{{ auth.isMember ? (auth.me.phone || '绑定手机，开启便捷加气') : `站点 #${auth.me.stationId || '—'} · 移动工作账号` }}</p></div>
      </section>
      <van-cell-group inset title="账号与服务">
        <van-cell title="身份" :value="roleText" />
        <van-cell v-if="auth.isMember" title="手机号" :value="auth.me.phone || '未绑定'" is-link
                  @click="!auth.phoneBound && $router.push('/bind')" />
        <van-cell v-if="auth.me.stationId" title="所属站点" :value="`站点 #${auth.me.stationId}`" />
        <van-cell v-if="auth.isStationAdmin" icon="shop-o" title="本站采购与补货" is-link @click="$router.push('/manager')" />
        <van-cell v-if="auth.isMember" icon="qr" title="会员码" is-link @click="$router.push('/member-token')" />
        <van-cell v-if="auth.isMember" icon="orders-o" title="我的订单" is-link @click="$router.push('/orders')" />
        <van-cell v-if="auth.isMember" icon="coupon-o" title="优惠券" is-link @click="$router.push('/coupons')" />
      </van-cell-group>
      <div class="actions">
        <van-button v-if="auth.isMember && !auth.phoneBound" block round type="primary" @click="$router.push('/bind')">
          绑定手机号
        </van-button>
        <van-button block round plain hairline type="danger" @click="onLogout">退出登录</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const ROLE_TEXT = {
  MEMBER: '消费者',
  STAFF: '加气员',
  STATION_ADMIN: '站点管理员'
}
const roleText = computed(() => (auth.me ? ROLE_TEXT[auth.me.role] || auth.me.role : ''))

function onLogout() {
  auth.logout()
  router.replace('/index')
}

onMounted(() => {
  auth.loadMe().catch(() => {})
})
</script>

<style scoped>
.profile-hero { display: flex; align-items: center; gap: 16px; margin: 20px 16px 0; padding: 24px 20px; border: 1px solid #dce8f7; border-radius: 20px; background: linear-gradient(125deg, #edf5ff, #fff); }
.profile-avatar { display: grid; place-items: center; flex-shrink: 0; width: 60px; height: 60px; border: 4px solid white; border-radius: 20px; color: var(--brand); background: #dceafe; box-shadow: 0 3px 12px #2a78d615; }
.profile-hero h1 { font-size: 22px; margin: 6px 0 7px; }
.profile-hero p:last-child { font-size: 11px; line-height: 1.8; color: var(--muted); }
:deep(.van-cell__left-icon) { margin-right: 12px; color: var(--brand); }
</style>
