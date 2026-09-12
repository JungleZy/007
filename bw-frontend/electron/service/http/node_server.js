const express = require('express')
const compression = require('compression')
const bodyParser = require('body-parser')
const fileRoute = require('./routers/file')
const context = require('./core/node_core_ctx')

const HTTP_PORT = 8000
// 默认只监听本机回环：文件服务无鉴权，绑 0.0.0.0 等于把整个资源目录暴露到局域网。
// 仍保留可配置能力 —— UI 的「资源服务地址」明示支持填远端。
const HTTP_HOST = '127.0.0.1'

class NodeServer {
  constructor(config) {
    this.allow_origin = config.http.allow_origin
    this.port = config.http.port || HTTP_PORT
    this.host = config.http.host || HTTP_HOST
    this.httpServer = null
  }

  run() {
    let app = express()
    app.use(bodyParser.json())
    app.use(bodyParser.urlencoded({ extended: true }))
    app.use(compression())
    app.all('*', (req, res, next) => {
      res.header('Access-Control-Allow-Origin', this.allow_origin)
      res.header(
        'Access-Control-Allow-Headers',
        'Content-Type,Content-Length, Authorization, Accept,X-Requested-With'
      )
      res.header('Access-Control-Allow-Methods', 'PUT,POST,GET,DELETE,OPTIONS')
      res.header('Access-Control-Allow-Credentials', true)
      req.method === 'OPTIONS' ? res.sendStatus(200) : next()
    })
    app.use('/api/file', fileRoute())

    // this.httpServer = Http.createServer(app)
    this.httpServer = app.listen(this.port, this.host, () => {
      process.send(` Startup complete. Listen ports: ${this.host}:${this.port}`)
      process.send(` Server file directory: ${context.appPath}`)
    })
  }
}

module.exports = NodeServer
