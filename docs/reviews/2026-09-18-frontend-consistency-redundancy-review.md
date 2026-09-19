# 前端一致性与冗余评审结论文档（多模型对抗评审 · 终审）

- **日期**：2026-09-18
- **范围**：`bw-frontend/frontend/`（Vue 3.5.42 + Vite + ant-design-vue 2.2.8 + vue-router 4.5 hash 模式 + Pinia；src 下 248 .vue + 264 .js）+ `bw-frontend/` Electron 外壳（`electron/` 19 .js/1607 行、`main.js`、`preload.js`、`scripts/`、两份 `package.json`、`frontend/index.html`、`frontend/vite.config.js`、`frontend/test/`、`frontend/public/`）
- **维度**：代码一致性、代码冗余优化（静态取证 + 主持人真实构建实测；**未做运行时验证**）
- **方法**：model-debate 三轮对抗（R1 独立立论 → R2 交叉反驳 → R3 定稿）+ 轮流裁判（R2 由 A 侧模型独立裁判实例裁决、R3 由 B 侧模型独立裁判实例裁决）+ 终审裁判汇总。辩手称**辩手A**/**辩手B**，主持人全程独立实测 H1-H10。完整记录留存于会话工件 `local://debate/`。
- **HEAD**：`4bc26ec`
- **过程事件**：辩手B 的 R2 第一次派发因上游连续 503 失败（10m51s，零产出），重试成功（32m25s）。无其他失败。
- **文档定位（与上一轮的关系）**：`docs/reviews/2026-09-17-frontend-dual-model-review.md` 的 P0（1-7）与 P1（8-14b）已全部执行（commits `57c360e→64c0fd1`、`cde9c5a→70c5059`），P2/P3 未执行。本轮**不是重做上一轮**，四类归因贯穿全文：`[闭合核验]`（上轮声称已修的在 HEAD 是否真闭合）、`[遗留]`（P2/P3 用新证据重定性）、`[新发现]`（上轮盲区：Electron 外壳、`public/` vendored 资产、`src/assets`）、`[撤销/纠错]`。被本轮推翻的上轮结论集中列于 §2.4。
- **终审采信标准**：可复算实测 > 逐行阅读 > 推断 > 断言。两辩手一致不等于正确（本轮 R3 裁判即查出双方都没发现的两处定稿错误）。终审裁判另行抽查 8 条关键结论（§2.5），8/8 与记录一致。

---

## 一、共识点

### 1.1 上一轮 P0/P1 闭合核验（15 项，四档定性）

采信四档措辞（裁决依据见 §2.2-1）：

| 档 | 项 | 实测状态 | 证据强度 |
|---|---|---|---|
| **真闭合（13 项）** | P0-2/3/4/5/6/7、P1-8/9/11/13/14/14b + P0-1 的规则落地部分 | 死代码批删、debounce 修复、name 残留、v-waves、契约面消歧、Pinia 收编、localforage ESM、WebSerialChannel 更名、Pagination emit、moment 收敛、拼写改名批——双辩手独立逐项复核，无残留引用、无假闭合 | 已验证（双方 R1 闭合核验表 + 裁判抽核） |
| **修复闭合、防线未闭合（P0-1）** | g2plot log 死 import 已清 ✓，但 `.eslintrc.js` 的 `no-unused-vars` **无执行入口**：devDeps 无 eslint/prettier 全家、extends 三链悬空、无 lint 脚本、`prettierrc.js` 缺前导点；且已装的 eslint 是 v10.10.0，v9 起不读 `.eslintrc.*`（实跑报错，H2 + 终审复跑）——「防回潮」是纸面的，且**补齐依赖不可行，必须迁 `eslint.config.js` 扁平配置** | 已验证（H2 + 双方 R3 亲核 + 终审抽查 ✓） |
| **名义闭合（P1-10）** | 上轮删 frontend 包 5 项死依赖，但本轮扫出死声明存量 47 条（43 纯死 + 2 配置引用 + 2 休眠，见 §3 档 1），完成度 ≈10%；根包 `dayjs` 即 P1-14 收敛 moment 的直接残留（只扫了 frontend 包） | 已验证（双方 R2 D7 对齐 + R3 T2 封板 + 两轮裁判抽核） |
| **范围闭合（P1-12）** | 字节级合并执行范围内闭合；余 6 组 14 文件为 `ded6af0` 提交信息明示的 J2 闸门暂缓（organization 三胞胎待生产菜单、preJob/postJob 并入 P3、equipment/Index 对菜单耦合），计划内非半途 | 已验证（`git show ded6af0` + md5 复扫） |

### 1.2 一致性共识（去重合并）

| # | 归因 | 问题 | 关键证据 | 证据强度 | 来源 |
|---|---|---|---|---|---|
| C1 | 新发现 | **组训 WS 码表三方不一致（本轮唯一正确性缺陷，跨栈）**：前端 `UnionWs.js:5` `ADD_ROOM_SUCCESS:120 ≡ ADD_ROOM_FAIL:120` 自撞；后端 `UnionConstants.java:21-22` `ADD_ROOM_FAIL(121) ≡ UPDATE_ROOM_INFO(121)` 自撞；前端 `Index.vue:156`、`Room.vue:259` 订阅 121，会把后端失败帧（`WebSocketUnionService.java:339,353`）当房间更新分发；唯一天然对齐码是 120 | 双侧源码对照 + 终审抽查 ✓ | 双方 R1 独立同发现 |
| C2 | 新发现 | **`UnionWs.run()` 全 src 零调用 → 联合训练整域断死**：`run()` 是 connect 唯一入口，两活菜单页（t_menus id 1509）仅 `getInstance()` 后 `handleIsOpen` 100ms 自旋等 `isOpen`，永挂 loading；两页 15 处 `PubSub.subscribe(UnionWsCode.*)` 全为死订阅。`git grep` 证明迁移前提交（19253ec^）同断——存量断裂非近期回归 | grep + 裁判 SQL + 终审抽查 ✓（`.run(` 命中仅 `Ws.getInstance().run()`×2 与 `submission.run`） | B-R1，A-R2 让步并升级 |
| C3 | 新发现 | `unionTrain/Index.vue` 退订错位：`:184-190` ROOM_LIST 退两次（:185,:187）、`:135` 订阅的 USER_JOIN 从未退订——建连修复后即成跨页残留订阅；Room.vue 8↔8 对齐无需改 | H8 + 双方 R3 亲核 | B-R2 新增 |
| C4 | 新发现 | **luckysheet JS 全仓零加载点但被活菜单页调用**：`index.html` script 仅 4 条（:13 runtime-config、:115 entry、:116 animejs、:117 tinymce），luckysheet 仅剩 4 条 CSS link（:96,:101,:106,:111）+ 内联 `#luckysheet-input-box`(:16)；`Excel.vue:44` `window.luckysheet.destroy()` 在活菜单页（structure，t_menus id 1602）必 TypeError；加载器 `initJS(jss)` 在 19253ec 前已注释、b8c215b 删净。员工在线导入功能 100% 断死（模板下载 case 0 独立存活） | 终审抽查 ✓ | B-R1，A-R2 让步 |
| C5 | 新发现 | Electron IPC 三种 RPC 风格并存（send+reply / sendSync+returnValue / invoke+handle，共 11 通道）；`controller.system.getConfig` on+handle **双注册**（system.js:5-14，DB 查询复制两份），消费方分裂（NetSetting.vue:188 sendSync vs HJJ_LJ.vue:217 invoke）；`runtime.js:48` 裸字符串绕开 `ipcApiRoute`；`specialIpcRoute` 空导出零消费 | 终审抽查 ✓（system.js:5-14 双注册确认） | A/B R1 互补 |
| C6 | 新发现 | `changeConfig` 返回契约失真：`system.js:16` 对 nedb `update()` 返回值（**数字**，nedb-promises@6.2.3 `Datastore.js` 无 returnUpdatedDocs 时返回 numAffected——H3 源码实测）取 `.text` 恒 undefined；渲染侧 `NetSetting.vue:249` `let a` 从不读取——双侧死协议数据（写路径本身生效） | H3 + 终审抽查 ✓ | 双方 R1 独立同发现 |
| C7 | 新发现 | **lint 工具链双重失效**：见 §1.1 P0-1 行。附加：`npm ls` 实证 eslint@10.10.0 由 babel-eslint+vue-eslint-parser 传递拉入、prettier@2.8.8 悬在死依赖 `vue-unity-webgl→vue@2.7.16→@vue/compiler-sfc` 传递枝上（删 vue-unity-webgl 即消失）；`vue-unity-webgl` 还把整套 Vue 2 运行时拖进 node_modules | H1/H2 + A-R3 npm ls 亲核 | A-R1，B-R2 让步致谢 |
| C8 | 新发现 | **`@/` 别名配置本身是坏的**：`vite.config.js:21-22` `path.resolve(__dirname, '/src')` 前导 `/` 使 resolve 丢弃 `__dirname`，别名实指文件系统根 `/src`——上轮 A7「已配置零使用」实为「零使用 + 配置错误，启用即断」 | H4 + 终审抽查 ✓ | 第 2 轮裁判独立发现 |
| C9 | 遗留 P2-15/16 | 业务码判断四写法 **365** 处（`===`221/`==`29/`!==`109/`!=`6，宽松 35）无统一 helper；`@/` 零使用、6+ 层深相对路径 446 处——两数与上轮持平，前置未解锁 | 三方复算一致 | 双方 R1 |
| C10 | 遗留 A11 修正 | WS 上层实为**三套**（上轮记两套漏 UnionWs）：Ws.js 用户级单例 / PublicSocket.js 房间级工厂 / UnionWs.js 组训单例（与 Ws.js 26 行单例模板逐行同构，且放在 views/ 而非 common/ws）；URL 获取分裂（PublicSocket 拼 `window.wsUrl`）；logout.js:24-27 已统一四通道清理（P2-20 部分解锁） | 双方独立纠上轮 | 双方 R1 |
| C11 | 新发现 | `common/api` 同端点重复声明 **8 组**（findPage×3、signin×2、getUserDirectory×2、getTwelvemonth×2、getRoleAll×2、mtd/findAll×2、statistics×2、detail×2），含 3 组同文件内双导出；P0-6 commit 自陈的 getUserAll/getUserDirectory 双映射仍在 | H7 + 终审抽查 ✓（8 组精确复现） | A-R1 报 7，B-R2 补至 8 |
| C12 | 新发现 | `window.interfaceStyle = 'HJJ'` 硬编码（`runtime.js:75`，在 :47-65 isEE/web 分支体**之外**无条件执行，:78 `classList.add`）——皮肤是部署期常量非用户可切换项；`window.*` 全局配置总线 27 处与 Pinia 并存；`window.mqttUrl` 只写不读、桌面分支 `mqttWsUrl` 恒空（4 设备页 MQTT 在 Electron 下无地址）、`ocrUrl` 桌面硬编码 | H5 + 终审抽查 ✓（:75/:78 分支外确认） | B-R2/A-R1 |
| C13 | 新发现 | 全局脚本注入存量：animejs vendored **未在两份 package.json 声明**却被 ≥15 文件裸用 `anime(`（承重墙）；tinymce 三轨（npm ^7.2.0 死声明 + vendored 3.0MB 活脚本 + @tinymce/tinymce-vue 活包装器），版本认知失真 | 裁判复核 15 文件 | B-R1，A-R2 加固 |
| C14 | 新发现 | 命名/杂项批：`hasPermission` 名实相反（无权限返回 true 再删元素，ButtonPermission.js）；图片回退指令双轨（img-fallback 1 用 vs real-img 3 用）；`mqttClint` 拼写 46 处；WZTrain 同名 4 份（968/495/225/48 行，48 行版异质）；`equipment.js`(/api/equipmentDevice/*) vs `EquipmentApi.js`(/api/deviceType/*) 端点零重叠——命名误导非重复；API 模块命名仍多轨；guards 直改 router 内部结构 + SESSION_KEYS 双维护；`electron/index.js:20` `restore(focus)` 误传参、survival 死键、端口双写、generateFilename 重复分支（无扩展名产物前导横杠）、routers/file.js 空参 bind、子进程 node_core_ctx 仅 appPath 被用 | 各 file:line 见辩论记录，裁判抽核 | 双方 R1/R2 |

### 1.3 冗余共识

| # | 归因 | 问题 | 量化（采信口径见 §2.1-1） | 证据强度 | 来源 |
|---|---|---|---|---|---|
| R1 | 新发现 | **`src/assets` 跨皮肤字节级重复**：3895 文件/167.8MB 中 952 重复组/2796 文件，可回收 **64.1MiB（全组）/62.5MiB（950 跨皮肤组）**；五皮肤 less 同构组（public.less×5、index.less×4）为代码层旁证。**H9 构建实测定性：仓库/克隆/维护收益，产物收益≈0**（dist 内重复仅 9 组/0.11MiB，vite 内容 hash 折叠 + 零引用不进构建双机制） | 主持人 H5/H9/H10 + 两轮裁判独立复扫 | A-R1（B 自认 assets 盲区） |
| R2 | 新发现 | **零引用死资产（仓库侧）**：字面量路径检测上限 **102.8MiB/1914 文件**（真死 80.1MiB/1235 + 他处被引用内容的纯副本 22.7MiB/679）；首例 GD/KJ/LJ `train/cs.gif` 三胞胎 30.0MiB 零静态引用、从未进 dist，删除零构建影响 | R3 裁判全量实测 | H9 勘误链 + R3 裁判 |
| R3 | 新发现 | **public/ vendored 死资产 18,756,334B ≈17.9MiB**（luckysheet 7,809,487 + pdf 7,249,794 + 3d 2,623,281 + luckyexcel 1,073,772 + jquery **0 字节入库 blob**）+ 零引用小件 5,815B；public/ verbatim 拷贝语义 → **产物侧同额 17.9MiB，是产物体积的最大即得杠杆**。其中 9.9MB（pdf+3d+jquery+小件）无条件可删，8.9MB（luckysheet+luckyexcel+4 CSS）绑 0b 决策 | H9 + 裁判 du -sb 逐字节 | B-R1（A 漏 luckysheet 让步） |
| R4 | 新发现 | **死依赖声明 47 条**：档一纯死 43（根包 6：dayjs/lodash/globby/is-type-of/debug/shortid + frontend deps 16 + devDeps 21 含 uuid）；档二配置引用型 2（vue-demi、@iconify/iconify，删需同步改 vite.config.js:45-47）；档三休眠 lint 链 2（babel-eslint、vue-eslint-parser，随 lint 决策）；@types/tailwindcss 改判活（类型级）保留；tinymce npm 声明单列三轨收敛；另 tauri 死脚本×3、`node_core_utils.js` 8/10 死导出（~140 行，连带 shortid/crypto 死 require） | 双方 D7 逐项对齐 + 两轮裁判抽核 | 双方 R1/R2 |
| R5 | 遗留 | P2/P3 规模冻结：preJob/postJob 同名 35、organization 三胞胎、equipment 双树（171.js 行为差异仍在）、TelegramApi 485 行/72 导出、addTest.js 同构异码复制对（新档 P3）、`Waves.vue` 死组件（注释引用实为 4 处 + LoginHJ.vue:248 一条活的未使用 import，删除须连带）、datagramZuXun/telexZuXun 本地菜单 0 引用 + useNotification.js:46 断链、broadcastTeacheing 菜单闸门不变 | md5/comm/SQL 复算 | 双方 + 裁判补正 |
| R6 | 闭合核验 | 字节级重复无回潮：`.js/.vue` 口径 6 组 14 文件（真值）；含 less + `>100c` 口径 9 组 20 文件（6−1[96B Index 对被滤]+4[less 组]）——双口径并记，全部为已建档门控项 | 双方 R2 D2 精确对账 | 双方 |

### 1.4 已撤回、不得再计的项

- ~~ElectronMorse.js 两个「无主 IPC 频道」~~：主持人误判自撤（`controller` 是本地 `morseController`，非 IPC），辩手A R2 指出正确、辩手B R2 亲读佐证。
- ~~辩手A R9「uuid 10 文件运行时 import、devDeps 分类错误」~~：H6 终结，`from 'uuid'` 全仓零命中（运行时仅 `uuid-umd`×2），A-R3 正式撤回；uuid = 纯死 devDep。
- ~~jquery「4KB」~~：`git cat-file -s` = 0 字节（du 块大小假象，B 自纠），该删但零体积收益。
- ~~「luckyexcel 需运行时确认是否被动态拉起」~~：宿主 luckysheet JS 本体零加载点，外挂无从拉起，确定死（与 luckysheet 同绑 0b 决策）。
- ~~`==`/`var` 总数指标~~：三轮四口径四数字（模板表达式/CSS var() 污染），无决策价值，三方一致弃用。

### 1.5 终审抽查（8 条，全部复核通过）

| # | 抽查项 | 终审实测 | 结果 |
|---|---|---|---|
| 1 | `UnionWs.run()` 零调用 | `\.run\(` 全 src 命中仅 `Ws.getInstance().run()`×2（PreviewHJ.vue:548、PreviewHJJ_LJ.vue:273）与 `submission.run`，unionJob 内零建连调用 | ✓ 一致 |
| 2 | `UnionConstants.java` 121 撞号 | :21 `ADD_ROOM_FAIL(121)` = :22 `UPDATE_ROOM_INFO(121)` | ✓ 一致 |
| 3 | luckysheet 无 JS 加载点 | index.html script 仅 4 条（:13/:115/:116/:117），luckysheet 仅 4 CSS link + :16 内联样式 | ✓ 一致 |
| 4 | eslint v10 不读 `.eslintrc` | `./node_modules/.bin/eslint --version` = v10.10.0；实跑 `eslint src/config/runtime.js` 报 `couldn't find an eslint.config.*`，rc=2 | ✓ 一致 |
| 5 | `vite.config.js:21` 别名 | :21-22 `path.resolve(__dirname, '/src')` / `'/src/assets'` 前导 `/` 确认 | ✓ 一致 |
| 6 | `system.js:16` nedb 返回值 | :16 `db.update(...)` 结果取 `.text`；:5/:11 getConfig on+handle 双注册同步确认 | ✓ 一致 |
| 7 | `common/api` 8 组同端点 | 脚本复现恰 8 组，逐组名与 H7 一致 | ✓ 一致 |
| 8 | `interfaceStyle` 硬编码 | runtime.js:75 `window.interfaceStyle = 'HJJ'` 在 :47-65 分支体之外无条件执行，:78 `classList.add` | ✓ 一致 |

---

## 二、分歧裁决

### 2.1 三处口径冲突的唯一采信口径（终审裁定）

**1. 资产冗余量化 —— 采信第 3 轮裁判的「三笔账」表为唯一口径**（来源：verdict-round3 一.6，裁判全量实测），其余数字列为口径说明：

| 账目 | 机制 | 仓库侧可回收 | 产物侧可回收 | 写进整改工单的数字 |
|---|---|---|---|---|
| ① 零引用不进构建 | 无静态引用的 src/assets 文件不进 dist | **上限 102.8MiB/1914 文件**（真死 80.1MiB/1235 + 纯副本 22.7MiB/679），首例 cs.gif 三胞胎 30.0MiB | 0（本来就不在产物） | 档 1⑥：普查即删，上限 102.8MiB，须过动态引用闸 |
| ② 同字节重复（hash 折叠） | 被引用的同字节文件在 dist 折叠为一份 | 跨皮肤 **62.5MiB**（950 组）/ 全组 64.1MiB（952 组，含 2 同皮肤组）——双口径并记 | ≈0.11MiB（dist 内残留 9 组/119,682B 实测） | 档 4 共享层：62.5-64.1MiB，收益=仓库+维护，**不承诺产物体积** |
| ③ public/ verbatim 死资产 | public/ 整体拷贝入 dist | 17.9MiB（18,756,334B） | **17.9MiB**（dist 逐字节相等） | 档 0b/1：唯一有真实产物收益的即得项 |

口径说明（每个数字量的是什么）：
- **主持人 H10**（源文件 md5 是否出现在 dist + 文件名/皮肤路径文本引用二次筛分）：「从未进 dist 3023 文件/82.5MB」按**内容**计——与裁判「真死 80.1MiB」同一实体的两种测法，量级吻合；「537 文件/35.8MB 文本零引用」是**最保守首批安全集**，但文件名过滤会产生假「被引用」（cs.gif 三胞胎因 HJ 路径引用了同名文件而落入「2486/46.7MB 需调查」桶，实为裁判已证的可即删真死）——故 537/35.8MB 作**首批执行下限**，1914/102.8MiB 作**普查上限**，2486/46.7MB 是两者之间需逐项调查的地带（构建日志的 `P.png didn't resolve` 警告证明「未进产物 ≠ 可安全删除」）。
- **第 3 轮裁判**：字面量路径检测的内容级账，采为工单口径（见上表）。
- **辩手A/B R3**：与裁判表一致（B 的分皮肤被引用字节 GD 2.1/HJ 26.2/HJJ 11.6/KJ 12.5/LJ 12.6MB 经裁判独立测量证实）。
- 旁证算术：源 167.8MB − 重复 64.1MB ≈ 唯一字节 103.7MB ≈ dist 102.8MB（注意：dist 总量 102.8MB 与零引用上限 102.8MiB 是**巧合同数的两个不同量**）。
- **删皮肤 ≠ 删目录**（裁判实测坐实）：HJ 树被全局承重 26.2MiB（`main.js:1-2` 无条件 import HJ 样式、`unionTrain/Index.vue:111` 硬编码 HJ 资产）——即使部署 HJJ，HJ 仍占 dist 21.0MiB。产物体积诉求另立「按部署皮肤裁剪」独立项（档 4b）。

**2. 上轮闭合定性措辞 —— 采信四档框架**（辩手B R2 D1 提出，第 2 轮裁判封板，辩手A R3 X2 全盘接受）：真闭合 13 / 范围闭合 P1-12 / 名义闭合 P1-10 / 修复闭合但防线未闭合 P0-1。P1-10 完成度按 R3 对账刷新为 ≈10%（5/47 条声明件）。弃用「全部真闭合」总标签（A-R2 自纠 headline 过度概括；B-R2 自纠分母 14→15）。

**3. 优先级排序哲学 —— 采信「依赖导向框架 + 裁判细化 + 资产并行泳道折中」**（来源：R2 裁决二.9，R3 双方无保留接受）：
- 纯删除批（死依赖 43+2、无条件 vendored 9.9MB、零引用资产）**引用已证零，不需要 lint 兜底，先于 lint 执行**——此处采辩手A 的收益直觉；
- lint 重建是**一切 codemod 类**（@/ 迁移 446、defineOptions 239、业务码 helper 365、共享层字面量改写 4543）的硬前置——此处采辩手B 的依赖论证；H2 抬高了 lint 成本（必须迁扁平配置）但不改变依赖方向；
- 资产共享层无下游依赖者、自带验证闭环（build 绿基线 57s + dist 对比 + 视觉冒烟），作**并行泳道**随时启动，不阻塞不插队；
- 档 0 正确性包（unionJob WS）绝对最先——唯一正确性缺陷且菜单可达，双方 R2 起即一致。

### 2.2 事实层分歧逐条裁决

| # | 争议 | 裁决 | 采信方 | 理由 | 来源轮次 |
|---|---|---|---|---|---|
| 1 | 闭合定性总标签 | 四档框架（见 §2.1-2） | 辩手B | 行级事实双方本一致，B 框架对读者信息量最大；P1-10 用「真闭合」会误导资源分配 | R2 D1，R2 裁决 |
| 2 | 字节级重复 6 vs 9 组 | 双方各对，口径差；`.js/.vue` 6 组 14 文件为真值，双轨并记 | 各半 | 6−1（96B 被 `>100c` 滤）+4（less 组）=9 精确对账 | R2 D2 |
| 3 | 死 vendored 终值 10.7 vs 18.4MB | **18,756,334B**；A 漏 luckysheet JS 7.8MB | 辩手B | 裁判 du -sb 逐字节吻合；附裁判纠 B「10.9MB 无条件可删」算术笔误 → 实为 **9.9MB** | R2 D3，R2 裁决 |
| 4 | 同端点 7 vs 8 组 | **8 组** | 辩手B/主持人 | A 漏 `role/getRoleAll` 且 ZuXun 镜像合算；裁判注：B 所附命令有引号缺陷，数值采信、命令不采信；终审抽查 8 组复现 ✓ | R2，H7 |
| 5 | 业务码 364 vs 365 | **365**（221/29/109/6） | 辩手A/主持人 | B 自纠加法错（分项和即 365） | R2 |
| 6 | `<script setup>` 207 vs 208 | **208** | 辩手A/主持人 | 裁判复算 208；「裸 script 239」计数已验证、「仅声明 name」内容断言降 [推断] | R2 裁决 |
| 7 | uuid 双库「10 文件运行时 import」 | 不成立，uuid=纯死 devDep | 辩手B | H6：`from 'uuid'` 全仓 0；A-R3 正式撤回 | R2→R3 |
| 8 | file-saver / vue-demi / @purge-icons/generated 归属 | file-saver 死（A 漏）；vue-demi 配置引用型（B 漏）；@purge-icons/generated **未在 package.json 声明**，从清单剔除（B 口径错） | B / A / A | 逐项裁判复核 | R2 D7 |
| 9 | @types/tailwindcss、vue-eslint-parser | 前者**活（类型级）保留**（B 标签 + A 的机械处理：将来删须同步删 tailwind.config.js:3 JSDoc）；后者休眠链，扁平配置方案下可能转正 | 辩手B | tailwind 工具链是活的；H2 后休眠链不可原样复活 | R2→R3，R3 裁决 |
| 10 | assets 可合并性 | 机械安全（4543 静态字面量=3982+561css，无 glob/动态拼接，部署期常量）+ 语义逐组放行（吸收 A 的刻意隔离担忧）；收益定性按 H9 修正 | 辩手B（框架）| 裁判四项全部复核属实；B 的 hash 折叠 [推断] 被 H9 实测证实 | R2 D4，R2 裁决 |
| 11 | Waves.vue 证据形态 | 死组件结论不变；A「3 处引用均注释」不准——LoginHJ.vue:248 是活的未使用 import，注释引用实为 4 处（裁判补 LoginLJ:6） | 辩手B + 裁判 | 亲读复核 | R2，R2 裁决 |
| 12 | equipment.js vs EquipmentApi.js 同源？ | 端点零重叠（comm 空集），命名误导非重复实现 | 辩手B | 上轮 A5 的 [推断] 就此定性关闭 | R2 |
| 13 | T1 载荷计数 12/3 vs 13/2 | **13 处 JSON.parse / 2 处不读载荷 / 0 处期待对象**；A-R3 汇总行算术笔误（其逐处罗列本身支持 13/2） | 辩手B + R3 裁判 | 裁判 15 处逐格亲核；两辩手三轮均未发现，裁判清点纠正 | R3 裁决 |
| 14 | 失败帧边界 `JSON.parse(null)` 不抛错？ | **前提错误**：Gson 默认不序列化 null → 线上帧 `{"code":121}` data 字段缺席 → `frame.data === undefined` → `JSON.parse(undefined)` 抛 SyntaxError 被 `PubSub.safeExecute` 吞成 console.error。失败订阅回调**禁止 parse** | 辩手B + R3 裁判 | 裁判 node -e 实测 + JSONUtils.java/ResponseModel.java 亲核；A 落地代码侥幸不受影响，事实表述按 B 口径 | R3 裁决 |
| 15 | Ws.js 可否作载荷判例 | 不可——Ws.js:15 分发异构（对象/true/整帧混发）；判例是 UnionWs 订阅者齐套期望字符串 | 辩手A | B-R3 采纳该推理修正；另 A 纠 B「TypeError」应为「SyntaxError 且被吞」 | R3 |
| 16 | 跨皮肤重复 64.1MiB 口径 | 混用口径勘误：**全组 952 组=64.14MiB；纯跨皮肤 950 组=62.46MiB**（差 2 个同皮肤组 1,758,407B），双口径并记 | R3 裁判 | 双方记录均未察觉 | R3 裁决 |
| 17 | 选码 122 | 双方一致（12X 家族语义、两侧空闲经全枚举亲核、测试仅断言 120 不破坏） | 无分歧 | 程序性注记 | R3 |
| 18 | lint「补齐依赖即可生效」 | 不成立——eslint v10 不读 `.eslintrc.*`，唯一正道迁 `eslint.config.js` 扁平配置（或不推荐的显式降版 v8） | 主持人 H2 | 实跑报错 + 终审复跑 ✓ | H2，R3 |

### 2.3 上一轮结论的修正（本轮推翻/改判的上轮结论）

| 上轮结论 | 本轮改判 | 依据 |
|---|---|---|
| 总评「传输与封装层健康」 | **部分推翻**：WS 底层单基座结论仍立，但 unionJob 上层整域断死（UnionWs.run 零调用）+ 码表三方不一致——上轮完全未覆盖 UnionWs | C1/C2，B-R1 |
| A7「`@/` 别名已配置却零使用」（纯风格问题，P2-16 codemod 即可） | **别名配置本身是坏的**（前导 `/` 丢弃 `__dirname`），codemod 前必须先修 vite.config.js:21-22 两行 | H4，第 2 轮裁判发现 |
| P1-10「依赖清理 ✅ 已全部执行」 | 改判**名义闭合**：完成度 ≈10%（5/47 条声明件）；上轮清理范围只扫了 frontend 包 5 项，根包 dayjs 即 P1-14 的直接残留 | R2 D1/D7，R3 T2 |
| P0-1「CI 加 no-unused-imports ✅」 | 改判**修复闭合、防线未闭合**：规则落为 no-unused-vars 且无执行入口（工具链缺席 + eslint v10 不读 .eslintrc），防回潮纸面化 | H2，A-R1 C7 |
| A11「WS 上层两套并行」 | **实为三套**（漏计 UnionWs.js） | 双方 R1 |
| P0-6「契约面同名导出去重 ✅」 | 真闭合但**同端点双映射残留**（getUserAll/getUserDirectory）且同族扩展至 8 组 | C11，H7 |
| A5「equipment.js 与 EquipmentApi.js 两后端资源是否同源 [推断]」 | 定性关闭：端点零重叠，命名误导而非重复 | B-R1 comm 比对 |
| P1-12「字节级相同合并 ✅」 | 改判**范围闭合**：余 6 组为 ded6af0 明示的计划内暂缓（非缺陷，但「✅ 已全部执行」表述过强） | R2 D1 |
| 上轮评审范围本身 | **三大盲区补齐**：Electron 外壳（19 js/1607 行）、public/ vendored 22MB（死 17.9MiB）、src/assets 167.8MB（重复 64.1MiB + 零引用上限 102.8MiB）——冗余量级冠军全部在上轮范围之外 | 本轮 R1-R3 |
| 上轮 B12 依赖冗余（5 项） | 扩容至 47 条声明件三档清单 | R3 T2 |

---

## 三、最终结论与整改路线图

### 3.1 总体结论

上轮 P0/P1 执行质量高（13/15 真闭合，无假闭合、无执行期残留引用），但「闭合」语义须按四档修正。本轮增量的重心不在 Vue 视图层（P2/P3 遗留规模冻结），而在三个上轮盲区：**① 唯一正确性缺陷**——联合训练 WS 整域断死（run 零调用）疑加码表三方撞号，跨栈契约面；**② 工程防线缺席**——lint 工具链双重失效使上轮 P0-1 的防回潮承诺落空，且修复路径只有迁扁平配置一条；**③ 冗余数量级冠军换位**——资产层（零引用上限 102.8MiB + 跨皮肤重复 62.5MiB + vendored 死资产 17.9MiB）远超代码层全部拷贝之和，但 H9 构建实测证明其中**只有 public/ vendored 的 17.9MiB 有产物体积收益**，其余是仓库/克隆/维护成本的账——整改叙事必须按此定性，不得再以「装包瘦身」立项资产去重。

### 3.2 整改路线图（终审定稿，可直接作执行蓝本）

每条标：前置条件 / 独立回滚 / 红线 5（跨栈契约不可单侧改）/ commit 切分。

| 档 | 内容 | 前置条件 | 独立回滚 | 红线 5 | commit 切分 |
|----|------|---------|---------|--------|------------|
| **0. unionJob WS 修复包（正确性，最先）** | 后端 `UnionConstants.java:21` `ADD_ROOM_FAIL(121→122)` + 前端 `UnionWs.js:5` `ADD_ROOM_FAIL:120→122`（同时解除前端 120 自撞；`UPDATE_ROOM_INFO(121)` 不动=零订阅方改动）；两页建连 `Index.vue:167`/`Room.vue:263` 后各插 `unionWs.run(frame => PubSub.publish(frame.code, frame.data))`（13/2 载荷规格，订阅者期望字符串）；修 `Index.vue:187` 重复退订 ROOM_LIST→改退 USER_JOIN；可选 ADD_ROOM_FAIL 失败订阅弹错（**回调禁 parse**，失败帧 data=undefined） | 跨栈协议会签（后端改号是唯一后端动作）；**建连 commit 禁止先于改码 commit**（否则激活 121 误分发） | Commit 2/3 各自独立可回滚（回到零建连现状）；Commit 1 在 Commit 2 已上后须连带回滚 | **是**（Commit 1） | C1=后端:21+前端:5 同 commit（+建议补 122 失败路径测试断言，现测试仅断 120）；C2=建连两行+退订修正；C3(可选)=失败订阅 UI；C4=真实建连运行时冒烟（验收标准，见 §4） |
| **0b. 决策闸（零工时）** | luckysheet 员工导入存废：甲=恢复（index.html 补 2 行 script；若做，改 structure 页按需动态注入而非全局标签——7.4MB 首屏 parse 代价；注意 vendored plugins JS 疑缺、loading.gif 依赖 /cdn、2.1.13 上游停更）；乙=整链删除（public 两目录 + 4 CSS + 内联样式 + Excel.vue 134 行 + Utils.js mergeExcelInfo + structure 页导入链 + UserApi.importUser；仓库 −8.9MB / dist −8.4MB）。**双方与两轮裁判一致推荐乙** | 业务确认（功能现状 100% 断死，是「复活 vs 安葬」而非修 regression；模板下载通道独立存活） | 是 | 否 | 甲=1 commit（2 行+冒烟）；乙=1-2 commit（vendored 删除可与代码链分拆） |
| **1. 纯删除批** | ① 死依赖 43 纯死 + 2 配置引用型（同步删 vite.config.js:47 exclude / :45-46 include 与注释）；② 无条件 vendored 9.9MB（pdf+3d+jquery 0B+小件 5,815B）；③ tauri 死脚本×3；④ node_core_utils 8 死导出+子进程 ctx 裁为 `{appPath}`；⑤ Waves.vue+LoginHJ.vue:248；⑥ **零引用资产普查即删**：首批取 H10 的 537 文件/35.8MB 保守集 + 裁判已证实例 cs.gif 三胞胎 30.0MiB，普查上限 102.8MiB/1914 文件，「引用未进产物」的 46.7MB 逐项调查后分批 | 引用已证零，无前置；删后跑一次 `vite build`（绿基线 57s 已立）+ 启动冒烟兜底动态 require 盲区；**不需要 lint 前置** | 逐包/逐文件/逐目录独立 | 否 | 按①-⑥各自成 commit；①内根包/frontend deps/devDeps 三 commit；⑥按皮肤或目录分批 |
| **2. lint 重建** | 迁 `eslint.config.js` 扁平配置 + 显式声明 eslint/eslint-plugin-vue（vue-eslint-parser 转正、babel-eslint 废弃删除、不得依赖传递 prettier@2.8.8）；`prettierrc.js`→`.prettierrc.js` 或并入扁平配置；commitlint 去留决策；跑基线定 warn/error 门槛 | 建议在档 1 后（死重先清再定基线）；**是档 4 codemod 与 P3-25 的硬前置** | 是 | 否 | 1 commit 配置迁移 + 1 commit 基线豁免清单 |
| **3. 外壳小修批** | IPC 收敛（新通道一律 invoke/handle；getConfig 双注册合一保留 handle；sendSync 存量按 preload.js:9-12 清单迁移并同步注释）；changeConfig 返回值修正（nedb 返回数字，回填 `{ok:true}` 或读回文档，渲染侧删死变量）；restore/focus 误传参；survival 死键；端口双写；generateFilename；routers/file.js 空 bind；runtime.js:48 接入 ipcApiRoute+删 specialIpcRoute | preload 注释同步更新 | 逐项独立 | 否 | IPC 收敛 1 commit；其余杂项 1 commit |
| **4. 资产共享层（并行泳道，不阻塞 0-3）** | 第一个 commit 先修 `vite.config.js:21-22` 别名前导 `/`（H4，两行零风险，`@/` 现零使用无行为变化）；md5 清单冻结 → 「共享层+皮肤覆盖层」迁移 + 字面量 codemod（4543 处：ESM 819+url() 3713+属性 11）；五皮肤 less 同构组并入；试点=cs.gif 同字节组 + receiveBg.mp4 同 hash 组成员 | 产品逐组语义放行（防刻意皮肤隔离）+ 档 2 lint 兜底 codemod；验证=vite build+dist manifest 对比（同字节迁移后 hash 名不变）+ 视觉冒烟 | 是（纯移动+路径改写，单 commit revert） | 否 | 别名修复 1 commit；试点每组 1 commit；批量迁移按目录分 commit |
| **4b. 按部署皮肤裁剪产物（独立立项）** | 构建期只保留 interfaceStyle 对应皮肤+共享层——**资产侧唯一真实产物体积杠杆**（4 套不可达皮肤被引用差异字节实测占 dist ≈30MiB：GD 1.7+HJ 21.0+KJ 6.4+LJ 2.0） | 先解 HJ 全局承重（main.js:1-2 等 26.2MiB 引用迁到部署变量）+ 部署形态确认 | 是（构建管线单点） | 否 | 承重迁移与管线改造分 commit |
| **5. 维持门控（不提前）** | 446 深路径 codemod、365 业务码 helper、defineOptions 批改、preJob/postJob 35 同名、organization/equipment 双树、broadcastTeacheing 改名（菜单同事务）、window 总线 27 处单独立项、UnionWs 迁 common/ws（**须在档 0 验证后**——功能未复活前做结构合并等于给死代码搬家）、mqttClint/hasPermission/WZTrain-48 行版改名等小修 | 生产菜单表复核 / 业务确认（J2/J3 闸门不变）；codemod 类等档 2 | 各项独立 | 否（broadcastTeacheing 需同步本地菜单数据，非代码契约） | 按原 P2/P3 分批 |

### 3.3 执行进展与决策回填（2026-09-18 当日，评审后执行）

本节记录路线图落地情况与三处待决项的业主决策，文档其余部分维持评审当时的结论口径不改写。

| 档 | 状态 | 提交 |
|---|---|---|
| 0 unionJob WS 修复包 | ✅ 已执行。Commit 1 后端 `UnionConstants.java` `ADD_ROOM_FAIL(121→122)` + 前端码表同 commit（红线 5），并加 `UnionConstantsTest` 守 `getByCode` 往返（撞号还原即失败）；Commit 2 两页补 `run()` 建连、分发规格固定在 `UnionWs` 内（按 code 广播 `frame.data`）、修 `Index.vue` 退订错位、新增 `ADD_ROOM_FAIL` 弹错（回调不 parse） | `3c424f1`、`1eefd1a` |
| 0b luckysheet 决策闸 | ✅ 业主决策 **预案乙（整链删除）**，已执行 | `f34c1c5` |
| 1 纯删除批 | ✅ 已执行：死依赖 40 条 + tauri 死脚本 3 个；vendored 9.9MB；luckysheet 链 8.9MB；electron 子进程脚手架（`node_core_utils` 223→90 行、子进程 ctx 20→8 行）；`Waves.vue`；零引用资产 541 文件/68.0MB；structure 页注释态导入导出入口 | `4bad88c`、`d3e1183`、`a4cccbb`、`da82e06`、`8f24764`、`930354f` |
| 2 lint 重建 | ✅ 已执行：迁 `eslint.config.mjs` 扁平配置，显式声明 eslint/eslint-plugin-vue/globals，`vue-eslint-parser` 升为 v10 必需 peer，删废弃 `babel-eslint` 与失效 `prettierrc.js`，新增 `lint`/`lint:fix` 脚本。**基线 2444 problems = 487 error + 1957 warning / 363 文件**，不设失败闸。**基线已清零至 11 error / 163 warning / 0 解析失败，见 §3.5** | `8f4239a`，清零批 `eb03616`→`3486cb0` |
| 3 外壳小修批 | ✅ 已执行（2026-09-19）：① IPC 全面收敛 invoke/handle，`getConfig` 双注册合一、`changeConfig`/`getLocalIP`/`linkPort` 由 on 改 handle，preload 下线 `sendSync`；② 端口与默认服务地址收敛到新增的 `electron/shared/ports.js`（原本四处各写一遍）。其余条目（`restore()/focus()` 误传参、survival 死键、generateFilename、`routers/file.js` 空 bind、specialIpcRoute、changeConfig 返回值）逐项核对**已在档 0/1 期间修掉**，现不存在。**首次在 WSLg 下真启 Electron 外壳验收**，详见 §3.6 | `0d35210`、`14d9528` |
| 4 / 4b / 5 | 未执行（门控与决策条件不变） | — |

**执行期新增发现（评审与两轮裁判均未覆盖）**

1. **`terser` 从未声明却是构建必需**：`vite.config.js:55` 用 `minify:'terser'`。档 1 删掉 40 条死声明后 npm 首次真正剪枝（removed 1229 packages），terser 随传递树消失，构建立即报 `terser not found`。已显式声明 —— 与 eslint/prettier/animejs 同属「已装未声明」缺口，说明该类缺口的危害不止于认知失真，会在依赖清理时变成真实故障。
2. **零引用资产删除集不能按扩展名建语料**：`src/views/manage/basicTheory/**/css/` 下有 9 个**无扩展名**的 CSS 文件带活的 `url()` 引用；纳入语料后 13 个文件（各皮肤 `test/newJt.png`、`test/jt.png`、`test/titleTop.png`）退出删除集，否则会误删（dist 里 `newJt-*.png` 证明其在产出）。引用语料必须按内容遍历。
3. **lint 上线即抓出三类真缺陷**：① `components/danmaku/Danmaku.vue:27` 解析失败（活文件带解析错早会打断构建，反向印证其为死代码，属评审 §1.5 的零引用候选之一）；② 组训 `student.vue` 续训快照漏 `.value`，`patPage`/`speed` 落盘恒 undefined（已修 `4e99bb1`；另查明该快照 key 全仓无读取方，活模块实际走 `useConfirmedSubmission`）；③ `equipment/trainScore/Index.vue`（菜单 id 190012，活页）把 Ref 对象裸传给 paho 当 MQTT 主题名、`f`/`s` 从未赋 `.value`（已修 `f32ea3a`，按孪生页语义以 `res.data.deviceId` 构造主题并加就绪守卫）。
4. **联合训练大厅页无菜单路由**：`t_menus` 只有 `unionTrainRoom`（id 1509、`isMenu:false`、需 `?id=`），大厅 `unionTrain/Index` 无任何路由，而 `Room.vue:311` 解散房间后正指向该不存在的路由。

**三处待决项的业主决策**

| 待决项 | 决策 | 处置 |
|---|---|---|
| luckysheet 员工在线导入存废（§4.2-2） | 预案乙，整链删除 | 已执行 `f34c1c5` |
| 后端 `POST /api/user/importUser` 去留 | **保留**（挂着密码迁移与 207 授权两条活测试契约，日后重做导入 UI 可复用） | 前端 `UserApi.js` 的 `importUser` 导出一并保留，并就地注明「死代码扫除勿删」，避免单删前端造出孤儿端点 |
| 联合训练大厅页菜单路由 | **不补充**——该页系早期实验功能 | 不再投入；档 0 的建连与码表修复已落地，功能面维持实验状态 |

### 3.4 页面级可视验收（2026-09-18 晚，最大未决风险已闭合）

此前 §4.2-1 记录的「全程未做页面级可视验证」已闭合：业主告知**可使用内置管理员万能码**
（`common/utils/VerifyLicense.js:8` 的 `testCode = 'wjkj2025~'`，`:241` 跳过解密与设备码比对）。
据此起真实环境验收：后端 `quarkus:dev` + MySQL `project006` + `vite dev` + 无头 Chromium，
以免鉴权 `signin` 注册一次性账号走真实登录（事后已连 `t_user_role` 一并删除，`admin`/`user` 角色映射未动）。

| 验收对象 | 结果 |
|---|---|
| **联合训练房间页**（档 0 核心） | **通过，且验收中又发现并修掉一层缺陷**。建连修复（`1eefd1a`）让 loading 自旋退出，但房间标题恒 `--`、人数恒 `/` —— 根因是 `WebSocketUnionService.onMessage` 对入站 `data` 一律 `JSONUtils.toJson`，字符串 id 被加引号，`onlineRooms.get("\"id\"")` 永远查不到（另致 `Integer.parseInt` 抛异常被兜底 catch 吞成「处理失败」）。修复见 `4ace543`（新增 `scalarOrJson`：字符串原样、对象才序列化）+ 回归测试 `roomIdSentAsStringResolvesRoom`。修复后页面渲染「验收房间-1-0」、人数「2/4」、两成员入座，建房方 WS 依次收到 `2`(USER_JOIN)、`121`、`111`(ROOM_USER_BROADCAST `{"type":"join",...}`) |
| **404 页**（`1eb1a53`） | 通过。未知路径重定向到 `/404` 后渲染「404 / 页面不存在或当前账号无权访问 / 返回上一页」，修复前是全白 |
| **固定报文区段**（`1eb1a53`） | 通过。`fixedMessageManage` 子页面完整渲染（筛选、5 条数据表格、分页、新增按钮）；修复前空 `v-slot` 会让子页面完全不渲染 |
| **组织结构页**（`f34c1c5` 删 luckysheet 链、`930354f` 删注释态入口） | 通过。检索/新增人员/7 条人员表格/分页/每行 4 个操作按钮齐全，**无 pageerror**（头像的 `ERR_CONNECTION_REFUSED` 是未起文件服务 8000 端口，与改动无关） |
| **设备考核页**（`f32ea3a` MQTT 主题） | 页面加载无 pageerror（删掉的 `interval`/`numValue`、改名的 `f`/`s`、新增就绪守卫均未破坏模块）；但设备训练列表无数据、也无真实 MQTT broker，**呼叫路径仍需现场带设备验收** |
| **`@` 别名**（`f9f7224`） | 通过。临时把 `runtime.js:2` 改成 `@/common/http/endpoint.js`，`vite build` 无 `Could not resolve`；临时改动已还原 |
| **Slider**（`bb16c12` 本地 `val`→`current`） | 通过。收报 Koch 训练页 3 个 `radioSlider` 全部渲染，档位标签取到真实值（`60`/`六`/`关`）、手柄位于 `left:100%` —— 标签的 `.on` 命中与手柄位置**都由 `current` 计算**，若绑定失效两者都不会成立。拖拽交互被页面遮罩（「请开始练习」蒙层与残留弹窗）拦住，未能实测滑动 |
| **Pagination**（`bb16c12` 本地 `tableAllData`→`rows`） | 结构通过。线路通报、装备训练列表两页渲染「共 0 条数据」（即模板读 `rows.length` 生效）与上下页按钮，无 pageerror；但这几页无业务数据，**翻页切片逻辑未能实测** |
| **PreviewMessage / GradeModal**（`bb16c12`） | 未覆盖。二者分别依赖收报训练记录与评分规则弹窗入口，本轮环境无对应业务数据 |

仍需现场/实机验收的剩余项：设备 MQTT 呼叫（需真实设备与 broker）、Electron 桌面壳侧（网络设置页 sendSync 路径、串口连接）、生产菜单表相关判定。

### 3.5 档 2 基线清零（2026-09-18 深夜，commits `eb03616`→`3486cb0`）

档 2 只建了 lint 基线（2444 problems = 487 error + 1957 warning / 363 文件）、不设失败闸。
本轮把基线按「每类一个可独立回滚的 commit」清到：**error 487→11、warning 1957→163、解析失败 1→0**。

| 类别 | 处置 | 结果 | 提交 |
|---|---|---|---|
| 未使用 import | codemod 逐名摘除具名说明符，摘空则整条删；232 文件 | `no-unused-vars` 1799→793 | `eb03616` |
| `no-undef` 40 | 分真缺陷与裸用全局两类，见下 | **40→0** | `ad7b4ef`、`0fcd14a`、`99bfd49` |
| `no-console` 109 | 删 80 处调试输出；27 处 `console.error`/`warn` 是唯一故障上报通道，改 lint 规则显式放行；2 处上传失败诊断由 `log` 升 `error` | **109→0** | `fda8dc7` |
| `no-unused-vars` 余量 | 四批：残余 import + CountDown 碰撞治根、catch 绑定、解构成员、声明与死函数 | 793→163 | `d185a13`、`85ecc98`、`5c0aeed`、`04d148b`、`25dd429`、`3486cb0` |
| `vue/no-mutating-props` 11 | **未改，见 §4.2-9** | 11 | — |

**本轮修掉的运行时缺陷（都做了「修复前坏、修复后好」的同环境 A/B 实测）**

1. **报务用语训练页整页白屏**：`preJob/ditto/wording/js/termTrain.js` 用 `wpms` 但从未声明，
   且该名出现在 setup 的 return 对象里，ReferenceError 在 setup 阶段抛出。
   实测修复前 `#app` 仅 31 节点（只剩外壳），修复后完整渲染、播放码率 40/45/…/95 共 12 档。
