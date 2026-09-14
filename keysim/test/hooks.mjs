/**
 * 前端源码里存在无扩展名的相对 import（如 WebSerial.js 的 `./PubSub`），
 * Vite 解析得了，node ESM 解析不了。这里只在测试进程里补一个解析兜底，
 * 不去改生产代码的 import 写法。
 */
import {registerHooks} from 'node:module'

const CANDIDATES = ['.js', '.mjs', '/index.js', '/index.mjs']

registerHooks({
  resolve(specifier, context, nextResolve) {
    try {
      return nextResolve(specifier, context)
    } catch (error) {
      if (!specifier.startsWith('.')) throw error
      for (const suffix of CANDIDATES) {
        try {
          return nextResolve(specifier + suffix, context)
        } catch { /* 继续试下一个后缀 */ }
      }
      throw error
    }
  }
})
