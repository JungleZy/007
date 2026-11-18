# ARM64 ttyCH 串口修复交付

Linux串口枚举及显式授权增加WCH驱动的ttyCH系列设备支持，例如ttyCH0、ttyCH341USB0、ttyCH9344USB0；保留USB和ACM支持及授权路径校验。

本次按用户要求替换上午的 `bw-frontend/out/kylin-v10-arm64/麒麟ARM64-3.1.2全量更新补丁.zip`，保留原适用归档摘要。补丁只接受报告中记录的旧包；上午旧补丁已经安装的机器可使用新的完整安装包升级。补丁摘要以同目录 `kylin-update-patch-report.json` 为准。

完整包沿用已交付3.1.3-1三种风格的内容，仅替换 `electron/serial/nativeSerialPort.js`，归档内其他文件逐字节比较一致。Debian修订号提升为3.1.3-2。未重新编译后端，原有后端及运行组件继续沿用。

完整包输出在 `bw-frontend/out/releases/`，文件名为 `多功能报务综合训练系统-ARM64-{HJJ,HJ,LJ}-3.1.3-2_arm64.deb`。大小和SHA-256见 `bw-frontend/out/releases/arm64-ttych-release-report.json`。

补丁：退出软件并等待数据库退出，解压后执行 `sudo bash ./repair-update.sh`，成功后以普通桌面用户启动软件。完整包：使用 `sudo apt install /绝对路径/对应安装包.deb` 覆盖升级。

已执行5项串口测试、4项Debian封装测试；补丁ZIP完整性及Git Bash语法检查通过。三个完整包均已逐项读取TAR/XZ，目录条目和app.asar摘要核对通过。本机WSL服务连接超时，未进行dpkg实际安装；真实ARM64串口枚举、授权、连接与收发仍需目标机验证。

清理范围为本次多余ttyCH定向补丁、构建暂存文件，以及新包验证成功后被替代的三种风格3.1.3-1完整包。保留上午替换后的更新补丁和Windows交付文件。
