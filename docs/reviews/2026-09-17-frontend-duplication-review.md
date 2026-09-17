# 前端复制粘贴/一次性代码专项评审报告（bw-frontend）

- 日期：2026-09-17
- 范围：`bw-frontend/` 全仓（Vue3 前端 `frontend/src`、Electron 外壳 `main.js` + `electron/**`、`frontend/public` 资产）
- 主题：**一次性代码（拷贝-粘贴产物）与冗余优化**。安全/性能问题仅在与冗余直接相关时提及（其余以 `docs/reviews/archive/2026-09-08-frontend-review.md` 等历史评审为准）
- 方法：机械扫描（行归一化 + 8 行窗口哈希的跨文件克隆检测；md5 字节级去重；同名文件 Jaccard 聚类；import 图死码检测；本地 `t_menus` 路由表交叉验证）+ 6 个并行只读评审代理逐域 diff 级复核 + 父代理对关键结论独立抽查
- 权威性：所有 P1 结论均经 `文件:行` 级复核；死码判定经「import + PascalCase/kebab 模板标签 + `:is` 动态值 + `main.js` 全局注册 + 菜单路由表」五路核查（`vite.config.js:15` 有 unplugin-vue-components 自动导入、`config/router/guards.js:6` 有 `import.meta.glob('../../views/**/*.vue')` 动态路由，均已纳入判定）。标 `[INFERENCE]` 与「待确认」者需业务/生产数据确认

---

## 1. 执行摘要

前端 541 个代码文件、约 147K 原始行（归一化 136K 行）。**约 23.6%（32,082 行）是第二份及以上的复制拷贝**；另有约 64MB 主题图片资产重复、约 20MB 死 vendored 资产/文件。复制粘贴不是局部现象，而是本代码库的主要生产方式，并已产生三类典型后果：

1. **副本已开始静默分叉**：同一逻辑的 N 份拷贝各自演化——`datagramZuXun` 的 `submitPromise` 重入保护在 `telexZuXun` 副本里丢失（回归）；`electronKeyZuXun/teacher.js:243` 的滚轮 bug（`e.scrollLeft += 100`，事件对象无此属性）只在一份里被修复；`125W_400W.js:321` 的 `==` vs `indexOf` 两侧行为不一；`patStandard.js` 两套评分阈值已不可互换。修一个 bug 要改 N 份，实际上只改了其中一份。
2. **死代码大量滞留**：确证可删死码约 **5,400 行 + 约 20MB 死资产**（清单见 §2），包括文件名自己写着 `IndexDelete` 的组件、`MorseVoiceOld.js`、`Index1.vue` 三连、整目录废弃的 `equipmentList1`/`danmaku`/`dept`。
3. **拷贝痕迹污染环境**：拼写错误随复制扩散——`parctice.js`（practice）×4、`knowledgeTabel.js`（table）×4、`compoents/` 目录 ×4、`perviewTest`/`studentPerviewTest`、`GardRule`、`broaddcastTeacheing`（9 个文件）、`wb_colork.js`；组件 `name` 字段张冠李戴（`ElectronKeyZuXunTrain` ×3、`HandKeyZuXunTeacher` ×4、学生组件 name 叫 `PreviewTheTopic`）。

### 量化总览

| 维度 | 数值 | 说明 |
|---|---|---|
| 代码克隆 | **32,082 归一化行（23.6%）** | 3,578 个 ≥12 行跨文件克隆块；61 个克隆家族（169 文件）；31 个同名高相似家族 |
| 字节级相同文件 | 15 组 / 冗余 1.29MB | 最大：`unpkg.js` 745.6KB×2、`table.js` 272KB×2、`wbColor` 245KB×2 |
| 确证死码 | **≈5,400 行**（§2 逐项） | 另加 8 个死 API 导出、6+2 个死 npm 依赖 |
| 死资产（已提交） | **≈18MB**（`frontend/public` 内 UEditor/pdf/3d/jquery）+ 1.74MB 死 vendored JS | luckysheet 8.8MB 功能已坏、去留待业务确认 |
| 主题资产重复 | **64.1MB / 1,844 个文件** | `src/assets` 下 GD/HJ/HJJ/KJ/LJ 五套主题目录互拷；代码内 2,451 处硬编码 `assets/<主题>/` 引用（132 文件） |
| 整合后预估净省 | **代码 ≈28,000–32,000 行（~21%）+ 资产 ≈80MB+** | 分项见 §2–§5 |

