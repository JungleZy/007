# Rust 迁移交接（每批开新会话先读本文件）

最后更新：2026-10-11，基线提交见 `git log -1`。本文件只记"下一步要做什么"；历史证据在 `docs/reviews/rust-migration/`，不要在这里追加长段落。

## 现状一句话
B4、WS 逐帧设施、B5 组训三域（手键 2f997d9f/2956b15d、电子键 12af8262/f6475709、报文 b193b052/235309bc）已完成，0 FAIL。**下一步：查时钟旅程不稳定 + B6 仿真。** Phase10 仍阻断。

## 成本规则（用户明确要求，2026-10-11 修订）
- 主会话（Opus）只做调度与判断：拆任务、写子代理指令、拍板判断题、审回报、决定提交；不亲自读大段代码或日志。
- 执行交给子代理：改代码/写旅程/跑对账用 Sonnet；检索、列清单、跑脚本汇总用 Haiku。子代理回报限 300 字，细节留在磁盘。
- 写代码与跑对账同一时间只有一个子代理（共享源码和对账库）；只读调研可并行。
- 子代理遇到判断题（是否复制 Java 缺陷、错误码口径、锁方案）停下上报，不自行决定。
- 迭代只跑本域旅程，提交前跑一次全量扫描；证据只记提交号和计数。

## 环境与命令（WSL，本机已就绪）
```bash
S=backend-rust/scripts/local-dual-stack.sh
$S setup      # 仅新机器：MySQL 容器 + 快照 + 迁移 + 隔离库
$S build      # 改了 Java 或 Rust 后
$S schema && $S up
$S journeys [文件]   # 默认全部旅程；输出 FAIL/OPEN 摘要，exit 1 表示有未解决差异
$S full              # 提交前一次
$S down
# 隔离库回归（锁/事务）：
RUST_PHASE2_DATABASE_URL=mysql://root:root@127.0.0.1:3306/rust_scratch_lockdemo \
  cargo test --locked -p rust-backend <模块>:: -- --ignored --test-threads=1
python3 -m unittest discover -s backend-rust/scripts -p 'test_*.py'
```
JDK：`~/.local/opt/jdk21`。git 作者已在仓库本地配置为 Jungle。旅程格式（含 `headers` 会话步骤、`ws` 步骤）见 `dual-stack-sweep.py` 顶部和已有 `b4-*`、`ws-*` 旅程。

## B5 组训三域：下一步
范围：`generalKeyPat`（电子键，19 个）、`generalTickerPatTrain` 及 openApi/socket（手键，14 个）、`generalTelexPat`（报文/电传，14 个）+ 各自 WS。
调研笔记与旅程草稿：`docs/reviews/rust-migration/b5-research/`（键、手键草稿已实跑修正；报文草稿未实跑）。修复顺序：手键（已完成：2f997d9f、2956b15d）→ 电子键 → 报文。
待补验证：手键结算复用了岗位报务比对引擎，`post_telegram_write.rs` 的 `group_slice` 粘组耗时改为 Java 的 `300.0` 格式，岗位报务旅程仍全过但没有一条覆盖粘组耗时，需补一条岗位报务粘组旅程。

1. （Sonnet 子代理）把两份草稿逐条跑：`$S journeys docs/reviews/rust-migration/b5-research/generalKeyPat-journeys.draft.json`，草稿本身的错误先修草稿，记录真实差异清单。
2. （Sonnet 子代理，窄范围）补报文域调研，同样产出旅程草稿。
3. 已拍板（2026-10-11，用户整体采纳）：
   - 状态类错误按 Java 返回 202（不用 208）；非成员/非学员按 Java（202，getPatValue 读自己 200 空数组）；手键 detail 学员传他人 uid 按 Java 补 207。
   - 缺页扣分复制 Java 的重复累加（登记为已知 Java 缺陷，日后两栈一起修）。
   - 交卷/收尾不补生成缺页（同 Java）。
   - 锁：先锁训练行主键、普通读出 id、按主键写；不加索引、不改表结构。
   - 刻意差异（登记 registry）：报文 updateTrainStatus 的 Rust 状态机；报文规则缺 alterError 时回退 correctMistakes（Java 500）；手键 getByTrainIdAndUserId 的在线 status、findTrainInfo 名单结构、Java 导出页 id 乱值。
   - 报文补充（2026-10-12）：成员 detail 的 validTime/speedLog/duration 照 Java 返回 null；非成员读 getPage 返回与 Java 相同的 200 和形态，但不建页、不写库（登记刻意差异）；页组数非 10 倍数且末行错码时照 Java 返回 500（不改）。
   差异明细：`docs/reviews/rust-migration/b5-research/b5-diffs.md`（键、手键）与 `generalTelexPat.md`（报文）。
4. （Sonnet 子代理）按结论修：前端会直接报错的优先——findAll 缺 userInfoList（两域）、patDetail 缺 pageAnalyzeVOS、手键开训不写采集时钟、结算不回写 resolver、通知发到所有 WS 会话（Java 只发经典 /websocket）。
5. 每个修复配隔离库回归 + 一次变异；旅程并入 `dual-stack-journeys.json`；提交按"修复 / 旅程 / 证据"拆分。

## B5 收尾（已完成：b2e643b5 非成员读页不写库，2fbc6ead 岗位报务粘组旅程）
- 待查：时钟相关旅程不稳定——`b5-general-ticker-scoring-expiry` 偶发 validTime Java -3 / Rust 0，`personal-ticker-tape` 偶发 2 FAIL。需查明 Java 负值来源并固定夹具。
- 已知遗留：Java/Rust 报底类型列表顺序不同（cable 域），排入 B8。

## 之后的批次（按顺序）
B6 仿真（router/recept/report/routerRoomContent，含仿真 WS）→ B7 报话/军语/mtd → B8 理论题库余项与杂项 → 单域真实切换与回滚演练。浏览器/Electron、Windows/arm64 构建、生产切换均未做。
