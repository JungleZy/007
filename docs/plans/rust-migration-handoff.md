# Rust 迁移交接（每次接手先读本文件）

最后更新：2026-10-14 UTC，基线提交见 `git log -1`。本文件只记"现状与下一步"；历史证据在 `docs/reviews/rust-migration/` 与各提交正文，不要在这里追加长段落。

## 现状
- 接口层双栈对账：B4 用户/角色/菜单、WS 逐帧设施、B5 组训三域、B6 仿真、B7 报话/军语、B8 理论题库余项与杂项 **已完成**。全部旅程 2454 请求；最终全量扫描 3903 请求，0 FAIL/OPEN。
- 单域真实网关演练 **已完成**：`/api/postTrainGlobalRule/**` 在同一隔离库上 Java→Rust→Java→Rust，最终 108 个网关请求、三次真实阻塞写排空及七次 Java WS 回退验证通过。不是生产切换，不是下线 Java。
- Linux 浏览器及真实打包 Electron 的 Rust 主链（登录、上传、英语键盘训练、WS、退出）**已实跑通过**；Windows GNU / arm64 release 已交叉构建并分别通过 Wine / QEMU smoke。原生平台 CI、现场硬件及生产备份/切换仍未验收，Phase10（下线 Java）继续阻断。
- 所有提交均未 push。

## 工作方式（用户要求，全局 ~/.claude/CLAUDE.md 第 5 条）
- 主会话（Opus）只调度与拍板；执行交给子代理：改代码/跑对账用 Sonnet，检索用 Haiku。子代理回报 ≤300 字。
- 共享双栈与对账库：同一时间只派一个写代码/跑对账的子代理；只读调研可并行。
- 子代理遇到判断题停下上报，由用户拍板；拍板结论写进本文件"已拍板口径"。
- 每批流程：只读调研（产出 `bN-research/*.md` 与旅程草稿）→ 用户拍板 → 一个 Sonnet 子代理：实跑草稿并修草稿 → 修 Rust → 旅程并入 `dual-stack-journeys.json` → 锁改动配隔离库回归 + 每处一次变异 → 门禁 → 两个提交（`fix(...)` 代码+回归；`test(rust-contract)` 旅程+registry）。
- 门禁：`cargo fmt --check`、`cargo test --locked`、相关 ignored 隔离库测试、python unittest、`$S journeys` 全部 0 FAIL/OPEN、一次 `$S full` 0 FAIL/OPEN、`$S down`。
- 子代理撞额度限额或会话结束会中断：工作区保留未提交改动，用 SendMessage 让它从断点继续（先 `docker start mysql-project006`）。子代理指令里写明"门禁一过就提交""不要设空闲计时器"。

## 环境与命令（WSL，本机已就绪）
```bash
S=backend-rust/scripts/local-dual-stack.sh
$S setup      # 仅新机器：MySQL 容器 + 快照 + 迁移 + 隔离库
$S build      # 改了 Java 或 Rust 后
docker start mysql-project006   # 容器停了时
$S schema && $S up
$S journeys [文件]   # 默认全部旅程；输出 FAIL/OPEN 摘要，exit 1 表示有未解决差异
$S full              # 提交前一次
$S down
RUST_PHASE2_DATABASE_URL=mysql://root:root@127.0.0.1:3306/rust_scratch_lockdemo \
  cargo test --locked -p rust-backend <模块>:: -- --ignored --test-threads=1
python3 -m unittest discover -s backend-rust/scripts -p 'test_*.py'
```
JDK：`~/.local/opt/jdk21`。git 作者已在仓库本地配置为 Jungle。旅程格式见 `dual-stack-sweep.py` 顶部；支持 `headers` 会话步骤、`ws` 步骤、`presence`、`parse_data`。harness 的 POST 一律带 JSON 体，无体请求需 curl 另验。
`local-dual-stack.sh up` 已修正 Java PID：后台子 shell 用 `exec` 替换为 Java，`java.pid` 与监听 PID 一致；`down` 实测关闭两端口。此前旧 shell PID 会留下 Java 孤儿进程，最终门禁已在修复后的全新进程上重跑通过。

