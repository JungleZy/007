import { userLoginOut } from '../api/UserApi.js'
import { Ws } from '../ws/Ws.js'
import UnionWs from '../../views/manage/unionJob/js/UnionWs.js'
import { shutdownMessageWebSocket } from '../ws/MessageWebSocket.js'
import { closePublicSockets } from '../ws/PublicSocket.js'

const SESSION_KEYS = ['token', 'deviceId', 'userInfo', 'userRole', 'userRouter']

let closingSession = null

export const closeSession = router => {
  if (closingSession) return closingSession
  const token = window.localStorage.getItem('token')
  closingSession = (async () => {
    try {
      if (token) {
        const response = await userLoginOut()
        if (response?.code !== 200) console.warn('退出登录接口返回失败', response)
      }
    } catch (error) {
      console.warn('退出登录接口失败，继续清理本地会话', error)
    } finally {
      await shutdownMessageWebSocket()
      Ws.shutdown()
      closePublicSockets()
      UnionWs.shutdown()
      SESSION_KEYS.forEach(key => window.localStorage.removeItem(key))
      window.localStorage.removeItem('tabCache')
      try {
        await window.localforage.removeItem('autoLoginInfo')
      } catch (error) {
        console.warn('清理自动登录缓存失败', error)
      }
      await router.replace('/login')
    }
  })().finally(() => {
    closingSession = null
  })
  return closingSession
}
