# 客户报障整改实施计划（Plan）

- 日期：2026-09-10
- 状态：**T00–T16 仓内实现与运行验证已完成（2026-09-12：backend clean verify 316 项全绿、前端构建与真实页面验证、真实桌面打包）；T17 只剩发布负责人与 G4 现场交付未完成，本计划因此保持未完成**。逐项终态见 §5.5，模式边界见 T00.2 台账。
- 依据：[`../reviews/2026-09-10-customer-issue-analysis.md`](../reviews/2026-09-10-customer-issue-analysis.md)；规格：[`../specs/2026-09-10-customer-issue-fix-spec.md`](../specs/2026-09-10-customer-issue-fix-spec.md)。
- 源码取证基线：`3360221`；执行时记录实际提交。既有联合计划中已完成的事项只回归，不重新算本计划成果。

## 1. 执行约定

1. Spec 的 R01–R12/H1–H5/M1–M5、G1–G4、V01–V13 是本计划唯一需求、门禁和验收编号；T00–T17 是实施工作包。
2. 一个可独立回滚的修复一个 commit；工作包内多个独立修复分别提交。跨栈参数切换、迁移与依赖代码、修复与回归测试不得拆成不可用中间状态。提交格式 `fix(scope): 中文摘要` 等，不攒无关改动。
3. 接触导出符号前查 LSP references；当前未配置 LSP，不能伪造结果，使用 API 定义、导入、路由、内部调用全量搜索代替并记录。修改 API 必须检查 `bw-frontend/frontend/src/common/api/` 和实际消费者。
4. 不改 203/204/206 后端码文，不建 user_session，不重做既有密码/会话方案，不把 frontend 旧根路径误当当前工程。
5. 未通过 G1/G2/G3 的语义变更不先写代码猜需求；确定性 bug 可独立修。缺客户设备不妨碍仓内复现，但不能关闭客户验收。
6. 不以 count/length/源码文本断言代替行为。真实页面/音频/串口、数据库状态和评分结果是验收对象；永久测试仅保留防止真实回归的案例。
7. 并行分支只实现各自独占文件，期间不跑全量构建/测试；合并稳定后由集成者统一验证一次，独立任务交付前仍须完成受影响验证。

## 2. 依赖与文件所有权

### 2.1 推进顺序

| 波次 | 可并行工作 | 必须先满足 |
|---|---|---|
| A 基线与交付 | T00、T01 取证/打包链修复、T15 模板往返 | 先读取工作树，保护用户已有改动；客户发布必须等 G4 和验证 |
| B 确定性修复 | T02 公式、T05 有序事件、T11 网络数据、T16 凭证提示 | 不互改同一文件；G1/G2 未闭合的部分不猜测 |
| C 状态与设置 | T03 提交/结束、T04 身份契约、T06 控制符、T07 配置、T12 通知 | T02→T03→T04 共享后端；T05→T06→T07 共享输入；T11→T12 |
| D 权威与语义 | T08 原始评分、T09 截止恢复、T10 音频、T13 详情/对齐、T14 综合评分 | T08 需 G1、T03/T04/T05；T09 需 G1/T08；T10 语义需 G2；T13 需 G3/T11/T12；T14 需 G1 |
| E 综合交付 | T17 全矩阵、迁移与客户验收 | 所有实际适用的 G/V 完成，或明确记录阻塞且不得总体验收通过 |

波次不是把所有任务串行：例如 T10 的 ready 修复可在 B 开始，T07 的往返修复不依赖 G2 的产品决定；但共享文件最终由同一集成者按依赖合并。

### 2.2 共享文件单一所有权

| 文件/目录 | 串行所有权顺序 |
|---|---|
| `backend/src/main/java/com/nip/service/general/GeneralTickerPatService.java` | 后端评分集成者：T02 → T03 → T04 → T08 |
| `backend/src/main/java/com/nip/service/general/GeneralKeyPatService.java` | 同上：T03/T04 → T08，避免覆盖既有 reset |
| `backend/src/main/java/com/nip/service/PostTelexPatTrainService.java` | T02 → T03 → T08 → T09 |
| `bw-frontend/frontend/src/views/manage/organization/handkeyZuXun/train/student/` | 输入集成者：T05 → T06/T07 → T03/T04 的提交接线 → T08 |
| `bw-frontend/frontend/src/views/manage/organization/electronKeyZuXun/train/student/` | 输入集成者：T05 → T03/T04 → T08/T10 |
| `bw-frontend/frontend/src/common/utils/voice/MorseVoiceHighPerformance.js`、`bw-frontend/frontend/public/processor.js`、`bw-frontend/frontend/src/common/utils/ElectronMorse.js` | 音频集成者独占T10；public为实际worklet源，核对src副本引用后收口；两模式产物分别验证 |
| `backend/src/main/java/com/nip/service/simulation/`、`backend/src/main/java/com/nip/ws/WebSocketSimulationService.java` | 网络集成者：T11 → T12 → T13 |
| `bw-frontend/frontend/src/components/BroadcastTeachTrain/` | 网络集成者：T12 → T13；不得同时改订阅和草稿处理 |
| `backend/database/migrations/`、`backend/scripts/rehearse-migrations.sh` | 迁移集成者为 T08/T09/T11 串行编号与演练，不让不同工作包覆盖同一迁移 |
| `bw-frontend/frontend/src/common/http/index.js` | T16 独占提示；T03 在调用方判 code，不重写全局返回/抛错协议 |

## 3. 工作包

### T00 取证与业务口径冻结（R01–R12）

- [x] 仓内环境、测试隔离、后端全量验证、Vue可复现安装/构建及Electron运行时核验完成；结果和边界见T00.1。此项不等于整个T00或两模式功能验收已完成。
- [ ] G4分别记录Web协议/origin/浏览器版本/静态根与反代、Electron壳/OS/本机或局域网配置及FE/BE/hash；记录串口实际传输与许可、授权存储、设备/采样率和schema。核实客户已报告的故障环境，不要求重复证明故障现象。
- [ ] 按 Spec G1 列每个活跃训练域/模式的单位、时间轴、规则满分/加扣、空/少/多页样例及原始 DTO 字段表；冻结 reset 后旧请求隔离方案、规则快照和存量进行中训练切换窗口。
- [ ] 按 G2 取得低速/划比/5与7间隔/校准样文、配置优先级、F2 与目标机延迟阈值；按 G3 取得实时草稿与漏多组对齐决定。回填 Spec §3，不另起竞争规格。
- [x] 已建立V01–V13验收登记台账（T00.2），逐项标记未实施、前置/局部已验证及G1–G4门禁；建立台账不等于验收通过。单token互踢不修的决定保持不变。

**出口**：确定性 bug 与产品变更清单分离；G1/G2/G3 的业务字段有责任人确认，G4 有真实环境记录或明确缺项。缺项只阻塞相关任务，不阻塞 T02 等确定性修复。

#### T00.1 仓内环境与测试基线（2026-09-10完成）

**验证对象**：`e5cbfec483b860895569324ef9debf655dc22c6e`。开始时存在用户已有的 `docs/README.md` 修改及三份Rust/WASM方向未跟踪文档，未纳入本次变更；未修改业务代码、测试、依赖版本、lockfile或全局npm配置。安装生成的node_modules及构建target/dist仅为本地验证产物。

| 检查 | 实际结果 | 证据/范围 |
|---|---|---|
| Java/Maven | Adoptium JDK21.0.12.1、Maven3.9.9 | 在backend显式设置JAVA_HOME后执行 `./mvnw -version`；JDK实际目录为 `/home/zhang/.local/opt/jdk-21.0.12.1+1` |
| Docker | 29.1.3，linux/x86_64，overlayfs；daemon可访问 | 未发现QUARKUS/TESTCONTAINERS/DOCKER/Maven相关环境覆盖变量；测试使用独立DevServices，不使用既有mysql-project006 |
| 测试数据库 | mysql:8.0，project006_test，动态端口32769 | 本轮启动日志显示 `jdbc:mysql://localhost:32769/project006_test`；测试HTTP端口18081，drop-and-create；结束后未发现仍运行的mysql:8.0测试容器 |
| 后端验证 | **BUILD SUCCESS，退出0；67个测试类、240 tests，0 failures / 0 errors / 0 skipped** | `JAVA_HOME=/home/zhang/.local/opt/jdk21 ./mvnw -B clean verify`；Surefire XML汇总与Maven总计一致，耗时2分31秒；产出 `backend/target/quarkus-app/quarkus-run.jar` |
| Node/npm | Node24.15.0、npm11.14.1 | 与CI的Node24主版本一致 |
| Vue依赖 | 可复现安装成功；已安装Vue3.5.42、Vite4.5.14 | 在bw-frontend/frontend执行 `npm ci --ignore-scripts --no-audit --no-fund`，npm日志exit0；依赖查询成功 |
| Vue生产构建 | **退出0；8225 modules transformed，built in 1m11s** | `npm run build`；产出 `bw-frontend/frontend/dist/index.html`，不等于真实Web反代或Electron安装包验收 |
| 实际worklet资源 | public与dist的processor.js SHA256一致 | `69370998a8779bdc25983e0dbca499b99b907a0d3a64667751c72827890e6fc1`；确认当前构建复制的是public源，未修其中逻辑 |
| Electron依赖/运行时 | 镜像安装退出0，414 packages；Electron30.5.1、electron-builder22.14.13 | 两个本地可执行文件 `--version` 均成功；npm ls确认版本。仅可执行性，不含实际应用窗口/串口/安装包 |

