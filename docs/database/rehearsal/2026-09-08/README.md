# 2026-09-08 双快照迁移演练产物（迁移 03 加入后）

本目录是 `scripts/rehearse-migrations.sh` 在一次性 Docker 容器上对两个快照执行
迁移 01→02→**03** 后产出的**纯 schema 证据**。不含任何业务数据行、容器凭据或令牌。

与 `2026-09-07-postbackfill` 那次的关键不同：本轮多跑了第三条迁移
`2026-09-08-01-unique-lazy-create.sql`（读路径懒建的两条唯一索引），因此
`timings.tsv` 从 3 列变 4 列，断言集多出两条 unique index 断言
（详见下文「与 2026-09-07-postbackfill 那次的差别」）。

## 如何复现

```bash
# 1) 实体权威 schema（entity-schema.tsv）
#    脚本按「证据目录 → REHEARSAL_ENTITY_SCHEMA → target/migration-rehearsal/」顺序取用，
#    最后一项会被自动复制进证据目录（scripts/rehearse-migrations.sh:50-59）。
#    本目录已带 entity-schema.tsv；要重新导出时才需要：
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test

# 2) 跑双快照演练（不接受任何位置参数；拒绝 DB_HOST/JDBC_URL/QUARKUS_DATASOURCE_JDBC_URL）
#    REHEARSAL_OUT_NAME 决定证据落盘目录名，缺省为当天日期；本次显式指定以固定目录名、
#    避免覆盖 2026-09-07-postbackfill 的历史证据（scripts/rehearse-migrations.sh:20-21,44-48）。
REHEARSAL_OUT_NAME=2026-09-08 bash scripts/rehearse-migrations.sh
```

脚本对 `project006.sql`（current）与 `project006-base.sql`（base）各起一个全新、
唯一命名的 `mysql:8.0` 容器+卷，导入快照 → 迁移 01（schema-sync）→ 迁移 02
（engine-innodb）→ 迁移 03（unique-lazy-create），断言最终 schema 与计时，并对**实体表**
生成 validate 等价差分。所有出口路径 trap 清理容器与卷（`scripts/rehearse-migrations.sh:76-87`）。

本次实测：`exit=0`，`REHEARSAL PASSED: all assertions green for both snapshots`。
墙钟未由脚本打印；按本目录产物 mtime 推算约 **551s**（`entity-schema.tsv` 13:19:17 →
`diff-base.txt` 13:28:28）。

## 产物清单

本目录共 **8 个证据文件**（除本 README 外无其他 `.md`）。行数/表数均由本目录文件实际统计
得出，非沿用上一轮数值。

