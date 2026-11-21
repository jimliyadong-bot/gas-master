import request from '../api/request'

/** 当前登录身份（role: MEMBER/STAFF/STATION_ADMIN，phoneBound，memberId/userId...） */
export function fetchMe() {
  return request.get('/api/h5/c/me')
}

/** 扫码进站：站点信息 + 在线枪列表（含当前价） */
export function stationEntry(code) {
  return request.get(`/api/h5/c/station/${code}`)
}

/** 试算：枪价 / 预计气量 / 应付金额 */
export function previewOrder(gunId, orderAmount, couponId = null) {
  return request.post('/api/h5/c/order/preview', { gunId, orderAmount, couponId })
}

/** 创建订单（WAIT_PAY），每单可选一张本人优惠券 */
export function createOrder(gunId, orderAmount, couponId = null, requestId = null) {
  return request.post('/api/h5/c/order', { gunId, orderAmount, couponId, requestId })
}

/** 我的订单列表 */
export function myOrders(page = 1, size = 10) {
  return request.get('/api/h5/c/order/list', { params: { page, size } })
}

/** 订单详情 */
export function orderDetail(orderNo) {
  return request.get(`/api/h5/c/order/${orderNo}`)
}

/** 发起支付：返回 payUrl（收银台）或 payParams（JSAPI） */
export function payOrder(orderNo) {
  return request.post(`/api/h5/c/order/${orderNo}/pay`)
}

/** 订单核销码（30 秒刷新、60 秒有效） */
export function redeemToken(orderNo) {
  return request.get(`/api/h5/c/order/${orderNo}/token`)
}

/** 会员码 */
export function memberToken() {
  return request.get('/api/h5/c/member/token')
}

/** 发送绑定验证码 */
export function sendCode(phone) {
  return request.post('/api/h5/sms/send-code', { phone })
}

/** 绑定手机号（成功返回新 token） */
export function bindPhone(phone, code) {
  return request.post('/api/h5/sms/bind-phone', { phone, code })
}

/** 员工绑定（后台账号密码，成功返回员工 token） */
export function staffBind(loginName, password) {
  return request.post('/api/h5/staff/bind', { loginName, password })
}

/** 阶段6：员工工作台与班次 */
export function staffWorkbench() { return request.get('/api/h5/s/workbench') }
export function startShift() { return request.post('/api/h5/s/shift/start') }
export function endShift(shiftId) { return request.post(`/api/h5/s/shift/${shiftId}/end`) }
export function forceEndShift(shiftId, reason) { return request.post(`/api/h5/s/shift/${shiftId}/force-end`, { reason }) }

/** 在线核销与会员码代下单 */
export function inspectRedeemToken(token) { return request.post('/api/h5/s/redeem/inspect', { token }) }
export function redeemOrder(data) { return request.post('/api/h5/s/redeem', data) }
export function inspectMemberToken(token) { return request.post('/api/h5/s/proxy/inspect', { token }) }
export function createProxyOrder(data) { return request.post('/api/h5/s/proxy/order', data) }

/** 司机确认/拒绝代下单 */
export function confirmProxyOrder(orderNo, couponId = null) { return request.post(`/api/h5/c/order/${orderNo}/confirm`, { couponId }) }
export function rejectProxyOrder(orderNo) { return request.post(`/api/h5/c/order/${orderNo}/reject`) }

/** 阶段7：本人券包与待确认代下单可用券 */
export function myCoupons(status = '') { return request.get('/api/h5/c/coupon/list', { params: { status } }) }
export function availableCoupons(orderNo) { return request.get('/api/h5/c/coupon/available', { params: { orderNo } }) }

/** 微信 JS-SDK 扫一扫签名 */
export function jsSdkConfig(url) { return request.get('/api/h5/wx/jssdk-config', { params: { url } }) }
