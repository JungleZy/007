const {fork, spawn} = require("child_process");
const fs = require("node:fs");
const context = require("../core/node_core_ctx");
const path = require("node:path");
const {tryUsePort} = require("../utils/utils");
const {BACKEND_PORT, HTTP_PORT} = require("../shared/ports");
const {backendEnvironment} = require('./mysql-tools');

function onHttpService(port = HTTP_PORT) {
	return new Promise((resolve, reject) => {
	if (!Number.isInteger(port) || port < 1 || port > 65535) { reject(new Error('文件服务端口无效')); return }
	context.httpService = fork(
		require.resolve('./http/index.js'),
		[context.dataPath ? path.join(context.dataPath, 'files') : path.join(context.appPath, 'bin', 'file'), path.join(context.appPath, 'bin', 'file'), String(port)],
		{}
	)
	const timer = setTimeout(() => reject(new Error('文件服务启动超时')), 15000)
	context.httpService.on('message', (m) => {
		if (m?.type === 'ready') { clearTimeout(timer); resolve() }
		else console.log(`[HTTP] ${m}`)
	})
	context.httpService.once('error', error => { clearTimeout(timer); reject(error) })
	context.httpService.on('close', (code) => {
		clearTimeout(timer)
		reject(new Error(`文件服务已退出 (${code})，请检查端口是否被占用`))
	})
	})
}

function onBackendService() {
	tryUsePort(BACKEND_PORT).then((port) => {
		if (port) {
			const defaultServerPath = process.platform === 'win32'
				? path.join(context.appPath, 'bin', 'server', 'server.exe')
				: path.join(context.appPath, 'bin', 'server', 'server')
			const serverPath = process.env.NIP_BACKEND_PATH || defaultServerPath
			if (!fs.existsSync(serverPath)) {
				console.warn(`[backend] Native backend not found: ${serverPath}`)
				console.warn(`[backend] Start Quarkus externally on port ${BACKEND_PORT}, or set NIP_BACKEND_PATH to a native executable.`)
				return
			}
			context.backendService = spawn(serverPath, [], {
				env: backendEnvironment(context.appPath),
				windowsHide: true,
				detached: true
			})
			context.backendService.stdout.on('data', (data) => {
				console.log(`[backend] : ${data}`);
			});

			context.backendService.stderr.on('data', (data) => {
				console.log(`[backend] : ${data}`);
			});

			context.backendService.on('error', (error) => {
				console.log(`[backend] : ${error}`);
			});

			context.backendService.on('close', (code) => {
				console.log(`[backend] : ${code}`);
			});
		}
	}).catch((err) => {
		console.error(err)
	})
}

module.exports = {
	onHttpService,
	onBackendService
}
