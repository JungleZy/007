const context = require("../core/node_core_ctx");
const {platform} = process;

function serialApiHandle() {
	context.mainWindow.webContents.session.on('select-serial-port', async (event, portList, webContents, callback) => {
		context.mainWindow.webContents.session.on('serial-port-added', (event, port) => {
			console.log('serial-port-added FIRED WITH', port)
		})

		context.mainWindow.webContents.session.on('serial-port-removed', (event, port) => {
			console.log('serial-port-removed FIRED WITH', port)
		})
		event.preventDefault();
		const findOne = await context.db.findOne({_id: 3});
		console.log("connect:",findOne.text)
		const selectedPort = portList.find((device) => {
			return device.portName === findOne.text || device.displayName === findOne.text ||device.portName.indexOf("USB")>-1
		})
		console.log(selectedPort)
		if(platform==='linux'){
			callback(selectedPort.portId)
		}else {
			callback(selectedPort)
		}
	})
	//授权
	context.mainWindow.webContents.session.setPermissionCheckHandler((webContents, permission, requestingOrigin, details) => {
		return permission === 'serial'
	})
	//授权
	context.mainWindow.webContents.session.setDevicePermissionHandler((details) => {
		return details.deviceType === 'serial'
	})
}

function listSerialPorts(data) {

}

module.exports = {
	serialApiHandle,
	listSerialPorts
}