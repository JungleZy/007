const {fork, spawn} = require("child_process");
const context = require("../core/node_core_ctx");
const path = require("node:path");
const {tryUsePort} = require("../utils/utils");

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
	tryUsePort(18001).then((port) => {
		if (port) {
			let serverPath
			if (process.platform === 'win32') {
				serverPath = path.join(context.appPath, 'bin', 'server', 'server.exe')
			} else {
				serverPath = path.join(context.appPath, 'bin', 'server', 'server')
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