单域演练使用本机原生 Nginx（同 Java/Rust 网络命名空间），不再使用旧 Rust-only 起停脚本。现有缓存二进制可直接复跑：
```bash
MYSQL_PWD=root backend-rust/scripts/phase9-cutover-rehearsal.sh \
  --nginx-binary "$HOME/.cache/rust-cutover/native-nginx/root/usr/sbin/nginx" \
  --out "$HOME/.cache/rust-cutover/global-rule-gateway-native.json"
```
默认回环端口 18140/18141/18142；每次只克隆 schema，创建唯一 `rust_cutover_global_rule_*` 合成库，完成或失败均清理本次进程和库。脚本不 build、不下载、不改生产；新机器需先准备已验证的 JDK21 fast-jar、Rust binary 和原生 Nginx。

## 已拍板口径（后续批次默认沿用）
- 业务码：状态类错误按 Java 202（不用 208）；非成员/非学员按 Java；Java 的 500 一般不照搬，Rust 返回定义好的 202 并登记——例外：报文页组数非 10 倍数且末行错码时照 Java 500（用户 3b）。
- 计分/取整/时长：一律按 Java（ScoreMath 取整、向上取整时长等）；Java 计分缺陷照抄并登记为已知 Java 缺陷（如缺页重复扣分）。
- 锁：先锁父行/训练行主键、普通读出子行 id、按主键写；读接口不加 FOR UPDATE；不加索引、不改表结构。
- 刻意差异（已登记）：Rust 更严的状态机/权限、Java 双重编码或乱值且前端不读的字段、时钟回跳时 Rust 截 0、listPage 无体 Java 415/Rust 200、非成员读页不写库、规则缺 alterError 回退 correctMistakes。
- 数据：不复制会写脏数据的 Java 行为（非成员读页建页）；Java 有意保留数据的行为照做（如开始前断线删成员照 Java）。
- B8 删除：用户确认课程/题库节点只删自身，保留学习记录、成绩及题目；不复制 Rust 额外级联。
- B8 列表：用户确认完整对齐 Java，开放课程固定 `status=1`，只显示有有效分类、有课件的课程；空分类数组按空集。
- B8 权限/能力：用户确认保留答题属主 207 与现有解析能力；实跑 `view` 是 Rust 有效预览 / Java 空 DTO，`uploadFileToNip` 文本能力两栈一致。
- B8 覆盖：用户确认无前端调用端点采用最小扫描与已有夹具，特殊协议做一次性 curl，不扩展 harness。

## B8 完成记录
- 证据：`docs/reviews/rust-migration/b8-research/misc.md`；已修订草稿同步并入 `backend-rust/scripts/dual-stack-journeys.json`，精确差异登记在同目录 registry。
- 52 个缺口中实际有前端调用的是 **40 个**：`uploadFileToNip` 的动态上传 action 被原静态扫描漏记。47 个由旅程覆盖，另 5 个特殊协议已 curl 实测；中文路径常规编码两栈 404，字面 UTF-8 Java 400 / Rust 200，保留为已知边界，不宣称普通客户端可用。
- 已修：完整成绩统计、创建人、课程筛选/时间、LIKE、全部错题/分档、返回形态、202、保留学习数据、主键锁及 WS 404/REST 405。题目读取按 Java `createTime` 而非 `sort`，夹具用反序固定时间验证；平均分实测保留 DECIMAL（`100/80/70` → `83.3333`）。
- 验证：JDK21 `clean verify` 513；Rust 常规 79、B8 隔离库 11、相关 WS 隔离库 2；8 处锁变异全部捕获并恢复；Python 68。B8 专项 202 / 全量 3903 请求，均 0 FAIL/OPEN；已 `$S down`。Python 有既存未关闭 socket 的 ResourceWarning，无测试失败。

