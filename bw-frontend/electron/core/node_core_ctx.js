const EventEmitter = require('events')
const {join} = require("path");
let appPath = null
let runtimePath = null
let mainWindow = null
let httpService = null
let backendService = null
let cmdSessions = new Map()
let sessions = new Map()
let nodeEvent = new EventEmitter()
let db = {}
let config = {}


module.exports = {
	appPath,
	runtimePath,
	mainWindow,
	cmdSessions,
	sessions,
	nodeEvent,
	db,
	config,
	httpService,
	backendService
}
