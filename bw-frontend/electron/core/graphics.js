const fs = require('node:fs');
const path = require('node:path');

// 默认走Chromium自动选择（硬件GPU优先，驱动被拉黑时由Chromium自带的软件合成兜底）。
// 强制ANGLE/SwiftShader会让整页合成和视频绘制都落到CPU上，低配机器明显卡顿，因此只在需要时启用：
// 1. 部署环境显式设置 NIP_GRAPHICS_MODE=software；
// 2. 本机曾自动检测到GPU进程崩溃或WebGL不可用，已写入回退标记（见 watchGraphics）。
// NIP_GRAPHICS_MODE=auto 显式忽略回退标记。
// 不能同时 disable-gpu 和 disable-software-rasterizer：那会关闭两条渲染路径。
const FALLBACK_FILE = 'graphics-fallback.json';

function fallbackPath(app) {
    return path.join(app.getPath('userData'), FALLBACK_FILE);
}

function readFallback(app) {
    try {
        return JSON.parse(fs.readFileSync(fallbackPath(app), 'utf8')).mode === 'software';
    } catch {
        return false;
    }
}

function writeFallback(app, reason) {
    try {
        fs.mkdirSync(app.getPath('userData'), {recursive: true});
        fs.writeFileSync(fallbackPath(app), JSON.stringify({mode: 'software', reason, at: new Date().toISOString()}));
        return true;
    } catch {
        return false;
    }
}

function configureGraphics(app, {mode = process.env.NIP_GRAPHICS_MODE, fallback} = {}) {
    const software = mode === 'software' || (mode !== 'auto' && (fallback ?? readFallback(app)));
    if (software) {
        app.commandLine.appendSwitch('use-gl', 'angle');
        app.commandLine.appendSwitch('use-angle', 'swiftshader');
    }
    return software ? 'software' : 'auto';
}

// WebGL状态带 _off 表示当前没有任何WebGL实现（硬件被拉黑且未启用软件兜底）。
function webglUnavailable(status) {
    return /_off/.test(String(status?.webgl ?? ''));
}

// 仅在自动模式且未被部署环境显式指定时监控：
// - GPU进程异常退出：写回退标记，下次启动改用软件渲染（本次会话Chromium会自行降级）。
// - 主窗口就绪后WebGL不可用：写回退标记并返回true，由调用方重启应用。
function watchGraphics(app, {graphicsMode, mode = process.env.NIP_GRAPHICS_MODE} = {}) {
    const enabled = graphicsMode === 'auto' && mode === undefined;
    if (enabled) {
        app.on('child-process-gone', (_event, details) => {
            if (details?.type === 'GPU' && !['clean-exit', 'killed'].includes(details.reason)) {
                writeFallback(app, `gpu-${details.reason}`);
            }
        });
    }
    return {
        needsSoftwareRelaunch() {
            if (!enabled || !webglUnavailable(app.getGPUFeatureStatus())) return false;
            return writeFallback(app, 'webgl-unavailable');
        }
    };
}

module.exports = {configureGraphics, watchGraphics, webglUnavailable};
