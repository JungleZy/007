const {exec, execSync} = require('child_process');
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
				// 列举串口是只读操作，绝不在此自动 pkexec 提权：
				// 提权必须由用户在串口列表里显式点击「授权」触发（controller.serialPort.grantAccess）。
				Promise.all(devicePromises)
					.then(results => resolve(results.filter(Boolean)))
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

	// 授权 Linux 串口（pkexec 会弹出系统提权框，仅允许用户显式点击后调用）。
	// 恒不抛异常：用户取消 pkexec、缺少 polkit agent 等失败都只返回结果对象，
	// 否则异常会落到无 catch 的异步回调栈上，直接打挂主进程。
	// 返回值必须是可结构化克隆的纯数据（要经 preload 桥回渲染进程）。
	static grantAccess(ports, user = process.env.USER) {
		if (platform !== 'linux') {
			return {ok: false, message: '当前系统无需串口授权'};
		}
		if (!/^\w+$/.test(user || '')) {
			return {ok: false, message: '无效用户名格式，无法授权'};
		}
		const targets = (Array.isArray(ports) ? ports : [ports])
			.filter(Boolean)
			.map(port => (typeof port === 'string' ? port : port.path))
			.filter(path => typeof path === 'string' && /^\/dev\/[\w/.-]+$/.test(path));
		try {
			execSync(`pkexec usermod -a -G dialout ${user}`);
			targets.forEach(path => {
				execSync(`pkexec chmod 666 ${path}`);
			});
			return {
				ok: true,
				message: `已将用户 ${user} 加入 dialout 组${targets.length ? `，并放开 ${targets.join('、')} 的读写权限` : ''}`,
				granted: targets
			};
		} catch (error) {
			return {ok: false, message: `授权失败：${error.message}`, granted: []};
		}
	}
}

module.exports = NativeSerialPort
