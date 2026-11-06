const path = require('path')
const fs = require('fs')
const context = require('./core/node_core_ctx')
const NodeServer = require('./node_server')
const {HTTP_HOST, HTTP_PORT} = require('../../shared/ports')

const argv = process.argv
const configuredPort = argv[4] === undefined ? HTTP_PORT : Number(argv[4])
if (!Number.isInteger(configuredPort) || configuredPort < 1 || configuredPort > 65535) throw new Error('文件服务端口无效')
context.appPath = path.resolve(argv[2])
context.bundledPath = path.resolve(argv[3])
// mkdir 的 recursive 本身就会逐级创建，原实现按 path.sep 切分再逐级 existsSync + 异步 mkdir，
// 既多余又与下面的 run() 竞态（异步创建未完成就开始监听）。改为同步一次建到位。
fs.mkdirSync(context.appPath, { recursive: true })
new NodeServer({
  http: {
    // 主机与端口的唯一出处在 shared/ports.js；需要跨机访问资源服务时改那里
    //（UI 的「资源服务地址」也可填远端）。
    host: HTTP_HOST,
    port: configuredPort,
    // ACAO 维持 `*`：file:// 页面永远不会带上可匹配的 Origin（不透明源序列化为
    // 字符串 null），且该响应头不能是列表，收紧无实际收益。
    allow_origin: '*'
  }
}).run()
