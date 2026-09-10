const {ipcMain} = require('electron')
const {listSerialPorts} = require("../serial");
const context = require("../core/node_core_ctx");
const NativeSerialPort = require("../serial/nativeSerialPort");

ipcMain.on("controller.serialPort.getSerialPortList", async (event) => {
	try {
		const ports = await NativeSerialPort.list()
		console.log('SerialPorts:');
		console.log(ports.map(p => `- ${p}`).join('\n'));
		event.returnValue = ports;
		event.reply(`controller.serialPort.getSerialPortList`, ports);
	} catch (error) {
		console.error('获取串口失败:', error.message);
		event.returnValue = [];
		event.reply(`controller.serialPort.getSerialPortList`, []);
	}
})
ipcMain.on("controller.serialPort.linkPort", async (event, args) => {
	await context.db.remove({_id: 3})
	const findOne = await context.db.insert({_id: 3, text: args})
	console.log('SelectSerialPort:' + args, findOne);
	event.returnValue = args;
	event.reply(`controller.serialPort.getSerialPortList`, args);
})