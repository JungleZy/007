const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {execFile} = require('node:child_process');
const archive = process.argv[2], output = process.argv[3];
const NativeSerialPort = require(path.join(archive, 'electron/serial/nativeSerialPort.js'));
const report = {os: os.release(), arch: process.arch, electron: process.versions.electron};
function execute(file, args) {
    return new Promise(resolve => execFile(file, args, {windowsHide: true, timeout: 10000, maxBuffer: 1024 * 1024, encoding: 'utf8'},
        (error, stdout, stderr) => resolve({exit: error ? error.code : 0, timeout: !!error?.killed, stdout, stderr})));
}
(async () => {
    const root = process.env.SystemRoot || 'C:\\Windows';
    report.registry = await execute(path.join(root, 'System32/reg.exe'), ['query', 'HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM']);
    report.applicationPorts = await NativeSerialPort.list().catch(error => ({error: error.message}));
    const script = '[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding; '
        + 'Get-WmiObject Win32_PnPEntity | Where-Object { $_.Name -match "COM[0-9]+" -or $_.PNPDeviceID -match "VID_1A86" } '
        + '| ForEach-Object { "{0}|{1}|ErrorCode={2}" -f $_.Name,$_.PNPDeviceID,$_.ConfigManagerErrorCode }';
    report.pnp = await execute(path.join(root, 'System32/WindowsPowerShell/v1.0/powershell.exe'),
        ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')]);
    fs.writeFileSync(output, JSON.stringify(report, null, 2));
    console.log('SERIAL_REPORT_SAVED: ' + output);
})().catch(error => {fs.writeFileSync(output, JSON.stringify({error: error.message}));process.exitCode = 1;});
