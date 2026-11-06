const {exec, execFile, execFileSync} = require('child_process');
const {readdirSync, realpathSync, statSync} = require('fs');
const {posix, join} = require('path');
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
            // SERIALCOMM contains currently registered ports, including CH340, onboard and Bluetooth.
            // Avoid an unbounded Win32_PnPEntity query on old Win7 WMI providers.
            const registry = join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'reg.exe');
            execFile(registry, ['query', 'HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM'],
                {windowsHide: true, timeout: 8000, maxBuffer: 1024 * 1024}, (error, stdout) => {
                    const ports = [...new Set(String(stdout || '').split(/\r?\n/)
                        .map(line => /\bREG_SZ\s+(COM\d+)\s*$/i.exec(line)?.[1].toUpperCase())
                        .filter(Boolean))].sort((a, b) => Number(a.slice(3)) - Number(b.slice(3)));
                    if (error && (error.killed || error.code !== 1)) {
                        return reject(new Error(error.killed ? '读取串口列表超时，请刷新重试' : '读取系统串口列表失败，请提供串口诊断报告'));
                    }
                    // reg query exits 1 when no SERIALCOMM key exists (no registered serial devices).
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
    const requested = Array.isArray(ports) ? ports : [ports];
    const granted = [];
    let groupUpdated = false;
    try {
      if (!requested.length) {
        return {ok: false, message: '请选择需要授权的串口设备', granted: []};
      }
      const devices = new Set(readdirSync('/dev'));
      const targets = requested.map(port => {
        const path = typeof port === 'string' ? port : port?.path;
        if (typeof path !== 'string' || !/^\/dev\/tty(?:USB|ACM)\d+$/.test(path)
          || posix.normalize(path) !== path || !devices.has(posix.basename(path))) {
          throw new Error('无效串口设备路径');
        }
        const canonical = realpathSync(path);
        if (canonical !== path || !statSync(canonical).isCharacterDevice()) {
          throw new Error('无效串口设备路径');
        }
        return canonical;
      });
      execFileSync('pkexec', ['usermod', '-a', '-G', 'dialout', user]);
      groupUpdated = true;
      targets.forEach(path => {
        execFileSync('pkexec', ['chmod', '666', path]);
        granted.push(path);
      });
      return {
        ok: true,
        message: `已将用户 ${user} 加入 dialout 组${targets.length ? `，并放开 ${targets.join('、')} 的读写权限` : ''}`,
        granted,
        groupUpdated
      };
    } catch (error) {
      return {
        ok: false,
        message: groupUpdated
          ? `用户组已更新${granted.length ? `，已授权 ${granted.join('、')}` : ''}，后续授权失败：${error.message}`
          : `授权失败：${error.message}`,
        granted,
        groupUpdated
      };
		}
	}
}

module.exports = NativeSerialPort
