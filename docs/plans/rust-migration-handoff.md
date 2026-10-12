# Rust 迁移交接（每批开新会话先读本文件）

最后更新：2026-10-11，基线提交见 `git log -1`。本文件只记"下一步要做什么"；历史证据在 `docs/reviews/rust-migration/`，不要在这里追加长段落。

## 现状一句话
用户/角色/菜单（B4）和 WS 逐帧设施已完成，0 FAIL；**下一批是 B5 组训三域**，调研完成两域，未开始修。Phase10 仍阻断。

## 成本规则（用户明确要求）
- 默认 Sonnet 5.5 / medium 执行；只有"判断题"（下方待决项）用 Opus / high，且单独短会话。
- 一批一个会话；做完更新本文件后结束会话。
- 迭代只跑本域旅程，提交前跑一次全量扫描。
- 证据只记提交号和计数；计划第 13 节每批一行。
- 子代理：仅在必要时、只读、限 300 字回复、用便宜模型。

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
调研笔记和**未验证**的旅程草稿：`docs/reviews/rust-migration/b5-research/`（报文域调研因限额中断，需补做）。

1. （Sonnet）把两份草稿逐条跑：`$S journeys docs/reviews/rust-migration/b5-research/generalKeyPat-journeys.draft.json`，草稿本身的错误先修草稿，记录真实差异清单。
2. （Sonnet，窄范围）补报文域调研，同样产出旅程草稿。
3. （Opus 短会话）拍板以下判断题，结论写回本文件：
   - Java 抛 IllegalStateException → 202 的地方，Rust 用了 208/207（reset、finish、startTrain、upload、非成员读页）：统一回 202，还是登记为刻意差异？
   - Java 缺页扣分重复累加（GeneralKeyPatService.java:897-925）：复制还是登记？
   - 正确率 Java 用 ScoreMath 取整（"92.00"），Rust 保留两位小数（"91.67"）：按 Java 改（倾向）。
   - Rust 交卷时补生成缺页、Java 不补：按 Java 改（倾向，且有大 totalNumber 的拒绝服务风险）。
   - 成员表/拍发值表只有主键，Rust 按 train_id/user_id 加锁：加索引迁移，还是改"先锁训练行主键、普通读出 id、按主键写"（与 B2–B4 一致，倾向后者）。
4. （Sonnet）按结论修：前端会直接报错的优先——findAll 缺 userInfoList（两域）、patDetail 缺 pageAnalyzeVOS、手键开训不写采集时钟、结算不回写 resolver、通知发到所有 WS 会话（Java 只发经典 /websocket）。
5. 每个修复配隔离库回归 + 一次变异；旅程并入 `dual-stack-journeys.json`；提交按"修复 / 旅程 / 证据"拆分。

## 之后的批次（按顺序）
B6 仿真（router/recept/report/routerRoomContent，含仿真 WS）→ B7 报话/军语/mtd → B8 理论题库余项与杂项 → 单域真实切换与回滚演练。浏览器/Electron、Windows/arm64 构建、生产切换均未做。