### 子树冗余密度（归一化行口径）

| 子树 | 冗余行 | 占比 |
|---|---|---|
| views/manage/equipmentOperate | 2,857 / 3,318 | **86%** |
| components/gradingRule | 1,491 / 3,006 | 50% |
| views/manage/organization | 5,232 / 12,158 | 43% |
| views/manage/login | 1,014 / 2,532 | 40% |
| views/manage/fixedMessage | 751 / 2,017 | 37% |
| views/manage/postJob | 5,362 / 17,889 | 30% |
| views/manage/preJob | 5,836 / 20,796 | 28% |
| views/manage/basicTheory | 4,154 / 21,862 | 19% |

---

## 2. P1：确证死代码（零风险删除，≈5,400 行 + ≈20MB）

判死方法见文首；以下每条均为五路核查后结论。

| # | 对象 | 行数/字节 | 关键证据 |
|---|---|---|---|
| 2.1 | `components/gradingRule/IndexDelete.vue` + `Telex.vue` | 1,002 行 | 六路检索零引用；两者 diff 仅 16 行，是 `HandKey.vue` 的旧快照（同 `getGradingRuleListByType({type:0})`）；`IndexDelete` 仅存于自动生成的 `components.d.ts:75` |
| 2.2 | `components/danmaku/` 整目录（Danmaku.vue 559 + utils.ts + typings） | 598 行 | 仅自引用，零外部使用 |
| 2.3 | `components/dept/**`（job/member/DeptTree 及 js） | 919 行 | `useJob.js:1`/`useMember.js:1`/`DeptTree.vue:20` 均 import 不存在的 `common/api/DeptApi.js`——自该 API 模块删除后整目录即死 |
| 2.4 | `components/dashboard/NipTop.vue` + `NipCenter.vue` | 447 行 | `dashboard/Index.vue:12-13` 的 `:is` 只取 HJ/HJJ_LJ 两组件 |
| 2.5 | `components/model/Confirm.vue` + `confirm.js` | 159 行 | 唯一引用是 `App.vue:40` 已注释的 import；命令式弹窗已被 antd Modal 取代 |
| 2.6 | `components/test/StudentPreviewTheTopic/StudentPreviewTheTopicTwo.vue`（`:120` name 竟为 `PreviewTheTopic`，拷贝痕迹）+ `{previewTheTopic,StudentPreviewTheTopic}/js/usePreviewTheTopic.js`（0 行空文件 ×2，仅被注释掉的 import 引用） | 311 行 | 五路检索零引用 |
| 2.7 | `components/common/NipTreeNode.vue`（仅自递归）、`AudioPlayer.vue`、`components/audio/playAudio.vue` | ≈280 行 | playAudio 特例：4 个 explain 页（`preJob/{datagram,hanzi,receive,telegram}/explain/Index.vue:41-43`）模板用了 `<playAudio>` 但从未 import/注册 → Vue3 按原生元素处理，**组件从未渲染**，是「死组件 + 4 处坏引用」的潜在 bug，删组件时须同步清 4 处标签（或补 import 恢复功能，二选一） |
| 2.8 | `common/anime.js`（全文 5 字节）、`common/directive/vueTouch.js` + `tools.js`（互引，无 v-tap/v-longtap 使用）、`common/mixin/useBase.js`、`config/directive/waves/index.js`（整个文件被注释） | ≈160 行 | `main.js` 的 `anime()` 来自 `index.html:121` 全局 anime.min.js，与该文件无关 |
| 2.9 | `views/manage/equipmentOperate/equipmentList1/` 整目录（Index.vue + js/{125w,400w,171}.js） | 527 行 | 自身 Index.vue 都不 import 自己的 js/；`project006.sql` 与 `project006-base.sql` 菜单均无此行；旧 `window.open('equipmentXXX://')` 协议方案，已被 equipmentUnity 取代 |
| 2.10 | `views/manage/unionJob/broadcastTeacheing/js/useBroadcastTrain.js`（空函数桩）+ `BroadcastTeachTrain.vue` 壳内 234 行死 scoped CSS | ≈305 行 | 壳模板（:1-8）只用 3 个类，scoped 不穿透子组件，整块 CSS 克隆自 BroadTeacher 且无渲染对象 |
| 2.11 | `views/manage/unionJob/unionTrain/js/Room.js` | 27 行 | `Room.vue:265` 唯一实例化点已注释；其 :15-16 读不存在的 `wsOnline.value`，必抛错被 `catch(e){}` 吞掉 |
| 2.12 | `views/manage/organization/telexZuXun/train/student/js/keyCode.js` | 146 行 | 零引用（datagram/electronKey 变体有引用，telex 学员端走 textarea 拍发不需要电子键码表） |
| 2.13 | `useControl.js` 3 行 re-export ×2（datagramZuXun/telexZuXun） | 6 行 + 2 文件 | 零引用 |
| 2.14 | 死 API 导出 ×8：`CableApi.js:10` 与 `ToolsApi.js:9` 的 `getTwelvemonth`（同端点双份且双死）、`GradingRuleApi.js:24,31`、`MilitaryTermApi.js:3`、`RoleApi.js:9`、`UserApi.js:19,33`、`TelegramApi.js:287` | ≈56 行 | 逐函数全仓 grep 零调用；不改变任何活端点 |
| 2.15 | Electron 外壳：`electron/service/http/core/node_core_utils.js` 死导出 8/10（连带 shortid/Crypto/spawn/net 死 import）、两份 `node_core_ctx.js` 的死字段、`controller/system.js` 无人监听的 `event.reply` 死发送 | ≈210 行 | 调用面仅 `controllers/file.js` 用 sendFile/getMime/genRandomName |
| 2.16 | `preJob/{datagram,telegram}/teaching/js/unpkg.js` ×2（babylonjs-inspector UMD） | **1.49MB** | grep `unpkg/inspector/debugLayer` 全 src 仅命中自身；3D 走 npm `@babylonjs/core` |
| 2.17 | `preJob/telegram/addTelex/js/wb_colork.js`（=`wubi/practice/js/wbColor.js` 字节级副本，文件名大小写变体）+ 同目录 `addTelex.js` | **245KB** + 48 行 | `addTelex/Index.vue:11` 只 import `./js/canvas` |
| 2.18 | `frontend/public` 死资产：`UEditor/`（8.1MB/273 文件，现用 tinymce，`config/runtime.js:63` 的 `window.ueditorUrl` 是 write-only 残留）、`js/pdf/`（7MB，现用 @vue-office/pdf）、`js/3d/`（2.6MB 旧物理引擎）、`js/jquery/`、`splashscreen.html`、`l.gif` | **≈18MB** | 全 src/index.html 零引用；每次构建还会被双份拷贝进 `frontend/dist` 与 `public/dist` |
| 2.19 | `public/html/`、`public/images/`、`bin/config.json`（3 字节 `{}` 占位） | ≈36KB | 仅打包清单引用；`core/index.js:10` 只读 nip.db |
| 2.20 | 死 npm 依赖：外壳 6 个（globby/is-type-of/debug/lodash/dayjs/shortid）+ 前端 2 个（@dropb/diskinfo、@fingerprintjs/fingerprintjs——唯一 import 在 `studyManage/analyze/js/pointUseChart.js:4` 且 `sources` 未使用） | 8 个依赖 | 全仓 require/import 清单核对 |
| 2.21 | vuex `config/store` 的 `router`/`online` 两片 + 连带死引用（`Room.js`、`TransitionPage.vue:51`、`PreviewHJ.vue:459,487` 死 import） | ≈30 行 | 唯一活链是 `setPermissions`（`PreviewHJJ_LJ.vue:361` 写 → `ButtonPermission.js:16` 读）；彻底方案见 §4.6 迁 pinia 卸载 vuex |
| 2.22 | `common/http/index.js:6` console.log 调试残留 + `:39` `config.method === 'POST'` 恒假死分支（axios 实例 method 恒小写，`http/axios.js:17`） | ≈10 行 | 注意：若有人「修正」为 `'post'`，所有 POST body 将被双重 stringify 静默损坏——应删不应修 |

