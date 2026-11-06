# HJ风格整合安装包 3.1.2

本轮目标：清理历史打包输出，从当前源码重新生成HJ风格Win7 ia32兼容包与麒麟V10桌面ARM64包。两套产物已完成构建，验证范围与证据见下文。

## 包含的修改

- 前端默认风格由HJJ改为HJ；应用版本3.1.2。
- Win7和Linux ARM64默认启用ANGLE/SwiftShader，移除旧禁用GPU/软件渲染配置；无需再叠加WebGL补丁。
- 新数据库默认管理员为 `admin / 123456a`，保存PBKDF2密码摘要；已有实例不重置账号密码。
- Debian归档包含父目录、root归属和明确执行权限，保留实际解包验收。
- Win7继续使用compat2内置指令安装流程，不引入曾崩溃的System/UAC/StdUtils/nsDialogs插件。

## Win7 CH340/CH341离线驱动

驱动来自沁恒官方 `https://www.wch-ic.com/download/file?id=5`，原文件CH341SER.ZIP，版本 `4.0.2026.02`，SHA-256为 `59967d9ce371d0bf3df02dec0b66c8dfbf9ca576da0572ff4404148a7c381807`。官方INF列出Windows7并包含x86、x64架构。准备脚本 `bw-frontend/scripts/prepare-ch340.cjs` 校验原始ZIP摘要、厂商SETUP.EXE及CAT/x86/x64驱动签名，并生成逐文件摘要清单。构建脚本再次核对最终应用目录中的驱动文件摘要。

驱动安装步骤：

1. 正常完成Win7软件安装，安装器随后以 `runas` 打开厂商驱动安装窗口。
2. 允许管理员权限，点击厂商窗口中的INSTALL（安装），等待驱动安装完成，再拔插设备。
3. 如果取消了权限提示，可在开始菜单 `NipTrafficSystem Win7 x86 → CH340 Driver` 重试；安装目录 `drivers/ch340/install-driver.cmd` 也可启动。
4. 检查设备管理器出现 `USB-SERIAL CH340(COMx)`，无黄色警告，再到业务软件中选择该COM口并实际收发。

软件安装器及业务软件继续使用普通用户权限，只有驱动安装请求管理员权限。静默安装软件不自动运行驱动安装器，部署人员需通过入口单独安装。卸载软件不移除系统驱动，避免影响其他程序。完整厂商驱动同时携带多个Windows架构，这是按操作系统选择驱动所需，不属于误混应用架构。

构建机校验通过不代表所有Win7补丁环境/CH340硬件已经验证。若现场出现签名错误，提供Win7版本/补丁、设备管理器错误码及厂商安装窗口信息；不能通过关闭强制签名或测试模式处理。麒麟包不携带Windows驱动，Linux使用系统内核串口驱动和现有授权流程。

## 清理与验证边界

清理范围为 `bw-frontend/out/` 下历史EXE、deb、ZIP补丁、解包目录和打包报告；保留独立的初始化业务SQL与清单。源码、组件下载缓存、数据库用户数据不属于清理范围。旧运行资产为刷新后端暂存到 `.cache/pre-hj-3.1.2-*`，属于构建缓存。

本轮已运行前端测试167项、相关桌面测试13项及Debian封装测试4项，均通过；驱动下载与签名检查通过。标准MySQL8与MySQL5.7 profile分别执行 `DatabaseTransferServiceTest` 的7项测试及package，均成功，使用JDK21。构建机未安装Docker，未完成要求Docker的后端全量验证，不能将上述结果表述为全量测试通过。

## Win7产物

- `bw-frontend/out/win7-ia32/多功能报务综合训练系统-HJ-win7-3.1.2-ia32-compat2.exe`，1,286,612,488字节。
- SHA-256：`353a4a9b323f3e98d097523ee5d246d4b8d633e7aa2c42db30e955acf45018f1`。
- 同版本备用ZIP：`多功能报务综合训练系统-HJ-win7-3.1.2-ia32-unpacked.zip`，1,465,852,761字节，SHA-256 `c38472b524ccd5d04234c69e96f894fec0c935af5e415bb307e0fcbf72972001`。ZIP使用者需另行运行随包 `drivers/ch340/install-driver.cmd` 安装驱动。
- 报告：`bw-frontend/out/win7-ia32/win7-compat2-report.json`。本轮最终归档中的HJ配置、图形配置、固定默认密码已与源码核对。

Win7包本地实际验收已通过：

