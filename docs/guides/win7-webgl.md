# Win7 WebGL 不可用修复

用户报告部分功能显示 `your browser does not support WebGl`。当前入口 `bw-frontend/main.js` 原先同时调用 `app.disableHardwareAcceleration()` 并传入 `disable-gpu`、`disable-software-rasterizer`，禁用了硬件及软件渲染路径。该配置会妨碍WebGL上下文创建，不应直接归因于用户缺少浏览器插件。

## 当前实现

- 已移除上述全局禁用参数。
- 新增 `bw-frontend/electron/core/graphics.js`，Win7（Windows内核6.1）默认使用 Electron 自带的 ANGLE/SwiftShader 软件渲染，支持WebGL；正常新版Windows/Linux保留自动选择。
- 可由部署环境设置 `NIP_GRAPHICS_MODE=software` 强制软件兼容，或 `NIP_GRAPHICS_MODE=auto` 让Electron自动选择。软件模式性能取决于CPU，仍需实机验证具体3D场景与驱动交互。
- 不使用 `ignore-gpu-blocklist` 等强行忽略驱动限制的参数，不需要用户安装所谓WebGL浏览器插件。

## 已安装 compat2 的修复

文件：`bw-frontend/out/win7-ia32/Win7-WebGL修复补丁.zip`，272,650,513字节（约273MB）。

SHA-256：`31faabe23c940ad805a7773e15d787ed1005fb0e116d10474e01c41e886d5332`。

1. 退出软件和报错弹窗。
2. 完整解压补丁，双击 `repair-installed.cmd`。
3. 出现 `REPAIR_OK` 后重启软件，再打开原报错功能。

默认优先查找 `%LOCALAPPDATA%/Programs/nip-traffic-system-win7`，否则查找旧默认目录。自定义安装目录时，把软件EXE拖到 `repair-installed.cmd` 上运行。

补丁仅接受已核对的Win7原归档，摘要为 `28eba04be6db21f5f787a6cb14ab0b624f5b15d31c2fac1ba6680ffb7b641c63`，修复后为 `135afa7e79b41a71611790b5bb34f739973c291efd89903d23d2ac7e82dd479f`。只改变归档中的 `main.js` 和新增 `electron/core/graphics.js`，其他归档文件逐字节检查一致。原文件自动备份为 `app.asar.before-webgl-*.bak`，依赖探测失败时自动回退。数据库和用户配置不被替换。

若提示不是已知版本，不要绕过校验；提供 `repair-result.txt` 和 `installed-runtime-report.json` 核对实际版本。旧compat2安装器/ZIP仍保留原归档，已安装用户应用补丁；以后从当前源码重新生成应用目录，再走compat2封装流程才会带入新配置。

## 验证证据与范围

- 2个图形启动配置测试通过，保证Win7启用SwiftShader且不再同时关闭硬件与软件渲染。
- 使用Electron22.3.27 ia32，在本机构建环境强制Win7软件兼容配置，分别创建WebGL和WebGL2上下文。
- 两种上下文都实际清屏为红色、读取到RGBA `[255,0,0,255]`，`gl.getError()` 为0；渲染器为 ANGLE/SwiftShader。
- 使用原应用副本执行补丁，原文件备份和新摘要校验通过；包内依赖加载通过，验证用用户数据标记保留。
- 随后从补丁后的实际app.asar加载图形配置，再次完成上述WebGL1/2绘制测试。
- 本轮尚未收到用户Win7故障机应用补丁后的反馈；未把简单绘制测试等同于完整教学模型、附件和所有3D页面实机验收。

重建补丁：在 `bw-frontend/` 执行 `node scripts/build-win7-webgl-patch.cjs`，输入为已验证的旧Win7 `out/win7-ia32/win-ia32-unpacked/resources/app.asar`。脚本检测版本、仅替换两个文件、验证依赖并生成摘要报告，不静默给其他版本打补丁。
# Win7补丁脚本兼容修正（2026-11-06）

后续用户反馈：应用脚本兼容修正后确认“可以了”，随后要求重新生成HJ风格完整包。新完整包集成图形源码修复，发布记录见 [HJ 3.1.2](2026-11-06-hj-desktop-release.md)。下文“待反馈”为修正包生成时的历史验证边界。

用户提供 `bw-frontend/repair-result.txt`：`SHA256Managed` 不含 `Dispose` 方法。失败发生在首次读取补丁摘要后的清理阶段，尚未替换安装文件。旧.NET的HashAlgorithm公开 `Clear()`，因此将校验函数清理改为 `$stream.Close(); $algorithm.Clear()`；共用模板 `bw-frontend/support/installed-runtime/repair-installed.ps1` 已同步修正。

已解压旧补丁的用户：使用 `bw-frontend/out/win7-ia32/Win7-WebGL补丁脚本兼容修正.zip`（2,822字节），将其中 `repair-installed.ps1` 覆盖到原完整补丁目录，保留payload和其他文件，再执行原 `repair-installed.cmd`。

首次使用可选完整修正版：`bw-frontend/out/win7-ia32/Win7-WebGL修复补丁-pscompat.zip`（272,650,516字节），SHA-256为 `57dbfca245a5ec541aba4ebcd71285d01de579e31e428edcf5a2979853d996cf`。补丁payload及原有版本校验摘要均保持不变；不需要重新生成应用归档，不修改数据库。构建脚本为 `bw-frontend/scripts/fix-win7-webgl-script.cjs`。

验证：修复后的实际FileHash函数在构建机PowerShell对已知输入计算SHA-256通过，两份ZIP完整性检查通过。逐文件比对完整新旧ZIP，除脚本中的清理方法外均一致。旧Win7 PowerShell/.NET实机执行结果仍待用户反馈，不能将本机构建验证等同于现场验证。
