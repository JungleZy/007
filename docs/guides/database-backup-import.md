# 数据库 SQL 备份与导入

入口：系统管理 → 数据备份。菜单与路由由前端 `bw-frontend/frontend/src/config/router/localMenus.js` 定义，不写入 `t_menus` / `t_role_menus`，不需要菜单迁移或角色菜单分配；管理员登录或刷新页面时加载，并替换旧缓存中的同名路由。三个接口均要求 `token`、`deviceId` 请求头以及管理员角色（`t_role.is_admin=0`），普通用户返回 HTTP 200 + code 207。

## 部署

1. Windows x64 客户端工具已经放入项目文件服务目录 `bw-frontend/bin/file/tools/mysql/windows-x64/`，包含 MySQL 8.0.46 的 `mysqldump.exe`、`mysql.exe`、两个 OpenSSL DLL、原始许可说明和 SHA-256 清单。现有 `extraFiles: ["./bin"]` 会将此目录复制到安装目录；Windows 打包前自动校验清单，缺失或被修改时中止打包。工具运行需要 Windows 的 Visual C++ 2015–2022 x64 运行库（当前机器已具备）；尚未在纯净 Windows 系统上验收。
2. Electron 启动本机后端时按操作系统和 CPU 架构注入工具路径。直接在仓库根目录、`backend/` 或 `bw-frontend/` 启动 Windows/Linux 后端时，也会自动查找对应架构的随附工具。显式环境配置优先；独立服务器可安装兼容的官方 MySQL 客户端，加入 PATH 或配置以下属性（Windows 路径可包含空格，不要额外加命令行引号）：

   ```properties
   nip.database.mysql-path=C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe
   nip.database.mysqldump-path=C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe
   nip.database.ssl-mode=REQUIRED
   ```

   对应环境变量为 `NIP_DATABASE_MYSQL_PATH`、`NIP_DATABASE_MYSQLDUMP_PATH`、`NIP_DATABASE_SSL_MODE`。未显式配置且找不到随附工具时，回退 PATH 中的 `mysql`、`mysqldump`；默认 TLS 模式是 `REQUIRED`。客户端 TLS 参数独立于 JDBC URL，未自动继承 JDBC 的证书选项；当前配置支持 MySQL 内置系统信任配置，需要自定义 CA 的部署应先完成工具连接配置适配。工具包制作和架构要求见下节。

3. 工具读取运行时的 `quarkus.datasource.jdbc.url`、`username`、`password`，不接收浏览器传入的连接配置。当前支持 `jdbc:mysql://单主机:端口/库名?参数`，库名限字母、数字、下划线；不支持多主机、代理负载均衡 URL。口令仅通过子进程环境传递，不写入命令行、SQL 或日志。
4. 发布前端后刷新页面，即可加载本地“数据备份”菜单。该入口仅向管理员显示，不参与数据库菜单配置。
5. 代理层允许至少 513 MiB 请求体，并将该功能请求的超时配置为大于 16 分钟。后端 SQL 文件限制为 512 MiB，整体请求上限为 513 MiB；其他文件解析接口仍执行自身文件大小限制。

工具需要读取表、视图、触发器、存储过程及事件定义的权限，以及导入内容对应的写表/建表权限。使用限制在业务库范围内的数据库账号；本功能执行管理员提供的可信 SQL，**不是 SQL 沙箱**，默认库参数不能阻止 SQL 中显式引用其他库。禁止上传来源不明的文件。

## 国产系统与多架构工具包

工具根目录统一为 `bw-frontend/bin/file/tools/mysql/`。以下是**代码能够识别和校验的目标**，不是已经完成实机验收的系统列表。

| 系统/CPU | 目标目录 | 当前二进制交付状态 |
| --- | --- | --- |
| Windows x86_64 | `windows-x64` | 已随项目提供，已验证实际导出 |
| Linux x86_64（海光、兆芯等） | `linux-x64` | 待提供目标发行版兼容工具包 |
| Linux ARM64（飞腾、鲲鹏等） | `linux-arm64` | 待提供目标发行版兼容工具包 |
| Linux LoongArch64（新款龙芯） | `linux-loong64` | 待提供目标发行版兼容工具包 |
| Linux MIPS64 小端（部分旧款龙芯） | `linux-mips64el` | 待提供目标发行版兼容工具包 |
| Linux SW64（申威） | `linux-sw64` | 待提供目标发行版兼容工具包 |
| Linux RISC-V 64 | `linux-riscv64` | 待提供目标发行版兼容工具包 |

架构名称自动归一：`amd64/x86_64 → x64`、`aarch64 → arm64`、`loongarch64 → loong64`、`mips64le → mips64el`。没有大小端信息的 `mips64` 不会猜测为 MIPS64EL。Windows ARM64、32 位架构、macOS 尚未纳入随包工具目标。