| 文件 | 含义 | 实测规模 |
|---|---|---|
| `entity-schema.tsv` | 实体权威 schema（Hibernate `drop-and-create` 导出的 `information_schema.columns`）。validate 期望的来源。 | 876 行 = 表头 + **875 列行**，**103 张实体表** |
| `current-schema.tsv` | current 快照迁移后**实体表**的列转储（6 列：表/列/序号/类型/可空/默认，按 表,列 排序） | 903 行 = 表头 + 902 行，**103 张表** |
| `base-schema.tsv` | 同上，base 快照 | 903 行 = 表头 + 902 行，103 张表 |
| `current-engine.tsv` | 迁移后**全部** `BASE TABLE` 的存储引擎（独立证据：实体 DDL 不编码生产引擎期望） | 106 行 = 表头 + **105 行，105 张表全为 InnoDB** |
| `base-engine.tsv` | 同上，base 快照 | 106 行 = 表头 + 105 行，105 张表全为 InnoDB |
| `diff-current.txt` | validate 等价差分，**必须为空** | **0 字节**（`sha256:e3b0c442…b7852b855`，空输入的规范 SHA-256） |
| `diff-base.txt` | 同上 | **0 字节**（同上校验和） |
| `timings.tsv` | 每快照迁移 01/02/**03** 耗时（毫秒），**4 列**：`label  ms01  ms02  ms03` | 2 行：`current 182 2450 110`、`base 644 2431 165` |

105（全表）− 103（实体表）= **2 张非实体表**：`seq`、`t_union_room`（`comm` 实测）；
二者不在 validate 契约内，故被排除出 `*-schema.tsv` 与差分，但仍计入引擎证据与表计数断言。

两份 `*-engine.tsv` 内容逐字节相同（`sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1`）——
三条迁移路径收敛到同一引擎分布。`current-schema.tsv` 与 `base-schema.tsv` 仅有 5 处差异
（`diff` 输出 3 个 hunk / 16 行）：`t_post_telegraph_key_pat_train_more` 的 4 处
`ordinal_position`（`more_group`/`more_line`/`train_id`/`user_id` 在两侧为 6/5/2/3 与 2/3/5/6）
与 `t_post_telegraph_key_pat_train_page_value.value` 的 `text` vs `varchar`，
两类差异均在 validate 契约之外（见下节），故两侧差分同为空。

**注意：本目录任何 TSV 都不包含索引信息。** `*-schema.tsv` 只投影列，`*-engine.tsv` 只投影
引擎，所以本轮两条唯一索引的证据**只存在于脚本断言输出**（`scripts/rehearse-migrations.sh:183-189`），
不在产物文件里；差分为空**不**构成唯一索引存在的证据。这也是本目录 5 份 TSV 与
`2026-09-07-postbackfill/` 同名文件逐字节相同（`cmp` 实测）的原因。

## 差分口径（= Hibernate validate 结构契约）

`diff-*.txt` = `comm -23`（实体列投影，迁移后快照列投影），即“**实体所需列未被快照满足**”的集合。
两侧投影均为 `(table, lower(column), norm_type)`，其中字符串族
（`char/varchar/*text/enum/set`）归一为 `string`。这精确对齐 Hibernate `validate`：

- 只要求实体所需的每个 (表,列) 存在且类型族兼容；
- 容忍库中多余列/多余表（故用单向 `comm -23`，忽略快照侧多余列）；
- 容忍字符串族等价（`varchar` ↔ `longtext`/`text` 无害）；
- 列名大小写不敏感。

`ordinal_position` / `column_default` / `is_nullable` / 长度 / 字符集 / **索引** 在本差分之外，
由脚本的**独立断言**覆盖（表计数 105、MyISAM=0、5 张命名表存在、两处 `is_start_sign` 默认=1、
`general_key_pat_page.id`=varchar，base 迁前 id=int→迁后 varchar，以及本轮新增的两条唯一索引）。
`int/bigint/decimal/double/datetime/bit` 保持原样，故任何真实类型漂移仍会现形。

## 与 2026-09-07-postbackfill 那次的差别

**(a) 多跑了迁移 03。** 本轮在迁移 01→02 之后追加执行
`docs/database/migrations/2026-09-08-01-unique-lazy-create.sql`（54 行），为两条读路径懒建
补上并发闸门：`t_radiotelephone_train` 的 `uk_radiotelephone_train_user_type(user_id, type)`
与 `t_theory_knowledge_test_fallible` 的 `uk_theory_test_fallible_user(user_id)`。
脚本侧的改动是 `MIG03` 常量（`scripts/rehearse-migrations.sh:62`）与第三段计时
（`:160-162`，输出行 `:163`）。

**(b) 多了两条唯一索引断言。** `scripts/rehearse-migrations.sh:183-189` 以
`表:索引名:期望列数` 三元组循环断言
`select count(*) from information_schema.statistics where … and index_name='…' and non_unique=0`
等于期望列数（复合索引 2、单列索引 1）。用列数而非「存在与否」，一次锁住三件事：索引存在、
组合列数正确、`non_unique=0`（是唯一索引而非普通索引）。两侧各 2 条，本次全 PASS。

**(c) `timings.tsv` 从 3 列变 4 列。** 上一轮是 `label  ms01  ms02`，本轮是
`label  ms01  ms02  ms03`（`scripts/rehearse-migrations.sh:164`）。读旧目录的 `timings.tsv`
时不要套用 4 列口径。

**(d) 本次耗时。** current：01 = 182ms、02 = 2450ms、**03 = 110ms**；
base：01 = 644ms、02 = 2431ms、**03 = 165ms**。迁移 03 只有毫秒级，因为两张目标表在快照与
活库里都是 **0 行**（迁移脚本头注 `:24-26` 记录了活库实测 0 行 / 0 重复），建索引几乎只剩
元数据操作；**这不是生产窗口的可用估计**，有存量行会随行数近似线性变慢，有重复行则直接报
1062 并必须先去重。迁移 02 两侧仍在 2.4 秒，停服窗口预算仍由它主导。

**(e) current 快照已含这两条索引（幂等回归）。** 本波把迁移 03 的结果回灌进
`docs/database/project006.sql`（`:28678`、`:29538` 各一行 `UNIQUE INDEX`，尾注 `:36466-36470`
记录口径，34626 条 INSERT 数据行未动）。所以 current 分支导入的库本来就带索引，再跑一遍
迁移 03 必须走 `DO 0` 空操作且断言仍绿——**幂等因此成为每次演练都被验证的性质**。

**(f) 未捕获新缺陷。** 上一轮捕获并修复的「MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`」
不再复现；本轮无新增缺陷。

演练权威结论以 `docs/reviews/2026-09-08-migration-rehearsal.md` 为准
（含停服窗口评估、完整断言矩阵、回滚预案与未验证边界）。
