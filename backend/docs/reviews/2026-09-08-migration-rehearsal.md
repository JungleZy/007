# 2026-09-08 双快照迁移演练（迁移 03 加入后）验收报告

**结论：迁移 01→02→03 在 `docs/database/project006.sql`（current）与 `docs/database/project006-base.sql`（base）两个快照上均演练通过（`exit=0`，`REHEARSAL PASSED`，两侧差分 0 字节、105 表 / 0 MyISAM），本轮新增的两条唯一索引断言在两侧各 2 条全 PASS；迁移 03（`2026-09-08-01-unique-lazy-create.sql`）实测 current 110ms / base 165ms，比迁移 02 的 2.4 秒小一个半数量级——因为它的两张目标表在演练库里都是 0 行。迁移 03 已由活库连跑三次确证幂等（第三次 `mysql exit=0`、索引列数仍为 3）；prod jar 在默认 `%prod`（`generation: validate`）下启动成功，无 `SchemaManagementException`，即实体新增的唯一约束被 validate 接受。** 本轮没有捕获新缺陷（上一轮捕获的「MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`」已在 2026-09-07 修复）。所有版本化产物为纯 schema，不含业务数据行、容器凭据或令牌。

## 元信息

| 项 | 值 |
|---|---|
| 演练日期 | 2026-09-08（偏离收口批 4 之后，证据目录 `2026-09-08`） |
| 脚本 | `scripts/rehearse-migrations.sh`（**235 行**，上一轮 224 行），调用方式 `REHEARSAL_OUT_NAME=2026-09-08 bash scripts/rehearse-migrations.sh` |
| 引擎镜像 | `mysql:8.0`（`scripts/rehearse-migrations.sh:42` 硬编码），字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`（`:127`） |
| current 快照 | `docs/database/project006.sql`，本报告定稿时实测 `sha256:678829b3b1dc15b9c2f5ab61602d48500d1bbf4efc7302b44f4f803ad83b1662`，36559 行 / 105 `CREATE TABLE` / 34626 `INSERT`。已含本波回灌的两条 `UNIQUE INDEX`（`:28678`、`:29538`） |
| base 快照 | `docs/database/project006-base.sql`，`sha256:725632dcb9e7b100cce09ff55d6135385387659bb831a2ca77c77dd713f11c0a`，31960 行 / 100 `CREATE TABLE` / 30139 `INSERT`（与上一轮逐字节相同，未改动） |
| 迁移 01 | `docs/database/migrations/2026-08-26-01-schema-sync.sql`（128 行，已幂等） |
| 迁移 02 | `docs/database/migrations/2026-08-26-02-engine-innodb.sql`（37 行，22 条 `ALTER TABLE … ENGINE = InnoDB`） |
| 迁移 03（本轮新增） | `docs/database/migrations/2026-09-08-01-unique-lazy-create.sql`（**54 行**，2 条 `ADD CONSTRAINT … UNIQUE`，`information_schema.statistics` 判存 + `PREPARE`） |
| 实体权威 schema | `docs/database/rehearsal/2026-09-08/entity-schema.tsv`，`sha256:520f39f86a268280aa01dc928dc2f7757030886b367e6b642a1883e83137075f`，103 张实体表 / 875 列行（与上一轮**逐字节相同**——唯一约束不增列，不改变 validate 的列契约） |
| 断言口径 | 表计数、MyISAM=0、5 张命名表存在、两处 `is_start_sign` 默认=1、`general_key_pat_page.id`=varchar、**两条唯一索引存在且 `non_unique=0`**、实体列 ⊆ 快照列（validate 等价差分为空）；详见「4. 断言矩阵」 |
| 证据目录 | `docs/database/rehearsal/2026-09-08/`（8 个证据文件 + README） |
| 边界 | 一次性 Docker 容器 + 卷，全新唯一命名，`trap` 全出口清理；脚本不接受位置参数、拒绝 `DB_HOST`/`JDBC_URL`/`QUARKUS_DATASOURCE_JDBC_URL`、绝不读 `application.yml` 数据源（`:11-17,27-36`） |

