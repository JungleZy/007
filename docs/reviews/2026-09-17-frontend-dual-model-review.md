# 前端代码评审结论文档（双模型对抗评审）

- **日期**：2026-09-17
- **范围**：`bw-frontend/frontend/`（Vue 3.5.42 + Vite + ant-design-vue 2.2.8 + vue-router 4.5 hash 模式；src 下 543 个源文件：views 362 / components 90 / common 73 / config 12 / electron 3）
- **维度**：代码一致性、代码冗余优化（纯静态分析，未跑构建）
- **方法**：model-debate 双模型对抗评审。`kimi/k3` 与 `glm/glm-5.3` 各自独立全面评审（R1）→ 逐字交换报告交叉反驳（R2，双方均动工具复核对方证据）→ 两模型各自盲裁 → 交叉裁判互评 → 主会话对事实分歧直接测量终裁并汇总。辩论完整记录留存于会话工件 `local://debate/full-record.md`。
- **过程事件**：glm-5.3 上游在 R1 中段 503 宕机约 25 分钟，自动探测恢复后续跑，其研究上下文未丢失，不影响结论完整性。

---

## 一、总体结论

前端基建的**传输与封装层健康**：`common/http` 拦截器 + 28 个 API 模块统一走同一 axios 包装器（全仓最一致的一条线）；WebSocket 底层已收敛到 `SocketConnection` 单基座（全仓唯一业务 `new WebSocket`，SocketConnection.js:50）。

主要问题集中在**视图层「复制-改字」式扩张**：preJob/postJob 平行树（35 个同名文件，三层分化）、equipment 三份近全拷贝（~948KB）、organization 组训「三胞胎+一异类」。工程规范缺位：Vuex/Pinia 双轨、组件写法三轨、`code===200` 判断四种写法 366 处散落、`@/` 别名已配置却零使用、拼写错误固化为目录名。

核心维护性风险是**同一改动要改 N 处**；最高危区是 `src/common/api/` 跨栈契约面（同名导出指向不同端点，IDE 自动 import 无告警引错）。

---

## 二、共识问题清单（双方一致认可，已去重合并）

### A. 一致性

