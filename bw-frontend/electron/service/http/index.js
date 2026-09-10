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
    port: 8000,
    allow_origin: '*'
  },
  survival: {
    time: 300
  }
}).run()
