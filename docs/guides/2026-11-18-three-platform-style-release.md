# 2026-11-18 三平台三风格交付

ARM64后续已增加ttyCH串口支持，当前交付应使用3.1.3-2，见 [ARM64串口修复交付](2026-11-18-arm64-ttych-release.md)。下表ARM64 3.1.3-1摘要仅保留为历史构建记录，旧文件在新包验证通过后清理。Windows产物不受本次ARM64修复影响。

源码提交：`6a27d756ba5223d12ab9f235f5b7c7a5c75a8854`。应用版本：3.1.3；ARM64 Debian修订号：3.1.3-1。

本轮分别构建HJJ、HJ、LJ三种风格，并在构建后恢复源码默认风格HJJ。所有产物位于仓库根相对路径 `bw-frontend/out/releases/`。

## 产物

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| 多功能报务综合训练系统-Win7-HJJ-3.1.3-ia32.zip | 1488375442 | `21eeee5e3bbd09501b5442da83593b36893348ec26729ece715c0183736796f0` |
| 多功能报务综合训练系统-Win7-HJ-3.1.3-ia32.zip | 1488373986 | `1fbac85a985a2b841706ba7eb7067dceb886a0ca018aa1139df25a6c7a716c90` |
| 多功能报务综合训练系统-Win7-LJ-3.1.3-ia32.zip | 1488378206 | `62b1ea66343c8c054e574e51b8def8fc3472eacfd38b4697fd4b5d9148bee222` |
| 多功能报务综合训练系统-Win10-HJJ-3.1.3-x64.zip | 1597518281 | `c2f857615c93d4618543c61e1f63612657216af1d8223b03fb07cdde81521ebe` |
| 多功能报务综合训练系统-Win10-HJ-3.1.3-x64.zip | 1597516825 | `dae3956cc36f8128f6c75e0ebde4a6a9dc198f11347eeb8440d9f179d317e79f` |
| 多功能报务综合训练系统-Win10-LJ-3.1.3-x64.zip | 1597521045 | `68e2b17e15d6c3f777b1accc3ddd4bff59f11f3c00e432c420cbff83867419ca` |
| 多功能报务综合训练系统-ARM64-HJJ-3.1.3-1_arm64.deb | 1770339756 | `96f4c5ac8e8a4ed1ec5b8af9b9147a2acc8a0ca9284fa27a8739248ba4f95426` |
| 多功能报务综合训练系统-ARM64-HJ-3.1.3-1_arm64.deb | 1770309648 | `fdbd6f2e0f09ce89ccfa603e0d3db39e01d89283a49445b344967819814ed12f` |
| 多功能报务综合训练系统-ARM64-LJ-3.1.3-1_arm64.deb | 1770309792 | `48712b7cdffc54e7b18b2448c4898b3706df56cbff256c92827ec88787f791cf` |

## 格式与使用

- Win7：Electron22.3.27 ia32完整解压包；完整解压后启动软件EXE。CH340驱动位于 `drivers/ch340/install-driver.cmd`。
- Win10：Electron30.5.1 x64完整解压包；完整解压后启动软件EXE。
- ARM64：银河麒麟V10桌面ARM64标准Debian安装包；用 `sudo apt install /绝对路径/对应文件.deb` 安装，安装后以普通桌面用户启动。

后续重试已成功下载NSIS 3.0.4.1与nsis-resources 3.4.1，并通过Electron Builder内置摘要校验。已从上述6个Windows ZIP分别封装同名 `.exe` 安装器，文件位于同一交付目录。Win7使用仓库compat2模板及32位7za解压器；Win10使用标准NSIS，可选择安装目录，卸载保留用户数据。ZIP保留为备用解压包。

EXE构建脚本：`bw-frontend/scripts/package-release-exe.cjs`；6个EXE的大小与SHA-256清单：`bw-frontend/out/releases/exe-release-report.json`。6个EXE均通过7zip归档完整性检查，尚未执行本轮EXE实际安装与卸载验收。

## 验证与限制

- 三种风格均重新构建前端与三个平台的应用目录。
- Windows包内Electron依赖加载与NeDB持久化探测通过，每包111份依赖清单。
- ARM64每包111份依赖清单检查通过；Debian封装检查156个ELF均为AArch64，最高glibc符号版本2.28。
- 6个Windows ZIP完整性测试全部通过，9个产物的大小与SHA-256已逐个计算。
- 19个桌面Node测试通过；4个Debian封装Python测试通过（使用 `py` 启动Python3.12.10）。
- 后端、Java、MySQL运行资产沿用本地已有资产，本轮未重新编译后端，也未执行后端Docker全量测试。
- 本轮未进行三平台实机安装、WebGL、音频、物理串口及数据库升级验收，ARM64为交叉构建结果。

两套本地设备资源 `frontend/public/equipment/` 与 `frontend/public/J210-742/` 已存在并随正常Vite/Electron构建流程打入应用归档。
