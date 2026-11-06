// 文件服务**子进程**（由 electron/service/index.js fork 出的 service/http/index.js）专用的上下文。
// 与主进程的 electron/core/node_core_ctx.js 同名但互不相干：两者各在自己的进程里，
// 字段不共享。appPath 是可写文件根，bundledPath 是安装包内只读基础资源根。
// 两个路径均由主进程明确传入，不能扩大为包含数据库/后端的安装根目录。
// 消费点：service/http/node_server.js、service/http/index.js、service/http/controllers/file.js。
module.exports = {
  appPath: null,
  bundledPath: null
}