- 在Windows构建机执行最终安装EXE，安装到独立中文/空格路径，退出码0；校验安装后的app.asar与构建目录一致，运行组件、工具和CH340文件摘要均匹配。
- 用安装后的ia32 Electron执行归档依赖探测，通过。使用独立数据库16912、后端18097及临时数据目录，完成首次建库/迁移、`admin / 123456a`真实HTTP登录、正常停机、再次启动与再次登录；重启不再创建账号。
- 使用安装后的Electron EXE/DLL和归档内图形配置，在隔离测试入口验证WebGL1与WebGL2：均可创建上下文、绘制并读回红色像素 `[255,0,0,255]`，GL错误码0。测试强制软件模式，模拟Win7默认图形路径。必须读取像素报告；仅进程返回0不能认为测试已执行（打包后的Electron可能忽略传入脚本而启动主程序）。
- 实际卸载删除程序归档、保留测试用未知文件；测试结束恢复构建机原有快捷方式与注册项，清理临时实例。
- 报告：`bw-frontend/out/win7-ia32/hj-installed-validation.json`。以上是Windows构建机的ia32运行证据；没有在本机安装CH340系统驱动，不代替新Win7机器上的驱动安装与物理串口收发验收。

## 麒麟ARM64产物

- `bw-frontend/out/kylin-v10-arm64/nip-traffic-system-kylin-arm64_3.1.2-1_arm64.deb`，1,770,367,338字节。
- SHA-256：`80f218c4cdc258652f3a28bd2c95e33c5c24a933f5421e690ea2b90c65d644ad`。
- `kylin-arm64-report.json`记录156个AArch64 ELF检查通过、最高glibc符号要求2.28；HJ前端、入口图形配置和默认密码源码已从新app.asar核对。
- 最终deb在空白WSL隔离根目录实际 `dpkg --unpack` 返回0，配置文件存在、启动器具有执行权限、app.asar摘要与构建目录一致，725个运行资产文件摘要匹配。报告：`bw-frontend/out/kylin-v10-arm64/hj-deb-validation.json`。此为真实解包检查，不是APT模拟安装；麒麟ARM64原生启动、WebGL、音频与串口仍需现场验收。
- Windows应用驱动不进入Linux包。麒麟的CH340识别依赖系统内核驱动及串口权限，须在实机插入设备验收。

统一安装说明：`bw-frontend/out/HJ-3.1.2安装说明.txt`；统一摘要清单：`bw-frontend/out/HJ-3.1.2-SHA256.txt`。

## Win7现场串口搜索问题及定向补丁

用户反馈：设备管理器可见串口且同时存在其他串口，应用一直搜索不到。未收到现场诊断报告，不能仅凭此反馈认定驱动未安装或其他串口产生冲突。

代码检查确认两处问题：Windows枚举使用无超时的全量 `Win32_PnPEntity` WMI查询；Electron自动选择仅检查portName中是否包含USB，而Windows常返回COM编号。修复后枚举使用 `HKLM\HARDWARE\DEVICEMAP\SERIALCOMM`，8秒超时，完整列出、去重并按编号排序全部已登记COM口；用户选择按COM编号匹配，已保存设备失效时不自动连接其他端口，多个USB设备时要求显式选择。

补丁：`bw-frontend/out/win7-ia32/Win7-HJ-3.1.2串口修复补丁.zip`，281,813,055字节，SHA-256 `1269bece64e0f47f95d47f72a442d278ae5c1cd989607673efba603f57822efa`。仅修改归档中的 `electron/serial/nativeSerialPort.js` 与 `electron/serial/index.js`，其余内容逐文件比较一致。仅接受本轮原始3.1.2归档，严格校验摘要，替换前备份，不修改数据库。

操作：退出软件，完整解压补丁，运行 `repair-installed.cmd`；出现REPAIR_OK后重新启动软件，刷新真实串口列表，选择设备管理器中CH340对应的COM号。其他串口无需卸载。如果仍失败，执行补丁中的 `check-serial.cmd`，提供 `serial-report.json`、CH340的COM号和设备管理器设备状态。诊断读取注册表和PnP设备状态，WMI诊断自身限时10秒，不安装驱动或改变设备设置。

验证：3个回归测试覆盖多COM枚举、排序/去重、超时、精确选口及失效选择；归档依赖探测与ZIP完整性检查通过；实际PowerShell补丁安装、备份、重复运行、未知版本拒绝、用户数据保留以及诊断工具在Windows构建机执行均通过。构建机本次无已登记COM设备；未将这些结果当作客户Win7物理串口连接已恢复。报告为 `serial-patch-report.json` 与 `serial-patch-validation.json`。

当前原始3.1.2完整安装包文件尚未集成此串口补丁，已安装实例使用上方定向补丁；后续全量包必须从修正后的源码重建应用目录。

后续反馈：用户确认串口补丁“可行”，要求重打Win7完整安装包，已进入 [Win7 HJ 3.1.3交付记录](2026-11-06-win7-hj-3.1.3-release.md)。麒麟ARM64的3.1.2包仍按本页记录交付。
