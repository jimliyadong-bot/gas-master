<template>
  <nav v-if="visible" class="app-tabs" aria-label="主导航">
    <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="app-tab" :class="{ 'is-active': route.path === item.to }" :aria-current="route.path === item.to ? 'page' : undefined">
      <AppIcon :name="item.icon" :size="22" /><span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppIcon from './AppIcon.vue'
const route = useRoute()
const auth = useAuthStore()
const visible = computed(() => auth.loggedIn && ['/index', '/mine', '/orders', '/coupons', '/manager', '/staff'].includes(route.path))
const items = computed(() => [
  { to: '/index', label: '首页', icon: 'home' },
  ...(auth.isStationAdmin ? [{ to: '/manager', label: '本站采购', icon: 'stock' }]
    : auth.isStaff ? [{ to: '/staff', label: '工作台', icon: 'staff' }]
      : auth.isMember ? [{ to: '/orders', label: '订单', icon: 'order' }] : []),
  { to: '/mine', label: '我的', icon: 'user' }
])
</script>