2. **装备考核页整页白屏**：`equipment/trainScore/Index.vue` 用 `useRoute()`/`useRouter()`
   却无 vue-router import（孪生页有）。实测 31 节点 → 92 节点（干扰音列表齐全）。
3. **注册表单性别不回填**：`login/useLogin.js` 的 `idCardMessage` 用 `moment()` 但该模块未 import
   （5 个登录皮肤各自 import 的是 .vue 模块作用域，composable 取不到；`window.moment` 实测不存在）。
   页内直接 import 真实模块调用：修复前抛 `moment is not defined`，修复后正常置 `userSex`。
   症状静默——Vue 把 handler 异常吞在错误边界里，用户只看到「失焦后什么也没发生」。
4. **装备训练三处跳转必跳 404**：三处 `router.push` 把子菜单 path 写成裸名
   （`/equipmentScore`×2、`/equipmentList`×1），而菜单里带皮肤后缀（`equipmentScoreHJJ` 等）。
   新增 `Utils.js: resolveSiblingPath(route, prefix)` 在 matched 链上找真正持有该前缀子节点的层级
   （不按下标，因 `guards.js:112` 的 `nestedPatDown` 会 splice 掉 TransitionPage 层使下标漂移）。
   实测：生成训练→考核页 624 节点、开始训练→考核页、新增训练→装备列表 4 张卡，此前三者全 404。