墙钟未由脚本打印；按证据目录产物 mtime 推算约 **551 秒**（`entity-schema.tsv` 13:19:17 → `diff-base.txt` 13:28:28），与上一轮 550.61s 同量级，主体仍是两次容器冷启动与 6 万余行快照导入，非迁移耗时。

## 1. 停服窗口评估（迁移 03）

**实测（毫秒，两快照分别计时，来源 `docs/database/rehearsal/2026-09-08/timings.tsv`）：**

| 快照 | 迁移 01（schema-sync） | 迁移 02（engine-innodb） | 迁移 03（unique-lazy-create） |
|---|---|---|---|
| current（已迁移状态再跑一遍） | 182 | 2450 | **110** |
| base（旧基线，真做全部变更） | 644 | 2431 | **165** |

`timings.tsv` 原文两行：`current 182 2450 110`、`base 644 2431 165`。

**迁移 03 为什么这么快——两张表都是 0 行。** 迁移脚本头注 `docs/database/migrations/2026-09-08-01-unique-lazy-create.sql:24-26` 记录了 2026-09-08 活库实测：`t_radiotelephone_train` **0 行 / 0 重复**、`t_theory_knowledge_test_fallible` **0 行 / 0 重复**。演练库同样来自快照，这两张表在快照里也没有数据行。`ADD CONSTRAINT … UNIQUE` 的代价主要是**建索引时的全表扫描 + 排序 + 索引落盘**，行数为 0 时几乎只剩元数据操作，所以 110–165ms 里绝大部分是 `mysql` 客户端连接、`information_schema.statistics` 判存查询与两次 `PREPARE`/`EXECUTE` 的固定开销，而不是索引构建本身。

对比迁移 02 的 2.4 秒也印证这一点：迁移 02 的 22 条 `ALTER … ENGINE=InnoDB` 作用在**有数据**的业务表上（current 快照 34626 条 INSERT / base 30139 条），是带数据重建；迁移 03 作用在空表上。**两者的耗时不可类比，也不能互相外推。**

**生产上会变慢的两种情形，必须提前处置：**

1. **有存量行 → 变慢。** `ALTER TABLE … ADD CONSTRAINT … UNIQUE` 在 MySQL 8.0 InnoDB 上是 `INPLACE` 建二级索引、允许并发 DML，但仍需扫描全表并排序构建索引，耗时随行数与列宽近似线性增长；110ms 只是**0 行下界**，对生产没有任何预算意义。上线前应先取实际规模：

   ```sql
   select table_name, table_rows, data_length
     from information_schema.tables
    where table_schema='project006'
      and table_name in ('t_radiotelephone_train','t_theory_knowledge_test_fallible');
   ```

2. **有重复行 → 直接失败，必须先去重。** 目标列上若已存在重复键，`ALTER` 会以 **`ERROR 1062 Duplicate entry`** 中止（脚本头注 `:27-28` 已写明）。此时**必须**先按 `(user_id, type)`（话报统计）/ `(user_id)`（易错题缓存）去重、每组保留一行，再重跑本脚本。**禁止**把唯一索引降级成普通索引绕过——那样代码侧的幂等写法（撞唯一键换新事务重读）就失去了闸门，并发双插会原样回来（见 `docs/reviews/2026-09-07-full-project-review.md` §9）。去重前的探查 SQL：

   ```sql
   select user_id, type, count(*) c from t_radiotelephone_train
    group by user_id, type having c > 1;
   select user_id, count(*) c from t_theory_knowledge_test_fallible
    group by user_id having c > 1;
   ```

**判断：** 三条迁移在演练数据量下合计 current 2742ms / base 3240ms。迁移 03 单独看是**毫秒级、非停服窗口的主要成分**；停服窗口预算仍由迁移 02（22 张表带数据重建）主导，其取值方法照 `docs/reviews/2026-09-07-migration-rehearsal.md` 第 1 节（只能给下界，需按生产行数线性外推并留 ≥10× 冗余）。

## 2. 幂等确证（活库连跑三次）

迁移 03 的幂等不是纸面声明，有两处独立实证：

**(a) 活库连跑三次。** `docker` 容器 `mysql-project006` / db `project006` 上连续执行同一脚本三次，**第三次 `mysql exit=0`**，且

