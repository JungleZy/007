/**
 * 端口与默认服务地址的唯一出处。
 *
 * 这几个值此前散在四处各写一遍：node_server.js 的 HTTP_PORT 常量、
 * service/http/index.js 传进 NodeServer 的字面量 8000、service/index.js 里
 * tryUsePort(18001) 的字面量、以及 core/index.js 首次启动写进 nip.db 的默认地址。
 * 改一处漏三处就会出现「文件服务实际监听 A、nip.db 告诉前端去连 B」这类错位。
 *
 * 本模块只导出纯常量，不 require 任何重依赖，主进程与 fork 出来的 HTTP 子进程都能安全引入。
 */
const HTTP_PORT = 8000
// 默认只监听本机回环：文件服务无鉴权，绑 0.0.0.0 等于把整个资源目录暴露到局域网。
// 仍保留可配置能力 —— UI 的「资源服务地址」明示支持填远端。
const HTTP_HOST = '127.0.0.1'

/** 随包后端（bin/server）的监听端口，与 backend/src/main/resources/application.yml 的 quarkus.http.port 一致。 */
const BACKEND_PORT = 18001
const BACKEND_HOST = 'localhost'

module.exports = {
  HTTP_PORT,
  HTTP_HOST,
  BACKEND_PORT,
  BACKEND_HOST
}
