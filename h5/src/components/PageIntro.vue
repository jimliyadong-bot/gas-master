<template>
  <section class="page-intro" :class="{ 'page-intro--compact': compact }">
    <span class="intro-icon" :class="`tone-${tone}`"><AppIcon :name="icon" :size="24" /></span>
    <div class="intro-copy">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="intro-description">{{ description }}</p>
    </div>
  </section>
</template>

<script setup>
import AppIcon from './AppIcon.vue'
defineProps({
  title: { type: String, required: true }, description: { type: String, default: '' },
  eyebrow: { type: String, default: '' }, icon: { type: String, default: 'station' },
  tone: { type: String, default: 'blue' }, compact: Boolean
})
</script>
