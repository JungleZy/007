const {fork, spawn} = require("child_process");
const fs = require("node:fs");
const context = require("../core/node_core_ctx");
const path = require("node:path");
const {tryUsePort} = require("../utils/utils");
const {BACKEND_PORT} = require("../shared/ports");

function onHttpService() {
	context.httpService = fork(
		require.resolve('./http/index.js'),
		[context.appPath],
		{}
	)
	context.httpService.on('message', (m) => {
		console.log(`[HTTP] ${m}`)
	})
	context.httpService.on('close', (code) => {
		console.log(`[HTTP] unexpected shutdown`)
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
