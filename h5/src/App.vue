<template>
  <div class="app-shell">
    <router-view v-slot="{ Component, route }">
      <component :is="Component" :key="route.fullPath" />
    </router-view>
    <AppTabs />
  </div>
</template>

<script setup>
import AppTabs from './components/AppTabs.vue'
</script>