| # | 问题 | 关键证据 | 证据强度 | 来源 |
|---|------|---------|---------|------|
| A1 | 同名 API 导出跨模块双定义、指向不同端点：`hanziAdd`（→/api/enteringExercise/add vs →/api/postEnteringExercise/add）、`getAllStudent`（→findAllStu vs →getUserDirectory），引用已按 preJob/postJob 分裂 | TelegramApi.js:66 / postHanZi.js:3；UserApi.js:52 / broaddcastTeacheingApi.js:3 | 已验证（含主持人复核） | K3-R1，GLM-R2 认可 |
| A2 | 状态管理双轨：Vuex+Pinia 并存；Vuex 仅 3 处使用，其 router 状态 setRouter/getRouter 全仓零调用（纯死代码） | main.js:97/100；config/store/index.js:11-13 | 已验证 | 双方 R1；K3-R2 加码 |
| A3 | 组件写法三轨：214 `<script setup>` + ~25 `setup()` + 18 纯 Options（3 含 `data()`）；~205 个双 script 块仅为声明 name；18 个组件同名 'Index' | App.vue:19-30 同文件双范式；R2 统一计数口径 | 已验证 | 双方 R1，R2 统一 |
| A4 | 业务成功判断四种写法 366 处（===223/==29/!==108/!=6，宽松 35 处），无统一 helper；拦截器已统一弹错但 ≥10 处视图 else 分支重复弹错 | GLM-R2 复算，K3-R2 逐条复算一致；http/index.js:85-91 | 已验证 | GLM-R1/R2，K3 |
| A5 | API 模块命名三种风格 + 拼写错误文件名（broaddcastTeacheingApi.js）；设备域 equipment.js 与 EquipmentApi.js 双文件（两后端资源是否同源 [推断]） | common/api/ 目录；equipment.js:3-49 / EquipmentApi.js:3-57 | 已验证 | 双方 R1 |
| A6 | TelegramApi.js 巨石：485 行 / 72 导出，跨 6+ 业务域，文件内两种排版并存 | F1 实测 | 已验证 | GLM-R1，数字 K3-R2 纠正 |
| A7 | `@/` 别名已配置零使用，446 处 6 层以上深相对路径 | vite.config.js:21；grep 0 命中 | 已验证（主持人复核） | K3-R1 |
| A8 | localforage 经 script 标签全局注入绕开模块体系，7 文件裸用，package.json 无声明 | index.html:15；App.vue:77/91 等 | 已验证 | GLM-R1，K3-R2 认可 |
| A9 | 日期库双轨：moment（50 文件，已声明）vs dayjs（2 文件，未声明，靠传递依赖提升） | package.json:49 | 已验证（主持人复核） | K3-R1 |
| A10 | MessageWebSocket.js 名实不符（实为 WebSerial 串口通道，文件头自述 ws 桥已退役）；MQTT（paho-mqtt 94.9KB vendor）服务 4 个设备页，WS/MQTT 双通道并存 | MessageWebSocket.js:7,34-38 | 已验证 | GLM-R1，K3-R2 补证 |
| A11 | WS 上层两套并行：Ws.js（应用级单例+PubSub，绑用户身份）vs PublicSocket.js（房间级工厂+连接池），重连/关闭/登出清理语义各异 | Ws.js:11；PublicSocket.js:2-7 | 已验证 | K3-R1（R2 修正计数），GLM |
| A12 | 路由真源为 localStorage userRouter；guards.js:17-18 直接改写 router.options 内部结构 | guards.js:60,17-18 | 已验证 | GLM-R1 |
| A13 | 异步风格双轨（.then 50 文件 vs await 7）；`==` 1487 vs `===` 1206；var 残留 21；console.log 129 处（含框架层 http/index.js:6） | K3 统计 | 已验证（单方实测，GLM 采信；F6 旁证可信） | K3-R1 |
| A14 | 样式规范薄弱：内联 style 3519 处、!important 580 处、style 标签属性顺序四种、@import 扩展名混用 | K3 统计；GLM C-7 抽样证实 | 已验证（单方实测） | 双方 |
| A15 | props 声明风格分裂；components/common/Pagination.vue:23-36 函数当 prop 违反 emit 约定 | 两处 Pagination 对照 | 已验证 | K3-R1 |
| A16 | 拼写错误固化为路径：perviewTest、parctice.js、wb_colork.js、messsageBody.css、GardRule、broadcastTeacheing、compoents | 目录清单 | 已验证 | K3-R1，GLM-R1/K3-R2 补 |
| A17 | main.js 私货：:21 base64 内嵌图、内联指令注册与 config/directive 注册式并存 | main.js:21-93 | 已验证 | K3-R1 |
| A18 | common/mixin 目录名实不符（内容全为 useXxx composable） | 目录内容 | 已验证 | GLM-R1 |

### B. 冗余

