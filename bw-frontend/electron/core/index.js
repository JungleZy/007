const fs = require('fs')
const path = require("node:path")
const context = require("./node_core_ctx")
const Datastore = require('nedb-promises')

function loadConfig() {
	const configPath = path.join(context.appPath, 'bin', 'config.json');
	try {
		const rawData = fs.readFileSync(configPath, 'utf-8');
		context.config = JSON.parse(rawData);
		context.db = Datastore.create(path.join(context.appPath, '/bin', '/nip.db'))
		context.db.findOne({_id: 1}).then((ret) => {
			if (!ret) {
				context.db.insert({
					_id: 1, text: {
						http: {
							port: 8000,
							allow_origin: '*'
						},
						survival: {
							time: 300
						}
					}
				})
			}
		}).catch((err) => {
			console.error(err)
		})
		context.db.findOne({_id: 2}).then((ret) => {
			if (!ret) {
				context.db.insert({
					_id: 2, text: {
						dataUrl: {
							url: 'localhost',
							port: 18001
						},
						fileUrl: {
							url: 'localhost',
							port: 8000
						},
					}
				})
			}
		}).catch((err) => {
			console.error(err)
		})
	} catch (err) {
		console.error('读取配置文件失败:', err);
		context.config = {}
	}
}

module.exports = {
	loadConfig
}