5. **装备启动缺配置闸**：`openEquipment()` 在 `window.mqttUrl` 缺失时会把
   `equipment173://undefined,<id>` 传给外部装备程序（裸 `mqttUrl` 则直接 ReferenceError）。
   已在唯一入口加闸：地址缺失 `message.error` 并返回。
6. **`wb_color.js` 的 `d_BA` 是 `d_AO` 的过期副本**：仍用裸 `x` 与裸 `m()`/`g()`（活方法已改 `this.m`/`this.g`），
   调用即抛；零引用，连同另 3 个死方法删除（−128 行），并在真实页面实测活方法链
   `init→d_AE→Line→ZG_FillColor→ZG_Show→d_AO` 全跑通且真实写入像素。

**执行期踩到并记录的三个坑（均已回退重做，供后人规避）**

1. **`no-unused-vars` 对「模板 kebab 标签 + 脚本同名 camelCase 变量」恒误报**：7 个文件
   `<count-down ref="countDown">` + `import CountDown` + `const countDown = ref(null)`，
   Vue 解析 `<count-down>` 时先命中 ref，于是 `CountDown` 被判未使用；删掉后编译产物从
   `$setup["CountDown"]` 退化为 `$setup["countDown"]`（即 `ref(null)`），倒计时废掉。
   第一次（`eb03616`）靠「kebab 碰撞审计」抓出并整体回退，第二次（`d185a13`）治根——
   把碰撞的 ref 改名 `countDownRef`，lint 不再误报，无需抑制注释。
   **此后每批删改都强制重跑该审计**（逐文件比对「删掉的名字 camelize 后是否仍被模板用作标签」）。