| # | 问题 | 关键证据 | 证据强度 | 来源 |
|---|------|---------|---------|------|
| B1 | preJob/postJob 平行树 35 个同名文件，三层结构：字节级相同（Index.vue、examTrain/js/useControl.js）/ 轻漂移（telegram 子树 10 differ 1 same）/ 大幅分化（receiveTrain.js 524/560 行、WordTrain.vue 801/543 行） | md5 + comm 复算恰 35 | 已验证 | 双方 R1，R2 统一 |
| B2 | equipment/equipmentOperate/equipmentList1 三份近全拷贝 ~948KB；171.js 仅 :19 一行**行为性**差异（`i<4` vs `i<1`）；table.js 87 行零差异 | F3/F5 实测 | 已验证 | 双方 R1/R2 |
| B3 | organization 组训「三胞胎+一异类」：useControl.js×3 md5 相同、keyCode.js×3 相同、teacher.js×3 高度同构；handkeyZuXun 为异类 | md5 | 已验证 | K3-R1，GLM-R2 补第四模块 |
| B4 | 死代码批次（均零引用）：gradingRule/IndexDelete.vue（501 行）、components/model/（仅 App.vue:40 注释引用）、common/mixin/useBase.js、common/anime.js（5 字节）、unpkg.js×2（各 763,499 字节 md5 相同，1.5MB）、explain/Index.vue:119 axios 死导入、vue-ueditor-wrap 整链（唯一富文本 NipUEditor 实为 TinyMCE 封装）、Vuex router 死状态 | 主持人 F6 复核 | 已验证 | K3-R1，GLM-R2 逐条认可 |
| B5 | g2plot 内部 `log` 死 import 扩散 8 处（含契约面 UserApi.js:1），各文件 `log(` 零调用 | 8 文件逐一点名 | 已验证 | GLM-R1/R2，K3-R2 认漏 |
| B6 | 两个 Pagination 组件并行（2 vs 6 处引用，模板 class 相同） | 双方认可保留 common 版 | 已验证 | K3-R1，GLM-R2 |
| B7 | gradingRule 家族复制 + `name:'TelexGradingRule'` 残留共 4 处；GDStyle.vue:7 `name:"LJStyle"` | 4 处行号点名 | 已验证 | K3-R1，GLM-R2 加码 |
| B8 | components/test 预览组件成对复制 + 「Two」后缀拷贝对；共享 js 字节级相同 | diff 数据 | 已验证 | 双方 |
| B9 | WZTrain.vue 三份（wubi/pinyin/english = 968/495/225 行，互为裁剪拷贝，diff 689-1164 行） | 行数/diff | 存在性已验证；差异有意性 [推断] | GLM-R1 |
| B10 | 15 组字节级重复文件（含 selectTimeTag.js×2 等） | md5 全仓扫描 | 已验证 | K3-R1 |
| B11 | App.vue 注释态死代码成片（:37/:40/:45-51/:60-65） | 逐行点名 | 已验证 | GLM-R1 |
| B12 | 依赖冗余：bluebird/body-parser/compression/formidable/mockjs 声明但 src 零引用 | package.json | 已验证 | K3-R1，GLM-R2 采信 |
| B13 | main.js:91 debounce 指令 unmounted 错写 addEventListener 且 el.$handle 从未赋值（确凿 bug）；waves 指令含 Vue2 死钩子 bind/unbind 且 v-waves 全仓仅 1 用 | 主持人 F6 复核 | 已验证 | K3-R1，GLM-R2 |
| B14 | http/index.js:39 `config.method === 'POST'` 死分支（axios 先归一小写，永不命中） | axios 源码 Axios.js:41-47 | 已验证 | K3-R1，GLM-R2 源码证实 |
| B15 | equipment 双入口：Index.vue（9 行）vs equipmentIndex.vue（370 行） | 菜单表终判：`/manage/equipment/Index` 与 `/manage/equipment/equipmentIndex` **均被菜单引用，两者皆活**；收敛需菜单合并，见 3.4 | 已验证 | GLM-R1，菜单表终判 |

---

## 三、分歧与裁决记录

### 3.1 事实层分歧（主持人直接测量终裁）

| # | 争议 | 实测 | 采信方 |
|---|------|------|--------|
| F1 | TelegramApi.js 规模：485行/72导出 vs 767行/~120 | `wc -l`=485、`export const`=72 | K3 |
| F2 | API 模块总数：28 vs 29 | 28 | K3 |
| F3 | equipment/trainScore/Index.vue 是否字节级相同 | md5 不同（c9ac86c1 vs 1ccef6da） | GLM |
| F4 | components/style/ 五组件是否死代码 | GlobalStyle.vue:14-28 按 window.interfaceStyle 动态 import 全部五个 → **活代码**；K3 死代码定性撤销，仅 GDStyle name 残留保留 | GLM |
| F5 | 171.js 差异行数 1 vs 2 | 恰好 1 行（:19） | K3 |
| F6 | K3 R1 证据质量 | 主持人独立复核 11 项全属实 | — |

### 3.2 判断层分歧（双裁判 + 交叉裁判终裁）

**J1 WS 上层整改力度 → 分阶段**（两裁判方案同构）：本期执行「MessageWebSocket→WebSerialChannel 更名 + Ws.js/PublicSocket.js 语义对齐（重连/关闭/登出清理）+ 文档注明双通道边界」；单分发层收敛作为终态愿景，以「全仓 WS 使用点映射完成 + Phase A 契约稳定」为触发条件。理由：两上层生命周期不同（用户级单例 vs 房间级连接池），强并需重写 8+ 调用方且位于在线训练核心路径，风险收益不划算。

**J2 拷贝合并 → 按实测分化度分级推进**（两裁判一致）：字节级相同层立即合并（零行为变化）；轻漂移层先业务确认再参数化（171.js 的 `i<4` vs `i<1` 证明漂移可为行为性差异——若为未察觉的 bug，合并会封存 bug）；大幅分化层（receiveTrain/WordTrain）需回归 + 业务确认 + 后端菜单 path 协同（guards.js:64 按路径匹配，目录改动不同步即白屏）。equipment 合并的硬性前置：拉后端菜单表重指组件 path + 已枚举行为差异的意图确认。