```sql
select count(*) from information_schema.statistics
 where table_schema='project006'
   and index_name in ('uk_radiotelephone_train_user_type','uk_theory_test_fallible_user');
-- → 3   （2 列复合索引的 2 行 + 单列索引的 1 行；重复执行未新增任何行）
```

即索引列数**仍为 3**、`non_unique=0`，没有出现重复索引或第二条同名索引。这正是判存 + `DO 0` 空操作生效的证据（`2026-09-08-01-unique-lazy-create.sql:36-44`、`:46-54`）。

**(b) 演练 current 分支本身就是幂等回归。** 快照回灌（第 5 节）已把两条 `UNIQUE INDEX` 写进 `project006.sql` 的建表块，所以 current 分支导入的库**已经带着这两条索引**，随后再跑一遍迁移 03——它必须走 `DO 0` 且断言仍绿。current 侧两条 `unique index exists` 断言 PASS，即幂等在每次演练中被重新验证一次，而不是一句注释。

**为什么必须幂等：** MySQL 8.0 没有 `ADD CONSTRAINT IF NOT EXISTS`（`:31-32`）；不幂等的脚本在「部分已迁移」的库上会以 1061/1062 中止，回滚预案（第 7 节）里的「重新前滚」路径与生产执行中断后的重试都会炸。

## 3. 演练脚本的变更（`scripts/rehearse-migrations.sh`，224 → 235 行）

| 变更 | 当前行号 | 内容 |
|---|---|---|
| 迁移 03 路径常量 | `:62` | `MIG03="$REPO_ROOT/docs/database/migrations/2026-09-08-01-unique-lazy-create.sql"`，并加入 `:64-68` 的存在性前置检查循环 |
| 计时第三段 | `:160-162` | `s03=$(date +%s%3N); mysql_exec … < "$MIG03"; e03=$(date +%s%3N); ms03=$((e03 - s03))` |
| 计时输出行 | `:163` | `TIMING migration-01=…ms migration-02=…ms migration-03=…ms` |
| `timings.tsv` 3 列 → **4 列** | `:164` | `printf '%s\t%s\t%s\t%s\n' "$label" "$ms01" "$ms02" "$ms03"`，列义 `label  ms01  ms02  ms03` |
| 两条唯一索引断言 | `:183-189` | 以 `表:索引名:期望列数` 三元组循环（`t_radiotelephone_train:uk_radiotelephone_train_user_type:2`、`t_theory_knowledge_test_fallible:uk_theory_test_fallible_user:1`），断言 `select count(*) from information_schema.statistics where … and index_name='…' and non_unique=0` 等于期望列数 |

断言用**列数**而非「存在与否」，一次锁住三件事：索引存在、组合列数正确（不是只建了 `user_id` 一列）、`non_unique=0`（是唯一索引而非普通索引）。

未改动的部分：安全守卫（`:27-36`）、trap 清理（`:76-87`）、schema/engine 转储与规范化差分（`:191-223`）一律沿用。

## 4. 断言矩阵

下表逐条取自 `scripts/rehearse-migrations.sh`（行号为断言/校验所在行），current/base 两列为本次演练结果。

