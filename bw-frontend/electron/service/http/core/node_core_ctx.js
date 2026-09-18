// 文件服务**子进程**（由 electron/service/index.js fork 出的 service/http/index.js）专用的上下文。
// 与主进程的 electron/core/node_core_ctx.js 同名但互不相干：两者各在自己的进程里，
// 字段不共享。这里只有 appPath —— 由 service/http/index.js 设为 `<安装目录>/bin/file`，
// 比主进程 appPath（main.js 的 dirname(exe)）窄一层，是文件服务可读写范围的唯一来源。
// 消费点：service/http/node_server.js、service/http/index.js、service/http/controllers/file.js。
module.exports = {
  appPath: null
}
