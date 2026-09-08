# 2026-08-28 双快照迁移演练验收报告

**结论：迁移 01→02 在 `project006.sql`（current）与 `project006-base.sql`（base）两个快照上均演练通过；迁移后以 `%prod`（`generation: validate`）启动 prod jar 零 schema 校验错误。** 演练还捕获并修复了迁移 01 的一处真实缺口（`general_ticker_pat_train_page.id` 在 base 快照仍为 int，实体要求 varchar）。所有产物为纯 schema，不含业务数据行、容器凭据或令牌。

## 环境与输入

| 项 | 值 |
|---|---|
| 引擎镜像 | `mysql:8.0`，digest `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b` |
| Docker | 29.1.3 |
| JDK | Temurin 21.0.12.1（`~/.local/opt/jdk21`） |
| current 快照校验和 | `sha256:b18e5b1743517e61066dcad5da5a54ba81c7b114cc6ed75f600532c64aeab408`（`docs/database/project006.sql`） |
| base 快照校验和 | `sha256:725632dcb9e7b100cce09ff55d6135385387659bb831a2ca77c77dd713f11c0a`（`docs/database/project006-base.sql`） |
| 迁移 01 | `docs/database/migrations/2026-08-26-01-schema-sync.sql` |
| 迁移 02 | `docs/database/migrations/2026-08-26-02-engine-innodb.sql` |
| 实体权威 schema | `entity-schema.tsv`，`sha256:520f39f86a268280aa01dc928dc2f7757030886b367e6b642a1883e83137075f`（103 张实体表） |

## 迁移耗时（毫秒，各快照分别计时）

| 快照 | 迁移 01（schema-sync） | 迁移 02（engine-innodb） |
|---|---|---|
| current | 722 | 2552 |
| base | 662 | 2628 |

## 断言输出（脚本实跑，两快照全绿）

```text
[current] post-migration table count=105 .......... PASS (105)
[current] MyISAM table count=0 .................... PASS (0)
[current] table exists: general_telex_pat ......... PASS
[current] table exists: general_telex_pat_page .... PASS
[current] table exists: general_telex_pat_user .... PASS
[current] table exists: general_telex_pat_user_value PASS
[current] table exists: t_masthead ................ PASS
[current] simulation_router_room.is_start_sign default=1 ..... PASS (1)
[current] t_post_ticker_tape_train.is_start_sign default=1 ... PASS (1)
[current] general_key_pat_page.id data_type=varchar .......... PASS (varchar)
[current] entity-table schema diff empty (validate-equivalent) PASS

[base] pre-migration general_key_pat_page.id data_type=int ... PASS (int)
[base] post-migration table count=105 ............. PASS (105)
[base] MyISAM table count=0 ....................... PASS (0)
[base] table exists: general_telex_pat/_page/_user/_user_value PASS
[base] table exists: t_masthead ................... PASS
[base] simulation_router_room.is_start_sign default=1 ........ PASS (1)
[base] t_post_ticker_tape_train.is_start_sign default=1 ...... PASS (1)
[base] general_key_pat_page.id data_type=varchar ............. PASS (varchar)
[base] entity-table schema diff empty (validate-equivalent) .. PASS

REHEARSAL PASSED: all assertions green for both snapshots
```

## 空差分证据（校验和 = 空文件 SHA-256）

```text
diff-current.txt  0 bytes  sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
diff-base.txt     0 bytes  sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

`e3b0c442…` 是空输入的规范 SHA-256，二者即为空差分的确定性证据。

其余产物校验和：

```text
current-schema.tsv  sha256:d405061628ec280ee576149e1e124c7d11c65ee1f902157ecf6a4ea9f5394a22
base-schema.tsv     sha256:a61581d0e4efb2d2020aa14d497965c6756e2227b16bd2b8e9d468bf024c8cf8
current-engine.tsv  sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1
base-engine.tsv     sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1
```

引擎证据：两快照迁移后引擎分布完全一致（同一校验和）——105 张 `BASE TABLE` 全为 InnoDB，MyISAM=0。

## 演练捕获的真实缺陷及修复

迁移 01 原仅对 `general_key_pat_page.id`（base 为 `int AUTO_INCREMENT`，实体为 UUID varchar）做 int→varchar 对齐，**遗漏了同型的 `general_ticker_pat_train_page.id`**：current 快照该列已是 `varchar(64)`（历史上已在生产修正），故仅针对 current 编写迁移的作者未发现 base 仍为 `int`。首轮演练的 `diff-base` 以 `general_ticker_pat_train_page id string vs int` 实证了该缺口。

修复：在迁移 01 第 3 节按既有同类语句补入
`ALTER TABLE general_ticker_pat_train_page MODIFY COLUMN id varchar(64) NOT NULL;`。
该语句对 current 快照为无害同型重建（幂等），补后重跑双快照演练 `diff-current`/`diff-base` 均归零，且 current 仍为 105 表、全断言绿——无非幂等副作用。

## Prod jar 冒烟（迁移后 current 快照，端口 18002，`%prod` `generation: validate`）

一次性 `mysql:8.0` 容器导入 current 快照 → 迁移 01→02（105 表 / 0 MyISAM）后，
以受管进程启动 prod jar，数据源经环境变量仅指向该容器：

- **启动**：`Profile prod activated`，`started in 2.822s`，监听 `http://0.0.0.0:18002`。
- **schema 校验**：全日志无 `ERROR`/`WARN`/`SchemaManagement`/`wrong column`/`HHH000` 等——`generation: validate` 对迁移后快照零校验错误。

四项冒烟（HTTP 状态 + 业务 `code`，敏感值已脱敏）：

| 检查 | 期望 | 实测 |
|---|---|---|
| `POST /api/cable/type/find` | HTTP 200，body `code=200` | HTTP 200，`code=200`，`message=ok`，`data=[<3 项，id/title 已脱敏>]` |
| `GET /q/openapi` | HTTP 200 | HTTP 200（`openapi: 3.1.0` YAML） |
| `GET /q/swagger-ui` | HTTP 404 | HTTP 404（prod 关闭 swagger-ui） |
| `GET /api/menus/getMenusAll`（@JWT，无 token） | HTTP 200，body `code=203` | HTTP 200，`{"code":203,"message":"token不能为空"}` |

进程与一次性容器/卷在全部路径已停止并清理，无残留。

## 安全约束落实

- `scripts/rehearse-migrations.sh`：`set -euo pipefail`；不接受任何参数（`[[ $# -eq 0 ]]` 否则 `exit 2`）；拒绝环境变量 `DB_HOST`/`JDBC_URL`/`QUARKUS_DATASOURCE_JDBC_URL`；绝不读取 `application.yml` 数据源；每快照全新唯一命名容器+卷；`mysqladmin ping` 等待就绪；仅经 `docker exec -i` 导入；`trap cleanup EXIT INT TERM`。
- 演练用例类名 `EntitySchemaSnapshotRehearsal`（不以 `Test` 结尾），Surefire 默认排除；仅 `-Dtest=EntitySchemaSnapshotRehearsal` 显式运行。
- 所有版本化产物仅含表/列/引擎结构元数据；未提交任何数据 dump、容器凭据或含种子行的日志。