麒麟、统信 UOS 等系统必须明确发行版版本、CPU 指令集及 glibc 版本。相同架构不代表二进制兼容；例如 Ubuntu 24.04 上的工具不能未经验证就宣称可运行于较早的麒麟或 UOS。LoongArch 新旧 ABI、MIPS ABI 和申威发行版补丁也需要目标机器验证。

准备 MySQL **8.x** 原生客户端目录（可以来自目标发行版可信软件源或在目标系统构建；本实现的参数不兼容 MariaDB，不能仅改名替代）：

```text
prepared-mysql/
  bin/mysql             # 原生 ELF 文件，不是 shell 启动脚本
  bin/mysqldump
  lib/                  # 可选：该架构的非 glibc 共享库，放入所有需要的 SONAME 文件
  LICENSE               # MySQL 及随附依赖的完整许可说明
  README                # 来源、版本、构建和验证说明
  runtime.json
```

Linux `runtime.json` 必须如实填写兼容要求；下面仅说明字段格式，值须替换为实际验证结果：

```json
{
  "clientFamily": "mysql",
  "clientVersion": "8.0.46",
  "libc": "glibc",
  "minLibc": "2.28",
  "testedOn": ["实际验证过的国产系统名称、版本、架构及 ABI"]
}
```

在 `bw-frontend/` 执行，例如：

```sh
node scripts/bundle-mysql-tools.cjs /path/to/prepared-mysql linux-arm64
node scripts/bundle-mysql-tools.cjs /path/to/prepared-mysql linux-loong64
```

脚本复制客户端、许可文件及 `lib/`，校验 ELF 架构/64 位/小端、glibc 符号最低版本与清单，再生成对应工具目录。不会复制数据库数据、服务器或连接凭据。Windows 仍接受 MySQL 安装目录作为输入，目标指定 `windows-x64`。已有目标目录需要先归档再更新，防止旧依赖残留或覆盖正在使用的工具。文件清单校验不代表依赖闭包、ABI 或业务恢复已通过实机验证。

运行时，后端对绝对路径工具自动将其同级 `lib/` 加入**子进程**的 `LD_LIBRARY_PATH`；状态检查和真实执行使用相同环境。不得在其中混入另一架构的库或系统动态加载器/glibc；glibc 由目标系统提供。Deb 安装后会恢复 `mysql` / `mysqldump` 的可执行权限。

Windows 和 Linux 打包均按 **electron-builder 的目标架构**检查工具包，缺少对应目标、二进制架构不符、文件被修改或依赖清单不符都会中止打包，不会拿构建机的 x64 工具替代 ARM64 等目标。

完整桌面交付还有独立前提：当前 Electron 30 / electron-builder 22.14.13 的常规构建流程可识别 x64、ARM64，但没有官方覆盖本表所有国产架构。LoongArch、MIPS、SW64、RISC-V 的桌面包还需要相应的 Electron/Node 发行版和打包工具链；后端 native 二进制也要匹配目标架构，或使用该架构 JDK 21 运行 JVM 后端。这里新增的工具选择与 ELF 校验不能代替这些工作。

## 使用与恢复语义

- 备份：在维护时段点击“下载 SQL 备份”。使用 `mysqldump --single-transaction --quick --hex-blob` 导出表结构、数据、触发器、存储过程和事件；不会生成 `CREATE DATABASE`/`USE`。一致性依赖 InnoDB，期间不得进行 DDL。文件包含账号与业务信息，不包含数据库之外的上传附件、配置文件或服务器资产。
- 导入：先下载当前备份，停止所有训练、编辑及后台业务写入，再选择 UTF-8 `.sql` 文件，输入页面显示的库名并确认。推荐同版本系统备份。工具执行到首个 SQL 错误即停止，不会继续忽略错误。
- MySQL DDL 隐式提交，导入不是整文件原子事务。失败、超时或网络断开时可能已写入部分数据；先检查数据库与正在执行的任务，再由管理员决定恢复备份。若账号/表已损坏而无法登录，应通过服务器上的 MySQL 客户端恢复。
- 成功仅表示 MySQL 客户端执行完成，不表示跨版本业务结构兼容。导入后重启服务器，让生产 schema validate 检查结构并清理内存训练状态，再以备份中的账号重新登录。备份中的账号、权限、菜单及会话摘要都会随 SQL 恢复；必要时由运维作会话失效处理。
- 单个后端进程内仅允许一个备份或导入任务，执行超时为 15 分钟；这个互斥不会自动停止训练接口或定时任务。维护时须暂停后台业务调度，多实例部署须停用其他实例，仅保留维护实例。
- 下载使用临时文件，响应传输结束后删除。传输前断开或进程异常导致的遗留文件，在服务运行时每小时清理超过 24 小时的 `nip-database-backup-*.sql` 文件；服务器临时目录应仅允许受信任用户访问。上传临时文件由 Quarkus 管理。
- 超过 512 MiB 的库需使用服务器数据库工具备份恢复。页面的工具状态只检查可执行文件能否启动，不替代连接、权限及客户端版本验证。

