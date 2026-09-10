# 客户报障整改实施计划（Plan）

- 日期：2026-09-10
- 状态：**T00仓内基线、T01仓内交付链与T15仓内题库/理论操作验证已完成；正式发布未完成**。按用户要求继续推进后续任务；G1–G3业务决定、G4客户部署/安装器/硬件与正式验收仍单独跟踪。
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
| V01 凭证/授权 | T16 | 待实施；T00有既有鉴权基线，T01有两模式启动证据，未验证稳定ID/时长预警目标 | T16实现及G4实际存储/授权环境 |
| V02 音频节拍 | T10 | 前置资源验证完成：实际processor可加载且hash一致；节拍精度未验收 | G2校准口径、T10实现、G4设备/采样率 |
| V03 权威评分 | T02/T08/T14 | 待实施；T00评分相关既有测试不覆盖全部新目标 | T02确定性修复；G1冻结后实施T08/T14及原始记录对账 |
| V04 提交/幂等 | T03/T08 | 待实施；T01网络探针不等于训练失败重试/并发结算验收 | T03；G1相关轮次/时序契约及T08；G4端到端场景 |
| V05 倒计时 | T03/T09 | 待实施；尚未验证跨零、暂停、离线与重启恢复 | G1截止/暂停/迟到规则，T03/T09及G4 |
| V06 有序采集 | T05/T07 | 待实施；未以模拟帧或资源加载冒充真实采集 | T05；G2相关配置优先级、T07与G4串口设备 |
| V07 控制符 | T06 | 待实施；翻页/改错正文保护尚无修复后证据 | T05/T06有序事件与控制符场景；真实输入补G4 |
| V08 组网一致性 | T11/T12/T13/T14 | 前置传输验证完成：T01实际WS代理探针通过；并发报底、答案、业务推送和混合房间未验收 | T11/T12；G1/G3对应语义、T13/T14及G4 |
| V09 学员详情 | T11/T12/T13 | 待实施；未将已有端点存在或授权页启动当详情验收 | G3实时草稿/结束明细决定；T11/T12/T13及G4 |
| V10 电子键响应 | T10 | 前置启动/资源验证完成；速度跟随和按键到声音延迟未验收 | G2合法组合/延迟阈值，T10及G4目标设备 |
| V11 训练配置 | T07 | 待实施；T01修的是网络端口配置，不是点划训练配置 | G2相关映射/优先级、T07保存往返与基础区间场景 |
| V12 双模式交付/题库 | T01/T15/T17 | 局部已验证：T01交付链；T15 Web/Electron实际XLSX/DOCX往返、错误行与跨库拒绝、真实隔离后端建卷建考、学员页面交卷和教员页面阅卷（10分/state4） | G4云CI/可信部署/安装器/客户业务与配套native；T17汇总，不以合成数据验收替代客户确认 |
| V13 学员授权归属 | T04 | 待实施；未以既有JWT测试代替General手键/电子键所有权验收 | T04跨栈切换及实际学生/教员场景 |

**T00整体仍未完成的原因**：T00.1环境基线与本台账可在仓内完成；G1评分/时间轴、G2节拍/配置、G3可见性决定以及G4客户现场清单尚未齐备。前者可继续整理源码事实，最终业务含义不能由实现者擅定；后者不能由本地探针替代。门禁按关联任务生效，因此T01及T02/T15等确定性工作不必等待所有G项，但T08/T09/T14、T07/T10语义调整与T13不能越过各自门禁。



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

- [ ] PostTelex trainType!=4 以规则满分起评；用非 100 满分原始记录验证扣分与最终分。
- [ ] PostTelegram 高于 r 加、低于 l 扣，与 ScoreMath 对账；移除反向旧注释，不改业务规则字段含义。
- [ ] General 手键划线 cap 改为 dash.max；dot.max=1/dash.max=5/lineScore=7 最终成绩与扣分明细均正确。

**提交**：上面三项各自一个可回滚修复提交；每项带它保护的行为回归。**验证**：V03 中对应案例；既有工具函数测试不能代替 General 活跃结算。

### T03 提交失败恢复与结束原子性（R04/R05/M1/M3，P1）

**文件**：postJob 数据报 `telexTrain.js`/`trainScore.js` 活跃调用树、电子键组训 `handKeyTrain.js`、相应后端 service；以 API 模块 `TelegramApi.js`、`electronKeyZuXun.js` 搜索实际消费者。

