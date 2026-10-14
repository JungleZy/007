// 文件服务**子进程**（由 electron/service/index.js fork 出的 service/http/index.js）专用的上下文。
// 与主进程的 electron/core/node_core_ctx.js 同名但互不相干：两者各在自己的进程里，
// 字段不共享。appPath 仅为 `<runtimePath>/bin/file`，Windows 在 userData/runtime 下，
// 其他平台仍在安装根下；它是文件服务可读写范围的唯一来源，不含配置库或程序。
// 消费点：service/http/node_server.js、service/http/index.js、service/http/controllers/file.js。
module.exports = {
  appPath: null
}
