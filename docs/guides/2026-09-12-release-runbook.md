# 发布与迁移 runbook（更新至2026-09-15）

- **适用范围**：覆盖09-12以来的迁移与发布约束；当前整改及证据以`docs/reviews/2026-09-15-full-project-review.md`和对应spec/plan为准，旧批次记录仅供追溯。
- **定位**：本文是**发布执行清单**，不是设计文档。迁移脚本本体在 `backend/database/migrations/`，演练脚本在 `backend/scripts/rehearse-migrations.sh`。
- **前置**：本批次含 schema 变更与**会话协议不兼容变更**，必须停写 + 备份后执行。

## 0. 一页速查

| 必做 | 为什么 |
|---|---|
| 全库备份（`mysqldump --single-transaction`） | 迁移含 `MODIFY`/`ADD COLUMN`/引擎转换，**无自动 down 脚本**，回滚依赖备份（见 §3） |
| 对照已执行清单，补齐 §2 的22个迁移 | 20个schema增量守生产validate；2个数据订正守路由和统计单位 |
| 注入 `DB_USER` / `DB_PASSWORD` | `%prod` 数据源凭据无默认值；漏注入时应用在 JPA 引导阶段失败退出（见 §4.1） |
| 通告「全员需重新登录一次」 | token 协议改不透明随机串 + 哈希存储，**存量会话全部失效**（见 §4.2） |
| 给需要上分的教员赋系统管理员角色 | 理论考试上分端点改为 `@RequireAdmin`，库中无独立教员角色（见 §4.3） |
| 桌面包重新分发 | 随包 `bin/nip.db` 归一为发布态默认、渲染进程启用 `contextIsolation`（见 §4.4） |

## 1. 停写与备份

```bash
# 1) 停应用（所有实例），确认无连接残留
#    DDL 隐式提交，边写边迁会产生半迁移状态
# 2) 全库备份
mysqldump --single-transaction --routines --triggers \
  -u root -p project006 > project006-$(date +%Y%m%d%H%M).sql
```

`2026-09-11-03-personal-electronic-capture.sql` 与 `2026-09-11-04-personal-handkey-capture.sql` 的脚本头注已明确要求「停写 + 备份后执行」。

## 2. 迁移执行顺序（22个脚本）

**按文件名字典序执行尚未执行的增量**。schema子集与`backend/scripts/rehearse-migrations.sh`的`MIGRATIONS`数组同序；#14菜单和#22统计为数据迁移，不混入schema数组，但必须纳入部署清单。同日重号按下表顺序，不能因旧文档止于09-12-03而漏掉后续迁移。

| # | 脚本 | 性质 |
|---|---|---|
| 1 | `2026-08-26-01-schema-sync.sql` | schema 对齐（建表 + 补列 + 改类型），**非幂等** |
| 2 | `2026-08-26-02-engine-innodb.sql` | 22 张 MyISAM → InnoDB，**非幂等** |
| 3 | `2026-09-08-01-unique-lazy-create.sql` | 懒建路径唯一约束，**非幂等** |
| 4 | `2026-09-10-01-theory-json-capacity.sql` | 理论考试 JSON 列 → `longtext` |
| 5 | `2026-09-10-02-scoring-json-capacity.sql` | 评分规则/拍发页/结算 JSON 列 → `longtext` |
| 6 | `2026-09-11-01-group-net-scoring.sql` | 综合组网题目/答案/冻结规则容量 |
| 7 | `2026-09-11-01-simulation-page-uniqueness.sql` | 仿真页/结果唯一键（**须在 #2 之后**） |
| 8 | `2026-09-11-03-personal-electronic-capture.sql` | 个人电子键采集列 + 原始页表 |
| 9 | `2026-09-11-03-post-telex-capture-clock.sql` | 数据报/电传采集列 + 持久化倒计时 |
| 10 | `2026-09-11-04-general-capture-clock.sql` | General 手键/电子键采集、轮次隔离、收尾索引 |
| 11 | `2026-09-11-04-personal-handkey-capture.sql` | 个人手键采集列 |
| 12 | `2026-09-12-01-general-telex-capture.sql` | 组训数据报/电传采集时间轴、冻结满分、收尾索引 |
| 13 | `2026-09-12-02-post-telex-due-index.sql` | 倒计时到期扫描索引 |
| 14 | `2026-09-12-03-menu-telex-component-path.sql` | **数据迁移**（菜单 component 路径），与前端同版本强耦合 |
| 15 | `2026-09-12-04-comprehensive-key-authority.sql` | 综合电子键冻结题面与权威活动时钟 |
| 16 | `2026-09-12-05-radio-study-clock.sql` | 报话学习持久化会话与活动时钟 |
| 17 | `2026-09-12-06-entering-accuracy-capacity.sql` | 岗位录入准确率容量修正 |
| 18 | `2026-09-14-01-classic-telegram-clock.sql` | 经典手键协议与暂停活动时钟 |
| 19 | `2026-09-14-02-classic-telex-clock.sql` | 经典数据报协议与活动时钟 |
| 20 | `2026-09-14-03-classic-receive-clock.sql` | 经典收报协议与活动时钟 |
| 21 | `2026-09-15-01-entering-exercise-clock.sql` | 个人拼音/五笔真实输入、冻结题面与时钟；旧行保持协议0 |
| 22 | `2026-09-15-02-electronic-statistics-time.sql` | **数据迁移**：按0/NULL旧协议及1新协议重算现有电子type2毫秒汇总；不改源训练及type0/1 |

