const path = require('path')
const fs = require('fs')
const context = require('./core/node_core_ctx')
const NodeServer = require('./node_server')

const argv = process.argv
const currentWorkingDirectory = argv[2]
const domain = '/bin/file'
context.appPath = path.join(currentWorkingDirectory, domain)
const dirs = domain.split(path.sep)
let str = ''
for (let i in dirs) {
  str += `${path.sep}${dirs[i]}`
  if (!fs.existsSync(path.join(currentWorkingDirectory, str))) {
    fs.mkdir(
      path.join(currentWorkingDirectory, str),
      { recursive: true },
      function (err) {
        if (err) {
          process.send(' Error creating folder')
        }
      }
    )
  }
}
new NodeServer({
  http: {
    // 默认只绑回环地址；需要跨机访问资源服务时改这里（UI 的「资源服务地址」可填远端）
    host: '127.0.0.1',
    port: 8000,
    // ACAO 维持 `*`：file:// 页面永远不会带上可匹配的 Origin（不透明源序列化为
    // 字符串 null），且该响应头不能是列表，收紧无实际收益。
    allow_origin: '*'
  },
  survival: {
    time: 300
  }
}).run()
