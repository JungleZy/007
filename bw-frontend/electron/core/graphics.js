const os = require('node:os');

// Win7 老驱动差异较大，使用 Electron 随附的 ANGLE/SwiftShader 提供 WebGL。
// 不能同时 disable-gpu 和 disable-software-rasterizer：那会关闭两条渲染路径。
function configureGraphics(app, {platform = process.platform, arch = process.arch, release = os.release(), mode = process.env.NIP_GRAPHICS_MODE} = {}) {
    const legacyWindows = platform === 'win32' && /^6\.[01](?:\.|$)/.test(release);
    // 麒麟ARM64桌面驱动差异较大，默认使用随Electron分发的SwiftShader。
    const software = mode === 'software' || (mode !== 'auto' && (legacyWindows || (platform === 'linux' && arch === 'arm64')));
    if (software) {
        app.commandLine.appendSwitch('use-gl', 'angle');
        app.commandLine.appendSwitch('use-angle', 'swiftshader');
    }
    return software ? 'software' : 'auto';
}

module.exports = {configureGraphics};