- [ ] 先复现 code 非200、reject、响应丢失和连点；页成功后才推进，最后一页确认后才 finish；失败保留内容且 finally 释放锁。鉴权失败只引导登录，不自动跨用户重传。
- [ ] 修 pageTime 前页结束点、单调时钟计时、coun<=0 一次触发；合法时长在开始时冻结。这里不提前实现未定 G1 的服务端超时语义。
- [ ] upload/finish/reset 在相同训练/学员互斥，防并发重复结算、上传重开已结束训练；已有 finish 短路保留但补并发保护。
- [ ] M3 结束流程以 DB 已保存参训记录为依据；异常时回滚状态和分数，不 catch 后空列表继续；离线已上传学员仍须结算。

**验证**：V04、V05 客户端部分，尤其“服务器已提交但客户端未收到响应”的重试；对数据行/成绩不变与用户可继续操作断言。按数据报恢复、电子键锁、后端结束各自提交，契约相关修改保持同提交。

### T04 学员身份与内部结算切换（H4/H5/M4，P1）

**文件**：`backend/src/main/java/com/nip/controller/general/GeneralTickerPatController.java`、`backend/src/main/java/com/nip/controller/general/GeneralKeyPatController.java`、对应 DTO/service、`handkeyZuXun.js`/`electronKeyZuXun.js` 与调用页。

- [ ] 手键 upload/finish/reset 和电子键 finish 从 token 取得主体，校验参训关系与可写状态；两侧删除 userId/uid 身份参数，不保留“传来但忽略”的兼容字段。
- [ ] 将教员结束的授权与内部按用户结算路径分开，迁移 GeneralTickerPatService.updateStatus 的所有调用，不把其结束全员操作变成只结算教员本人。
- [ ] 移除手键 finish 中未被 DTO 接收的 validTime/finishInfo；G1 需要的时序由 T08 在正式逐页契约实现。

**验证**：V13；A/B 身份、非参训者、未授权教员不能改变目标数据；授权教员批量结束及正常学生继续有效。

### T05 手键/电子键有序事件（R06/H1/M2，P1）

**文件**：`bw-frontend/frontend/src/common/utils/WebSerial.js`，手键/电子键组训学生的 useControl/student/handKeyTrain，及复用生产者的活跃 preJob/postJob 入口。

- [ ] 原始码按顺序逐项消费，同一对象传递码值和时序；替换 ref 覆盖和值/时间多个共享 ref 拼装，连拍相同码也不丢。
- [ ] 点阈值使用当前有效校准，剔除≤10ms抖动；重复按下/缺抬起/分包粘包明确恢复，正常抬起必须解锁。
- [ ] 硬件无时间戳时只标记 JS 接收单调时间，记录精度边界；离页清订阅/队列，跨训练不残留。
- [ ] 同时核对 `bw-frontend/frontend/src/common/ws/MessageWebSocket.js:89-108` 的WebSerial入口与visible过滤，按G1后台继续/暂停协议处理，不静默丢原始帧；Web拒绝选端口/无能力与Electron真实IPC选端口/数据通道分别验收，不由isEE猜测实际数据传输。

**验证**：V06；真实串口帧与同 tick 注入分开记录。无硬件仅能证明事件消费，不关闭客户采样精度问题。

### T06 手键控制符与翻页（R07，P1）

**文件**：手键组训 `handKeyTrain.js`、`bw-frontend/frontend/src/views/manage/postJob/telegram/train/js/details.js` 和其控制符共享定义；依赖 T05 的事件语义。

- [ ] 句号/当前组改错/前组改错优先识别，跟踪控制符产生的临时项，完成时只撤销这些项；修 legnth。
- [ ] 保留普通未知码 #，不得隐藏全部 # 或无边界拼接历史输入；干净三组不能固定删 3，完整粘连/分片按控制符边界处理。

**验证**：V07，断言实际正文、组/页位置及控制行为；不能只断言字符串里没有 #。

### T07 训练设置往返与基础分级边界（R11，P2）

**文件**：preJob/telegram/handkey 的 telegram.js、preJob/telegram/train 的 HandKeyTrain.vue/basicTrain.js、postJob 手键配置入口、`backend/src/main/java/com/nip/service/TelegramTrainService.java`。

