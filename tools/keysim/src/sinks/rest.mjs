/**
 * REST 驱动：把载荷打到真实后端，走完 取页 -> startTrain -> uploadResult -> finish。
 * 响应恒为 HTTP 200，业务状态在 JSON code 字段；鉴权靠 token + deviceId 请求头。
 * 端点取自前端 api 模块（handkeyZuXun.js、electronKeyZuXun.js），不另造路径。
 */

export const DEFAULT_BASE = 'http://localhost:18001/api'

export class BusinessError extends Error {
  constructor(path, envelope) {
    super(`${path} 返回业务码 ${envelope?.code}：${envelope?.message ?? ''}`)
    this.name = 'BusinessError'
    this.code = envelope?.code
    this.envelope = envelope
  }
}

/** 登录并拿到会话；deviceId 由客户端生成（后端只校验 token+deviceId 配对）。 */
export async function login({base = DEFAULT_BASE, userAccount, password, deviceId = `keysim-${process.pid}`} = {}) {
  const response = await fetch(`${base}/user/login`, {
    method: 'POST',
    headers: {'content-type': 'application/json'},
    body: JSON.stringify({userAccount, password, deviceId})
  })
  const envelope = await response.json()
  if (envelope.code !== 200) throw new BusinessError('/user/login', envelope)
  return createSession({base, token: envelope.data.token, deviceId: envelope.data.deviceId, user: envelope.data.user})
}

export function createSession({base = DEFAULT_BASE, token, deviceId, user = null} = {}) {
  const call = async (path, {method = 'POST', body = null, query = null, allowCodes = []} = {}) => {
    const url = new URL(`${base}${path}`)
    for (const [key, value] of Object.entries(query ?? {})) url.searchParams.set(key, String(value))
    const response = await fetch(url, {
      method,
      headers: {'content-type': 'application/json', token, deviceId},
      body: body === null ? undefined : JSON.stringify(body)
    })
    const envelope = await response.json()
    if (envelope.code !== 200 && !allowCodes.includes(envelope.code)) throw new BusinessError(path, envelope)
    return envelope
  }
  return {base, token, deviceId, user, call}
}

/** 手键组训：端点同 handkeyZuXun.js。 */
export function handKeyRun(session, {trainId, userId}) {
  return {
    detail: () => session.call('/generalTickerPatTrain/detail', {body: {id: trainId, uid: userId}}),
    findPage: floorNumber => session.call('/generalTickerPatTrain/findPage', {body: {id: trainId, userId, floorNumber}}),
    startTrain: attempt => session.call('/generalTickerPatTrain/startTrain', {method: 'GET', query: {trainId, attempt}}),
    upload: (payload, options) => session.call('/generalTickerPatTrain/uploadResult', {body: payload, ...options}),
    finish: attempt => session.call('/generalTickerPatTrain/finish', {body: {id: trainId, attempt}}),
    reset: attempt => session.call('/generalTickerPatTrain/reset', {body: {id: trainId, attempt}}),
    statistics: () => session.call('/generalTickerPatTrain/statistics', {body: {id: trainId}}),
    updateStatus: status => session.call('/socket/generalTickerPatTrain/updateTrainStatus', {body: {trainId, status}})
  }
}

/** 电子键组训：端点同 electronKeyZuXun.js。 */
export function electronKeyRun(session, {trainId, userId}) {
  return {
    detail: () => session.call('/generalKeyPat/detail', {body: {trainId, uid: userId}}),
    getPage: pageNumber => session.call('/generalKeyPat/getPage', {body: {trainId, userId, pageNumber}}),
    startTrain: attempt => session.call('/generalKeyPat/startTrain', {method: 'GET', query: {trainId, attempt}}),
    upload: (payload, options) => session.call('/generalKeyPat/uploadResult', {body: payload, ...options}),
    finish: attempt => session.call('/generalKeyPat/finish', {body: {trainId, attempt}}),
    reset: attempt => session.call('/generalKeyPat/reset', {body: {trainId, attempt}}),
    statistics: () => session.call('/generalKeyPat/statistics', {body: {trainId}}),
    updateStatus: status => session.call('/generalKeyPat/updateTrainStatus', {body: {trainId, status}})
  }
}
