const {dialog} = require("electron");
const context = require("../core/node_core_ctx");

// 主进程侧的可见提示：串口选择失败时用户必须能看到原因，只写控制台等于静默失败。
// 无窗口/无 GUI 的环境（例如单元验证）下 dialog 不可用，降级为日志。
function showSerialNotice(title, message) {
	console.warn(`[serial] ${title}：${message}`);
	try {
		dialog.showErrorBox(title, message);
	} catch (e) {
		console.warn(`[serial] 提示框不可用：${e.message}`);
	}
}

// 读取用户此前选定的串口名；首次安装时 _id:3 尚未写入，findOne 返回 null。
async function readSelectedPortName() {
	try {
		const saved = await context.db.findOne({_id: 3});
		return saved && saved.text ? String(saved.text) : '';
	} catch (e) {
		console.warn(`[serial] 读取已选串口失败：${e.message}`);
		return '';
	}
}

// 先按用户选定的串口名精确匹配，未命中再退回任意 USB 串口。
function pickPort(portList, savedPortName) {
	const devices = Array.isArray(portList) ? portList : [];
	const matched = savedPortName
		? devices.find((device) => device
			&& (device.portName === savedPortName || device.displayName === savedPortName))
		: undefined;
	if (matched) return matched;
	return devices.find((device) => device
		&& typeof device.portName === 'string'
		&& device.portName.indexOf('USB') > -1);
}

async function onSelectSerialPort(event, portList, webContents, callback) {
	event.preventDefault();
	const savedPortName = await readSelectedPortName();
	const selectedPort = pickPort(portList, savedPortName);
	if (!selectedPort) {
		showSerialNotice('未找到可用串口', savedPortName
			? `未找到已选串口「${savedPortName}」，请检查设备连接后在串口列表中重新选择。`
			: '未检测到可用的 USB 串口设备，请连接设备后在串口列表中选择。');
		// 传空串表示放弃本次选择；传 undefined 或设备对象都会让 Electron 端取值失败。
		callback('');
		return;
	}
	// 任何平台都只接受 portId 字符串，回传设备对象等同于没选。
	callback(selectedPort.portId);
}

function serialApiHandle() {
	const session = context.mainWindow.webContents.session;
	// 设备增删监听只在此注册一次；放进 select-serial-port 回调内会随每次选择累积泄漏。
	session.on('serial-port-added', (event, port) => {
		console.log('[serial] 串口接入：', port && port.portName ? port.portName : port);
	});
	session.on('serial-port-removed', (event, port) => {
		console.log('[serial] 串口移除：', port && port.portName ? port.portName : port);
	});
	session.on('select-serial-port', onSelectSerialPort);
	//授权
	session.setPermissionCheckHandler((webContents, permission, requestingOrigin, details) => {
		return permission === 'serial'
	})
	//授权
	session.setDevicePermissionHandler((details) => {
		return details.deviceType === 'serial'
	})
}

module.exports = {
	serialApiHandle
}