- [ ] 修 interval/gap 读取多减1；保存→加载→不改再保存毫秒值不漂移。跨入口映射及固定/自校准优先级遵循 G2。
- [ ] postJob 加载本域训练配置，不以 getBasicSetting 区间冒充四段毫秒字段。
- [ ] 基础练习保留已存在的区间分级，异步未就绪/缺异常区间/坏 JSON/无正区间时禁用开始并可恢复；saveSetting 全量校验后才替换，坏配置不清空旧值。

**验证**：V11 和 V06 的校准边界；往返修复与基础配置校验分别提交。

### T08 原始记录驱动权威评分（R03/R04/H2/M3/M4/M5，P1，需 G1）

- [ ] G1 表必须先补全为可实施的字段/单位/时间轴/错误策略；按模式先形成保存记录→score/time/speed/deductInfo 可手工验算的案例，不直接强行把六种公式合成一种。
- [ ] 同提交迁移逐页 DTO、存储、计算、API 和成绩页；删除客户端汇总评分输入，显示和扣分均来自同一后端结果。用服务端接收时间校验边界，不冒充原始拍发时刻。
- [ ] 页替换不累加 speedLog；reset 清本轮派生状态并以 G1 的轮次约束拒绝旧在途页。规则冻结与存量切换同步 migration，不重算缺原始数据的历史成绩。
- [ ] GeneralKey 固定/懒生成 value 一致并规范化历史 null；清除本次切换过时字段/计算，不保留新旧双算法作为运行兜底。

**验证**：V03/V04；客户端 speed/总分篡改、重复/乱序页、reset 后迟到请求、缺规则、非100满分与三页计时。定向单测之后必须从真实提交接口到数据库/详情回读完整结算。

### T09 服务端倒计时恢复（R05，P1，需 G1/T08）

- [ ] 按真实 begin/暂停/恢复持久化截止状态，不以创建时间假定已开始。
- [ ] 加周期扫描和重启恢复入口，复用同一互斥幂等结算；按 G1 明确未传页、迟到页、截止宽限和失败重试，不丢弃客户端仍可补交的数据。
- [ ] schema、DAO、截止处理与回归同提交；失败回滚，不产生“已结束无结果”终态。

**验证**：V05 后端部分；停止浏览器不发 finish、并发手动 finish、服务重启、持久化/结算故障恢复。不能只断言字段存在或定时方法被调用。

### T10 音频码速与响应（R02/R10，P2）

**文件**：Spec §5.4 列出的音频工具，postJob/preJob 收报及电子键 examTrain/组训训练设置消费点。

- [ ] 就绪前缓存最新全量参数并每场重置；Web用户手势初始化/恢复、Electron mounted初始化分别测试。受限/未ready状态禁用有声训练并给可恢复提示，不假装播放，不能用壳安全开关替代浏览器许可。
- [ ] 统一换算入口、修 speedRate、按 type/模式设置 criterion/ratio；postJob/组训恢复速度跟随，preJob 验证不回归。低速与5/7间隔遵循 G2。
- [ ] 修改实际运行资源 `bw-frontend/frontend/public/processor.js` 为样本累计/余数，清热路径日志；核对src/common/utils/processor.js引用后收口单一来源，不只改未加载副本。Web实际URL返回JS且hash正确，Electron安装包加载同一修复；F2遵循G2。

**验证**：V02/V10在W-HTTPS与E-PACK分别测采样/真实声音及按键延迟；W-HTTP明确音频能力限制，不能伪报ready。覆盖浏览器用户手势/后台节流与壳最小化恢复；每份结果记录实际processor URL/hash。参数ready、采样时钟、入口跟随可分子提交，共享契约同提交。

### T11 simulation 报底与答案幂等（R08/R09，P2）

**文件**：`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomService.java`、`backend/src/main/java/com/nip/service/simulation/SimulationRouterRoomContentService.java`、对应 Page/PageValue DAO/entity、migrations/rehearsal。

- [ ] 清点两表 null/重复键；冲突内容无可靠裁决来源则停止迁移，备份待人工确认，禁止无条件取第一条/最大 UUID；清理后唯一键各列设 NOT NULL，输入与实体同步，防空键绕过约束。
- [ ] 有界懒生成锁房间 DB 行、锁内重查整页；唯一键是 room/page/sort。统一创建/懒生成/删除锁顺序，不仅套 JVM 锁。
- [ ] uploadResult 保持整份数组语义，按 token 归属在事务内替换该用户页集合，删旧尾页；唯一键 room/user/page。状态只随保存成功提交。
- [ ] 对应实体、迁移和脚本纳入双快照演练；脚本当前显式列迁移，新增 SQL 不会自动执行，必须同步接线与断言。