2. **`no-unused-vars` 也报「只写不读」的变量**：按「右值是纯调用」直接删声明，会把后续赋值
   变成未声明标识符（ESM 严格模式运行即抛）。`trainScore.js` 的 `numberChart`/`columnChart`、
   `Room.vue` 的 `type_1`/`type_2` 就这样被误删，lint 立刻冒出 8 条 `no-undef`。
   加两条守卫后重做：① 该标识符（剔除注释与字符串后）在代码里只出现一次；② 行尾带逗号的
   多声明符语句一律不动（`NipSerial.vue:53` 跨行多声明符被删首行即失去 `let`）。
3. **块级删除器不能用「括号配平」找函数体**：箭头函数参数表 `(a, b) =>` 会让配平在 `)` 处提前收尾，
   切在语句中间，当场 28 个解析错误。改为只按函数体 `{` 配平；删除区间起点放行首、
   结尾只吞本行剩余与一个换行（否则残留缩进会粘到下一行，制造伪"新增"行）。

**验证口径**：每批都跑 lint（含解析失败计数）+ `vite build` + node 测试 39/39，
并对该批改动中**菜单可达**的路由做整页加载巡检（捕获 pageerror 与组件解析失败）。
累计巡检覆盖 20/31/42/58/11/24 条路由，节点数区间 72–624，零 pageerror。

