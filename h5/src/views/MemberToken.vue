<template>
  <div class="app-page">
    <van-nav-bar title="会员码" left-arrow @click-left="$router.back()" />
    <PageIntro icon="scan" eyebrow="MEMBER PASS" title="你的会员身份凭据" description="到站后向加气员出示，便于识别会员并代下单。" />
    <div class="token-card">
      <div class="token-symbol"><AppIcon name="user" :size="32" /></div>
      <div class="token-title">请向加气员出示会员码</div>
      <div class="token-code">{{ token ? token.token : '···· 生成中 ····' }}</div>
      <p class="surface-label">动态凭据 · 请勿向无关人员转发</p>
      <div class="token-countdown">
        <van-count-down :time="countdownMs" format="ss 秒后刷新" @finish="refresh" />
      </div>
    </div>
    <div class="pad">
      <van-button round block plain type="primary" @click="refresh">刷新会员码</van-button>
    </div>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { computed, onMounted, ref } from 'vue'
import { memberToken } from '../api/h5'

const token = ref(null)
const remainSeconds = ref(0)
const countdownMs = computed(() => remainSeconds.value * 1000)

async function refresh() {
  try {
    const res = await memberToken()
    token.value = res.data
    remainSeconds.value = res.data.remainSeconds
  } catch (e) {
    token.value = null
  }
}

onMounted(refresh)
</script>

<style scoped>
.token-card { margin-top: 4px; }
.token-countdown { margin-top: 12px; }
</style>
