<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <path v-for="(d, index) in paths[name] || paths.station" :key="index" :d="d" />
  </svg>
</template>

<script setup>
defineProps({ name: { type: String, default: 'station' }, size: { type: [Number, String], default: 24 } })
const paths = {
  flame: ['M12 3c1 5-5 6-5 11a5 5 0 0 0 10 0c0-2-1-4-3-6 0 3-2 4-2 4s2-5 0-9Z'],
  home: ['m3 10 9-7 9 7', 'M5 9v11h5v-6h4v6h5V9'],
  station: ['M4 21V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v16M2 21h15M7 7h5v5H7Z', 'm18 5 3 3v9a2 2 0 0 1-4 0v-3h-2M18 5v4h3'],
  order: ['M6 3h12v18l-3-2-3 2-3-2-3 2V3Z', 'M9 7h6M9 11h6M9 15h3'],
  user: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z', 'M4 21v-2a8 8 0 0 1 16 0v2'],
  phone: ['M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2ZM10 5h4M11 18h2'],
  scan: ['M8 3H3v5M16 3h5v5M3 16v5h5M21 16v5h-5M3 12h18', 'M7 7h2v2H7ZM15 7h2v2h-2ZM7 15h2v2H7ZM15 15h2v2h-2Z'],
  ticket: ['M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6Z', 'M15 6v2M15 11v2M15 16v2'],
  staff: ['M4 7h16v14H4ZM9 7V3h6v4M4 12h16M10 12v3h4v-3'],
  stock: ['m3 7 9-4 9 4-9 4-9-4Z', 'M3 7v10l9 4 9-4V7M12 11v10M7 5l10 4v4'],
  shield: ['m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z', 'm8 12 3 3 5-6'],
  arrow: ['M5 12h14m-5-5 5 5-5 5'],
  plus: ['M12 5v14M5 12h14'],
  clock: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z', 'M12 7v5l3 2'],
  check: ['m5 12 4 4L19 6'],
  refresh: ['M20 7v5h-5M4 17v-5h5', 'M6 6a8 8 0 0 1 14 6M4 12a8 8 0 0 0 14 6']
}
</script>
