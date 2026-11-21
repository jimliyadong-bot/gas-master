<template>
  <div class="app-page">
    <van-nav-bar title="员工入口" left-arrow @click-left="$router.back()" />
    <PageIntro icon="staff" tone="green" eyebrow="STAFF ACCESS" title="连接你的工作账号" description="加气员 / 站点管理员首次使用，请绑定后台账号。" />
    <div class="staff-hint"><AppIcon name="shield" :size="18" /><span>绑定后，本微信将自动识别员工身份与所属站点。</span></div>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="loginName"
          name="loginName"
          label="账号"
          placeholder="后台登录账号"
          :rules="[{ required: true, message: '请输入账号' }]"
        />
        <van-field
          v-model="password"
          name="password"
          label="密码"
          type="password"
          placeholder="后台登录密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>
      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">绑定</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { useRouter } from 'vue-router'
import { staffBind } from '../api/h5'
import { useAuthStore } from '../stores/auth'
import { goAuth } from '../api/request'

const router = useRouter()
const auth = useAuthStore()
const loginName = ref('')
const password = ref('')
const submitting = ref(false)

async function onSubmit() {
  submitting.value = true
  try {
    const res = await staffBind(loginName.value, password.value)
    if (res.token) auth.setToken(res.token)
    const roleText = res.role === 'STATION_ADMIN' ? '站点管理员' : '加气员'
    showToast(`绑定成功（${roleText}）`)
    router.replace('/index')
  } catch (e) {
    /* 错误提示由拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

// 未授权直接进入本页：先授权再回来
onMounted(() => {
  if (!auth.loggedIn) {
    goAuth('/staff/bind')
  }
})
</script>

<style scoped>
.staff-hint { display: flex; align-items: flex-start; gap: 8px; margin: 0 16px 20px; padding: 12px 14px; color: #39735e; background: #eaf5ef; border-radius: 12px; font-size: 12px; line-height: 1.8; }
.staff-hint svg { margin-top: 2px; }
.submit { padding-top: 24px; }
</style>