| # | 断言 | 脚本位置 | current | base |
|---|---|---|---|---|
| 1 | 容器就绪（`mysqladmin ping` ≤120s + 可查询 ≤30s） | `:129-142` | 绿（未触发 `container did not become ready`） | 绿 |
| 2 | 迁前 `general_key_pat_page.id data_type=int`（仅 base） | `:147-152` | 不适用 | 绿 |
| 3 | 迁后 `BASE TABLE` 计数 = 105 | `:167-168` | **PASS (105)** | **PASS (105)** |
| 4 | 迁后 MyISAM 表计数 = 0 | `:169-170` | **PASS (0)** | **PASS (0)** |
| 5 | 表存在：`general_telex_pat` | `:171-175` | 绿 | 绿 |
| 6 | 表存在：`general_telex_pat_page` | `:171-175` | 绿 | 绿 |
| 7 | 表存在：`general_telex_pat_user` | `:171-175` | 绿 | 绿 |
| 8 | 表存在：`general_telex_pat_user_value` | `:171-175` | 绿 | 绿 |
| 9 | 表存在：`t_masthead` | `:171-175` | 绿 | 绿 |
| 10 | `simulation_router_room.is_start_sign` 默认值 = 1 | `:176-177` | 绿 | 绿 |
| 11 | `t_post_ticker_tape_train.is_start_sign` 默认值 = 1 | `:178-179` | 绿 | 绿 |
| 12 | 迁后 `general_key_pat_page.id data_type=varchar` | `:180-182` | 绿 | 绿 |
| 13 | **唯一索引存在**：`t_radiotelephone_train.uk_radiotelephone_train_user_type`（2 列，`non_unique=0`） | `:183-189` | **PASS (2)** | **PASS (2)** |
| 14 | **唯一索引存在**：`t_theory_knowledge_test_fallible.uk_theory_test_fallible_user`（1 列，`non_unique=0`） | `:183-189` | **PASS (1)** | **PASS (1)** |
| 15 | 实体表 schema 差分为空（validate 等价） | `:217-223` | **PASS**，`diff-current.txt` 0 字节 | **PASS**，`diff-base.txt` 0 字节 |

共 15 条（含 base 独有的 #2；#13/#14 为本轮新增）。取证口径：#3/#4/#13/#14/#15 的 `PASS` 有逐条输出；#1/#2/#5–#12 标「绿」的依据是脚本失败计数器 `FAILURES`（`:102-107`）为 0、终判行输出 `REHEARSAL PASSED: all assertions green for both snapshots`（`:235`）且 `exit=0`——任一条失败都会走 `:231-234` 非零退出，不会打印该行。

**产物侧交叉证据（本目录实测）：**

```text
diff-current.txt     0 bytes   sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
diff-base.txt        0 bytes   sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
current-engine.tsv   106 行    sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1
base-engine.tsv      106 行    sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1
current-schema.tsv   903 行    sha256:d405061628ec280ee576149e1e124c7d11c65ee1f902157ecf6a4ea9f5394a22
base-schema.tsv      903 行    sha256:a61581d0e4efb2d2020aa14d497965c6756e2227b16bd2b8e9d468bf024c8cf8
```

`e3b0c442…` 是空输入的规范 SHA-256，即空差分的确定性证据。两份 engine TSV 校验和相同——三条迁移路径仍收敛到同一引擎分布（105 张表全 InnoDB）。两份 schema TSV 逐行比对**仅 5 处差异**（实测 `diff` 输出 3 个 hunk / 16 行）：`t_post_telegraph_key_pat_train_more` 的 4 处 `ordinal_position`（`more_group`/`more_line`/`train_id`/`user_id` 在 current 为 6/5/2/3、在 base 为 2/3/5/6，即两组列的位置整体互换）与 `t_post_telegraph_key_pat_train_page_value.value` 的 `text`（current）vs `varchar`（base）——列顺序与字符串族均在 Hibernate validate 契约之外，故两侧差分同为空。

**与上一轮的一致性：** 本目录 5 份 TSV（`entity-schema`/`current-schema`/`base-schema`/`current-engine`/`base-engine`）与 `2026-09-07-postbackfill/` 的同名文件**逐字节相同**（`cmp` 实测）。这是预期的：迁移 03 只加二级索引，不增删列、不改列类型、不改存储引擎，而这些 TSV 只投影列与引擎。**因此唯一索引的证据只能来自 #13/#14 两条断言，不在任何 TSV 里** ——差分为空不构成唯一索引存在的证据。

## 5. 快照回灌（`docs/database/project006.sql`）

本波把迁移 03 的结果就地写进版本化快照，使仓库快照与活库、与实体三者继续保持一致：

- 两张表的 `CREATE TABLE` 块内各加一行唯一索引（原文逐字如下，标识符在快照里带反引号）：

  ```sql
  -- docs/database/project006.sql:28678（t_radiotelephone_train 建表块内）
  UNIQUE INDEX `uk_radiotelephone_train_user_type`(`user_id`, `type`) USING BTREE,
  -- docs/database/project006.sql:29538（t_theory_knowledge_test_fallible 建表块内）
  UNIQUE INDEX `uk_theory_test_fallible_user`(`user_id`) USING BTREE,
  ```

