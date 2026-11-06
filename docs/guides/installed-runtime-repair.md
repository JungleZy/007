# 主进程依赖修复包使用说明

适用报错：`Cannot find module '@seald-io/nedb'`。

用户提供的 `bw-frontend/installed-runtime-report.json` 已确认，安装目录中的 `app.asar` 与原始错误包完全一致，摘要以 `832ed71e` 开头，依赖仍在错误的 `node_modules/node_modules` 层级。正确文件的摘要以 `fa24d77e` 开头。

## 使用步骤

1. 退出软件，并关闭原来的错误弹窗。
2. 将“主进程依赖修复包-fix1.zip”完整解压到一个可写目录。
3. 双击 `repair-installed.cmd`。
4. 看到 `REPAIR_OK` 后，再启动软件。

默认检查当前用户的 `AppData/Local/Programs/nip-traffic-system`。如果安装在其他目录，把安装目录内的软件 EXE 拖到 `repair-installed.cmd` 上运行。

如果仍提示程序正在运行，先在任务管理器中退出该软件，再运行修复工具。若提示无写入权限，可右键 `repair-installed.cmd` 选择“以管理员身份运行”；自定义安装目录需同时正确指定。

## 修复范围

- 只替换安装目录中的 `resources/app.asar`。
- 替换前校验修复文件和已安装文件的 SHA-256，只接受已确认的错误版或已修复版。
- 原文件备份为同目录下的 `app.asar.before-fix1-时间戳.bak`。
- 使用同目录原子替换，并调用安装目录里的 Electron 检查实际模块加载。检查失败时自动恢复原文件。
- 数据库目录、数据库口令、账号和用户配置不在修复范围内，不需要删除或重新初始化。
- 可以重复运行，已修复时仅再次检查，不重复覆盖或创建备份。

工具在自身目录生成 `repair-result.txt` 和 `installed-runtime-report.json`。如果没有出现 `REPAIR_OK`，请提供这两个文件的内容。

## 已验证的边界

在与用户报告摘要相同的错误归档和原安装器 EXE 副本上，验证了替换成功、备份摘要一致、模块加载成功、重复执行不重复替换、探测失败时恢复原文件，以及修复文件损坏时拒绝修改。配置和数据库占位文件在各步骤中均保持原内容。

此包只处理本次已确认的主进程依赖问题，不代表目标系统及所有业务功能已经完成实机验收。