仅对尚未执行过前三项的基线库，首次完整升级可用下例；已有库须按运维记录选取后续增量，不能盲重跑非幂等项。子shell在首个失败处退出，不把`break`后的零退出码当作全部成功。

```bash
(
  set -e
  cd backend
  export LC_ALL=C
  for m in database/migrations/*.sql; do
    printf '== %s\n' "$m"
    mysql -u root -p project006 < "$m"
  done
)
```

要点：

- **#1–#3非幂等**，重复执行会报错。后续schema脚本以结构守卫重复执行；数据迁移按确定源数据更新，幂等不等于每条语句都“零变更”。
- **无迁移账本表**：运维必须按文件名记录已跑清单，脚本不改名。
- **#14菜单数据迁移**仍与前端路由同版本；演练脚本不以schema差分替代菜单正确性。
- **#22统计数据迁移**在schema之后另有合成数据演练：旧协议0/NULL、新协议亚秒精度、未完成源、空源以及不相关type0/1，连续执行两遍。它直接从已完成训练重算，不能用统计列乘1000代替。

执行后验证：

```bash
cd backend
export JAVA_HOME=$HOME/.local/opt/jdk21
./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test
REHEARSAL_OUT_NAME=2026-09-15-release-check ./scripts/rehearse-migrations.sh
```

## 3. 逐脚本还原步骤

**通则**：本批次无自动 down 脚本。「补列/建索引」类可按下表就地还原；「改类型/转引擎/建唯一键」类一旦有新数据落入就**只能按 §1 的备份还原**，因为缩容会截断数据、删唯一键不恢复重复行。