## 单域网关演练完成记录
- 证据：`docs/reviews/rust-migration/phase9-cutover-rehearsal.json`；脚本及 13 项安全/状态/证据回归在 `backend-rust/scripts/`。本轮 JDK21 `clean verify` 513、Python 全套 81 通过；`/bin/false` 启动失败清理实测通过。
- 选择岗位全局评分规则单表域。`deviceType` 被排除，因为删除会跨写设备及说明表。四阶段均验证主键/业务字段/持久创建时间不丢失、无关哨兵不变、203/204/206/207 无写入；回滚 Java 可读写 Rust 已提交的数据，再切 Rust 可读 Java 回退写入。
- 必须先用共享停写标记拦住所有 worker，再 reload、排空旧请求与旧 worker，最后开放新后端。仅观察到一次新 generation 响应不够：已实测旧 worker 在 reload 交接时短暂仍能接受连接。三次切换均以真实 MySQL 主键锁阻塞旧写入，释放后只提交一次；逐请求核对 Nginx upstream，禁止自动重试。
- Nginx 原生二进制解决容器与后端回环地址的命名空间隔离问题，不把后端改绑 `0.0.0.0`。成功/失败后 18140/18141/18142 均无监听，临时库剩余 0。
- 此处是共享数据库上的**路由回滚**，不是业务数据回滚或生产备份恢复。旧 worker 整体排空可能被未迁移域长连接阻塞；超时维持停写并失败，不能宣称活跃 WS 无损生产切换。

## 客户端与平台验收完成记录
- 证据：`docs/reviews/rust-migration/phase9-frontend-smoke.json`、`docs/reviews/rust-migration/phase9-build-gate.json`。两名合成用户经真实 Rust 注册/登录，菜单来自隔离库；Electron 实际启动 `bin/server/server`，不是外部预启动后端。
- 真 multipart 文本上传在浏览器及 Electron 返回完整内容并显示在编辑器；英文键盘训练结果已与持久数据核对，finish 只提交 id/content。实测 UI 心跳、页面重连、退出登录关闭 WS，以及两次 Electron 菜单退出后 Rust exit0 / 端口释放。
- 发现并修复理论编辑器空分类列表访问 `[0].id` 的异常；重建后的 Electron 与独立原生 Chrome 均对空分类完成上传且无 JS 错误。开发激活走历史已批准的表单测试码，机器级 `/var/lib` 和用户 profile 均隔离；这不等于普通设备绑定授权验收。
- Rust1.99.0 已构建 Windows `x86_64-pc-windows-gnu` 与 `aarch64-unknown-linux-gnu` 产物。`backend-rust/scripts/platform-smoke.py` 对实际二进制验证 health/ready、203/204/206、请求头边界、退出及端口回收；Linux 原生、Windows Wine9.0（真实 CTRL_C_EVENT）、arm64 QEMU 均通过，模拟环境不冒充原生 OS。
- CI 已新增 `windows-2022` GNU 与 `ubuntu-24.04-arm` 原生构建/运行门禁，未 push、未运行远端 Actions。Windows 宿主 PowerShell/cmd 及显式 WSL_INTEROP 启动均报 `UtilAcceptVsock accept4 failed110`，故原生 Windows 结果仍缺。
- 本轮门禁：JDK21 clean verify513、Rust79、前端39、桌面产物5、Python81 全通过；前端生产构建及 Linux unpacked 打包通过。合成库已删除，浏览器/文件服务/Rust/Electron 测试进程已退出。

## 下一步
1. 遗留观察：`personal-ticker-tape` 偶发 FAIL 未复现。
2. 在原生 Windows、arm64 runner 实际执行已配置的 CI / smoke；不要把 Wine/QEMU 结果作为原生通过证据。
3. 原生 Windows/Electron 整包、签名/安装/升级、全训练域 UI、真实串口/按键/听感、普通绑定授权及客户完整资源/下载链仍待验收。
4. 生产备份恢复、切换窗口及观察期完成后再评估 Phase10（`docs/reviews/rust-migration/phase10-preconditions.json`），仍保留 Java 回滚入口。
