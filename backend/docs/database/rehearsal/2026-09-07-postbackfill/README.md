# 2026-09-07（回灌后）双快照迁移演练产物

本目录是 `scripts/rehearse-migrations.sh` 在一次性 Docker 容器上对两个快照执行
迁移 01→02 后产出的**纯 schema 证据**。不含任何业务数据行、容器凭据或令牌。

与 2026-08-28 那次的关键不同：`docs/database/project006.sql`（current 快照）已在本波
被回灌为**迁移后**状态，所以 current 分支实际是在「已迁移」的库上再跑一遍迁移
（详见下文「与 2026-08-28 那次的差别」）。

## 如何复现

```bash
# 1) 实体权威 schema（entity-schema.tsv）
#    脚本按「证据目录 → REHEARSAL_ENTITY_SCHEMA → target/migration-rehearsal/」顺序取用，
#    最后一项会被自动复制进证据目录（scripts/rehearse-migrations.sh:50-59）。
#    本目录已带 entity-schema.tsv；要重新导出时才需要：
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test

# 2) 跑双快照演练（不接受任何位置参数；拒绝 DB_HOST/JDBC_URL/QUARKUS_DATASOURCE_JDBC_URL）
#    REHEARSAL_OUT_NAME 决定证据落盘目录名，缺省为当天日期；本次显式指定以区别于
#    回灌前那一轮，避免覆盖历史证据（scripts/rehearse-migrations.sh:20-21,44-48）。
REHEARSAL_OUT_NAME=2026-09-07-postbackfill bash scripts/rehearse-migrations.sh
```

脚本对 `project006.sql`（current）与 `project006-base.sql`（base）各起一个全新、
唯一命名的 `mysql:8.0` 容器+卷，导入快照 → 迁移 01（schema-sync）→ 迁移 02
（engine-innodb），断言最终 schema 与计时，并对**实体表**生成 validate 等价差分。
所有出口路径 trap 清理容器与卷（`scripts/rehearse-migrations.sh:78-86`）。

本次实测：`exit=0`，`REHEARSAL PASSED: all assertions green for both snapshots`，
墙钟 550.61s。

## 产物清单

本目录共 8 个证据文件（除本 README 外无其他 `.md`）。行数/表数均由本目录文件实际统计
得出，非沿用上一轮数值。

| 文件 | 含义 | 实测规模 |
|---|---|---|
| `entity-schema.tsv` | 实体权威 schema（Hibernate `drop-and-create` 导出的 `information_schema.columns`）。validate 期望的来源。 | 表头 + 875 行，**103 张实体表** |
| `current-schema.tsv` | current 快照迁移后**实体表**的列转储（6 列：表/列/序号/类型/可空/默认，按 表,列 排序） | 表头 + 902 行，103 张表 |
| `base-schema.tsv` | 同上，base 快照 | 表头 + 902 行，103 张表 |
| `current-engine.tsv` | 迁移后**全部** `BASE TABLE` 的存储引擎（独立证据：实体 DDL 不编码生产引擎期望） | 表头 + **105 行，105 张表全为 InnoDB** |
| `base-engine.tsv` | 同上，base 快照 | 表头 + 105 行，105 张表全为 InnoDB |
| `diff-current.txt` | validate 等价差分，**必须为空** | **0 字节** |
| `diff-base.txt` | 同上 | **0 字节** |
| `timings.tsv` | 每快照迁移 01/02 耗时（毫秒），列：`label  ms01  ms02` | 2 行：`current 165 2542`、`base 662 2319` |

105（全表）− 103（实体表）= 2 张非实体表：`seq`、`t_union_room`；二者不在 validate
契约内，故被排除出 `*-schema.tsv` 与差分，但仍计入引擎证据与表计数断言。

两份 `*-engine.tsv` 内容逐字节相同（`sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1`）——
两条迁移路径收敛到同一引擎分布。`current-schema.tsv` 与 `base-schema.tsv` 仅在
`t_post_telegraph_key_pat_train_more` 的 4 处 `ordinal_position` 与
`t_post_telegraph_key_pat_train_page_value.value` 的 `text` vs `varchar` 上不同，
两类差异均在 validate 契约之外（见下节），故两侧差分同为空。

## 差分口径（= Hibernate validate 结构契约）

`diff-*.txt` = `comm -23`（实体列投影，迁移后快照列投影），即“**实体所需列未被快照满足**”的集合。
两侧投影均为 `(table, lower(column), norm_type)`，其中字符串族
（`char/varchar/*text/enum/set`）归一为 `string`。这精确对齐 Hibernate `validate`：

- 只要求实体所需的每个 (表,列) 存在且类型族兼容；
- 容忍库中多余列/多余表（故用单向 `comm -23`，忽略快照侧多余列）；
- 容忍字符串族等价（`varchar` ↔ `longtext`/`text` 无害）；
- 列名大小写不敏感。

`ordinal_position` / `column_default` / `is_nullable` / 长度 / 字符集 在 validate 契约之外，
由脚本的**独立断言**覆盖（表计数、MyISAM=0、命名表存在、两处 `is_start_sign` 默认=1、
`general_key_pat_page.id`=varchar，base 迁前 id=int→迁后 varchar）。`int/bigint/decimal/
double/datetime/bit` 保持原样，故任何真实类型漂移仍会现形。

## 与 2026-08-28 那次的差别

**(a) current 快照已是迁移后状态。** 本波把迁移 01+02 的结果就地回灌进
`docs/database/project006.sql`（`:36451-36463` 的尾注记录了回灌口径）：22 处
`ENGINE = MyISAM` 改为 `InnoDB`、补 5 张缺表 DDL、两处 `is_start_sign int NULL DEFAULT 1`
写进 `simulation_router_room`（`:295`）与 `t_post_ticker_tape_train`（`:28581`）的建表块，
34626 条 INSERT 数据行一行未动。因此 current 分支是**在已迁移的库上再跑一遍迁移**——
这正是回归价值所在：它把「迁移可重复执行」变成每次演练都被验证的性质。

**(b) 迁移 01 因此必须幂等，已改造。** 回灌后首跑即报
`ERROR 1060 (42S21) Duplicate column name 'is_start_sign'`：MySQL 8.0 **不支持**
`ADD COLUMN IF NOT EXISTS`（那是 MariaDB 语法）。
`docs/database/migrations/2026-08-26-01-schema-sync.sql:91-115` 改为
`information_schema.columns` 判存 + `PREPARE`/`EXECUTE`/`DEALLOCATE`，列已存在时执行
`DO 0` 空操作；脚本头部 `:17-18` 的幂等声明同步改为「可安全重复执行」。缺表侧本就是
`CREATE TABLE IF NOT EXISTS`（`:23`、`:84` 等），第 3 节两条 `MODIFY COLUMN`
（`:127-128`）为同型重建、本就幂等，未改。

**(c) 本次耗时。** current：迁移 01 = 165ms、迁移 02 = 2542ms；base：迁移 01 = 662ms、
迁移 02 = 2319ms（`timings.tsv`）。current 的迁移 01 明显更快，与 (a) 一致——该分支上
5 张表已存在、2 处 `ADD COLUMN` 走 `DO 0`，只剩两条 `MODIFY COLUMN` 真正落盘。
迁移 02 两侧都在 2.3–2.5 秒，是停服窗口评估的实测下界。

演练权威结论以 `docs/reviews/2026-09-07-migration-rehearsal.md` 为准。
