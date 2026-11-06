const path = require("node:path")
const fs = require('node:fs')
const context = require("./node_core_ctx")
const Datastore = require('nedb-promises')
const {HTTP_HOST, HTTP_PORT, BACKEND_HOST, BACKEND_PORT} = require("../shared/ports")

// 随包 bin/nip.db 已是发布态默认：_id:2 指向本机后端（localhost:18001）与本机文件服务（127.0.0.1:8000），
// 不预置 _id:3（串口号由用户在串口页选定后写入）。
// 整合部署将配置首次复制到用户数据目录；后续只在缺行时补默认值，升级不覆盖用户设置。
function loadConfig() {
	const original = path.join(context.appPath, 'bin', 'nip.db')
	const file = context.dataPath ? path.join(context.dataPath, 'config', 'nip.db') : original
	if (context.dataPath) {
		fs.mkdirSync(path.dirname(file), {recursive: true})
		if (!fs.existsSync(file) && fs.existsSync(original)) fs.copyFileSync(original, file)
	}
	context.db = Datastore.create(file)
	return context.db.findOne({_id: 2}).then((ret) => {
		if (ret) return ret
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