**J3 WZTrain×3 / WordTrain×2 → 保留不合并，建档 + 业务确认为闸**：diff 深度（689-1164 行）与业务解释相容，记录中无任何一方证明差异为表面性；GLM 交叉裁判时实测发现自己 J3 原建议的候选函数 `computationTime` 在 WZTrain 中根本不存在（误从 WordTrain 语境平移），真实候选（statistics/keyCodeDown）已见漂移（statisticsSelcet 拼写分叉、keyCodeDown1/2 变体）——抽取未证明等价的函数即是赌博。最终口径：**仅字节级相同的纯函数可机械搬运（移动+改 import，不改函数体）；其余全部等待业务确认**。即时动作仅：互加同源指引注释 + 发业务确认函。

**附带裁决（GLM 提出，K3 让步认可）**：保留 `common/http/axios.js` 统一包装层——K3 R1「直连 instance」建议与其自己「28 模块统一包装是全仓最一致的一条线」的总评自相矛盾，显式撤销。纠正细节：错误提示/token 收口点在 http/index.js 拦截器，包装层价值是全仓唯一调用约定；仅删 index.js:39 死分支，isBizOk helper 建在包装层之上。

**优先级对向让步（主持人终裁）**：契约面同名导出去重，K3 原列 P0 后让至 P1，GLM 原列 P1 后让至 P0。**终裁：P0 附双条件**——①本期仅限已实证对（hanziAdd、getAllStudent），同族其余（listPage/getById/begin 等）模式验证后作后续批次；②策略为**重命名消歧而非删除端点映射**，逐调用方保持「命中原端点」不变断言（静态 import 漏改会在构建期报错，自验证）。理由：改的是前端 JS 符号名，不动 URL/方法/参数/返回形态，不触跨栈契约红线。

### 3.3 已撤销项留档（防止误删）

- ~~components/style/ 五组件死代码~~（F4：活代码，动态 import）
- ~~equipment/trainScore/Index.vue 字节级相同~~（F3：92 行 diff）
- ~~WS「四套并行」~~（R2：MessageWebSocket 是串口；底层已收敛）
- ~~「41 个纯 Options API」~~（R2 口径修正：18 Options + ~25 setup()）
- ~~GLM「WZTrain 现在抽 computationTime」~~（函数不存在，GLM 自纠）

### 3.4 菜单表终判（本地 `t_menus` 查询，2026-09-17）

评审后查询本地 MySQL `t_menus`（130 条 DISTINCT component 路径），将动态路由卡住的 [推断] 项终判：

| 项 | 终判 | 依据 |
|---|------|------|
| `equipmentOperate/equipmentList1/`（第三份拷贝） | **确证死代码，可删** | 菜单 0 引用 |
| 各 `teaching/Index1.vue` | **确证死代码，可删** | 菜单 0 引用（菜单指向 `teaching/Index`） |
| `views/Index.vue`（空模板） | **确证死代码，可删** | 无对应菜单 |
| equipment 双入口 | **两者皆活** | `/manage/equipment/Index` 与 `/manage/equipment/equipmentIndex` 均在菜单表 |
| preJob/postJob | **双双重度存活**（37 + 33 条菜单） | 合并任何一层都必须迁移菜单 path，J2「菜单协同」前置证实为硬约束 |
| organization 四模块 | **2 活 + 2 疑似死**：electronKeyZuXun、handkeyZuXun 有路由；**datagramZuXun、telexZuXun 无菜单路由且无静态路由**，当前菜单数据下不可达 | 菜单表全列查询；但 `useNotification.js:46` 等 4 处代码仍 `router.push` 指向 `datagramZuXunTrain` 路由名——若生产菜单同样缺失，这些跳转是断链。**需生产菜单表复核** |
| 拼写目录改名 | **大部分无需菜单同步**：`compoents/`、`perviewTest`、`parctice.js` 菜单 0 引用（降 P1）；**例外** `unionJob/broadcastTeacheing/` 被菜单 `/manage/unionJob/broadcastTeacheing/BroadcastTeachTrain` 引用，改名必须同步菜单表（留 P2） | 菜单表逐名核查 |
| 菜单健康反向检查 | **130 条菜单路径全部有对应文件，无断链菜单**（唯一 `-1` 为占位符） | 菜单路径 × 文件系统双向比对 |

注意：本判定基于本地开发库 `project006` 快照；生产菜单数据若不同，以生产为准复核。

---

## 四、最终整改路线图（风险×收益排序）

