const {exec} = require('child_process');
const {platform} = process;

const net = require('net');
const tryUsePort = function (port, timeout = 1000) {
	return new Promise((resolve, reject) => {
		const server = net.createServer();
		const timeoutId = setTimeout(() => {
			server.close();
			reject(new Error(`端口检测超时（${timeout}ms）`));
		}, timeout);

		server.once('error', (err) => {
			clearTimeout(timeoutId);
			server.close();
			if (err.code === 'EADDRINUSE') resolve(false);
			else reject(err);
		});

		server.once('listening', () => {
			clearTimeout(timeoutId);
			server.close();
			resolve(true);
		});

		server.listen(port);
	});
}

module.exports = {
	tryUsePort
}