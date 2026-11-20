# 桌面端（Electron + Vue）

主进程入口为 `bw-frontend/main.js`，桌面业务代码在 `bw-frontend/electron/`，Vue 工程在 `bw-frontend/frontend/`。
详细工程文档统一位于仓库根 `docs/`；本页仅提供当前命令和文档入口。

## 开发入口

先分别在 `bw-frontend/` 和 `bw-frontend/frontend/` 安装锁定依赖（`npm ci`）。在 `bw-frontend/` 的两个终端分别执行：

```text
npm run dev-f
npm run dev-e
```

前端开发端口为 18000；后端、数据库和设备资源准备见相应指南。

## 打包入口

打包前先执行 [docs/guides/desktop-packaging-checklist.md](../docs/guides/desktop-packaging-checklist.md)，
补齐 Git 忽略的设备资源、目标平台运行时和经验证的后端产物。以下命令在 `bw-frontend/` 执行：

| 目标 | 应用目录 / 安装器构建入口 | 完整流程 |
| --- | --- | --- |
| Windows x64 | `npm run build-e-w -- --x64` | [docs/guides/integrated-desktop-install.md](../docs/guides/integrated-desktop-install.md) |
| Win7 ia32 | `npm run build-e-win7-32 -- --dir`，再执行 compat2 封装 | [docs/guides/win7-installer-system-dll.md](../docs/guides/win7-installer-system-dll.md) |
| 麒麟 ARM64 | `npm run build-e-kylin-arm64`，再运行 `py -3 scripts/build-kylin-deb.py --arch arm64` | [docs/guides/kylin-v10-arm64-install.md](../docs/guides/kylin-v10-arm64-install.md) |
| 麒麟龙芯 HJ/HJJ/LJ | `py -3 scripts/build-loongarch-styles.py` | [docs/guides/kylin-loongarch64-packaging.md](../docs/guides/kylin-loongarch64-packaging.md) |

通用 Electron 构建配置与实际 npm scripts 在 `bw-frontend/package.json`；平台覆盖配置在 `bw-frontend/build/`。
龙芯入口由 `bw-frontend/scripts/build-loongarch-desktop.cjs` 装配目标 Electron，不使用 electron-builder 22 的架构枚举。
旧模板中的 `build-frontend`、`encrypt`、`build-w`、`build-l` 等命令不适用于当前工程。

前端风格由 `NIP_INTERFACE_STYLE=HJ|HJJ|LJ` 在构建时选择，未设置时默认 HJJ。
龙芯入口 `npm run build-e-kylin-loongarch64 -- --style HJ` 会设置该变量并生成独立风格目录；
三风格批量入口负责依次构建前端，避免共享 `frontend/dist/` 相互覆盖。

## 交付目录

最终安装包统一放在 `bw-frontend/out/releases/`，交付时仅保留 `.deb`、`.exe`。
龙芯批量构建暂时在该目录生成摘要和报告，必须先完成验证，再按指南归档或清理这些非安装包文件。
未打包应用目录、组件缓存和日志保留在各自构建目录；不要把整个 `out/` 或 `.cache/` 当作安装包交付。

安装包存在、静态校验通过与实机验收是不同状态。平台兼容范围、同版本风格切换、重建前归档要求及验证命令见对应指南。