| 脚本 | 还原 | 数据风险 |
|---|---|---|
| #1 `schema-sync` | 备份还原 | 含建表与类型收敛，逐条反向不可靠 |
| #2 `engine-innodb` | `ALTER TABLE <t> ENGINE = MyISAM`（**不建议**） | 回到不可回滚引擎 = 恢复已知数据丢失缺陷 |
| #3 `unique-lazy-create` | `ALTER TABLE t_radiotelephone_train DROP INDEX uk_radiotelephone;` 等 4 个 `uk_*` | 删键不恢复被去重的重复行 |
| #4 `theory-json-capacity` | `MODIFY ... text`（缩容） | 超长 JSON 会被截断 → 先确认无超 64KB 行 |
| #5 `scoring-json-capacity` | 同 #4 | 同上 |
| #6 `group-net-scoring` | 同 #4 | 同上 |
| #7 `simulation-page-uniqueness` | `DROP INDEX uk_simulation_page_room_page_sort` / `uk_simulation_value_room_user_page` | 删键不恢复重复行 |
| #8 `personal-electronic-capture` | `ALTER TABLE t_post_telegraph_key_pat_train DROP COLUMN protocol_version, DROP COLUMN attempt, DROP COLUMN full_score;` + `DROP TABLE t_post_telegraph_key_pat_train_raw_page`（含 `uk_personal_key_raw_page`） | 丢弃全部原始采集行 → 成绩不再可重算 |
| #9 `post-telex-capture-clock` | `ALTER TABLE t_post_telex_pat_train DROP COLUMN protocol_version, DROP COLUMN attempt, DROP COLUMN countdown_seconds, DROP COLUMN deadline, DROP COLUMN paused_at;` + `t_post_telex_pat_train_page_value` 的采集列 | 同 #8；另丢失倒计时持久化 |
| #10 `general-capture-clock` | 删 13 个新列（`general_key_pat`/`general_ticker_pat` 及其 `*_user`/`*_user_value`）+ `DROP INDEX idx_general_key_pat_closing` / `idx_general_ticker_pat_closing` | 同 #8 |
| #11 `personal-handkey-capture` | 删 7 个新列（`t_post_telegram_train` 及 `*_floor_content_value`） | 同 #8 |
| #12 `general-telex-capture` | 删 8 个新列（`general_telex_pat` 及 `*_user`/`*_user_value`）+ `DROP INDEX idx_general_telex_pat_closing` | 同 #8 |
| #13 `post-telex-due-index` | `DROP INDEX idx_post_telex_due ON t_post_telex_pat_train;`（脚本头注已写） | 无，只影响扫描性能 |
| #14 `menu-telex-component-path` | 把 `t_menus.component` 改回旧路径 | 必须与前端版本一起回滚，否则菜单指向不存在的组件 |
| #15 综合电子键权威字段 | 备份还原，或先保留增量列再成对回退代码 | 丢列将失去冻结源和活动时钟，不可重算新协议记录 |
| #16 报话学习时钟 | 同 #15 | 丢失活动会话与已结算标识 |
| #17 录入准确率容量 | 优先备份还原，不盲缩容 | 100%结果可能无法写入旧容量 |
| #18–#20 经典训练时钟 | 备份还原；成对回退前评估新协议记录 | 丢失暂停/继续的服务器时间证据 |
| #21 个人码串协议 | 备份还原，并同步前端/后端/共享码表 | 新输入value与冻结题面不能交给旧判分模型处理 |
| #22 电子统计数据订正 | 恢复备份中的type2汇总行 | 不可用反向乘除重建混合协议的旧汇总 |

增量列通常不妨碍旧版ORM的结构校验，**但这不等于业务协议可以单侧回滚**。必须成对回退前端、后端及共享码表，并评估已经产生的新协议记录；回滚库而保留新代码会让生产validate失败。

## 4. 应用侧发布前提

### 4.1 数据源凭据（T3-6）

`%prod` 的 `username`/`password` 是 `${DB_USER}`/`${DB_PASSWORD}`，**刻意不带默认值**：

```bash
DB_USER=app DB_PASSWORD=**** java -jar target/quarkus-app/quarkus-run.jar
```

漏注入时 Quarkus 把未展开的表达式当作「未配置」，MySQL 驱动回退到操作系统用户名连库 → 应用在 JPA 引导阶段失败退出、**不会对外提供 HTTP**，但日志只有驱动级的 `Access denied for user '<OS 用户>'@…`。这条诊断信息无法在应用内改善（JPA 引导早于任何 `@Observes StartupEvent`；前移到 SmallRye 配置拦截器会连 `mvn package` 一起挡掉），所以它是**部署方硬前提**。详见 `backend/README.md`「生产凭据硬约束」。

### 4.2 会话协议不兼容：全员需重新登录（T3-1）

token 从「AES(账号-明文口令-deviceId)」改为 `SecureRandom` 不透明随机串，数据库只存 SHA-256 摘要。

- **影响**：`t_user.token` 里的存量 AES 密文按摘要查不到人 → **所有现存会话立即失效**，全员需重新登录一次。桌面端「自动登录」同样失效。
- **刻意不做双查兜底**：双查等于让旧凭据继续可用，本次整改的目标之一就是让泄露的旧 token 立刻失效。
- **通告时机**：与发布窗口一并通告，不要在训练时段发布。
- **回滚**：revert 该提交即恢复（存量 AES token 仍在库里未被改写）；但此后新签发的随机 token 会因旧代码按 AES 解析而失效 —— 回滚同样需要一次全员重新登录。