> 勘误（对 2026-09-08 旧评审与我方种子数据）：①`public/dist` 与 `frontend/dist` 均未提交（.gitignore），磁盘上的多份是构建流水线固有产物，仓库层面只有 `frontend/public` 一份源——真正问题是其内部 18MB 死资产（2.18）；②telex/datagram `student.vue` 的 localStorage 同键碰撞**已修复**（各用 `'telexZuXun'`/`'datagramZuXun'+trainId`）；③telexZuXun 并未 import electronKey API，它复用的是 `common/api/datagramZuXun.js`（common/api 下根本不存在 telexZuXun.js）。

## 3. P1：字节级/近字节级重复（单源化，≈4,700 行 + ≈320KB）

| # | 家族 | 现状 | 方案 | 节省 |
|---|---|---|---|---|
| 3.1 | `equipment/trainScore/js/` ↔ `equipmentOperate/trainScore/js/` | `table.js`（272KB，含 220定频 内嵌数据）、`121C.js`、`171.js` **字节级相同**；`134A.js`/`125W_400W.js`/`173.js` 差 2-8 行（console.log + 一处行为分叉） | 单副本收编；`125W_400W.js:321` 的 `==` vs `indexOf` 需确认以哪个为准（operate 版更像后修的修复 [INFERENCE]） | ≈289KB |
| 3.2 | `equipment/equipmentList/js/` ↔ `equipmentOperate/equipmentList/js/`（121C/125w/134A/171/173/400w ×2） | ~970 行/侧，差异**全部是演示参数**（循环上限 i<4 vs i<1） | 单源 + `initData({demo})` 参数 | ≈970 行 |
| 3.3 | `organization/*ZuXun/train/teacher/css/teacher.less` ×4（854/854/852/568 行） | datagram↔electronKey 仅差 1 行；↔handkey 仅 8 处值；telex = datagram 删 `.KJ` 块 | 单份 less + CSS 变量承载 8 处差异 | ≈2,250 行 |
| 3.4 | `score.less`（datagram==telex 字节级）+ `handKeyTrain.less`（datagram==electronKey 字节级；handkey 同内容 `.css` 重排版） | 见 TrainClone 证据（md5 实测） | 各收一份基准 + 变体增量 | ≈1,840 行 |
| 3.5 | `organization/*ZuXun/train/Index.vue` ×4 | 同一个 35 行角色分发器；datagram==telex 字节级；组件 name 全是 `ElectronKeyZuXunTrain` 拷贝痕迹；electronKey 版残留 `console.log(2222222)` | 合并为单 `TrainDispatcher.vue` | ≈106 行 |
| 3.6 | `keyCode.js` ×5（org×3 字节级 + postJob examTrain 差 1 空行 + preJob 285 行超集） | 电子键码表 5 份 | 提到 `common/data/electronicKeyCode.js`（以 285 行超集为基座），5 处 import 改路径 | ≈580 行 |
| 3.7 | `studyManage/{classHours,score}/js/selectTimeTag.js` | 32 行 diff 为空 | 保留一份 | 32 行 |
| 3.8 | `LoginKJ.vue` ↔ `LoginLJ.vue` ↔ `LoginGD.vue`（536/535/499 行） | KJ↔LJ 仅 39 行差异，全部是 `assets/KJ/` vs `assets/LJ/` 资源路径 + 3 个样式常量；业务逻辑已收敛于 `useLogin.js` | 单 `LoginPage.vue` + 主题配置表，资源用 `import.meta.glob('../../assets/*/login/*')` 按 `interfaceStyle` 取；`Login.vue:25-35` 的 5 个静态 import 变 1 个（顺带减首包）；LoginHJ(299 行) 布局差异大可缓 | ≈1,000–1,450 行 |
| 3.9 | `DatagramGardRule.vue` ≡ `TelexGardRule.vue`（455+455） | diff 仅 4 处格式差异，type/rateUnit/逻辑全同 | 删 TelexGardRule，`postJob/datagram/telex/Index.vue:179` 改引 | 455 行 |
| 3.10 | 自绘分页块 ×18 页内联 + `components/common/Pagination.vue` 与 `components/pagination/Pagination.vue` 双组件分叉 | 同构 `.table_pagination` 块（页码窗 curr±3）+ `selectTablePage` 函数 ~18 行逐份复制（20 个 js 文件） | 保留一个 Pagination（common 版接口 + 全量数组模式），18 页替换，删分叉组件迁 7 个调用方 | ≈550 行 |

