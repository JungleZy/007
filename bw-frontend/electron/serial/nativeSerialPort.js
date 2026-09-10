const {exec, execSync, spawn} = require('child_process');
const {platform} = process;

class NativeSerialPort {
	static list() {
		return new Promise((resolve, reject) => {
			(async () => {
				try {
					const ports = platform === 'win32'
						? await this._listWindowsPorts()
						: await this._listLinuxPorts();
					resolve(ports);
				} catch (error) {
					reject(error);
				}
			})();
		});
	}

	// Windows 实现
	static _listWindowsPorts() {
		return new Promise((resolve, reject) => {
			exec('powershell -command "Get-PnpDevice -Class Ports | Where-Object {$_.FriendlyName -like \'*COM*\'} | Select-Object FriendlyName"', (error, stdout) => {
				if (error) return reject(new Error('未检测到串口设备（尝试以管理员运行）'));
      
        const ports = stdout
					.toString()
					.split('\n')
					.filter(line => line.trim().startsWith('通信端口') || line.includes('COM'))
					.map(line => (/COM\d+/).exec(line)?.[0])
					.filter(Boolean);
				resolve(ports);
			});
		});
	}

	// Linux 实现
	static _listLinuxPorts() {
		return new Promise((resolve, reject) => {
			exec('ls /dev/tty*', async (error, stdout) => {
				if (error) return reject(new Error('无法访问设备目录'));
				const devices = stdout.split('\n')
					.filter(path => path && /USB\d+/.test(path));

				const devicePromises = devices.map(async device => {
					try {
						const {mode, group} = await this._getDeviceInfo(device);
						return {
							path: device,
							permission: mode,
							group,
							accessible: this._checkPermission(parseInt(mode, 8))
						};
					} catch (e) {
						console.warn(`跳过设备 ${device}: ${e.message}`);
					}
				});
				Promise.all(devicePromises)
					.then(results => {
						exec(`groups ${process.env.USER}`,(err,stdout)=>{
							if(stdout.indexOf('dialout')===-1){
								this.grantAccess(results)
							}
						})
						return resolve(results.filter(Boolean))
					})
					.catch(reject);
			});
		});
	}

	static _checkPermission(mode) {
		const userPerm = (mode >> 6) & 0o7;
		const groupPerm = (mode >> 3) & 0o7;
		return (userPerm & 0o6) === 0o6 || (groupPerm & 0o6) === 0o6;
	}

	// 获取Linux设备信息
	static _getDeviceInfo(device) {
		return new Promise((resolve, reject) => {
			exec(`stat -c "%a %G" ${device}`, (error, stdout) => {
				if (error) return reject(new Error('无法读取设备信息'));
				const [mode, group] = stdout.trim().split(' ');
				resolve({mode, group});
			});
		});
	}

	// 授权Linux串口（需要sudo）
	static grantAccess(ports,user = process.env.USER) {
		if (platform !== 'linux') throw new Error('仅支持Linux系统');
		if (!/^\w+$/.test(user)) throw new Error('无效用户名格式');
		try {
			execSync(`pkexec usermod -a -G dialout ${user}`);
			ports.forEach(port=>{
				execSync(`pkexec chmod 666 ${port.path}`);
			})
			return true;
		} catch (error) {
			throw new Error(`授权失败: ${error.message}`);
		}
	}
}

module.exports = NativeSerialPort
// // 使用示例
// (async () => {
// 	try {
// 		const ports = await NativeSerialPort.list();
// 		console.log('可用串口:', JSON.stringify(ports, null, 2));
//
// 		// Linux下授权检测
// 		if (platform === 'linux' && ports.some(p => !p.accessible)) {
// 			console.log('尝试自动授权...');
// 			const targetPort = ports.find(p => p.path.includes('USB'));
// 			if (targetPort) {
// 				NativeSerialPort.grantAccess(targetPort.path);
// 				console.log('授权完成，请重新插拔设备');
// 			}
// 		}
// 	} catch (error) {
// 		console.error('错误:', error);
// 	}
// })();