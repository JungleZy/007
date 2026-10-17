const {fork, spawn} = require("child_process");
const context = require("../core/node_core_ctx");
const path = require("node:path");
const {tryUsePort} = require("../utils/utils");
const {BACKEND_PORT} = require("../shared/ports");
const {BackendStartupError, loadBackendProfile, rustEnvironment, forwardRustDiagnostics, waitForRustStartup} = require('./backend-profile');
const {backendRuntime} = require('../../package.json');

function onHttpService() {
	context.httpService = fork(
		require.resolve('./http/index.js'),
		[context.runtimePath],
		{}
	)
	context.httpService.on('message', (m) => {
		console.log(`[HTTP] ${m}`)
	})
	context.httpService.on('close', (code) => {
		console.log(`[HTTP] unexpected shutdown`)
	})
}

async function onBackendService() {
	const serverPath = path.join(context.appPath, 'bin', 'server', process.platform === 'win32' ? 'server.exe' : 'server');
	if (backendRuntime !== undefined && backendRuntime !== 'rust') {
		throw new BackendStartupError('安装包声明了不支持的后端类型，请重新安装正确版本。');
	}
	const profile = loadBackendProfile(serverPath, backendRuntime === 'rust');
	const env = profile ? rustEnvironment(profile) : process.env;
	let available;
	try { available = await tryUsePort(BACKEND_PORT, '127.0.0.1'); } catch {
		throw new BackendStartupError('无法检查后端端口，请检查本机网络权限后重试。');
	}
	if (!available) {
		if (profile) throw new BackendStartupError(`Rust 后端端口 ${BACKEND_PORT} 已被占用。请自行关闭占用程序后重试；本应用不会终止该程序。`);
		return; // Preserve legacy Java behavior when its backend is already running.
	}
	const child = spawn(serverPath, [], {
		windowsHide: true,
		detached: !profile,
		env,
		stdio: ['ignore', 'pipe', 'pipe']
	});
	context.backendService = child;
	child.on('error', () => console.error('[backend] 无法运行后端进程'));
	child.on('close', code => console.log(`[backend] exited: ${code}`));
	if (profile) {
		forwardRustDiagnostics(child.stdout, env.RUST_BACKEND_DATABASE_URL, message => console.log(message));
		forwardRustDiagnostics(child.stderr, env.RUST_BACKEND_DATABASE_URL, message => console.error(message));
		await waitForRustStartup(child);
	} else {
		child.stdout.on('data', data => console.log(`[backend] : ${data}`));
		child.stderr.on('data', data => console.log(`[backend] : ${data}`));
		await new Promise((resolve, reject) => {
			child.once('spawn', resolve);
			child.once('error', () => reject(new BackendStartupError('无法启动 Java 后端，请检查安装完整性和系统执行权限。')));
		});
	}
}

module.exports = {
	onHttpService,
	onBackendService
}