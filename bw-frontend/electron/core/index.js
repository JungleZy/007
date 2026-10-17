const path = require("node:path")
const context = require("./node_core_ctx")
const Datastore = require('nedb-promises')
const {HTTP_HOST, HTTP_PORT, BACKEND_HOST, BACKEND_PORT} = require("../shared/ports")
const {app} = require('electron')
const {prepareWindowsRuntime} = require('./runtime-storage')

// Defaults seed only missing rows. Windows writes live in userData/runtime;
// install/bin is a first-launch source, never allowed to reset existing state.
function loadConfig() {
	context.runtimePath = process.platform === 'win32'
		? prepareWindowsRuntime(context.appPath, app.getPath('userData'))
		: context.appPath
	context.db = Datastore.create(path.join(context.runtimePath, 'bin', 'nip.db'))
	return context.db.findOne({_id: 2}).then((ret) => {
		if (ret) return null
		return context.db.insert({
			_id: 2, text: {
				dataUrl: {
					url: BACKEND_HOST,
					port: String(BACKEND_PORT)
				},
				fileUrl: {
					url: HTTP_HOST,
					port: String(HTTP_PORT)
				}
			}
		})
	})
}

module.exports = {
	loadConfig
}