- **34626 条 INSERT 数据行一行未动**（实测计数与回灌前一致）；
- 尾注已更新（`:36466-36470`）：记录本次追加的两条索引、指明二者是迁移 `2026-09-08-01-unique-lazy-create.sql` 的等价结果、并说明「无索引时两个并发首调会各插一行」的动机；`:36463` 的演练证据指针指向 `docs/database/rehearsal/2026-09-08/`。

回灌后快照仍是可导入的完整库：演练 current 分支即以它为输入，导入后跑完三条迁移仍是 105 表 / 0 MyISAM / 差分为空 / 两条唯一索引在位。

## 6. 生产启动验证（实体加约束后的必测项）

实体侧新增 `@UniqueConstraint` 会进入 Hibernate 的 schema 校验范围，而 `%prod` 是 `generation: validate`——若活库缺对应索引，启动即硬失败。故本项是本波的必测项，不可省。

实测：活库已执行迁移 03 后，以默认 `%prod` 启动 prod jar（1.1.0）：

```
quarkus-template 1.1.0 … started in 2.506s
```

**无 `SchemaManagementException`**，即实体的两条唯一约束被 `validate` 接受。

结论边界：这证明「活库已有索引 → validate 通过」，**不**证明「validate 会因缺索引而失败」——后者是 Hibernate 对唯一约束的校验强度问题，本轮未做负样本（见第 8 节第 6 条）。因此**部署顺序不能依赖启动自检兜底**，必须按第 7 节的硬约束执行。

## 7. 升级顺序与回滚预案

### 7.1 升级顺序（硬约束）

```
迁移 01（schema-sync）→ 迁移 02（engine-innodb）→ 迁移 03（unique-lazy-create）→ 部署带批 4 实体改动的 %prod 包
```

三条依赖，缺一不可（脚本头注 `2026-09-08-01-unique-lazy-create.sql:20-22` 已写明）：

1. **01 必须在前**：迁移 03 的两张目标表要先存在（01 补齐 5 张缺表与 2 列）。
2. **02 必须在 03 之前**：`ADD CONSTRAINT … UNIQUE` 要求目标表是 InnoDB；MyISAM 上虽可建唯一索引，但整库引擎口径必须先收口，否则 `LifecycleApplication.checkStorageEngine` 的启动自检会直接拒绝启动（`src/main/java/com/nip/common/LifecycleApplication.java:65-87`）。
3. **迁移 03 必须在部署新包之前**：新包实体带 `@UniqueConstraint`，`validate` 在缺索引时可能拒绝启动；且代码侧的幂等写法**依赖**唯一索引作为并发闸门——先部署代码后加索引，等于在无闸门期间继续放行并发双插。

### 7.2 回滚预案

迁移 03 的回滚是**删索引**，两条语句，代价极低（0 行表上是元数据操作）：

```sql
ALTER TABLE `t_radiotelephone_train`          DROP INDEX `uk_radiotelephone_train_user_type`;
ALTER TABLE `t_theory_knowledge_test_fallible` DROP INDEX `uk_theory_test_fallible_user`;
```

**⚠ 代价不在耗时上，在语义上。** 删掉索引后：

1. **幂等写法失去闸门。** `RadiotelephoneService.accumulate` / `ComprehensiveService.cacheErrorSubject` 的「撞唯一键 → 换新事务重读」分支永不触发，两个并发首调各自 `INSERT` 都会成功——**并发双插会原样回来**（计数割裂 + 孤儿行，机理见 `docs/reviews/2026-09-07-full-project-review.md` §9.2）。
2. **必须同时回滚代码。** 只删索引而留着新代码，等于用一套为「有闸门」设计的写法跑在无闸门的库上：独立事务的开销白付，行为退回修复前，而且写入不再随外层事务回滚（§9.5 的取舍）这一副作用仍然存在。**因此迁移 03 的回滚必须与代码回滚成对执行**，顺序是先回滚应用包，再删索引。
3. **回滚后重新前滚可能已需去重。** 无闸门期间产生的重复行会让重跑迁移 03 报 1062，必须先按第 1 节的去重 SQL 处理。

