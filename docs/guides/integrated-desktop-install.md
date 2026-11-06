# Electron 30 整合部署

每次打包先执行 [桌面打包与交付检查清单](desktop-packaging-checklist.md)。本文部分产物和验证记录为早期批次；麒麟ARM64后续已生成安装包并获得用户安装成功反馈，当前问题与WebGL补丁见 [麒麟指南](kylin-v10-arm64-install.md)。

新增32位版本：已按用户后续要求生成独立 Electron22 ia32＋Java21 x86＋MySQL5.7 Win32 完整安装包，见 `docs/guides/windows-ia32-install.md`。本文下方的 Electron30/MySQL8 说明仍用于原x64版本。

本轮按用户决定保持 Electron 30.5.1。Windows 7 的实际可用性由用户拿到安装包后验证，不作为本轮实现阻断项，也不标记为已兼容。

## 当前实现

- Windows x64 已准备绿色 MySQL 8.0.46 服务端、Java 21 运行环境及 Quarkus fast-jar。
- 安装态启动时先取得单实例锁，再初始化/启动数据库，应用迁移，等待后端响应及文件服务就绪，最后进入业务窗口。
- 首次启动创建 `admin` 管理员，默认密码固定为 `123456a`，通过本机弹窗显示。数据库root和应用连接口令仍独立随机生成；已有实例的账号密码不因升级或重启被重置。此规则用于更新后的源码构建产物，历史安装包不自动改变。
- 后端启动使用随包 Java，不依赖客户机器安装 Java 或配置 JAVA_HOME。
- MySQL 与客户端子进程可从随包 Java 的 `bin` 目录加载其配套 VC 运行库；客户不需要配置工具 PATH。
- Windows 中文/空格路径已做实际启动验证。检测到中文路径时，为 MySQL 自动创建位于 `ProgramData/NipTrafficRuntimeLinks/<实例标识>` 的 ASCII junction；目录 ACL 仅授予当前用户、SYSTEM 和管理员，真实数据仍留在 userData。服务完全关闭后移除映射，不删除真实数据。
- SQL 初始化资产包含表结构、菜单角色、理论学习和题库、个人岗前资料、评分规则、固定报底及文章管理资料，不带开发库用户、会话和训练记录。数据来源、数量和现有附件/关联缺陷见 `docs/guides/initial-business-data.md`。
- 配置、数据库、上传文件及日志位于 Electron `userData/system`；安装目录仅用于程序和只读基础资源。首次迁移网络配置后，后续启动不会覆盖用户设置。
- 当前源码数据库默认监听 `127.0.0.1:6912`，并支持旧默认 13306 的实例配置迁移；后端默认 18001，文件服务默认 8000。已安装 fix1 可用 `docs/guides/database-port-6912.md` 中的小工具修改保存的端口，无需重装。未知进程占用端口时不接管其他程序的数据库。
- 应用退出时先结束后端，再通过 SQL `SHUTDOWN` 关闭数据库，并等待 MySQL 进程真正释放文件。不能只检测监听端口已关闭。
- 网络设置已指向远端后端时，保留远端连接方式，不创建另一个本地业务数据库。

保留既有离线授权流程；初始化数据库不会生成或绕过软件授权。

## 准备构建资产

先在 `backend/` 下以 JDK 21 构建 Quarkus fast-jar，并按工程约定执行相关测试及全量验证。然后在 `bw-frontend/` 下执行：

```text
node scripts/prepare-integrated-runtime.cjs <MySQL服务端解压目录> <Java21目录> windows-x64
```

脚本只复制 MySQL 的程序/依赖/基础资源，以及 Java 的运行文件和许可；不会复制 MySQL 安装目录中的 `data`、`my.ini` 或口令。输出在 `bw-frontend/bin/integrated/windows-x64/`，该大型构建资产不入 Git。

输出包含：

```text
bin/integrated/windows-x64/
  database/               # mysqld、依赖和 share
  runtime/                # Java 21 运行环境及许可
  backend/                # Quarkus fast-jar 完整目录
  bootstrap/seed.sql      # 干净 schema 和白名单基础配置
  bootstrap/migrations/   # 按文件名排序的数据库迁移
  manifest.json           # 目标平台和文件摘要
```

更新组件前先归档旧输出再重新生成。脚本不会直接覆盖正在使用的组件目录。Linux使用独立原生产物；ARM64后续构建与现场反馈见麒麟指南，Linux x64仍需单独验证；缺失组件时打包前检查会中止，不会误用Windows资产。

客户端工具仍由 `npm run prepare:mysql` 准备。正常打包命令为：

```text
npm run build-e-w -- --x64
```

若构建机已安装完全相同版本的 Electron，可通过 electron-builder 的 `--config.electronDist=node_modules/electron/dist` 使用本机分发文件，避免重复下载。这是构建机选项，不是客户安装要求。

## 首次启动、数据保留与升级

数据库实例配置写入 `userData/system/config/database-instance.json`，包含本实例的凭据，不能公开或随安装包分发。初始管理员密码在用户确认弹窗后从实例配置中移除，数据库内仅保存 PBKDF2 摘要。

已存在数据库文件但缺少对应实例配置时，会停止初始化并保留数据。首次初始化异常中断而留下不完整文件时也不会擅自清空目录，需结合日志处理。正常退出、再次启动和数据保留已在独立实例中验证。

迁移执行记录存入本实例的 `nip_desktop_migrations`。后续出现未执行的新迁移时先自动备份，再依次执行；已执行迁移的文件摘要发生改变时停止升级并报告。MySQL DDL 不能保证整批回滚，升级失败需依照备份恢复，不能把“有迁移记录”当成整文件原子性。