**命令及原始结果位置**：
- 后端受管日志 `t00-backend-baseline`；完整JUnit明细在 `backend/target/surefire-reports/TEST-*.xml`。默认pom的skipITs=true，因此Failsafe阶段出现“Tests are skipped”；上述240项是实际执行的JVM/Surefire测试，非native集成测试，不隐藏默认范围，也未额外传-DskipTests。
- Vue受管日志 `t00-frontend-baseline`；首次npm ci成功日志为 `/home/zhang/.npm/_logs/2026-09-10T10_05_42_701Z-debug-0.log`。dist/index.html SHA256为 `3e4bf928dd1930711fc691c8943644d0fc8b90baaa294c224742cec7fb55f465`。
- Electron最终安装受管日志 `t00-electron-deps-mirror`。镜像下载档案SHA256为 `ec4707783d39e86005f42899e30ae59e50dd5d9c7f28531ed494eb43f2361403`，与包内checksums.json及[官方v30.5.1校验清单](https://github.com/electron/electron/releases/download/v30.5.1/SHASUMS256.txt)一致；没有跳过下载、关闭校验或改用远端checksum覆盖。
- target/dist和npm日志会随清理覆盖；此处固定了源码提交、命令、汇总结果及关键hash，不以未来同路径内容冒充本次证据。

**中断与环境处置**：
1. 首轮异步进程中断，Maven fork收到SHUTDOWN；仅176项无失败的部分报告，不作为最终基线。已成功的Vue npm ci复用，其余未完成步骤重新执行，受管日志/退出码完整保留。
2. Electron默认GitHub二进制下载失败：`connect ETIMEDOUT 20.205.243.166:443`。失败安装清理了electron目录，随后一次 `npm rebuild electron` 虽退出0但无实际包，未计为成功。
3. 按[Electron官方镜像安装说明](https://www.electronjs.org/docs/latest/tutorial/installation)，仅对本次命令设置镜像后完整重装；保留原版本及内置校验。在bw-frontend执行：

```bash
ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/ \
ELECTRON_CUSTOM_DIR='{{ version }}' \
npm ci --foreground-scripts --no-audit --no-fund
./node_modules/.bin/electron --version
./node_modules/.bin/electron-builder --version
npm ls electron electron-builder --depth=0
```

**非阻塞警告与未验收范围**：
- Vue构建保留既有 `/deep/`/`>>>` 弃用、部分图片路径无法构建期解析、bluebird eval及大chunk警告；它们不导致本轮构建失败，不据此宣称运行页面无问题，也未顺手整改。
- Electron安装有旧依赖弃用警告；默认GitHub下载在本环境超时，后续清装需可达网络或上述命令级镜像。这是环境前置，不改锁定版本规避。
- 未运行native构建、迁移演练、Web真实HTTP/HTTPS反代、Electron实际应用窗口/Windows或Linux安装包、真实音频/串口、混合房间验收；没有发布或操作生产数据库。G4不能标为完成。
- G1评分/计时、G2节拍/配置、G3实时草稿/对齐决定未取得；对应任务继续受门禁约束。本结果只证明可进入确定性缺陷编码，不代表客户12项问题已解决。

**结论**：T00的“仓内环境/测试基线”通过（Electron安装有明确的网络前置）；240项测试及Vue构建构成后续变更对照，单token互踢与203/204/206契约未改。

#### T00.2 验收登记台账（已建立，持续更新）

登记依据：T00.1的仓内基线与T01.1的交付链证据（实现提交 `b8c215b`）。完整通过仍以Spec §8为准；240项既有测试全绿不自动关闭本轮新增契约。每项后续按W-HTTPS/W-HTTP/E-PACK及适用混合场景补执行提交、输入/实际结果、环境、日志/截图和责任角色；不能以一端的结果填写另一端。

| 验收 | 对应任务 | 已有证据与当前状态 | 仍需完成/门禁 |
|---|---|---|---|
| V01 凭证/授权 | T16 | 仓内完成（BE+W-HTTP+桌面首启）：203/204/206 提示分别解释、604800 秒阈值预警实测、桌面设备码含哨兵槽、机器级副本写失败如实报错不清库 | 真实多机互踢/长期凭证与可信存储环境属 G4 |
| V02 音频节拍 | T10 | 仓内完成（离线渲染矩阵）：44.1/48kHz × short/long/letter/mix × 常速/低速 6 组，边界误差 ≤1 sample、静音段严格为 0、进度事件顺序一致 | 真实声卡输出与按键到声音延迟属 G4（W-HTTPS/E-PACK） |
| V03 权威评分 | T02/T08/T14 | 仓内完成（BE+W-HTTP）：四域保存记录→speed/用时/扣分同源，页面单位与数值一致（§5.4）；客户端汇总篡改无效；组网冻结规则评分 14 例 | 客户历史数据对账属 G4 |
| V04 提交/幂等 | T03/T08 | 仓内完成：真实注入 code!=200、响应丢失、finish 拒绝均保留内容可重试；重试/乱序/reset 栅栏与末次写库失败整体回滚有回归 | 客户长断网与大并发规模未覆盖 |
| V05 倒计时 | T03/T09 | 仓内完成：窗口内补交入库、越界区间拒绝、无人 finish 由 5 秒扫描结算、结算后迟到页拒绝；新增锁等待跨窗口回归（双模式） | 服务重启矩阵仅隔离环境；客户端后台节流属 G4 |
| V06 有序采集 | T05/T07 | 仓内完成（模拟帧 + 真实壳 IPC）：同帧连续相同码按序落库为 `["1","1","2"]`；抖动/分包/未知字节按帧长恢复 | 真实串口硬件采样精度与时间戳属 G4 |
| V07 控制符 | T06 | 仓内完成：控制符优先识别、临时项只撤销自身、未知码保留 `#`、`legnth` 缺陷消除 | 真实长时手键操作属 G4 |
| V08 组网一致性 | T11/T12/T13/T14 | 仓内完成：并发首读同页一致、整份重填删旧尾页、事务中断恢复、事务后通知双教员 37ms/21ms、丢通知 4998ms 补偿、双向混合房间提交与对齐 | 多机规模/跨网段属 G4 |
| V09 学员详情 | T11/T12/T13 | 仓内完成：结束后教员页显示每人已提交明细与漏/多码对齐，三类房型详情按 roomId 绑定并校验成员 | 不含实时草稿（G3 决定）；客户场景属 G4 |
| V10 电子键响应 | T10 | 部分完成：换算入口统一、未 ready 明确禁用并可恢复提示、processor 样本累计修复 | 按键到声音延迟阈值需真实设备（G4） |
| V11 训练配置 | T07 | 仓内完成：毫秒往返不漂移、坏配置不清空旧值（6 例） | — |
| V12 双模式交付/题库 | T01/T15/T17 | 仓内完成（W-HTTP/E-PACK 制品层）：dist 与 asar 同源且含本轮改动；题库 XLSX/DOCX 往返、错误行回滚、跨库拒绝、建卷建考阅卷闭环 | 客户真实站点、安装器、云 CI、客户数据验收属 G4 |
| V13 学员授权归属 | T04 | 仓内完成：A/B 身份、非参训者、非教员均不能改他人记录；新 DTO 无旧身份参数 | 管理端角色授权（红线 6）为独立议题 |

**T00进展更新**：环境与台账已完成；用户已确认Spec §3.1的分域单位、有效采集、固定35拉间隔、结束明细对齐及60秒补交窗口。剩余为仓内可完成的DTO/规则版本/迁移设计，不再等待这些业务选择。G4按用户选择转后续真实现场验收；本轮须完成全部仓内代码与验证，不假称正式发布通过。

2026-09-11补充：用户确认General教员手动结束采用“学员收到通知时停止”，保留60秒补交与最终原子结算，不按教员HTTP请求时刻硬截断晚收到通知的合法最后页；倒计时仍严格按服务端deadline。需要先处理停止通知之前收到的输入队列，不能把status3当作丢弃队列的理由。



### T01 Web发布与Electron打包闭环（R12，P0）

**文件**：`.github/workflows/build-quarkus-native.yml`、`bw-frontend/package.json`、`bw-frontend/frontend/index.html`、`bw-frontend/frontend/vite.config.js`、`bw-frontend/electron/index.js` 及本次活跃地址消费点；只修交付/配置边界，不顺带升级依赖或重做全站部署架构。

- [x] release增frontend成功依赖，Web及三架构BE制品均带版本/SHA/hash清单；独立Git fixture已执行真实zip归档、8件完整集合验证及缺失/篡改/dirty/错误或空SHA拒绝。未触发云CI发布。
- [ ] Web分支按Spec §7.3发布本次dist到实际静态站点，冻结serverConfig配置来源；分别验证HTTP直连与HTTPS的/data、/push、/file代理映射、WS Upgrade、跨域预检。修本次活跃HTTP上传/文件地址在HTTPS下的混合内容，不把题库saveBatch误绑到文件服务。
- [x] 仓内Web配置已收口runtime-config.js，entry等待配置后导入业务；本地HTTP/HTTPS静态站点完成API/文件上传/WS代理与错误配置验证。此证据不替代上一项客户真实部署。
- [x] 核心入口资源已改为相对base可解析路径；实际生产dist在/app/下启动、hash路由入口、动态业务chunk、localforage和processor资源可加载，Web无IPC可到授权页；缺配置/HTTPS配HTTP可见失败。未承诺全部业务页面任意子路径均验收。
- [x] beforePack自动重建、清理、双次校验并复制frontend/dist至public/dist；Linux真实--dir包从/tmp启动成功，ASAR内核心资源与清单hash一致；构建失败注入不留下旧public/dist。
- [ ] 选定提交同时包含 `1c40aae`/`9596c6c`；G4、测试、模板往返，以及Web真实站点和Electron真实安装包各自smoke通过后再由负责人发布。旧打开Web页/旧壳不得在切换后继续写不兼容协议；当前文档会话不发布。

**验证**：V12分别留W-HTTPS/W-HTTP/E-PACK证据；frontend构建失败不得产正式release。Web核验站点资源hash和实际反代Network；Electron核验安装包资源hash及IPC配置，不能彼此替代，也不能用Vite开发页冒充部署态。

#### T01.1 仓内实现与验收记录（2026-09-10）

**边界**：基于 `9cabeef7bb39ea1f81610bcaf7dbcaa29f1a90a8` 上的T01工作区验证；本地制品如实标记sourceDirty=true，**不是可发布的clean候选包**。仓内源码/脚本已落地，未打tag、push、发布或改生产数据库。CI/helper、Web入口、桌面hook共用新清单契约，作为同一可回滚T01提交，避免拆出缺helper或缺runtime-config的中间状态。

**落地文件**：
- `.github/workflows/build-quarkus-native.yml`：release显式依赖frontend/build/test，只下载四个预期artifact；校验完成后才交8件获准资产给release action。保留原native矩阵/测试/glibc与runner smoke。
- `bw-frontend/scripts/artifact-manifest.cjs`：生成/验证前端与native清单、Web ZIP归档和发布集合；`bw-frontend/frontend/package.json`在Vite成功后生成清单。
- `bw-frontend/frontend/public/runtime-config.js`、`bw-frontend/frontend/src/config/runtime.js`、`bw-frontend/frontend/src/entry.js`与index.html：独立部署配置、顺序启动、协议正确的服务基础地址与静态资源；Axios继续复用endpoint helper。
- 课件useForm完整文件前缀读入/保存互逆；两处Paho连接使用mqttWsUrl完整URI，外部equipment://仍用mqttUrl裸主机；NetSetting端口边界与运行时一致。
- `bw-frontend/scripts/prepare-desktop.cjs`、桌面package.json与electron/index.js：强制新前端、manifest双校验、运行文件白名单、app.getAppPath绝对加载、加载失败明确退出。

| 验证 | 实际执行与结果 | 不能由此推导 |
|---|---|---|
| Web构建 | 实际npm run build成功；后续真实桌面beforePack再次执行完整Vite构建并生成清单 | 不是所有页面/外部服务联调 |
| Web HTTP | 实际dist经127.0.0.1:18800/app/#/login加载到授权页，无Electron桥、无未捕获页面错误；API GET到独立18801、文件POST到18802、WebSocket回显成功，token/deviceId在API探针中保留 | 探针后端仅回显路由/头，不是假冒已测Quarkus业务；localhost不替代远程HTTP能力限制验收 |
| Web HTTPS | 实际dist经18443/app/加载；/data、/file反代分别转发/api/t01-probe与/api/file/upload，/push完成真实WS升级/转发；默认地址全部HTTPS/WSS，mqttUrl保留裸主机 | 本地自签证书仅在隔离浏览器调试会话放行证书错误，未关闭CORS/webSecurity/混合内容策略；不等于客户可信证书验收 |
| 启动失败 | HTTPS页面配置HTTP、缺runtime-config文件都显示具体启动错误且app未挂载；真实Electron包移走自身bin/config.json后显示invoke拒绝，renderer响应正常，不再sendSync挂起 | 未改所有历史IPC调用和配置存储机制；不宣称全部错误已治理 |
| 默认端口边界 | 直接执行真实configureRuntime模块：显式example.invalid:80及[::1]:80不被改为18001/8000；显式完整URL保持其请求路径 | 不以URL格式正确代替服务可达 |
| 资源与课件 | HTTP/HTTPS实际请求processor返回200、text/javascript，SHA256=`69370998a8779bdc25983e0dbca499b99b907a0d3a64667751c72827890e6fc1`；可移植媒体前缀替换往返保持第三方URL | 本轮不修改processor节拍；课件完整编辑/保存业务仍需授权场景验收 |
| Electron真实包 | `npm run build-e-l -- --dir --config.electronDist=node_modules/electron/dist`成功；旧t01-obsolete.txt消失，源/目标manifest一致；从/tmp启动真实nip-traffic-system，file URL在app.asar/public/dist，IPC地址生效，到达授权页 | --dir不是DEB安装或Windows安装器验证；未改壳既有安全开关，使用现有--no-sandbox启动参数 |
| ASAR完整性 | 在真实Electron渲染器经fs读取ASAR内index.html、processor.js、runtime-config.js，三者hash均匹配包内manifest | hash清单不等于签名，也不是clean发布资格 |
| 打包失败 | 在隔离fixture调用真实beforePack，npm build故意退出7：hook抛错且删除旧public/dist，不继续打包 | 这是故障注入，不是假称真实Vite自然失败 |
| 发布CLI | 使用实际zip/unzip与真实helper，在独立clean Git fixture生成Web档案及三架构清单，完整集合输出8件；空/错SHA、dirty、Web篡改/未列文件、缺Windows制品、native篡改均拒绝 | native字节是fixture，仅验证清单/门禁；未编译三架构或跑云CI |

**工具与复审**：本机zip缺失，通过apt-get download并在临时目录解包官方Ubuntu zip工具后执行真实package-web CLI，未改系统安装。三个只读复审已完成；落实空SHA降级、异步IPC启动、HTTPS课件消费、MQTT URI与端口边界建议。另做隔离ZIP符号链接写目录实验，unzip返回2且未发生临时目录外写入；不扩大为全面归档安全审计结论。

**复现命令**（在对应工程目录）：

```bash
# bw-frontend/frontend：生成Web构建与manifest
npm run build
# 仓库根：开发工作区校验（保留dirty标记）
node bw-frontend/scripts/artifact-manifest.cjs verify bw-frontend/frontend/dist
# bw-frontend：真实目录包，使用T00已安装并校验的Electron发行版
npm run build-e-l -- --dir --config.electronDist=node_modules/electron/dist
```

**发布操作说明**：Web配置及前缀规则见Spec §7.4；clean checkout构建后执行package-web才能得到可发布档案。Web站点入口/配置需重验证缓存，配置覆盖另记hash；桌面重新打包自动重建前端。不要手改sourceDirty=false或SHA规避门禁，也不要把本地dirty包上传正式release。

**仍未完成/未关闭**：
- G4真实Web部署/可信TLS、客户授权后的业务、混合房间、真实串口/声音、Windows/DEB安装、GitHub Actions云执行，以及T15题库闭环。
- 本地目录包没有配套bin/server/server，启动日志出现既有ENOENT；前端IPC配置指向外置后端，仍能验证资源启动。单机交付必须准备对应native制品及配置，不能把该包标为单机验收通过。
- 桌面原有OCR地址ws://localhost:13300与fetch调用的协议冲突保留为外部服务契约风险；未捏造HTTP服务或实现假OCR。MQTT真实broker及/mqtt代理需部署确认，未配置时明确提示。
- 既有/deep/、资源路径、大chunk等构建警告未顺带治理；未改后端业务、评分、会话/鉴权码，也未重跑不受影响的Java套件冒充新证据，后端对照仍为T00的240项。

**结论**：T01仓内实现和可执行验证完成；发布复选框保持未勾选。临时故障配置已恢复，测试服务/浏览器和临时脚本在交付前清理，生成的dist/目录包留作本地检查产物。


### T02 确定性评分公式修复（R03/H3，P1）

**文件**：`backend/src/main/java/com/nip/service/PostTelexPatTrainService.java`、`backend/src/main/java/com/nip/service/PostTelegramTrainService.java`、`backend/src/main/java/com/nip/service/general/GeneralTickerPatService.java` 及受影响评分测试。

- [x] PostTelex trainType!=4以训练创建时捕获的满分起评；真实记录验证80扣7得73、100扣7得93，修改原规则及重复finish不改变已结算结果（ce3cc09）。
- [x] PostTelegram高于r加、低于l扣，复用ScoreMath；修正SpeedDeduct反向注释（6eb28ee）。
- [x] General手键划线cap取dash.max；完整保存/结算/报表回读验证划扣5、点扣1、错组扣3，满分150得141（e7c757f）。

**提交**：上面三项各自一个可回滚修复提交；每项带它保护的行为回归。**验证**：V03 中对应案例；既有工具函数测试不能代替 General 活跃结算。

**2026-09-11仓内证据**：`PostTelegramTrainScoreTest,PostTelexPatTrainServiceTest,PostTelexPatTrainScoreTest,GeneralTickerPatScoreTest,EntitySchemaSnapshotRehearsal`共12项，失败/错误/跳过均0。初次完整General结算因deduct_info默认varchar(255)真实失败；独立提交8454687扩容并对齐12个评分/原始载荷映射。迁移`backend/database/migrations/2026-09-10-02-scoring-json-capacity.sql`已接双快照演练：两侧105表、0 MyISAM、目标列容量正确、实体差分均空；证据`backend/database/rehearsal/2026-09-11-t02-scoring-json/`。没有操作生产库或重算历史成绩。全部工作包完成后仍须统一clean verify。

### T03 提交失败恢复与结束原子性（R04/R05/M1/M3，P1）

**文件**：postJob 数据报 `telexTrain.js`/`trainScore.js` 活跃调用树、电子键组训 `handKeyTrain.js`、相应后端 service；以 API 模块 `TelegramApi.js`、`electronKeyZuXun.js` 搜索实际消费者。

- [x] `useConfirmedSubmission.js` 提供待提交/提交中/页已确认/结算成功/可重试失败五态与 `saveSnapshot`；数据报与两类拍发页在 code!=200、reject、响应丢失、连点下保留正文/页号并可重试，`finally` 释放锁；鉴权失败按会话不一致中止而不跨用户重发。真实页面证据：`capture_failure_probe`（拦截首次上传→保留 `1234 5678`、当前第1页、出现重试）、`capture_response_loss`（服务端已落库但响应丢弃→仍在第2页且内容不变）、`capture_finish_failure`（finish 拒绝→停在第3页可重试）。
- [x] 逐页用时改为服务端 `captureIntervals` 派生（`CaptureTimeline`），三页实测 613ms/114256ms/82110ms 不再出现累计相减错值；倒计时跨零只触发一次由 T09 的 deadline 恢复覆盖。
- [x] upload/finish/reset 全部走父行 `lockedTrain` + `attempt` 栅栏；`GeneralCaptureContractTest.retriesAndOutOfOrderDeliveryDoNotDoubleCountAndInvalidTimelinesDoNotReplaceSavedPages`、`resetFencesLateUploadFinishResetAndStartWithoutChangingNewAttempt`（双模式）覆盖并发重复结算与 reset 后迟到页。
- [x] 结束以 DB 已保存参训记录为准：`closingAllowsNotificationDelayedTailThenSettlesOfflineAndAbsentStudents`（离线/无页学员仍结算）、`finalRoomWriteFailureRollsBackEveryStudentsSettlementAndRetryClosesRoom`（末次写库失败整体回滚、重试后关闭房间）。

**验证**：V04、V05 客户端部分，尤其“服务器已提交但客户端未收到响应”的重试；对数据行/成绩不变与用户可继续操作断言。按数据报恢复、电子键锁、后端结束各自提交，契约相关修改保持同提交。

### T04 学员身份与内部结算切换（H4/H5/M4，P1）

**文件**：`backend/src/main/java/com/nip/controller/general/GeneralTickerPatController.java`、`backend/src/main/java/com/nip/controller/general/GeneralKeyPatController.java`、对应 DTO/service、`handkeyZuXun.js`/`electronKeyZuXun.js` 与调用页。

- [x] 手键 upload/finish/reset 与电子键 finish 均以 `@RestHeader(TOKEN)` 主体定位学员并校验参训与可写状态，DTO 不再接收身份参数；`GeneralCaptureContractTest.tokenIdentitySeparatesStudentsAndRejectsOutsidersAndNonTeachers`（双模式）断言 A/B 身份、非参训者、非教员均被拒。
- [x] 教员结束授权与内部按用户结算分开：`GeneralTickerSocketController.updateTrainStatus` 走 token 授权，内部 `settleExpired`/`countScore` 按 DB 参训逐人结算；socket 只读接口 `getByTrainIdAndUserId` 保留 `userId` 作为展示查询，不写任何成绩。
- [x] 手键 finish 的 `validTime`/`finishInfo` 冗余字段已移除，所需时序走逐页 `captureIntervals` 正式契约（见 §5.4 与 Spec §4.3.1）。

**验证**：V13；A/B 身份、非参训者、未授权教员不能改变目标数据；授权教员批量结束及正常学生继续有效。

### T05 手键/电子键有序事件（R06/H1/M2，P1）

**文件**：`bw-frontend/frontend/src/common/utils/WebSerial.js`，手键/电子键组训学生的 useControl/student/handKeyTrain，及复用生产者的活跃 preJob/postJob 入口。

- [x] `WebSerial.js:74-98` 按帧长逐项解包并对每个码发独立冻结事件（含 `receivedAt`/`timeSource`），不再用单个 ref 当事件队列；真实打包 Electron 注入同帧 `[12,44,21,21,22]`（含连续相同码）后页面按序显示 `1 1 2`，上传载荷 `value:["1","1","2"]`。
- [x] 点/划阈值取当前有效自校准，≤10ms 抖动剔除；重复按下/缺抬起/分包粘包按帧长边界恢复，未知字节告警后跳过而不静默丢弃。
- [x] 硬件无时间戳时统一标记 `timeSource:'js-receive'` 的单调接收时刻；`useTrainingCapture` 在离页/换轮次清理区间与队列。
- [x] `MessageWebSocket.js` 的串口入口与可见性过滤按“通知到达即停止新采集、先消费已入队事件”处理；`recordQueued` 把停用期间到达的事件并入采集区间，不静默丢原始帧。Web（无串口能力）与 Electron 真实 IPC 数据通道分别记录。

**验证**：V06；真实串口帧与同 tick 注入分开记录。无硬件仅能证明事件消费，不关闭客户采样精度问题。

### T06 手键控制符与翻页（R07，P1）

**文件**：手键组训 `handKeyTrain.js`、`bw-frontend/frontend/src/views/manage/postJob/telegram/train/js/details.js` 和其控制符共享定义；依赖 T05 的事件语义。

- [x] 句号/当前组改错/前组改错优先识别并跟踪控制符产生的临时项，完成时只撤销这些项；原 `legnth` 拼写缺陷已消除（全仓无 `legnth`）。
- [x] 普通未知码保留 `#` 并计入正文；控制符按完整/分片边界处理，干净三组不再固定删 3。

**验证**：V07，断言实际正文、组/页位置及控制行为；不能只断言字符串里没有 #。

### T07 训练设置往返与基础分级边界（R11，P2）

**文件**：preJob/telegram/handkey的telegram.js、preJob/telegram/train的HandKeyTrain.vue/basicTrain.js、`backend/src/main/java/com/nip/service/TelegramTrainService.java`。postJob保持现有纯自校准，不新增配置入口。

- [x] `telegramSettings.js` 统一毫秒↔比例换算，修掉 interval/gap 多减 1；`TelegramTrainSettingTest.startingAgainPreservesSavedMillisecondBoundaries` 断言保存→加载→再保存毫秒不漂移。
- [x] 2026-09-11核对postJob实体、创建页和训练消费，当前无持久化配置或固定模式；用户选择“保留纯自校准”，撤销原加载本域配置要求，不新增字段/控件。
- [x] 基础练习区间分级保留并全量校验后替换：`TelegramTrainSettingTest` 的 `nonFiniteBoundaryPreservesExistingSettings`、`missingAbnormalIntervalPreservesExistingSettings`、`noPositiveIntervalPreservesExistingSettings`、`malformedLastRowPreservesExistingSettings`、`validReplacementKeepsFractionalBoundsAndAllGrades` 覆盖坏配置不清空旧值。

**验证**：V11 和 V06 的校准边界；往返修复与基础配置校验分别提交。

### T08 原始记录驱动权威评分（R03/R04/H2/M3/M4/M5，P1，需 G1）

- [x] 字段表已在 Spec §4.3.1 冻结（protocolVersion/attempt/captureIntervals/serverElapsedMs/receivedAt/rateUnit），各域按保存记录→speed/用时/扣分可手工验算：手键 4 字符/60000ms→4 字符/分、电子键 4 字符/60000ms→1 四码组/分，实测与页面一致（§5.4 表）。
- [x] 逐页 DTO、存储、计算、API 与成绩页同批切换；客户端 speed/errorNumber/accuracy/totalSpeed 不再作为评分输入（`replacementRetainsCaptureTimeAndFrozenRuleWithoutAccumulatingOldBody` 篡改 `speed:99999` 仍得 60）；`receivedAt` 仅用于边界校验。
- [x] 页替换不累加 speedLog（结算按已保存页重算 `pageRates`）；reset 递增 attempt 并清本轮派生状态、拒绝旧在途页；规则冻结与容量迁移随 `2026-09-10-02-scoring-json-capacity.sql`、`2026-09-11-0*-capture*.sql` 同步，历史成绩不重算。
- [x] GeneralKey 固定/懒生成页 value 形态一致（`GeneralKeyPatResetTest.resetClearsOnlyCurrentUsersResultsAndKeepsGeneratedPages`），旧算法分支已删除，不保留双算法兜底。

**验证**：V03/V04；客户端 speed/总分篡改、重复/乱序页、reset 后迟到请求、缺规则、非100满分与三页计时。定向单测之后必须从真实提交接口到数据库/详情回读完整结算。

### T09 服务端倒计时恢复（R05，P1，需 G1/T08）

- [x] 截止状态按真实 begin/恢复持久化（`deadline`/`pausedAt`/`serverElapsedMs`），不再以创建时间假定已开始；`PostTelexPatTrainServiceTest` 与实测 `capture_late_allowed`/`capture_late_rejected` 证明窗口内补交入库、越界区间被拒（code 202「采集区间必须有序、非负且位于训练有效时间内」）。
- [x] `PostTelexPatTrainRecovery`、`GeneralSettlementRecovery` 均为 `@Scheduled(every="5s", SKIP)` + `StartupEvent` 恢复入口，复用同一互斥幂等结算；实测「浏览器不发 finish」的训练由扫描结算为 status=3、score=70、`validTimeLog=[1]`，结算后迟到页返回 202「训练已完成，不能修改」。
- [x] schema/DAO/截止处理与回归同批：迁移 `2026-09-11-03-post-telex-capture-clock.sql`、`2026-09-11-04-general-capture-clock.sql` 已接入双快照演练（证据 `backend/database/rehearsal/2026-09-11-customer-integrated-v1/`，两侧实体差分为空）；结算失败保留待重试状态而非「已结束无结果」。

**验证**：V05 后端部分；停止浏览器不发 finish、并发手动 finish、服务重启、持久化/结算故障恢复。不能只断言字段存在或定时方法被调用。

### T10 音频码速与响应（R02/R10，P2）

**文件**：Spec §5.4 列出的音频工具，postJob/preJob 收报及电子键 examTrain/组训训练设置消费点。

- [x] 参数在 ready 前缓存并每场重置；Web 需用户手势、Electron mounted 初始化分别验证，受限/未 ready 时禁用有声训练并给可恢复提示（真实页面出现「点击启用音频（点击重试）」「音频尚未就绪，请点击启用或恢复音频后重试」）。
- [x] 换算入口统一到 `MorseVoiceHighPerformance.calculateTiming`（字符/四码组/WPM + 低速 35 字符/分固定符号速度、拉长间隔），postJob/组训速度跟随恢复，preJob 未回归。
- [x] 实际运行资源 `public/processor.js` 改为样本累计 + 余数（`sampleRemainder`，`processor.js:89-96`），热路径无日志；实测 6 组矩阵（44.1/48kHz × short/long/letter/mix × 常速/低速）渲染样本与理论值最大偏差 ≤1 sample、进度事件顺序一致、静音段严格为 0。

**验证**：V02/V10在W-HTTPS与E-PACK分别测采样/真实声音及按键延迟；W-HTTP明确音频能力限制，不能伪报ready。覆盖浏览器用户手势/后台节流与壳最小化恢复；每份结果记录实际processor URL/hash。参数ready、采样时钟、入口跟随可分子提交，共享契约同提交。

### T11 simulation 报底与答案幂等（R08/R09，P2）

**文件**：`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomService.java`、`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomContentService.java`、对应 Page/PageValue DAO/entity、migrations/rehearsal。

- [x] 迁移 `2026-09-11-01-simulation-page-uniqueness.sql` 先清点 null/重复键：实测 9 类负例（null page/answer key、完全重复、冲突标准/答案、错索引）全部在 ALTER 前拒绝且数据保留；清理后唯一键各列 NOT NULL。
- [x] 有界懒生成锁房间 DB 行并在锁内重查整页，唯一键 room/page/sort：`SimulationPagePersistenceTest.concurrentFirstReadsWithOldSnapshotsReturnOneIdenticalCompletePage`、`missingCablePageIsNotReplacedWithRandomStandardContent`。
- [x] uploadResult 保持整份数组语义并按 token 归属在事务内替换该用户页集合、删旧尾页：`completeRetriesReplaceOnlyAuthenticatedUsersPagesAndRemoveOldTail`、`onlyEnrolledReceiversCanWriteAndMalformedRequestsLeaveSavedAnswersUntouched`、`databaseFailureAfterReplacementStartsRestoresAnswersAndSubmissionState`。
- [x] 实体/迁移/脚本已接入 `rehearse-migrations.sh` 显式列表并扩展断言，双快照差分为空。

**验证**：V08/V09 数据部分；并发首读同页、重复完整提交、三页改两页、两个学员隔离、事务中断恢复。两种数据不变量分别提交，不让 schema 与消费者分离。

### T12 simulation 事务后通知与可恢复详情（R08/R09，P2）

- [x] 各房型统一走现有 SocketConnection；心跳不进业务 JSON 解析，结束态仍建连，卸载/手动关闭停止重连（`WebSocketHeartbeatTest`、`WebSocketSimulationTest.malformedMessageReturnsProtocolErrorAndKeepsParticipantConnected`）。
- [x] REST 提交在事务提交后由 `SimulationResultNotifier`（`AFTER_SUCCESS`）向房间全体教员发身份完整的轻量通知，客户端不再后置写状态；`committedResultReachesBothTeachersButRollbackDoesNotNotify`、`clientResultFrameCannotSubmitOrNotifyTeachers` 覆盖。实测两教员收到同一提交的时延 37ms/21ms。
- [x] 首进/通知/重连均拉 REST 快照，`useSimulationRecovery.js` 以 5 秒一个有界在途请求兜底；实测丢弃通知后 4998ms 内补偿渲染，结果齐全即停止轮询。

**验证**：V08/V09；Web教员+Electron学员、Electron教员+Web学员两种混合房间及各模式独立场景均覆盖。两教员2秒内更新、丢通知10秒内补偿；服务重启、结束后刷新/重连正常。网络时限仅对Spec所列可达同局域网矩阵生效，不为不同壳另造协议。

### T13 结束明细可见性与对齐（R08/R09，P2，G3已确认）

- [x] 结束明细按有限页内对齐（`resultAlignment.js`），超出报底显式标注，不新增 simulation 数值评分；实测混合房间双向提交后教员页显示 `上行报底 / 下行答案 · − 漏码（删除） · + 多码（插入） · → 替换` 与逐组差异。
- [x] 用户已明确不新增实时草稿；只完成现有已提交结果的可见性与对齐，不实现草稿协议。
- [x] 三类教员详情入口（通播/报务/干扰）均按 roomId 绑定并校验成员归属：`SimulationRoomDetailParamTest` 四例（含 `answerReadsRespectRoomMembershipAndRouterSenderIsNotTeacher`）。

**验证**：V09和V08对齐部分；结束后新进入/刷新/重连可见同一已提交内容，正确展示漏/多码；无权者不可读，不因只做结束明细而遗漏现有查看问题。

### T14 综合组网服务端评分（R08，P2，需 G1）

**文件**：`backend/src/main/java/com/nip/service/GroupNetTrainService.java`、`backend/src/main/java/com/nip/controller/GroupNetTrainController.java`、`bw-frontend/frontend/src/common/api/TrainingDetails.js` 及其真实 trainingDetails 消费页。

- [x] 分数由服务端题目、冻结规则与答案计算（`GroupNetScoring.freeze/calculate`，含 deviceId 归属与版本校验），客户端自报总分不再作为权威：`GroupNetScoringApiTest.apiDatabaseAndDetailUseFrozenDeviceRuleAndDoNotAcceptForgedOrRepeatedScores`。
- [x] 参数与响应消费者同批切换，重复提交结果确定（`retriesCompareDecimalNumbersExactlyWithoutDependingOnJsonMemberOrder`、`completeZeroRulePersistsACompletedZeroAndSameAnswerRetryRemainsStable`）；历史已完成成绩不变（`historicalCompletedScoresStayUnchangedAndUnfrozenUnfinishedTrainingCannotBeScored`）。迁移 `2026-09-11-01-group-net-scoring.sql` 已接演练。

**验证**：V03/V08 综合组网部分；篡改自报分无效，同规则答案同分、规则归属正确，前端详情使用后端结果。

### T15 题库模板与理论测试操作（R12，P0/P2）

**文件**：`bw-frontend/frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js`、同目录 `questionImport.js`；必要时同步 `backend/src/main/java/com/nip/service/TheoryKnowledgeQuestionService.java` 列说明，不改 saveBatch 返回能力。

- [x] 模板数据表预填当前二级节点ID，中文说明/示例独立成页；空levelId用点击上传时的目标，显式其它题库拒绝；读取中切换节点不会改目标。
- [x] 规范选择答案/选项并给出行号错误，拒绝NaN等非法索引；DOCX填空用显式JSON标记保持多空与字面分隔符，普通方括号文本仍按文本读取。Web与真实Electron包均完成实际文件上传/下载往返。
- [x] 按真实菜单/源码记录下方操作指引；隔离后端执行建卷建考，学员和教员分别在真实页面完成作答/交卷/阅卷与成绩回读。客户培训/客户数据验收仍属G4，不冒充已完成。

#### T15.1 实施与证据（2026-09-10）

- **前置推送**：按用户要求先运行后端clean verify（240项，0失败/错误/跳过，2分13秒），再推送main：`bf5b3f8..4819227`。未顺带提交用户已有README/Rust/WASM文档。
- **前端**：questionImport.js、knowledgeTabel.js和6个node:test回归；CI在前端build前运行test:question-import，不引入测试框架或依赖版本变更。数据页仅含真实待填字段及目标ID，示例不会被导入；所有有效行先解析完成，再一次调用saveBatch。
- **复现与修复**：修前非法选择答案会持久化字符串NaN、XLSX可导向其它bank；真实DOCX导出/导入把两个填空答案压成一个“甲；乙”。修后拒绝前两者，导出“答案（JSON）：”保留数组，普通“答案：[A-Z]”或“[0,1]”仍是文本，不按首字符猜JSON。
- **真实文件证据**：浏览器点击导出的XLSX含“题库模板/填写说明”两页，数据页topic为空、levelId为当前bank。填写五题型后实际页面上传，后端回读所有题型及答案；DOCX往返保留 `["甲；乙","[0,1]"]`。Electron真实ASAR包指向隔离服务，完成B库模板下载/XLSX填回、DOCX导出再导入A库，数字option.value已规范为与答案匹配的字符串。
- **失败与竞态**：实际页面上传第3行非法答案，合法第2行也未入库；显式其它bank的文件在两个库均无新增；暂停真实File.arrayBuffer后切换A→B，释放读取，记录仍进入A且页面仍选B。直接调用真实saveBatch使第二行缺levelId，返回HTTP200/code202且第一行回滚。
- **环境隔离**：新MySQL库project006_t15、独立loopback Quarkus18316、Web18415、合成管理员/学员与题库；Web学员使用独立浏览器上下文，Electron只修改生成包内配置并在结束后恢复。使用有效合成授权/正常JWT路径，未修改授权门闸或复用客户凭据。
- **新增阻塞修复**：真实建考因single_choice_list容量不足返回500，单题快照长度为256。实体缺省varchar(255)，但current/base快照的五个试卷列表及学员content本已为longtext。已单独提交 `d7e1678`，补六列映射、可重复迁移与容量回归，不扩展单题表原有字段能力。定向8项通过；双快照各105表、InnoDB、六列longtext、实体差分均空，证据在 `backend/database/rehearsal/t15-theory-json-a2d49536/`。
- **恢复证明**：在同一隔离库执行真实迁移两次后，原建考请求成功；导入题目进入真实试卷/考核，学员页面选A并“交卷退出”，教员页面“提交分数/结束阅卷”，数据库及学员成绩接口读回10分、state4。
- **界限**：建卷/建考使用与页面相同的真实API载荷，操作页面/按钮通过源码核对；作答、交卷、阅卷与文件往返直接操作真实页面。未声称客户已掌握操作、Windows/DEB/可信HTTPS已验收，或全部题型自动评分正确。自测不是本次运行演示路径；遗留单题字段长度等其它能力不通过截断内容规避。
- **收口验证**：最终后端clean verify为241项、0失败/错误/跳过（2分7秒）；前端6项定向回归全绿并接入CI，生产构建与真实Electron目录包构建成功。包内为带dirty标记的本地验证制品，不冒充clean发布包；所有测试服务、合成数据库/授权profile与临时文件在交付前清理，生成包配置恢复。

#### T15.2 理论题库与测试操作指引

1. 使用具备现有教员/监考菜单的账号登录；先确认授权和网络配置。Web与Electron操作相同，但各自的授权存储/服务地址按T01配置。
2. 进入“基础理论→理论测试→题库”。展开一级分类，选择具体二级知识节点；“知识总览”或一级节点不能作为本次导入目标。
3. 点击“导出模板”。只在第一个“题库模板”工作表填题，第二页“填写说明”提供中文名称和示例，不会导入。不要把机器字段头改成中文。
4. type为1单选、2多选、3判断、4填空、5简答；options示例 `["A.甲","B.乙"]`，单选answer填A或0，多选填AB或0,1，判断填对/错或1/2。填空以$_$标空位，多空答案使用JSON数组。levelId已预填，后续行可空；不要手抄其它节点ID。
5. 点击“上传题库”，选DOCX或XLSX（沿用10MiB上限）。导入目标在开始读取文件时固定；失败按行号修复后再导入，成功后查看列表或导出核对。网络结果不明时先查询，不直接重复提交。
6. “导出题库”生成DOCX。常规章节为“一、单项选择题/二、不定项选择题/三、判断题/四、填空题/五、简答题”，题号用1、或1.；普通答案写“答案：…”。系统导出的填空数组带“答案（JSON）：”，请保留该标记；手工多空可用中文分号分隔，字面方括号不自动解释为JSON。
7. 进入“试卷→新建试卷”，填写考卷名称、考卷节点；在各题型旁“选择”图标打开题库选题，设每题分值和及格分比，总分自动汇总，点击“提交”。本次演示以导入的单选题建10分试卷。
8. 进入“测试→测试列表→新增测试”，填写考核名称、时长、开始时间、监考人，选中学员和试卷卡片再“提交”。卡片的“预览”不等于选中试卷。
9. 教员在测试列表点击“开始考试”；学员须等开始后进入。学员作答后点击“交卷退出”，不是直接关窗口。教员结束考试后到“评分列表→开始评分”，逐人检查，必要时给简答题人工分数，点击“提交分数”，最后“结束阅卷”。
10. 学员在评分列表查看已阅卷结果/详情。正常考核状态为1未开始→2进行中→3已结束→4已阅卷；不要把“自测列表→新增自测→交卷评分”当成教员分配的考核。

定位入口：题库 `/preview/basicTheoretical/theoryTestMenu/questionBank`；试卷编辑 `.../paperBank/theNewTest`；测试列表 `.../theoryTest/theoryTestList`，新增 `.../theoryTest/addTest`，开考 `.../theoryTest/startTest?id=...`，阅卷 `.../theoryTest/startGrade?id=...&state=3`。前缀来自当前菜单配置，不应硬编码到业务接口。

**本步验收结论**：T15仓内实现、真实文件与理论操作链验证通过；正式客户验收继续在V12/G4/T17跟踪。新增实体映射部署前同步 `backend/database/migrations/2026-09-10-01-theory-json-capacity.sql`；迁移后回退应用不得自动把longtext缩回varchar(255)。

### T16 设备身份与授权解释（R01，P3）

**文件**：`bw-frontend/frontend/src/views/manage/login/useLogin.js`、`bw-frontend/frontend/src/common/http/index.js`、`bw-frontend/frontend/src/common/utils/machineCode.js`、`bw-frontend/frontend/src/common/utils/VerifyLicense.js` 及实际提示组件。

- [x] 203/204/206 提示按 `http/index.js:47-64` 的 `authMessages` 解释，后端码值与文案未改；206 只说明「可能在其他位置登录、已退出或会话记录已变更；无法仅凭此响应确定原因」。
- [x] 设备标识稳定化只在登录路径使用，不替换有效会话 ID：`machineCode.js` 对 Electron 走硬件 4 槽因子 + 阈值容差，Web 按 profile+origin 保存并显式声明不跨清数据/换 origin 稳定；实测真实打包桌面首启设备码为 `13f1-fcbe-0000-0000-2a4b-e6ca-0000-0000`（未采到的槽位为哨兵而非伪造值），机器级副本写失败时如实报 `EACCES` 且不清除既有授权。
- [x] 剩余累计可运行时长按 604800 秒阈值预警（`VerifyLicense.js:19`），实测页面常驻「离线授权剩余累计可运行 117.x 小时，请联系管理员续发（不是自然日）」；存储错误、设备不匹配、耗尽分别走 `storage_error`/`hardware_error`/`unauthorized` 分支，保留自恢复。

**验证**：V01；安全 token/TTL/存储迁移仍引用既有计划的门禁，不以本任务宣布防重放完成。

### T17 仓内综合验收与现场移交（全部 R 项）

- [x] 全量门禁按本轮实际执行记录：`backend` `./mvnw -B clean verify` **316 项、0 失败/错误/跳过**（2026-09-12，74 个 suite）；前端 `npm run build` 成功并生成 manifest；前端定向回归 `npm run test:question-import` 6 项全绿；真实桌面 `build-e-l --dir` 打包成功。不复用历史 216/238/240/314 等数字。
- [x] V01–V13 登记见 T00.2 台账与 §5.4/§5.5；BE 共享证据标 BE，W-HTTP 为本轮真实浏览器页面证据，W-HTTPS 与 E-PACK 的 UI/音频/串口/授权仍分别标未验收，未用一种模式顶替另一种。
- [x] 12 条与 H1–H5/M1–M5 的逐项终态见 §5.5 收口表（含剩余限制与责任角色）；G3「不新增实时草稿」为用户明确决定并已登记。
- [x] 本轮过时字段/临时脚本已清理：路由排查探针已从 `guards.js` 移除（全仓无 `__routeProbe`）、`details.js` 遗留 `console.log` 已删、隔离数据库容器/静态服务/临时目录已销毁、打包壳 `bin/config.json` 已还原；操作说明与迁移前置随 T15.2、§4.2 更新；未删除无关 `teacherBack` 历史代码。
- [ ] 发布负责人按已验收双端清单交付并记录安装/回滚检查：仍待 G4（真实部署、可信证书、安装器、客户硬件与正式发布），本计划因此保持未完成。

## 4. 命令、迁移与回滚

### 4.1 执行时验证命令（本次基线实跑范围见T00.1）

所有 Maven 命令在 `backend/`，显式 Java21；需要 Docker。按当前实际类名运行受影响测试，新增有价值的回归纳入对应工作包。

```bash
export JAVA_HOME="$HOME/.local/opt/jdk21"
./mvnw -B -Dtest=PostTelexPatTrainScoreTest,PostTelegramTrainScoreTest,ScoringConsistencyTest test
./mvnw -B -Dtest=TheoryKnowledgeUploadExportTest test
./mvnw -B clean verify
```

前端在 `bw-frontend/frontend/`，沿用 CI 的安装/构建方式：

```bash
npm ci --ignore-scripts --no-audit --no-fund
npm run build
```

**Web发布验证独立于下面Electron命令**：将此构建的dist交付静态服务器，按G4确定的域名、根路径及反代部署；不臆造仓外nginx配置或部署命令。浏览器访问实际站点执行W-HTTPS/W-HTTP矩阵，确认/data/api请求、/push WS握手及文件服务实际路由、入口/动态资源/processor.js的状态与MIME、无活跃混合内容，并验证旧缓存更新和hash路由深链刷新。普通远程HTTP不能用localhost的安全上下文例外替代完整串口/AudioWorklet要求。

桌面在 `bw-frontend/` 的对应目标 OS 执行：

```bash
npm ci
npm run build-f
# T01 打包入口应已把本次 frontend/dist 纳入 public/dist；未闭环不得继续发布。
# Windows:
npm run build-e-w
# Linux:
npm run build-e-l
```

Windows/Linux 是各自目标矩阵，不要求在同一主机依次执行；Electron 安装脚本需要正常下载运行时，不能照搬前端的 ignore-scripts。Node 版本与当前 CI（24）及目标打包依赖一起记录。

### 4.2 迁移演练

新增迁移必须接入 `backend/scripts/rehearse-migrations.sh` 显式列表，并扩展其 schema/唯一键断言。导出实体 schema 后在 `backend/` 运行：

```bash
export JAVA_HOME="$HOME/.local/opt/jdk21"
./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test
REHEARSAL_OUT_NAME=customer-issue-fix ./scripts/rehearse-migrations.sh
```

重跑换唯一证据目录名，不覆盖旧证据。脚本拒绝外部 DB_HOST/JDBC_URL，不能改成直接连生产；schema 演练不证明真实重复数据已裁决，T11 另需隔离库数据案例。

### 4.3 切换与回滚

- 上线前暂停相关训练写入，按G1处理进行中训练；备份/迁移后同时发布兼容Web dist、Electron安装包与BE。Web强制更新旧打开页面/缓存的处置、旧壳升级或拒绝旧协议均纳入窗口；仅替换服务器静态文件不能保证所有标签页已更新。
- 唯一约束/清理前确认保留记录清单，冲突不自动选赢家。回滚代码不等于恢复被去重数据；涉及数据处理须同时有备份恢复演练和停写窗口。
- 回滚时恢复上一对兼容 FE/BE 及匹配 schema/数据；不得只退后端而让新 DTO 客户端继续写。已产生新协议数据时先停写导出恢复点，按演练方案处理，不盲目 drop 列/索引。
- 每个独立修复提交可回滚，但共享契约与其迁移/调用者必须整体回退；发布说明列明不兼容边界。

## 5. 证据登记与三文档复审

### 5.1 实施记录格式

执行后追加：`任务/需求 | 模式W-HTTPS/W-HTTP/E-PACK | commit | origin/浏览器或壳版本 | 实际输入结果 | 能力/许可/processor hash | 测试/日志/截图路径 | 验收状态 | 未满足门禁及责任角色`。服务端共享证据明确标BE，不能自动填满所有前端模式。

**当前已有T00.1基线和T01.1仓内交付实现证据；正式发布及其余业务整改仍待完成。** 后续两轮文档复审是历史取证，不能据此把V01–V13或客户验收全部标为通过。

### 5.2 本次三文档交叉复审

- 核对范围：分析的事实/推断、Spec 的全量追溯/边界、Plan 的覆盖/依赖/验证/回滚；文档通过不代表业务验收通过。
- **首轮结论：PASS（规划基线）**。后续用户指出双模式覆盖不足，增量补审见§5.3；不能用首轮通过替代新增Web专项。G1评分/计时、G2节拍/配置、G3实时草稿/对齐、G4现场交付仍是对应实施/验收门禁。
- **复审修正**：分析中低速35与F2缩窗的无条件建议改为G2先决；C5从“单点投递”改为“通知内容不一致”；Spec/Plan补唯一键各列NOT NULL，避免只加UNIQUE仍允许多个空键；确认整份答案删除旧尾页、事务后通知、两类配置隔离及前端制品门禁一致。
- **首轮结构验证**：脚本核对22个追溯项、18个任务、13组验收、4项门禁；当时8个相对链接与45个源码路径均存在，任务/验收引用无悬空、代码围栏成对、实施复选框待执行。双模式补充后的结构验证见§5.3。
- **行为取证**：运行真实 parseSpreadsheetRows，模板的非空levelId说明文字覆盖选中题库（correctlyUsesSelectedBank=false）；算术反例确认三页错误用时100/60/140。它们证明修订依据，不代表问题已修复。
- **验证限制**：三路补充核查服务503失败，未取得独立子代理审查报告；最终复审由主评审直接完成。只改三份文档，未运行Java全量测试、前端构建、真实音频/串口/客户安装验收，也未发布或迁移数据库。

### 5.3 Web / Electron增量复审

- **范围修正**：Web与Electron均为正式模式；新增Spec §7.3，落实到T00/T01/T05/T10/T15/T16/T17和原有V矩阵，不复制业务实现、不新增竞争会话方案。
- **关键事实更正**：App两模式都有授权；Web仅IndexedDB，Electron有文件副本；Web音频需用户手势；HTTPS初始化仍生成HTTP上传地址；实际worklet是public/processor.js且包含console.log，先前“不存在”结论查错副本；上游visible过滤也可能丢采集帧。
- **已运行取证**：在隔离对象中执行真实index.html初始化脚本，分别输入Web HTTP、Web HTTPS、Electron file三种环境；得到直连18001、/data+/push反代、IPC地址三种结果，HTTPS场景uploadFileUrl仍为HTTP。另用SHA256比较public与src两份processor确认不同。这不是浏览器Network/真实安装验证。
- **结论：PASS（双模式规划基线）**。交叉复审覆盖模式边界、任务所有权、两种交付/回滚、原始输入/实际worklet和混合房间。结构检查通过：22个追溯项、18个任务、13组验收、8个相对文档链接、50个去重源码路径；W-HTTPS/W-HTTP/E-PACK及两种混合教员/学员组合均有要求，引用无悬空、代码围栏成对、实施复选框仍全部待执行。
- **验证边界**：本次只补三份文档，未改业务代码或生成临时仓库文件；初始化脚本取证不等于浏览器Network、真实串口/音频或安装验收。两模式与混合房间的功能验收仍待实施，不声称已联调通过。

### 5.4 码率口径与成绩页收口（2026-09-11，BE + W-HTTP）

**落地改动**（各自可独立回滚）：

- 后端：`PostTelegramTrainService.detail` 对 `protocolVersion=1` 返回 `pageAnalyzeVOS`（逐页 `pageNumber`/`patNumber`/`totalTime`，字符数复用结算 `countCharacters`，毫秒来自 `CaptureTimeline` 与 `captureIntervals`）；`PostTelegraphKeyPatTrainService.details` 新旧两个统计分支补 `setPageNumber`；`GeneralKeyPatService.patDetail` 回传父训练 `protocolVersion`；`GeneralKeyPatService.countScore` 始终输出 `speedScore`；`GeneralTickerPatTrainUserValueDao.countByTrainIdAndUserIdGroupByPageNumber` 补 `order by floorNumber`，避免 `speedLog` 与页号错位。
- 后端：General 两域 `uploadResult` 在取父行锁之前固定 `receivedAt`，同一时刻用于 60 秒补交窗口判定、采集区间上界与落库，锁等待不再把窗口内到达的补交判成迟交。
- 前端：手键（个人/组训）成绩页与实时预估固定“字符/分”，电子键（个人/组训）固定“四码组/分”，`protocolVersion=0` 历史成绩保留原数值并标注历史；个人手键曲线消费 `pageAnalyzeVOS`，组训手键消费后端 `speedLog`+`existNumber`；个人手键列表 `用时` 改按毫秒格式化（原先把秒传给毫秒格式化函数，导致 17 分钟以内一律空白），列表码率单位随协议版本切换。

**实际运行验证**（隔离 MySQL 8.0 + `quarkus-app` prod jar 18001 + 本次 `npm run build` dist 经 18425 `/app/`，真实浏览器登录）：

| 域 | 后端权威值 | 页面实际显示 |
|---|---|---|
| 个人手键 | speed=4、activeMillis=60000、score=70 | 列表 `1分`/`4字符/分`；成绩页 `70 字符/分`(基准) 与 `4 字符/分`(实际)、码率扣分 `-66 分`；曲线页签 `码率(字符/分)` |
| 个人电子键 | speed=1、duration=60、score=150 | 列表 `1分`/`1四码组/分`；成绩页 `1 四码组/分`、`0 分`；页签 `码率(四码组/分)` |
| 组训电子键 | speed=1、activeMillis=60000、score=150 | 成绩页 `1 四码组/分`、码率扣分 `0 分`（修前因 `deductInfo` 缺 `speedScore` 显示 `NaN 分`）；页签 `码率(四码组/分)` |
| 组训手键 | speed=4、speedLog=["4"]、activeMillis=60000、score=69 | 成绩页 `70 字符/分` 与 `4 字符/分`、码率扣分 `-66 分`；页签 `码率(字符/分)` |

四域成绩页均无 Vue 错误（`app.config.errorHandler` 捕获为空），曲线容器实际渲染 canvas。

**回归**：新增 `GeneralCaptureContractTest.graceWindowFollowsRequestArrivalRatherThanParentLockWaitCompletion`（Ticker/Key 两种模式）——父行锁持有到补交窗口之后，窗口内到达的页仍须保存且 `receivedAt` 保持锁前时刻，真正迟到的新页仍被拒绝。`backend` 全量 `./mvnw -B clean verify` 316 项、0 失败/错误/跳过（较上一基线 314 项 +2）。

**排查纠正（避免后来者重复踩）**：一次“个人手键成绩页整页空白”的现象最终定位为**验证夹具不完整**，不是产品缺陷——评分规则缺 `scale` 段、逐页载荷缺 `moresKey` 与 `finishInfo.patLogs`，成绩页模板读取 `ruleContent.scale.dot`、`key.patKeys` 时抛渲染错误。补成真实规则/载荷后页面正常。另需注意：本仓浏览器工具的 `page.evaluate` 运行在隔离世界，读不到应用的 `window` 全局；排查应用内状态必须用 CDP `Runtime.evaluate` 主世界求值。

**E-PACK 边界**：真实 `npm run build-e-l -- --dir` 重新打包成功（beforePack 重建前端并双向校验清单），包内 `app.asar` 实际含本轮单位文案（`四码组/分` 5 处、`本次采集码率（预估）` 4 处）与同名成绩页 chunk `HandKeyTrainScore-38bf2556.js`（dist 同文件 sha256 `5b9c790f851a7265b0fd…`）。包内成绩页的逐页面验收未完成：本机 `/var/lib/nip-traffic-system` 不可写，桌面离线授权只落到用户级副本后仍停在授权页，属环境权限前置（G4），不是代码缺陷；不得据此声称 E-PACK 已验收。

**未关闭**：G4 客户真实部署/安装器/硬件与正式发布仍未验收；本节证据为 BE + W-HTTP 仓内运行，Electron 包内同一改动的页面验收随 E-PACK 单独记录。

### 5.5 客户 12 条与附录 H/M 终态（2026-09-12）

仓内实现与运行证据已闭合的项标「仓内完成」；客户侧结果一律等 G4 现场验收，不在此声称已解决。

| 项 | 终态 | 证据 | 剩余限制 / 责任 |
|---|---|---|---|
| 1 掉线与凭证提示 | 仓内完成（互踢按设计保留） | T16 三项；`authMessages` 203/204/206 原文案未改 | 真实多机互踢与长期凭证观察属 G4 |
| 2 收报初始化/调速 | 仓内完成 | T10；6 组音频矩阵 ≤1 sample | 真实声卡/设备延迟阈值属 G4（W-HTTPS/E-PACK） |
| 3 单位/用时/扣分对账 | 仓内完成 | T02/T08 + §5.4 四域实测表 | 客户历史数据核对属 G4 |
| 4 上传/结算失败可见可重试 | 仓内完成 | T03 三类真实故障注入 + `GeneralCaptureContractTest` | 客户网络环境下的长时间断网未覆盖 |
| 5 倒计时与补交 | 仓内完成 | T09 扫描恢复实测 + 本轮锁等待窗口回归 | 服务重启矩阵只在隔离环境验证 |
| 6 手键完整有序 | 仓内完成（模拟帧+真实壳 IPC） | T05；打包 Electron 连续相同码 `1 1 2` | 真实串口硬件采样精度属 G4 |
| 7 控制符与翻页 | 仓内完成 | T06；`legnth` 缺陷消除 | 真实键盘/手键长时操作属 G4 |
| 8 组网一致性与评分 | 仓内完成 | T11/T12/T14；双向混合房间实测 | 多机并发规模、跨网段属 G4 |
| 9 学员详情可见性 | 仓内完成（不含实时草稿，G3 决定） | T13；对齐视图实测 | — |
| 10 电子键响应 | 仓内完成 | T10 + T05 | 按键到声音端到端延迟需真实设备（G4） |
| 11 训练配置往返 | 仓内完成 | T07；`TelegramTrainSettingTest` 6 例 | — |
| 12 交付与题库/理论链路 | 仓内完成，发布未完成 | T01.1/T15.1/T15.2 | Web 真实站点、安装器、云 CI 属 G4 |
| H1 电子键多码帧 | 仓内完成 | T05 实测同帧连码 | — |
| H2 码率不由客户端上报 | 仓内完成 | T08；篡改 `speed:99999` 无效 | — |
| H3 划线扣分上限 | 仓内完成 | T02；`GeneralTickerPatScoreTest` | — |
| H4/H5 身份归属 | 仓内完成 | T04；`tokenIdentitySeparatesStudents…` | 管理端授权（红线 6）仍是独立议题 |
| M1 失败释放锁 | 仓内完成 | T03 `useConfirmedSubmission` | — |
| M2 码值/时长同快照 | 仓内完成 | T05 `WebSerial` 冻结事件 | — |
| M3 离线学员结算 | 仓内完成 | T03 `closingAllows…SettlesOfflineAndAbsentStudents` | — |
| M4 无效 finish 字段 | 仓内完成 | T04 第三项 | — |
| M5 GeneralKey 页形态 | 仓内完成 | T08 第四项 | 存量 null 规范化随迁移执行，未操作生产库 |