## 4. P2：参数化合并（高价值，需设计与验证，≈15,000 行）

| # | 家族 | 规模 | 真实差异（合并时必须保留的） | 方案要点 | 节省 |
|---|---|---|---|---|---|
| 4.1 | **organization 四 ZuXun 组训树**（datagram/electronKey/handkey/telex，共 19,339 行） | teacher.js ×4（2,271 行）、teacher.vue ×4、list ×4（2,167 行）、student、score ×4（2,840 行） | API 模块、ws 通道、统计维度（9 类 vs 7 类扣分字段）、文案；**telex 与 datagram 后端同契约**（同 ws `/generalTelexPatTrain/`、同 API 模块）宜并为同一变体 | 两层：数据/逻辑上收 composable（`useZuXunTeacher/Student/Score`），表现层 5 个单组件 + `config/zuXunVariants/*.js` 配置对象。**合并时以 electronKey/handkey 新版为准回灌 datagram/telex**（status===3 收尾补交、submitPromise 重入保护、wheel bug 修复）；顺手修路由名大小写失配（`handKeyTrain.js:74` 查 `HandKeyZuXunTrain`，种子菜单 name=`HandkeyZuXunTrain` → 永不命中）与 6 处 `<TrainLeft>` 未导入坏引用 | ≈9,500 行（-49%） |
| 4.2 | **basicTheory 教师/学生成对页**：addTest↔studentAddTest（1,570+2,517 行）、startTest↔studentStartTest、grade↔studentGradeList、startGrade↔studentGradeDetails | 家族合计 ≈7,600 行 | 提交 API（`savetheoryKnowledgeExam` vs `saveTheoryKnowledgeExamSelfTesting`）、教师侧人员面板/监控/评分流；**studentAddTest 把 paperCard.less+KJ.less 共 ~2,000 行内联进 style 块**（addTest 用 @import 的同一文件） | ①studentAddTest 删内联 CSS 改 @import（立省 ~2,000 行零风险）；②各对合并为 `useAddTest({mode})` 等 + 单页 v-if；**注意 previewModel 已分叉**（学生版缺存在性判断、JSON.parse 口径不一），合并时以教师版为准统一 | ≈3,400 行 |
| 4.3 | components/test 试卷渲染器家族：perviewTest/StudentPerviewTest + PreviewTheTopic×2/StudentPreviewTheTopic | ≈1,978 行 | 学员作答/教师评分/学员自测三态；正解来源分叉（`correctAnswer` vs 旧 `params.answer` 回退） | 单 `PreviewTheTopic` + `mode` prop（正解口径以教师版为准）+ 单 `PerviewTest` 容器；`calculateScore`/`Earray`（×3 逐字复制）提 `paperUtils.js`；`theNewTest/Index.vue:547` 以组件复用 questionBank 是仓内已有的正确范例，沿用之 | ≈1,080 行 |
| 4.4 | **api 层锅炉**：28 个模块 331 个 `axios({...})` 五连块（2,348 行） | 2,348 行 | 仅 method/url/导出名不同 | `http/axios.js` 旁加 `get/post/put/del` 工厂，逐模块改单行声明（**端点 URL 逐一保留，前后端契约面不变**）；顺带统一文件名（`broaddcastTeacheingApi`→`broadcastTeachingApi` 改 9 处 import）并消除同端点双名（6 组，含 `electronKeyZuXun.js:60,67` 内嵌的 handkey 函数字节拷贝） | ≈1,250 行 |
| 4.5 | 列表页整页骨架（搜索+表格+分页+弹窗）×12 文件 | 每页 ~45-60 行骨架 | API、columns、表单项、action 按钮 | `useListPage({fetchFn, columns})` + `<ListPageShell>`；与 3.10 分页组件一并落地 | ≈400–550 行 |
| 4.6 | vuex→pinia 彻底迁移 | store 44 行 | permissions 链（PreviewHJJ_LJ 写、v-per 读） | 迁 pinia 后 `app.use(store)`（`main.js:100`）与 vuex 依赖整体移除 | 依赖卸载 |
| 4.7 | `equipment/trainScore/Index.vue` 双份（557 vs 559，差 92 行） | 1,116 行 | 数据源：route.query+getDetails vs props 注入；operate 版干扰音频块被整段注释 | 单文件：props 可选、缺省回落 route 查询；注释块改 v-if | ≈557 行 |
| 4.8 | `preJob/hanzi/js/enum.js` ↔ `telexTrain/js/enum.js`（733+555 行） | 1,288 行 | hanzi 版多 `isimg/color` 装饰字段 + KJ 字根表；telex 版是其子集，19 处 import | 以 hanzi 版为超集正本上收，19 处改路径；装饰字段可再外提 | ≈555–615 行 |
| 4.9 | 手键/电子键拍发家族：`HandKeyTrain.vue`（953 vs 380）、`details.js`/`useDetails.js`/`handKeyTrain.js`（~840×3）、拍发组码算法块 ×3 | ≈4,000 行 | API/ws 通道不同；**`patStandard.js` 两套评分阈值已分叉（line*2 vs line*3 等），需业务确认以哪套为准** | 抽 `useHandKeyPatting`/`useElectronicKeyPatting` composable；阈值参数化前先对齐行为 | ≈1,800 行（保守） |
| 4.10 | `gradingRule` 四活件（DatagramGardRule/ExamGardRule/HandKey + 3.9 收编后）参数化合单 | 2,119 行 → ~800 | type（0/2/3）、rateUnit、默认 content 字段、HandKey 的 Modal 编辑流 | 单 `GradingRule.vue` + props `{type, rateUnit, defaultContent, editable}`；GradingRuleApi 调用面不变 | ≈1,300 行 |
| 4.11 | `equipmentList/Index.vue` 双份（344 vs 587，差 467 行） | 931 行 | 布局容器/主题套数/训练启动方式；**`:123` 跳 `/equipmentUnityHJJ` 无对应 .vue，疑似死跳转 [INFERENCE]，合并前先确认** | 单组件 + props（showLeftMenu/theme/embedScore） | ≈300 行 |
| 4.12 | `questionBank`(1,760) ↔ `paperBank`(485) 知识树左栏；study/basic list↔manage；studyManage 三图表页 useChart 家族 | — | 右栏功能不同；图表 API/容器 id/小数位 | 知识树左栏组件 + `useKnowledgeTree`；`useList({manage})`；`useStudyChart({apiFn, containerId, decimal})` | ≈925 行 |
| 4.13 | vendored `common/mqtt/paho-mqtt.js`（97KB/2,415 行，2013 版权头旧版） | 2,415 行 | 4 处使用（equipment/equipmentOperate 各 2） | 改 npm 依赖 `paho-mqtt`；Electron 离线打包验证后删仓内文件 | 2,415 行 |
| 4.14 | 三个 ZuXun API 模块（datagramZuXun/electronKeyZuXun/handkeyZuXun，227 行） | 227 行 | URL 基（generalTelexPat/generalKeyPat/generalTickerPatTrain） | `makeZuXunApi(base)` 工厂；URL 不变 | ≈150 行 |