**P0 零/低风险，本期落地** ✅ 已全部执行（2026-09-17，commits 57c360e→64c0fd1；验证：前端 node 测试 39/39 通过、`vite build` 53s 构建成功）。各项与提交对应：P0-1=57c360e、P0-2=0822a99、P0-3=e6164d1、P0-4=717c406、P0-5=830c136、P0-6=c9abd5a、P0-7=9c07990、P0-8=2995ba7、P0-9=64c0fd1。
1. 删 8 处 g2plot `log` 死 import；CI 加 no-unused-imports。（GLM-R1）
2. 死代码批删：IndexDelete.vue、components/model/、useBase.js、anime.js、unpkg.js×2（省 1.5MB）、explain axios 死导入、vue-ueditor-wrap 注册+依赖+资源链、Vuex router 死状态、App.vue 注释块、http/index.js:39 死分支与 :6 框架层 console.log、**equipmentList1 整目录、各 teaching/Index1.vue、views/Index.vue（3.4 菜单表终判确证）**。（K3-R1/GLM-R2/F6/菜单表）
3. 修 main.js:91 debounce unmounted bug（v-debounce 7 处调用点）。（K3-R1/F6）
4. 组件 name 残留 5 处：gradingRule×4 + GDStyle.vue:7。（双方）
5. v-waves：确认唯一使用点后整条删除或重写（去 Vue2 死钩子）。（K3-R1/GLM）
6. 契约面同名导出去重（hanziAdd/getAllStudent，重命名消歧，双条件见 3.2）。（主持人终裁）
7. dayjs 显式声明（或直接并入 P1 的 moment 统一）。（K3-R1）

**P1 低风险高收益** ✅ 已全部执行（2026-09-17，commits cde9c5a→70c5059；验证：node 测试 39/39、`vite build` 52s 通过）。执行中更正与增量发现：
- **13 二合一裁决撤销**：两 Pagination 并非冗余——pagination/ 版是客户端全量分页（内部切片），common/ 版是服务端分页（父组件翻页拉取，changeListPage→findRoomInfo）。实际执行：common/ 版函数 prop 改 emit（6 调用点同步），两文件头互加模型指引注释。教训：评审时只对了引用数与 props 风格，未对数据流——执行期核实纠回。
- **14 口径修正**：dayjs 实为 10 处使用而非 2（8 处经 index.html script 标签全局注入裸用，与 localforage 同款反模式，评审漏检）。收敛方向取多数派 moment（58 文件规范 import）；script 注入与 vendored 文件已删。
- **12 增量**：usePreviewTheTopic.js×2（引用全为注释）与 preJob telexTrain/js/wordTrain.js×2（零导入）升级为死代码直接删除，未走合并。
- **8 增量**：Vuex 的 online 状态零写入方（Room.js 读取恒 undefined），Room.js 整类唯一实例化点已注释——删除整文件；permissions 流迁入 Pinia global store。
- 另修复执行引入的 2 处构建断点（commit 70c5059）。
8. ✅ Pinia 收编 Vuex，卸载 vuex（c45394b）
9. ✅ localforage 转 npm ESM（b00962a）
10. ✅ 依赖清理（cde9c5a）——注意：body-parser/compression/formidable 被 Electron 外壳真实使用，由顶层 bw-frontend/package.json 提供，仅前端包内副本为冗余
11. ✅ MessageWebSocket → WebSerialChannel（f043d30）
12. ✅ 字节级相同文件合并 + 死代码增量删除（ded6af0）
13. ✅ 更正为「两模型并存 + emit 改造」（9662608）
14. ✅ 收敛到 moment（128a68e）
14b. ✅ 拼写改名批次（df45eb2）

**P2 中风险，需排期 + 分批 PR**
15. common/http 导出 isBizOk/unwrap，统一 366 处 code 判断；先灭 35 处宽松等号；错误提示收口拦截器消灭双弹。（GLM-R1/K3-R1）
16. `@/` 别名迁移 446 处深相对路径（可 codemod；不影响 guards 文件路径匹配，可安全渐进）。（K3-R1）
17. equipment 三目录参数化合并——硬性前置：后端菜单表核对 + path 重指 + 行为差异意图确认（J2）。（双方）
18. TelegramApi.js 按业务域拆分（485 行/72 导出，仅动 import 路径）。（GLM-R1，P2 排序采 K3）
19. 组件名唯一化 + defineOptions 替换 ~205 个双 script 块 + App.vue 双 script 统一。（双方）
20. WS 上层语义对齐（J1 本期项）。（双方）
21. ~~views 层拼写目录改名~~ 经 3.4 终判：仅 `unionJob/broadcastTeacheing/` 被菜单引用需同步菜单表（保留本项）；compoents/perviewTest/parctice 已降 P1-14b。（GLM/K3 交叉裁判修正 + 菜单表终判）

