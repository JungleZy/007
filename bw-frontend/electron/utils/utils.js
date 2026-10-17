const {exec} = require('child_process');
const {platform} = process;

const net = require('net');
const tryUsePort = function (port, host, timeout = 1000) {
	return new Promise((resolve, reject) => {
		let settled = false;
		let server;
		const probe = net.createConnection({port, host});
		const finish = (error, available) => {
			if (settled) return;
			settled = true;
			clearTimeout(timer);
			probe.destroy();
			const complete = () => error ? reject(error) : resolve(available);
			if (server) server.close(complete);
			else complete();
		};
		const timer = setTimeout(() => finish(new Error(`端口检测超时（${timeout}ms）`)), timeout);
		probe.once('connect', () => finish(null, false));
		probe.once('error', error => {
			if (settled) return;
			if (error.code !== 'ECONNREFUSED') return finish(error);
			// Check the actual IPv4 listener first; a wildcard bind can miss it on Windows.
			// A refused connection still needs a bind check for reserved or newly occupied ports.
			server = net.createServer();
			server.once('error', bindError => finish(bindError.code === 'EADDRINUSE' ? null : bindError, false));
			server.once('listening', () => {
				if (settled) server.close();
				else finish(null, true);
			});
			server.listen(port, host);
		});
	});
}

module.exports = {
	tryUsePort
}