// 使用服务端提交记录判定参训，分数、在线状态和统一结算标记都不能证明实际参训。
export const hasTrainingSubmission = user => Number(user?.existPageNumber) > 0
  || (Array.isArray(user?.existNumber) && user.existNumber.length > 0)
  || (Array.isArray(user?.pageAnalyzeVOS) && user.pageAnalyzeVOS.length > 0)