回滚顺序（与升级严格逆序）：

```bash
# 0) 停应用，确认无写入连接
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 \
  mysql -uroot -N -B -e "select count(*) from information_schema.processlist where db='project006'"

# 1) 回滚应用包到不含批 4 实体改动的版本（实体带 @UniqueConstraint 的包在索引被删后可能启动失败）

# 2) 删两条唯一索引
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 mysql -uroot project006 \
  -e "ALTER TABLE \`t_radiotelephone_train\` DROP INDEX \`uk_radiotelephone_train_user_type\`; \
      ALTER TABLE \`t_theory_knowledge_test_fallible\` DROP INDEX \`uk_theory_test_fallible_user\`;"
```

**前滚（重新执行，脚本幂等，可重复）：**

```bash
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 \
  mysql -uroot --default-character-set=utf8mb4 project006 \
  < docs/database/migrations/2026-09-08-01-unique-lazy-create.sql
```

**校验 SQL（前滚后应得 3；回滚后应得 0）：**

```sql
select count(*) from information_schema.statistics
 where table_schema='project006' and non_unique=0
   and index_name in ('uk_radiotelephone_train_user_type','uk_theory_test_fallible_user');
-- 前滚后：3   （复合索引 2 行 + 单列索引 1 行）
-- 回滚后：0
```

迁移 01/02 的回滚预案不变，见 `docs/reviews/2026-09-07-migration-rehearsal.md` 第 7 节（含 `/tmp/project006-backup-20260907.sql` 的留存提醒——该备份是**迁移 01/02 之前**的状态，不含本轮索引，用它回滚会一并退回三条迁移）。

## 8. 未验证边界

照实列出，本次演练**没有**覆盖的部分：

1. **有存量行时的建索引耗时未测。** 两张目标表在演练库与活库均为 0 行；110/165ms 只是 0 行下界，对任何有存量数据的环境都没有预算意义（第 1 节）。
2. **有重复行时的 1062 失败路径未实跑。** 「必须先去重」的结论来自 MySQL 语义与迁移脚本头注（`:27-28`），未在容器里人工造重复行验证报错与中止行为。
3. **建索引期间的并发 DML 未测。** 演练库无并发连接。MySQL 8.0 InnoDB 的 `ADD UNIQUE INDEX` 为 `INPLACE`、理论上允许并发 DML，但**未实测**；也未验证并发写入与唯一约束同时生效时的冲突表现。
4. **回滚路径未实跑。** 第 7.2 节的 `DROP INDEX` 命令与「回滚需成对回滚代码」的结论是推导，未在容器上把「删索引 → 观察并发双插复现 → 重新前滚」整条链路跑过。
5. **迁移 03 的回滚 + 前滚组合未与迁移 01/02 联合演练。** 三条迁移的联合前滚有演练证据（本报告），联合回滚没有。
6. **`validate` 对缺失唯一索引的负样本未测。** 第 6 节只证明「索引在位 → 启动成功」；未通过临时 `DROP INDEX` 后启动 prod jar 来验证 Hibernate 是否真的会因缺唯一索引而抛 `SchemaManagementException`。因此「部署顺序」只能靠流程保证，不能假定启动自检会兜住。
7. **主从/复制场景未测。** 演练是单实例容器；`ADD UNIQUE INDEX` 在从库的重放耗时与复制中断风险未评估。
8. **镜像 digest 未记录。** 仅固定 tag `mysql:8.0`，未留存 digest；严格可复现性弱于 2026-08-28 那轮。
9. **数据正确性未断言。** 全部断言均为 schema 层（表/列/类型/引擎/默认值/索引）；行数与内容一致性不在演练范围内。
10. **墙钟未由脚本记录。** 551 秒是按产物 mtime 推算，非脚本输出。

演练产物与复现命令见 `docs/database/rehearsal/2026-09-08/README.md`。读路径懒建本身的评估结论见 `docs/reviews/2026-09-07-full-project-review.md` §9。
