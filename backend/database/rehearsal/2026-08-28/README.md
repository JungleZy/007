# 2026-08-28 双快照迁移演练产物

本目录是 `scripts/rehearse-migrations.sh` 在一次性 Docker 容器上对两个快照执行
迁移 01→02 后产出的**纯 schema 证据**。不含任何业务数据行、容器凭据或令牌。

## 如何复现

```bash
# 1) 先导出实体权威 schema（Hibernate drop-and-create → entity-schema.tsv）
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test
cp target/migration-rehearsal/entity-schema.tsv docs/database/rehearsal/2026-08-28/

# 2) 跑双快照演练（不接受任何参数；拒绝 DB_HOST/JDBC_URL/QUARKUS_DATASOURCE_JDBC_URL）
bash scripts/rehearse-migrations.sh
```

脚本对 `project006.sql`（current）与 `project006-base.sql`（base）各起一个全新、
唯一命名的 `mysql:8.0` 容器+卷，导入快照 → 迁移 01（schema-sync）→ 迁移 02
（engine-innodb），断言最终 schema 与计时，并对**实体表**生成 validate 等价差分。
所有出口路径 trap 清理容器与卷。

## 产物清单

| 文件 | 含义 |
|---|---|
| `entity-schema.tsv` | 实体权威 schema（Hibernate `drop-and-create` 导出的 `information_schema.columns`，103 张实体表）。validate 期望的来源。 |
| `current-schema.tsv` / `base-schema.tsv` | 迁移后快照库中**实体表**的 `information_schema.columns` 转储（6 列，按 表,列 排序）。 |
| `current-engine.tsv` / `base-engine.tsv` | 迁移后**全部** 105 张表的存储引擎（独立证据：实体 DDL 不编码生产引擎期望）。 |
| `diff-current.txt` / `diff-base.txt` | validate 等价差分，**必须为空**（0 字节）。 |
| `timings.tsv` | 每快照迁移 01/02 的耗时（毫秒），列：`label  ms01  ms02`。 |

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

演练权威结论以 `docs/reviews/archive/2026-08-28-migration-rehearsal.md` 为准。