**更正一条执行期误判**：`ad7b4ef` 的提交正文曾把 `/api/radiotelephone/listPage` 的 500 记为
「快照落后于迁移」。**该判断错误**——`backend/scripts/rehearse-migrations.sh` 的口径是
「导入快照 → 顺序执行全部迁移 → 实体 schema 差分为空」，快照本就是迁移起点而非终态。
真实原因是本地 `project006` 只导了快照没跑迁移；按序补跑 21 个迁移（全部成功、幂等无报错）后
该端点实测恢复 200。仓库无缺陷。

### 3.6 档 3 外壳小修与首次 Electron 实机验收（2026-09-19，commits `0d35210`、`14d9528`）

此前所有验收都在浏览器里做，Electron 外壳侧（§4.2-2 列的 sendSync 路径、串口连接）**从未实机验过**。
本轮发现 WSL2 的 WSLg 提供 `DISPLAY=:0`，可以真启外壳：
`electron . --remote-debugging-port=9333` + CDP 接入，并用 `Runtime.evaluate` 打**主世界**
（Puppeteer 默认落在隔离世界，看不到 contextBridge 注入的 `window.electron`，
 连 `window.interfaceStyle` 都取不到 —— 这一点后人复现时必须注意）。

**改动一：IPC 收敛（`0d35210`）**
`getConfig` 原先 `ipcMain.on` 与 `ipcMain.handle` 双注册；`changeConfig`/`getLocalIP`/`linkPort`
只有 on 一套且靠 `sendSync` 取值 —— 同步阻塞渲染进程，其中 linkPort 那条要等 nedb 两次写盘。
四条全改 handle，渲染侧 5 处 `sendSync` 改 `await invoke`，preload 白名单摘掉 `sendSync`。
`getSerialPortList`/`grantAccess` 保持 send + `event.reply`（渲染侧是 `ipc.on` 消费，
且 grantAccess 要等 pkexec 提权框）。