**验证**：V08/V09 数据部分；并发首读同页、重复完整提交、三页改两页、两个学员隔离、事务中断恢复。两种数据不变量分别提交，不让 schema 与消费者分离。

### T12 simulation 事务后通知与可恢复详情（R08/R09，P2）

- [ ] 各房型迁入现有 SocketConnection；心跳不进业务 JSON 解析，结束态进入也建连；卸载/手动关闭停止重连。
- [ ] REST 提交后发身份完整的轻量结果通知给房间全体教员，删除客户端后置 WS 写状态依赖。报务房同时删除补 id/原消息两路分歧，防重复。
- [ ] 首进、通知、重连均拉 REST 快照；5秒一个有界在途请求作等待结果时兜底，按可见性/结束结果齐全停止；不用内存在线名单冒充最终成绩。

**验证**：V08/V09；Web教员+Electron学员、Electron教员+Web学员两种混合房间及各模式独立场景均覆盖。两教员2秒内更新、丢通知10秒内补偿；服务重启、结束后刷新/重连正常。网络时限仅对Spec所列可达同局域网矩阵生效，不为不同壳另造协议。

### T13 详情可见性与对齐（R08/R09，P2，需 G3）

- [ ] G3 确认漏多组展示样例后做有限页内对齐，额外页显式“超出报底”；不新增 simulation 数值评分。
- [ ] 若需要实时草稿，同提交实现逐页草稿保存/版本、授权读取、通知、教员展示、最终冻结，不复用提交状态伪装草稿；不需要则在 G3 记录客户确认的结束明细范围。
- [ ] 检查三类教员详情入口，不把只修 BroadcastTeachTrain 一处当全部完成。

**验证**：V09 和 V08 对齐部分；草稿不结算、不向无权者泄露，最终提交后显示状态/内容一致；已结束详情无论 G3 分支都必须验收。

### T14 综合组网服务端评分（R08，P2，需 G1）

**文件**：`backend/src/main/java/com/nip/service/GroupNetTrainService.java`、`backend/src/main/java/com/nip/controller/GroupNetTrainController.java`、`bw-frontend/frontend/src/common/api/TrainingDetails.js` 及其真实 trainingDetails 消费页。

- [ ] 以服务端题目、规则与答案计算分数，明确 range/deviceId 的真实对应，删除客户端正式总分与浮点直接相等作为权威依据。
- [ ] 参数与响应消费者同时切换，重复提交结果确定；不同训练类型按 G1 各自样例，不误用 simulation 字符对齐规则。

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

- [ ] 按203/204/206解释前端提示，不改后端码文；206只说可能他处登录，不声称准确检测到互踢。
- [ ] 身份稳定化只在登录使用，不换有效会话ID；Electron复用硬件接口，Web按profile+origin持久化，不承诺跨清数据/换域/换浏览器稳定。Web仅IndexedDB授权与Electron多副本恢复分别测试，读取错误不误当真实空记录清除。
- [ ] 剩余累计可运行时长604800秒阈值预警；存储错误、设备不匹配与耗尽分别引导，保留现有授权自恢复机制。

**验证**：V01；安全 token/TTL/存储迁移仍引用既有计划的门禁，不以本任务宣布防重放完成。

### T17 综合验收与发布关闭（全部 R 项）

- [ ] 工作包稳定合入后统一运行 §4 全量门禁，记录实际测试数，不复用历史216/238等数字。
- [ ] V01–V13按Spec §7.3登记W-HTTPS/W-HTTP/E-PACK；同BE测试可共享，浏览器/壳UI、网络、授权、音频/串口必须分别留证。G4缺真实硬件/授权/反代/安装环境时阻塞对应模式，不能只拿另一模式或smoke关闭。
- [ ] 12条及H1–H5/M1–M5逐项登记：修复提交、验证证据、客户结果、剩余限制；G3不需要草稿须有明确决定，不悄悄跳过。
- [ ] 本轮切换过时字段/路径/临时脚本清理；随功能更新操作说明、发布变更与迁移前置；不得顺便删除无关 teacherBack 历史代码。
- [ ] 发布负责人按已验收双端清单交付，记录安装/回滚检查结果；文档、代码、客户版本一致才将本计划改为完成。

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