## 5. P3：模式级与待业务确认项

1. **主题资产与样式复制（系统性，最大单项）**：`src/assets` 下 GD/HJ/HJJ/KJ/LJ 五套主题目录互拷——952 个重复组、1,844 个冗余文件、**64.1MB**；代码内 2,451 处硬编码 `assets/<主题>/` 路径（132 文件），16 个 basicTheory 页面各内联 `.HJ{}/.HJJ{}/.LJ{}/.KJ{}` 四段近同 style 块。方向：①图片单源化 + 按主题符号链接或构建期复制；②样式改 CSS 变量/`[data-theme]` 选择器 + 每主题一份 less 变量；③`startTest` 对与 `perviewTest` 家族先行试点（≈600 行），全树潜力 >1,500 行代码 + 数十 MB 资产 [INFERENCE：需逐图确认主题间图片确实相同——md5 已证 64.1MB 字节级相同]。
2. **整树疑似不可达（待生产菜单确认）**：本地 `t_menus`（135 行）中 ZuXun 仅 electronKey/handkey 有路由；`useNotification.js:46` 却 push 到不存在的 `datagramZuXunTrain` 路径；telexZuXun 无菜单、无 push 入口。若生产菜单同样无此两项，则 `datagramZuXun`（≈5,700 行）+ `telexZuXun`（≈4,800 行）两整树可直接删除而非合并——**先查生产库菜单再决定 4.1 的合并范围**。
3. **luckysheet/luckyexcel vendored 8.8MB 且功能已坏**：`Excel.vue:44,89` 用的 `window.luckysheet`/`window.LuckyExcel` 全仓无任何 script 加载点 → 组织结构页 Excel 导入打开即 TypeError [INFERENCE]。二选一：补 script 加载修复功能，或（推荐先问业务）删 `Excel.vue` + `structure/Index.vue` 的 Excel 段 + `public/js/{luckysheet,luckyexcel}` + `index.html:100-118` 的 4 个 CSS。
4. **HJ 主题线是否仍发货**：`PreviewHJ.vue`（1,205 行）与 `PreviewHJJ_LJ.vue`（1,593 行）已深度分叉（diff 1,876 行），不宜整并；若 HJ 线停产，`PreviewHJ`+`LoginHJ` 整删候选（待产品确认）。
5. **MorseVoice 双实现**：`MorseVoice.js` 的 forwardTable 与 `MorseVoiceOld.js` 字节级同（码表应提单源，`MorseVoice.js:58-101` 的 keyCodes 声明后从未使用）；`MorseVoiceOld.js:1` 反向 import views 层（common→views 分层违规）。Old 版仍活（wordTrain/disturbCode 两处），中期评估统一切 New（HighPerformance）后可再省 ≈450 行。
6. **小项**：`BroadcastTeachTrain` BroadStudent/BroadTeacher composable 共享块（≈300 行）；`selectTypeTag.js` ×2 工厂化（30 行）；试卷数据归一化块（5 题型 JSON.parse）×4 → `normalizePaper.js`（95 行）；`main.js` 内联三指令移入 `config/directive/`；`pinia global.js:6` theme 字段写后不读；`frontend/docs/2026-09-09-license-fix-plan.md` 一次性文档归档；`frontend/package.json` 三个 tauri 死脚本。
7. **Electron 外壳卫生**：`main.js:9` 注释带 `<br/>` HTML 残渣；GPU 禁用三连语义重叠；`electron/index.js:20` 把 `focus` 当参数传给 `restore()` 而非调用（第二实例恢复后不聚焦 [INFERENCE]）；`controller/system.js:5-14` 同频道 `on`/`handle` 双注册（两路均有活消费者，保留双轨但提取公共实现）。