**该改动引入过一处真回归，由实机日志里的 Vue 警告抓到**：
`WebSerialChannel.js` 的 `webSerialChannel()` 自己在内部 `onUnmounted` 注册清理，
要求必须在 setup 同步期调用；linkPort 改 await 后它落到 await 之后，
Vue 报 `onUnmounted is called when there is no active component instance`，钩子被丢弃，
**组件卸载后串口通道不再关闭**。修法是把生命周期注册上移到消费组件
（`NipSerial.vue`、`PreviewHJ.vue` 各在 setup 同步期注册一次 `shutdownWebSerialChannel`），
工具函数不再自注册。附带补上了 PreviewHJ 一直缺失的卸载清理。

**改动二：端口单一出处（`14d9528`）**
`8000`/`18001` 原本在 `node_server.js`、`service/http/index.js`、`service/index.js`、
`core/index.js`（nip.db 默认值）四处各写一遍，改一处漏三处就会「服务监听 A、nip.db 告诉前端连 B」。
新增 `electron/shared/ports.js` 作为唯一出处（纯常量，主进程与 fork 子进程都能安全引入）。

**实机验收结果**

| 验收项 | 结果 |
|---|---|
| preload 白名单 | 实测 `[invoke, send, on, once, off]`，`typeof ipc.sendSync === 'undefined'` |
| 四条 channel | invoke 实测全部返回正确值：getConfig 取到 nip.db 两组地址、getLocalIP 返回两个网卡 IP、changeConfig 返回 true、linkPort 回显写入值 |
| 网络设置页真实 UI | 主页 popover → 网络设置 → 弹窗渲染「本机地址 / 数据服务=本机 / 资源服务=远程 127.0.0.1:8000」；点确定后 reload 跳 `/login`，`bin/nip.db` 内容与保存前一致 |
| 默认配置分支 | 删掉 nip.db 的 `_id:2` 重启外壳，`loadConfig` 用共享常量重建出 `localhost:18001`/`127.0.0.1:8000`，`_id:3` 串口值未被触碰 |
| 文件服务 | 仍监听 8000（`GET /api/file/getFile/...` 返回 404 = 服务已响应），日志 `Listen ports: 127.0.0.1:8000` |
| 回归检查 | 修复后重启、两次挂载主页：日志只有正常的 `SelectSerialPort:KEYSIM-VIRTUAL`，无 `No handler registered`、无生命周期警告 |

