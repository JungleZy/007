const EventEmitter = require('events')
let appPath = null
let mainWindow = null
let cmdSessions = new Map()
let sessions = new Map()
let nodeEvent = new EventEmitter()
let db = {}
let config = {}
let excludeFolder = []

module.exports = {
  appPath,
  mainWindow,
  cmdSessions,
  sessions,
  nodeEvent,
  db,
  config,
  excludeFolder
}
