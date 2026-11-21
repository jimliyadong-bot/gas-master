import request from './request'

export const stationProfile = identity => request.get('/api/h5/c/me', { stationIdentity: identity })
export const stationOverview = identity => request.get('/api/h5/m/overview', { stationIdentity: identity })
export const stationBatches = params => request.get('/api/h5/m/batches', { params })
export const stationBatch = (id, identity) => request.get(`/api/h5/m/batches/${id}`, { stationIdentity: identity })
export const stationReceipts = (id, page = 1, identity) => request.get(`/api/h5/m/batches/${id}/receipts`, { params: { page, size: 10 }, stationIdentity: identity })
const writeConfig = identity => {
  if (!identity) throw new Error('缺少站管表单身份，请重新加载')
  identity.assertCurrent()
  return { stationIdentity: identity }
}
export const createStationBatch = (data, identity) => request.post('/api/h5/m/batches', data, writeConfig(identity))
export const receiveStationBatch = (id, data, identity) => request.post(`/api/h5/m/batches/${id}/receipts`, data, writeConfig(identity))
