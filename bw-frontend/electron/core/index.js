const path = require("node:path")
const context = require("./node_core_ctx")
const Datastore = require('nedb-promises')

// 随包 bin/nip.db 已是发布态默认：_id:2 指向本机后端（localhost:18001）与本机文件服务（127.0.0.1:8000），
// 不预置 _id:3（串口号由用户在串口页选定后写入）。
// 该文件运行时可写——用户在「网络设置」里改的地址会写回 _id:2——所以这里只在缺行时补默认值，
// 绝不在启动时重置已有值，否则会抹掉用户配置。
function loadConfig() {
	context.db = Datastore.create(path.join(context.appPath, 'bin', 'nip.db'))
	context.db.findOne({_id: 2}).then((ret) => {
		if (ret) return null
		return context.db.insert({
			_id: 2, text: {
				dataUrl: {
					url: 'localhost',
					port: '18001'
				},
				fileUrl: {
					url: '127.0.0.1',
					port: '8000'
				}
			}
		})
	}).catch((err) => {
		console.error('初始化网络设置默认值失败:', err)
	})
}

module.exports = {
	loadConfig
}