用户文件优先于安装包内的同名资源；只读资源回退保持在 `bin/file` 内，禁止读取数据库配置或安装根目录。升级不覆盖 `userData/system`。卸载时如需保留数据，不要选择删除用户配置/数据。

## 本轮验证

- JDK 21：`./mvnw.cmd -B -Dtest=DatabaseTransferServiceTest package` 通过，6 个相关单测通过并生成 fast-jar。全量 `clean verify` 的既有 Docker/开发进程占用限制仍未解除，不能以本次受影响测试代替全量结果。
- Electron/Node 相关 14 个测试通过：干净种子提取、已有数据保护、数据库退出等待、可写/只读文件根边界、多平台工具校验、Windows 中文路径映射及清理幂等性等。
- 使用独立临时目录及 13396/18096 端口，实际运行随包 MySQL 与 Java 后端：首次初始化、全部 24 条迁移、管理员登录、SQL 备份→删除测试行→SQL 导入恢复测试行均通过。
- 正常关闭后再次启动，确认测试行保留且不会重复创建初始账号；再次关闭后数据库和后端均结束。验证期间没有连接或修改正在使用的 3306/18001 实例。
- 另外使用含中文和空格的应用目录映射及数据目录，重复验证首次初始化、管理员登录、SQL 备份恢复、第二次启动与正常停机，均通过。
- 初始管理员关联现有角色 `1`（is_admin=0），同时保留原有按角色 `1` 查询教师的业务契约；已在实际初始化后的登录响应中确认角色 ID。
- 以上是当前 Windows 开发环境上的服务链验证，不替代干净 Windows、Win7、串口硬件、音频或国产 Linux 实机验收。

## 当前 Windows 修复安装包

- 文件：`bw-frontend/out/多功能报务综合训练系统-win-3.1.1-x64-fix1.exe`。原不带 `fix1` 的安装包存在依赖目录错误，停止分发。
- 大小：547,436,206 字节。
- SHA-256：`b39adda8bfff6f5490cf077aad90c5c79737bf9b8cf2847b22be6e78973315c5`
- Electron 30.5.1、MySQL 8.0.46、Java 21；NSIS 构建退出码 0。
- `bw-frontend/out/runtime-fix-report.json` 记录此次依赖修复的产物校验；原 `integrated-installation-report.json` 对应旧包，不能作为本修复版的摘要来源。
- 已逐项核对安装目录中的整合运行资源及工具摘要，并比对 app.asar 中启动管理、中文路径处理、配置和文件服务源码与当前工作区一致。
- 安装器构建依赖经 SHA-512 核验缓存后使用，安装包自身为包含完整运行组件的离线包；客户不需要下载安装器构建工具。
- 本段记录的是当时Windows x64 fix1批次，不能代表Win7或Linux验收。后续Win7 compat2、麒麟ARM64产物和现场反馈分别见对应平台指南；Linux x64仍需单独验证。

## 主进程 NeDB 依赖修复

用户报错：`Cannot find module '@seald-io/nedb'`。使用旧包自带的 Electron 执行模块加载，已复现相同错误。

原因是构建目录混入了 `node_modules/node_modules` 重复依赖树，electron-builder 22.14.13 的依赖收集器把部分依赖归入该层级，导致包内出现 `app.asar/node_modules/node_modules/@seald-io/nedb`。开发环境正常可加载，不代表归档内可以按 Node 的规则解析。重复目录已备份移动到忽略提交的 `.cache/nested-dependencies-*`，未改动用户数据库或业务配置，也未通过添加运行时搜索别名绕过问题。

新增两层构建检查：

- `prepare-desktop.cjs` 在打包前拒绝重复根依赖树。
- `afterPack` 校验归档内生产依赖的解析关系；构建机与目标平台/架构相同时，用包内 Electron 实际加载所有直接运行依赖、配置模块和文件服务模块，并进行 NeDB 磁盘写入、更新和重新读取。禁止从开发机 node_modules 回退加载缺失模块。不同架构仅完成结构校验，目标机执行单独验收。

本轮 10 个受影响 Node 测试通过（含 3 个新增回归测试）。新包的 111 份依赖清单通过检查，包内 Electron 探测输出 `PACKAGED_RUNTIME_OK`；NSIS 构建退出码为 0。交付新包时应先退出旧程序，再安装文件名带 `fix1` 的版本，无需删除数据库或用户数据。

## 用户再次报告相同错误时的核查

已直接从 `fix1` NSIS 安装器提取 `resources/app.asar`，确认其 SHA-256 与 `out/win-unpacked` 中的文件一致：`fa24d77e0fc78443ecb360acf8d9af263f9ed6a461d530fd7bb6379288aa1774`。提取出的归档通过 111 份依赖清单检查及 Electron Node 模式加载。另在隔离的真实 Electron 主进程环境中加载归档内 `electron/index.js` 和 NeDB，验证通过。

这些结果不能证明用户机器上的实际安装文件相同。为避免盲目重复重装，提供只读检查工具：`bw-frontend/out/安装依赖检查.zip`，源文件位于 `bw-frontend/support/installed-runtime/`。

解压后双击 `check-installed.cmd`；默认查找当前用户的 `Programs/nip-traffic-system`，也可把安装目录中的软件 EXE 拖到 CMD 上指定目录。工具使用已安装的 Electron，不要求另装 Node 或 PowerShell 模块，生成 `installed-runtime-report.json`，记录实际 EXE/归档路径、归档摘要、NeDB 正确/错误层级是否存在以及真实依赖加载结果。

工具已同时验证两种情况：原始坏包正确报告错误层级并复现 `Cannot find module '@seald-io/nedb'`；修复版报告正确层级、摘要一致且加载成功。当前需要用户机器的报告才能确定重复错误的实际原因，尚未据此生成新的安装包或认定用户运行了旧版本。