## 6. 整合路线图（按提交粒度，遵循 AGENTS.md「一任务一提交」）

按「零风险先行、价值密度排序」：

1. **§2 死码删除**（≈5,400 行 + 20MB，可分 6-8 个 commit：组件死码/common 死码/equipmentList1/ZuXun 死件/electron 外壳/死资产/死依赖/死 API 导出）——全部五路核查确证，无行为变化。
2. **§3 字节级重复单源化**（≈4,700 行 + 320KB）——机械替换，风险低；3.1 的 `125W_400W.js:321` 分叉与 §4.1 的路由名失配先确认。
3. **§4.2① studentAddTest 内联 CSS 删除**（~2,000 行，单独立项，零风险高收益）。
4. **§4.4 + §4.14 api 层工厂化**（≈1,400 行）——契约面不变，逐模块提交。
5. **§4.1 ZuXun 四树合并**（≈9,500 行）——**先执行 §5.2 的生产菜单确认**，可能直接升级为「删两树 + 合两树」，工作量与收益双降双升。合并顺带修复 wheel bug、重入保护回灌、路由名失配、TrainLeft 坏引用。
6. **§4.2②/§4.3 basicTheory 成对页与渲染器家族**（≈4,500 行）。
7. **§4.9 拍发家族**（≈1,800 行）——以 patStandard 阈值对齐为前提。
8. **§5.1 主题资产/样式体系改造**（最大单项，单独排期）。
9. **§5.3/§5.4 待确认项**随业务答复处理。