仍未实机验的外壳能力：**真实串口硬件**（本环境只有 KEYSIM 虚拟口、`getSerialPortList` 返回空）、
`grantAccess` 的 pkexec 提权框、装备 MQTT 呼叫。§4.2-2 相应收窄为这三项。


## 四、置信度与未决风险

### 4.1 置信度

- **高置信（三方实测/裁判亲核）**：§1 全部「已验证」行、§2.2 全部裁决、路线图档 0 的 diff 级参数（122 空闲经两侧全枚举、13/2 载荷计数、失败帧 undefined 语义、SocketConnection 幂等/重连链）。终审另抽查 8 条全数复现（§1.5）。
- **数字引用规约**：任何下游文档引用本轮数字时须带口径——64.1（全组）/62.5（跨皮肤）MiB、6（js/vue）/9（含 less）组、47（含休眠）/45（裁判 R2）条依赖、13/2（非 A-R3 笔误的 12/3）。
- **[推断]（未升级项）**：裸 `<script>` 239 文件「仅声明 name」的内容断言；皮肤资产字节吻合率 ~3% 缺口的 data-URL 内联解释；「删皮肤可砍产物数十 MB」的精确值（需先做 HJ 承重迁移）；tinymce vendored 与 npm 7.2.0 版本同代性；window.* 27 处中 ±3 个第三方惯例未逐个甄别；甲案 vendored plugins JS 完整性。

