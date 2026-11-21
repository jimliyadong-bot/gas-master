<template>
  <div class="app-page">
    <van-nav-bar title="绑定手机号" left-arrow @click-left="$router.back()" />
    <PageIntro icon="phone" eyebrow="ACCOUNT SECURITY" title="绑定手机，便捷加气" description="用于下单与会员身份识别，请填写本人手机号。" />
    <div class="steps-strip"><b>01 验证手机号</b><i></i><span>02 开始使用</span></div>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="phone"
          name="phone"
          label="手机号"
          placeholder="请输入 11 位手机号"
          type="digit"
          maxlength="11"
          :rules="[
            { required: true, message: '请输入手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
          ]"
        />
        <van-field
          v-model="code"
          name="code"
          label="验证码"
          placeholder="6 位验证码"
          type="digit"
          maxlength="6"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <template #button>
            <van-button size="small" type="primary" :disabled="countdown > 0" :loading="sending" @click="onSend">
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
      </van-cell-group>
      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">绑定</van-button>
      </div>
    </van-form>
    <p class="tip">验证码5分钟内有效，请勿将验证码提供给他人。</p>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { showToast } from 'vant'
import { useRouter } from 'vue-router'
import { sendCode, bindPhone } from '../api/h5'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const phone = ref('')
const code = ref('')
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let timer = null

async function onSend() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showToast('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    await sendCode(phone.value)
    showToast('验证码已发送')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    /* 错误提示由拦截器统一处理 */
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  submitting.value = true
  try {
    const res = await bindPhone(phone.value, code.value)
    if (res.token) auth.setToken(res.token)
    showToast('绑定成功')
    router.replace('/mine')
  } catch (e) {
    /* 错误提示由拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.submit { padding-top: 24px; }
</style>