### 4.3 上分教员需具备系统管理员角色（T1-3）

`POST /api/theoryKnowledgeExamUser/teacherUploadScore` 现在要求 `@RequireAdmin`。库中没有独立的「教员」角色，因此**需要上分的教员必须被赋予系统管理员角色**，否则该端点返回 `code:207`。这是运营流程变更，发布前需确认名单。

### 4.4 桌面包

- 随包 `bin/nip.db` 已归一为发布态默认（`localhost:18001` / `127.0.0.1:8000`），`_id:3`（串口）不预置 —— 首次启动由用户在网络设置/串口页选择。该文件**运行时可写**，用户改设置即写回，因此不纳入 CI 脏检查。
- 渲染进程启用 `contextIsolation` + preload 白名单桥、移除 `--ignore-certificate-errors`：**错误证书不再被静默接受**，若现场用自签名 HTTPS 资源服务会直接失败（G4 可信证书门禁仍是外部前置）。
- 内嵌文件服务默认只监听 `127.0.0.1:8000`。跨机访问「资源服务地址」能力保留，但需显式配置监听地址。
- 发布校验会断言 git tag 与三个版本号（`backend/pom.xml`、`bw-frontend/frontend/package.json`、`bw-frontend/package.json`）一致，打 tag 前先对齐。

### 4.5 Native 构建：会话随机数必须运行期初始化

`backend/src/main/resources/application.yml` 的 `quarkus.native.additional-build-args` 包含
`--initialize-at-run-time=com.nip.common.security.SessionToken`。不可移除：该类的静态
`SecureRandom` 不能在构建机上初始化后固化进镜像，否则三个平台都在 Native Image 分析阶段失败
（[失败运行 34688361957](https://github.com/JungleZy/007/actions/runs/34688361957)，错误指向 `SessionToken.RANDOM`）。
此配置不改变令牌格式、随机强度或摘要算法，也不需要额外的部署启动参数。

Linux x64 本地验证命令（需 Docker，使用 CI 同款 builder）：

```bash
cd backend
JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify -Pnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 \
  -Dquarkus.native.march=x86-64
```

2026-09-12 修复阶段的**历史/中间执行记录**：392 个 JVM 测试全绿、Mandrel 23.1.12.1 Native 构建成功，产物最高 glibc
要求为 2.17（门槛 ≤2.28）。按 CI 的无库冒烟参数启动后 `/q/openapi` 返回 200；该冒烟不代表
数据库业务可用。本轮本地最终基线为 **502 测试 / 115 suite**、前端 **37/37 + build**，22个迁移（20 schema + 2 data）的schema与电子统计专项双快照演练通过。最终三平台native Actions结果见`docs/reviews/2026-09-15-full-project-review.md`；native CI不等于目标桌面实机、可信证书、生产凭据或真实硬件验收。

## 5. 发布后验证

```bash
# 后端起得来且 schema 校验通过
DB_USER=... DB_PASSWORD=... java -jar backend/target/quarkus-app/quarkus-run.jar   # 期望 started in …
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18001/q/openapi          # 200
```

- 用一个普通学员账号登录，确认能进训练页、WS 连得上（握手现在校验 `token`+`deviceId`）。
- 用管理员账号确认管理页可写；用学员账号确认管理写端点返回 `code:207`（不是 500、不是静默成功）。
- 桌面包冷启动确认：登录、训练页、摩尔斯发音、串口页、网络设置页、许可页全通。

## 6. 关联文档

- 当前评审与执行证据：`docs/reviews/2026-09-15-full-project-review.md`（§7）；09-12报告为历史执行记录。
- 当前规格与计划：`docs/specs/2026-09-15-full-project-fix-spec.md`、`docs/plans/2026-09-15-full-project-fix-plan.md`
- 后端工程说明（含迁移与凭据硬约束）：`backend/README.md`
- 会话/口令协议迁移设计：`docs/plans/2026-09-09-password-session-migration-plan.md`
