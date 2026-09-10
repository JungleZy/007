const express = require('express')
const compression = require('compression')
const bodyParser = require('body-parser')
const fileRoute = require('./routers/file')
const context = require('./core/node_core_ctx')

const HTTP_PORT = 8000

class NodeServer {
  constructor(config) {
    this.allow_origin = config.http.allow_origin
    this.port = config.http.port || HTTP_PORT
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
    this.httpServer = app.listen(this.port, () => {
      process.send(` Startup complete. Listen ports: ${this.port}`)
      process.send(` Server file directory: ${context.appPath}`)
    })
  }
}

module.exports = NodeServer