## 前后端契约

| 接口 | 请求 | 成功响应 |
| --- | --- | --- |
| `GET /api/database/status` | 请求头鉴权 | `Response<{database, backupAvailable, importAvailable, busy, maxFileBytes}>` |
| `POST /api/database/backup` | 请求头鉴权 | SQL 字节流，带附件名、长度及 `Cache-Control: no-store` |
| `POST /api/database/import` | multipart `file`、`confirmation` | `Response<boolean>` |

业务拒绝沿用 JSON 信封。备份调用设置 `responseType: blob`，下载前识别 JSON 错误，避免把鉴权失败保存成 SQL。前端调用面检索 `/api/database|backupDatabase|importDatabase|getDatabaseStatus` 只匹配新增 `bw-frontend/frontend/src/common/api/DatabaseApi.js` 和 `bw-frontend/frontend/src/views/manage/systemManage/database/Index.vue`。

## 本轮验证（2026-11-05）

- JDK 21，`backend/` 下 `./mvnw.cmd -B -Dtest=DatabaseTransferServiceTest test`：4 个单测通过，覆盖连接目标、命令安全选项、文件限制、工具缺失及锁释放。
- 最终复核 `./mvnw.cmd -B -Dtest=DatabaseTransferServiceTest,ControllerJwtGuardArchitectureTest test`：5 个测试通过，包含控制器 JWT 门禁架构检查；出现现有开发进程占用日志文件的轮转告警。
- `bw-frontend/frontend/` 下 `node --test test/databaseBackup.test.mjs`：2 个测试通过，覆盖业务/鉴权错误与 SQL 下载区分。
- 新增前端文件 ESLint 检查通过；`npm run build` 通过（存在项目现有大分块提示）。
- `./mvnw.cmd -B clean verify` 在 clean 阶段被占用的 `target/quarkus-template-dev.jar` 阻断。
- `./mvnw.cmd -B -Dtest=DatabaseAuthorizationTest test` 因 Docker 不可用、DevServices 无法启动 MySQL 而失败；新增授权测试尚未完成运行验证。
- 后续排查发现本机已安装 MySQL 8.0.46，工具位于 `C:/Users/11726/mysql/bin`，但未加入 PATH。配置完整路径后成功生成 404,992,784 字节（约 386 MiB）的 SQL，检查到建表语句及导出完成标记，验证文件已删除。为支持当前实际库体积，备份/导入上限统一提高至 512 MiB。之后按要求将工具移入项目随包目录，已移除本轮创建的 `backend/.env` 机器路径配置，改为自动定位。
- 真实导入往返及 native 运行仍未验收。上线前需在隔离测试库完成这些验证及全量 verify，导出成功不能替代数据恢复验收。
- 随包工具接入验证：`DatabaseTransferServiceTest` 共 5 个测试通过；`node --test electron/test/mysql-tools.test.cjs` 共 2 个测试通过，含安装目录迁移、显式配置优先、清单损坏拒绝及移动后两个工具实际启动。移除机器路径配置后，备份服务自动选择项目 `bin/file/tools/mysql/windows-x64/mysqldump.exe`，再次成功生成 404,992,784 字节完整 SQL，验证文件已删除。尚未生成和验收新的完整 Windows 安装包。
- 多架构改造后的最终检查：`DatabaseTransferServiceTest` 共 6 个测试通过，`node --test electron/test/mysql-tools.test.cjs electron/test/mysql-bundle-targets.test.cjs` 共 5 个测试通过；覆盖六种 Linux 架构的定位/ELF 门禁、大小端拒绝、glibc 需求与清单检查。Linux 架构测试使用最小 ELF 头夹具，不能视为国产实机运行证据。直接调用打包钩子验证 x64 构建机打 ARM64 包时会检查 `linux-arm64` 并因缺包阻断。Linux 安装脚本 `bash -n` 通过，已固定 LF 换行。
- WSL Ubuntu 24.04 x86_64 的真实 `mysql` / `mysqldump` 8.0.46 通过 ELF 架构校验及 `--version` 启动检查，最高引用 glibc 符号为 2.38。这些文件未作为国产系统通用工具包加入仓库。国产目标工具包、系统版本/ABI 兼容性及完整安装包实机验收仍待补齐。
- 菜单改为前端本地定义后，已删除当前开发库中原先添加的 1 条菜单及 1 条角色菜单关联，核实残留为 0；移除了对应新增迁移脚本与快照插入语句。`node --test test/localMenus.test.mjs test/databaseBackup.test.mjs` 共 5 个测试通过，覆盖无数据库菜单、重复加载、旧缓存替换及普通用户隐藏入口。
