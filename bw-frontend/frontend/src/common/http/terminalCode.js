// 终态业务码：后端已判定「同一请求重试必然再次失败」，前端必须提示不可重试，不给重试引导。
//   208 = 业务终态（训练已完成／已提交／轮次已变化／补交窗口已结束，后端 TerminalStateException → code 208）
//   207 = 无权限（授权拒绝本身就是终态，重试不会长出权限）
// 不在此列的码各有分工，不要往这里加：
//   202 = 参数或目标问题，改正后重试可以成功，必须保留重试入口；
//   203/204/206 = 会话码，由 http/index.js 的登录跳转分支处理，文案逐字冻结；
//   500 与网络错误 = 可重试。
const TERMINAL_MESSAGES = {
  207: '没有执行该操作的权限',
  208: '该操作已不可执行'
}

export const TERMINAL_CODES = Object.freeze(Object.keys(TERMINAL_MESSAGES).map(Number))

// 只认数字码：信封里的 code 是后端的 int 字段，字符串 '208' 说明取错了字段，不得当成终态放过。
export function isTerminalCode(code) {
  return typeof code === 'number' && Object.prototype.hasOwnProperty.call(TERMINAL_MESSAGES, code)
}

// 终态拒因优先用后端文案（「训练已完成，不能修改」这类就是要给用户看的），后端没给才退到本地兜底。
export function terminalReason(code, message) {
  const text = typeof message === 'string' ? message.trim() : ''
  return text || TERMINAL_MESSAGES[code] || ''
}