## 7. 防复发建议

- 变体开发一律走「单组件 + 配置对象」（§4.1 的两层方案是仓内可落地模板），禁止整目录复制；新训练类型只允许新增 `config/*.js`。
- 公共逻辑只许住 `common/` 或 `components/`；views 下 `js/` 私有助手若被第二个目录复制就必须上收（本仓 `telex.js` ×10、`trainScore.js` ×9、`telegram.js` ×6、`explain.js` ×4、`knowledgeTabel.js` ×4 均是失控信号）。
- 删功能时连带删组件/资产/API 导出（dept、danmaku、UEditor、gradingRule 双死件都是「删了入口留了身子」）。
- CI 可加 jscpd/simian 类重复率门槛（当前 23.6%，建议先卡「不再上升」）。

---

### 附：方法与局限

- 机械扫描：566 个 .vue/.js（排除 node_modules/dist/out/public），行归一化（去注释/空白）后 8 行窗口哈希 → 3,578 个 ≥12 行跨文件块；md5 字节级去重覆盖代码与 `src/assets`；同名文件 Jaccard ≥0.5 聚类；import 图（含 side-effect import、`@/` 别名、`import.meta.glob`、字符串路由）做死码初筛。
- 菜单可达性以本地 `mysql-project006`（库 `project006`，135 行菜单）+ `backend/database/project006.sql` 种子为准；**生产库菜单若不同，§5.2 结论需重估**。
- 未执行 build/lint/运行验证（只读评审）；行号基于当前工作树快照。
- 6 个评审代理的分域原始结论均已并入上文；父代理独立复核了：同键碰撞已修复、public/dist 未提交、unpkg/wb_colork 死 vendored、equipmentList1 无菜单、TrainLeft 未导入、ZuXun 菜单缺失。
