const {ipcMain} = require('electron')
const context = require("../core/node_core_ctx");
const NativeSerialPort = require("../serial/nativeSerialPort");

ipcMain.on("controller.serialPort.getSerialPortList", async (event) => {
	try {
		const ports = await NativeSerialPort.list()
		console.log('SerialPorts:');
		console.log(ports.map(p => `- ${p && p.path ? p.path : p}`).join('\n'));
		event.returnValue = ports;
		event.reply(`controller.serialPort.getSerialPortList`, ports);
	} catch (error) {
		console.error('获取串口失败:', error.message);
		event.returnValue = [];
		event.reply(`controller.serialPort.getSerialPortList`, []);
	}
})
ipcMain.on("controller.serialPort.linkPort", async (event, args) => {
	// 这是 sendSync 路由：任何分支都必须落 event.returnValue，否则渲染进程会永久卡死。
	try {
		await context.db.remove({_id: 3})
		await context.db.insert({_id: 3, text: args})
		console.log('SelectSerialPort:' + args);
		event.returnValue = args;
		event.reply(`controller.serialPort.linkPort`, args);
	} catch (error) {
		console.error('保存串口失败:', error.message);
		event.returnValue = '';
		event.reply(`controller.serialPort.linkPort`, '');
	}
})
// 显式授权：只由用户在串口列表中点击「授权」触发，列举串口本身不再提权。
ipcMain.on("controller.serialPort.grantAccess", async (event, args) => {
	let result
	try {
		let targets = Array.isArray(args) ? args.filter(Boolean) : (args ? [args] : [])
		if (targets.length === 0) {
			const ports = await NativeSerialPort.list()
			targets = ports.filter(port => port && port.accessible === false)
		}
		result = NativeSerialPort.grantAccess(targets)
	} catch (error) {
		result = {ok: false, message: `授权失败：${error.message}`, granted: []}
	}
	console.log('GrantSerialAccess:', result.ok, result.message);
	event.returnValue = result;
	event.reply(`controller.serialPort.grantAccess`, result);
})