### 4.2 未决风险（必须靠运行时/生产数据/业务确认定性，移交执行方）

1. **【最大缺口】本次全程为静态 + 构建验证，没有做过真实的联合训练建连运行时验证**——档 0 修复后的 PubSub 载荷时序（publish 走微任务异步投递）、重连链路、登出四通道清理、USER_JOIN 残留修复效果全部未经运行验证。移交执行方作为修复包 Commit 4 验收标准：进大厅 loading 消失 → 建房成功收 120 跳房间页 → 建房失败弹错 → 二次进大厅 onlineUsers 无重复。
2. luckysheet 员工导入存废（0b）——业务决策，辩论无法收敛。
3. Electron 桌面包 4 个设备页 MQTT 是否业务需要（桌面 mqttWsUrl 恒空是代码事实，是否缺陷取决于部署形态）。**外壳侧 2026-09-19 已首次实机验收**（§3.6：IPC 四条 channel、网络设置页读写、nip.db 默认值分支、文件服务监听），剩余未实机项收窄为三项：真实串口硬件、`grantAccess` 的 pkexec 提权框、装备 MQTT 呼叫。
4. 生产菜单表复核：datagramZuXun/telexZuXun 死活、broadcastTeacheing 改名、organization 三胞胎——本轮全部菜单证据仅及本地库 project006。
5. 952 组同字节资产是否存在刻意皮肤隔离——产品逐组放行（档 4 语义闸）。
6. 死依赖/零引用资产删除的动态引用盲区（动态字符串拼接 require、打包后手工注入）——静态检测已证为零但非穷尽，以档 1 删后 build+全路由冒烟兜底；零引用 102.8MiB 批删前须先跑「删除子集构建+冒烟」回归闸。
7. tinymce vendored 版本与 npm 7.2.0 同代性——富文本升级路径决策前需核。
8. `UnionWs.js:17` 帧级空 catch（连 console.error 都没有，比 safeExecute 更静默）与 `PubSub.unsubscribe(null)` 清全场脚枪——记录在案，档 0 不强制处理。
9. **`vue/no-mutating-props` 11 条（答题预览三件套）—— 有意不动，需考试 E2E 才能安全改**：
   `components/test/previewTheTopic/PreviewTheTopic.vue`、`PreviewTheTopicTwo.vue`、
   `StudentPreviewTheTopic/StudentPreviewTheTopic.vue` 用 `v-model:value="params.answer"`
   直接写父级题目对象。**这不是笔误而是当前数据通路**：父页 `startTest.js:46/113` 往
   `question.answer` 写回服务器答卷，`:152` 提交时又从同一批对象读
   （`questions.value[key].map(({id, answer, isAnswer}) => …)`）；子组件 setup
   （`PreviewTheTopic.vue:165-181`）还会写 `params.value.correctAnswer` 与清空 `answer`。
   改 emit 需同时动 3 个题目组件 + `previewTest`/`StudentPreviewTest` 两个包装 +
   约 5 个持有 `questions` 的父页，且验收必须覆盖「建卷→分配→作答→交卷→服务器答卷回读」全链，
   本轮环境无考试业务数据，盲改风险是**答案丢失**。故保留 11 条 error 如实暴露，
   不加 `eslint-disable` 掩盖；该项应与考试链重构一并排期。
10. **`no-unused-vars` 剩 163 条为「只写不读」的状态变量**（如
    `organization/*/train/student/js/trainScore.js` 的 `numberChart`/`columnChart`、
    `PublicSocket.js` 的 `flag`/`url`、`OcrComp.vue` 的 `mediaStreamTrack`）。
    删除须连同赋值链一起判断——赋值右值若是 `echarts.init(...)` 一类有副作用的调用，
    只能去掉赋值目标、保留调用；机械批量会误删渲染逻辑（本轮已实测踩过一次，见 §3.5 坑 2）。
    属个案清理，不建议再上 codemod。

---

## 五、过程附录：对抗评审的实际收益

三轮共修正 **18 项错误**（含主持人 1 项、两轮裁判贡献 3 项），任何单方评审都会携带其中一半以上交付。这是本文档置信度的直接依据：

| # | 纠正内容 | 犯错方→抓出方 | 轮次 |
|---|---|---|---|
| 1 | UnionWs.run 零调用（功能域整体断链，A-R1 完全漏报） | A ← B | R2 |
| 2 | luckysheet JS 零加载点；vendored 死资产 10.7→18.4MB（A 未查加载器本体） | A ← B | R2 |
| 3 | uuid「10 文件运行时 import、devDeps 分类错误」不成立（A-R3 正式撤回） | A ← B + H6 | R2→R3 |
| 4 | file-saver 死依赖漏报 | A ← B | R2 |
| 5 | 业务码 364→365（B 自己的分项和即 365，加法错） | B ← A + B 自纠 | R2 |
| 6 | `<script setup>` 207→208 | B ← A + 裁判复算 | R2 |
| 7 | @purge-icons/generated 计入死依赖（该包根本未声明，口径错） | B ← A | R2 |
| 8 | 闭合核验分母 14→15（B）；headline「全部真闭合」过度概括（A）——四档措辞封板 | 互纠 | R2 |
| 9 | **B 的 src/assets 全域盲区**（3895 文件/167.8MB 完全未扫，冗余维度最大一块）——A 补位 | B 自纠 | R2 |
| 10 | jquery「4KB」实为 0 字节入库 blob（du 块大小假象，双方 R1 均错） | B 自纠 | R2 |
| 11 | P0-1「真闭合」漏判 lint 无执行入口 → 降档「修复闭合、防线未闭合」 | B ← A | R2 |
| 12 | Waves.vue「3 处引用均注释」不准（LoginHJ:248 活 import；注释实为 4 处） | A ← B + 第 2 轮裁判补正 | R2 |
| 13 | @types/tailwindcss 判死 → 弱活/保留（tailwind.config.js:3 JSDoc） | A ← B | R2→R3 |
| 14 | Ws.js 分发异构不能作载荷判例；「TypeError」实为 SyntaxError 且被 safeExecute 吞 | B ← A | R3 |
| 15 | **主持人被纠正：H9 cs.gif 归属说反**——dist 中唯一 cs-25f18704.gif 是 HJ/HJJ 版（3.66MB），GD/KJ/LJ 三胞胎（10.49MB×3）零引用从未进 dist；机制是「零引用不进构建」而非「hash 折叠」。第 2 轮裁判发现，主持人复核认账，第 3 轮裁判再次独立实测坐实 | 主持人 ← 第 2 轮裁判 | R2 末 |
| 16 | 主持人 ElectronMorse「两个无主 IPC 频道」误报撤回（本地 morseController 方法，非 IPC） | 主持人 ← A（B 亲读佐证） | R2 |
| 17 | **第 3 轮裁判查出双方都没发现的两处定稿错误**：① A-R3 载荷计数「12 parse/3 忽略」应为 13/2；② A-R3「JSON.parse(null) 不抛错」前提错误（Gson 不序列化 null → data=undefined → 抛 SyntaxError） | A ← R3 裁判 | R3 |
| 18 | 「950 组跨皮肤/64.1MiB」混用口径（跨皮肤 62.5MiB / 全组 64.1MiB） | 双方 ← R3 裁判 | R3 |

**过程评价**（采 R3 裁判）：收敛是真收敛——每条让步均由复算驱动（附命令或亲读记录）而非妥协，无一条「双输式和稀泥」；双方 R3 均能据新证据（H1-H10）推翻自己前轮结论。唯一批评：R3 双方对彼此文本的数字级互校弱于前两轮（#17 两处均由裁判抓出）。另有第 2 轮裁判的独立增量发现一项（H4 别名配置错误，双辩手与主持人此前均未发现）——轮流裁判机制本身产出了实质证据。
