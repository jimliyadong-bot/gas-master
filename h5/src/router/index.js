import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { exchangeWxCode, goAuth } from '../api/request'

const routes = [
  { path: '/', redirect: '/index' },
  { path: '/index', name: 'index', component: () => import('../views/Index.vue'), meta: { public: true } },
  { path: '/bind', name: 'bind', component: () => import('../views/BindPhone.vue') },
  { path: '/mine', name: 'mine', component: () => import('../views/Mine.vue') },
  { path: '/staff/bind', name: 'staffBind', component: () => import('../views/StaffBind.vue'), meta: { public: true } },
  { path: '/station/:code', name: 'station', component: () => import('../views/Station.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/OrderList.vue') },
  { path: '/order/:orderNo', name: 'orderDetail', component: () => import('../views/OrderDetail.vue') },
  { path: '/member-token', name: 'memberToken', component: () => import('../views/MemberToken.vue') },
  { path: '/coupons', name: 'coupons', component: () => import('../views/CouponList.vue') },
  { path: '/manager', name: 'manager', component: () => import('../views/Manager.vue') },
  { path: '/manager/batches/new', name: 'managerBatchCreate', component: () => import('../views/ManagerBatchCreate.vue') },
  { path: '/manager/batches/:batchId', name: 'managerBatch', component: () => import('../views/ManagerBatch.vue') },
  { path: '/staff', name: 'staffWorkbench', component: () => import('../views/StaffWorkbench.vue') },
  { path: '/staff/redeem', name: 'staffRedeem', component: () => import('../views/StaffRedeem.vue') },
  { path: '/staff/proxy', name: 'staffProxy', component: () => import('../views/StaffProxy.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  // OAuth 回跳：一次性登录码经 XHR 换 JWT，随后立即摘除参数
  if (to.query.h5_code) {
    try {
      const res = await exchangeWxCode(String(to.query.h5_code))
      const auth = useAuthStore()
      auth.setToken(String(res.data))
      const query = { ...to.query }
      delete query.h5_code
      return { path: to.path, query, replace: true }
    } catch (e) {
      goAuth(to.path)
      return false
    }
  }
  // 受保护页无 token → 授权后回来
  if (!to.meta.public && !localStorage.getItem('h5_token')) {
    goAuth(to.fullPath)
    return false
  }
  return true
})

export default router
