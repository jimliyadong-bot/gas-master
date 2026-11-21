import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import './styles/theme.css'
import AppIcon from './components/AppIcon.vue'
import PageIntro from './components/PageIntro.vue'

// iOS 微信需使用首次进入 SPA 时的 URL 进行 JS-SDK 签名
window.__GAS_WX_ENTRY_URL = window.location.href.split('#')[0]

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Vant)
app.component('AppIcon', AppIcon)
app.component('PageIntro', PageIntro)
app.mount('#app')