**P3 长线，设闸推进**
22. preJob/postJob 三阶段合并：纯函数 → 相同文件共享 → 分化层参数化（业务确认为闸）。（J2）
23. WZTrain/WordTrain：建档 + 确认函；仅字节级相同纯函数可机械抽取（实测候选已漂移，预期无可抽）。（J3）
24. organization 收敛重定基线（3.4 终判）：electronKeyZuXun/handkeyZuXun 活，datagramZuXun/telexZuXun 疑似死（无路由）——先以生产菜单表复核两疑似死模块，若为死则直接删除而非合并；存活两模块再评估参数化。另需处理 4 处指向不存在路由的 router.push 断链。（K3-R1/GLM-R2/菜单表）
25. lint 收口：eqeqeq、no-console、no-var、命名规范。（双方）
26. 样式规范渐进收敛（内联 style/!important 评审卡口）。（双方）
27. WS 单分发层终态（J1 条件触发）。（J1）

---

## 五、未决风险与外部依赖

- ~~依赖后端菜单表~~ **已终判（3.4）**：equipmentList1/Index1.vue/views-Index.vue 确证可删；equipment 双入口皆活；preJob/postJob 双活；拼写改名大部分解锁。残余菜单依赖仅两项：**生产菜单表复核** datagramZuXun/telexZuXun（本地快照已判死）；equipment 合并时的菜单 path 重指。
- **依赖业务确认**：preJob/postJob 岗位隔离需求；WZTrain/WordTrain 差异意图；organization 四模块边界；`i<4` vs `i<1` 等已枚举行为差异的意图（刻意分叉 vs 未察觉 bug）。
- ~~依赖运行时验证：Ws.js:15 data.map 疑点~~ **已静态终判：非缺陷（2026-09-17）**。后端 `ResponseModel` 有线协议含字面 `map` 字段（`ResponseModel(int code, Map map)` 构造器），code 201 的唯一发送方 `GeneralPatResultNotifier.java:28-29` 以 `Map.of(type,userId,trainId)` 填充该字段；前端 `data.map` 恰好取出订阅方 `userSubmitStatus` 期望的 `{trainId,userId,type}`。对照组 code 200（`GeneralTickerPatService.java:124` 等三处）同样用 map 字段，前端处理方 `useNotification.js:24,26` 读 `data.map.type`——两种前端取法（整报文 vs 发布时预取 `.map`）均与线协议自洽。K3 的「数组方法误发布」假设被后端源码证伪，不开 bug 单。
- **另行立项**：ant-design-vue 2.2.8 × Vue 3.5.42 版本代差兼容性（超出本次维度）。
- **口径说明**：宏观统计（446 深路径、3519 内联 style、129 console.log 等）为 K3 单方实测、GLM 采信，F6 抽查 11 项全属实提供旁证，量级可信、精确值允许个位数偏差；code 判断 366 处为双方复算一致，已验证。

---

## 六、过程附录：对抗评审的实际收益

本次双模型对抗共纠正 **9 处单方错误**，任何单方评审都会携带这些错误交付：

| 错误 | 犯错方 | 抓出方 |
|------|--------|--------|
| components/style/ 误判死代码（实为动态 import 活代码） | K3 | GLM-R2，F4 实测 |
| trainScore/Index.vue 误报字节级相同 | K3 | GLM-R2，F3 实测 |
| WS「四套并行」计数错误（串口模块误计入） | K3 | GLM-R1/R2 |
| 「41 个纯 Options」口径错误 | K3 | GLM-R2 统一口径 |
| axios 包装层「直连」建议与自身总评矛盾 | K3 | GLM 附带裁决，K3 让步 |
| TelegramApi.js 规模 767行/120导出（实测 485/72） | GLM | K3-R2，F1 实测 |
| API 模块数 29（实为 28） | GLM | K3，F2 实测 |
| J3「抽 computationTime」建议（函数不存在） | GLM | GLM 交叉裁判自纠（实测） |
| g2plot 死 import 只报 1 处（实际扩散 8 处） | K3 漏报 | GLM-R1 |

独有发现互补：K3 独有 hanziAdd 双端点、Pagination 双实现、dayjs 未声明、debounce bug、ueditor 死链等；GLM 独有 localforage 注入、TelegramApi 巨石、路由双源、WS 底层收敛定性等。双方独有发现经反驳轮交叉核实后**全部互认为真**。
