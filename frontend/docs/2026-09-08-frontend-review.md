# 前端全面评审报告（frontend/）

- 日期：2026-09-08
- 范围：`frontend/`（project006 舰船报务综合训练系统的桌面前端）
- 技术栈：Vue 3.5 + Vite 4 + Less/Tailwind + ant-design-vue 2.x，运行于**外部 Electron 外壳**内（另有残留的 Tauri 脚手架）
- 规模：265 个 `.vue`、282 个 `.js`、约 155K LOC（其中大量为 vendored/生成的大文件）
- 方法：6 个只读评审子代理并行覆盖「架构/构建、安全、网络/数据层、路由/状态/鉴权、组件/代码质量、性能/可访问性」，父代理独立复核关键结论后合并。
- 权威性：所有 HIGH 结论均已用 `read`/`grep` 独立落到 `文件:行` 证据；带 `[INFERENCE]` 者为推断。

> 与后端评审（`backend/docs/reviews/2026-09-07-full-project-review.md`）同为「以本汇总为准」的评审文档。
>
> 本文所有相对路径以 **`frontend/`** 为根（`src/` 内文件再省略 `src/`，如 `common/http/index.js`）。

---

## 1. 执行摘要

前端功能可用，但建立在**脆弱且大量复制粘贴**的基础之上，并把客户端当成了鉴权与权限门控的信任锚。最突出的三点：

1. **XSS→账号接管链**（HIGH）：后端富文本经 `v-html`(`equipmentIndex.vue:24`)/`iframe.document.write`(`useDetails.js:50+`) 未净化渲染 + token/deviceId/userInfo/userRole/userRouter 全存 `localStorage` + 无 CSP + 自动登录明文存密码 → 一次注入即可外带凭据完成账号接管。
2. **鉴权/权限门控全靠客户端信任**（HIGH）：路由守卫只看 token「是否存在」而非「是否有效」；`v-per` 按钮权限**失败即放行**且非响应式；登出**从不调用后端**失效 token。安全性完全依赖后端强校验。
3. **工程债务巨大**：271MB 的 `node_modules.zip` 滞留工作树（已核实**未入库**，见 §3.2）且 `package-lock.json` 被忽略（构建不可复现）；约 1.3MB 字节级完全相同的文件多路径重复（`unpkg.js` 763KB×2、`table.js` 278KB×2、`wb_color*.js` 251KB×2）；**vuex 与 pinia 双状态并存**；三套 3D/表格/编辑器引擎并存与大量死依赖；构建 `manualChunks` 每包一 chunk 造成碎片化。

### 必修清单（Must-fix，按风险排序）

| # | 结论 | 严重度 | 证据 |
|---|------|--------|------|
| 1 | 后端富文本经 `v-html`/`document.write` 未净化渲染 → 存储型 XSS | HIGH | `equipment/equipmentIndex.vue:24`、`study/basic/details/js/useDetails.js:50-95` |
| 2 | 全量会话态（含 token）存 `localStorage`，无 CSP → XSS 窃取即接管 | HIGH | `login/useLogin.js:122-126`、`common/http/index.js:15-18` |
| 3 | 自动登录明文持久化用户名/密码到 localforage | HIGH | `login/useLogin.js:127-134`、`:14-20` |
| 4 | 无 lockfile → 构建不可复现（`node_modules.zip` 已确认未入库，本条半闭合） | HIGH | `.gitignore:8`、`frontend/node_modules.zip` |
| 5 | axios 无 timeout 且吞掉传输层错误（请求可永久挂起、用户无反馈） | HIGH | `common/http/index.js:7,70-111` |
| 6 | WebSocket 无退避/无上限/无心跳；部分用 `httpUrl` 拼 URL 在 Web 部署下畸形 | HIGH | `ws/Ws.js:74-81`、`unionJob/js/UnionWs.js:41` |
| 7 | 约 1.3MB 字节级重复文件 + 已开始分叉的整文件重复组件 | HIGH | `equipment(Operate)/trainScore/js/table.js`、`gradingRule/{Telex,IndexDelete}.vue` |

### 严重度统计（按域，含跨域重复计数）

| 域 | CRITICAL | HIGH | MEDIUM | LOW | INFO | 小计 |
|----|:--:|:--:|:--:|:--:|:--:|:--:|
| 安全 | 0 | 3 | 5 | 3 | 1 | 12 |
| 架构/构建/依赖 | 0 | 4 | 6 | 4 | 1 | 15 |
| 网络/数据层 | 0 | 4 | 6 | 3 | 2 | 15 |
| 路由/状态/鉴权 | 0 | 4 | 5 | 4 | 2 | 15 |
| 组件/代码质量 | 0 | 2 | 4 | 2 | 3 | 11 |
| 性能/可访问性/国际化 | 0 | 6 | 5 | 1 | 3 | 15 |
| **合计** | **0** | **23** | **31** | **17** | **12** | **83** |

> 说明：跨域重复计入（如「双状态」「node_modules.zip」「死 POST 分支」「localStorage 会话态」在多域各计一次）。去重后的系统性主题见 §2。

---

## 2. 系统性主题（跨域重复出现）

- **客户端信任锚**：鉴权（路由守卫）与权限门控（§3.4 的 `v-per`）都只在前端判定；安全性完全依赖后端强校验。任何 UI 门控都应视为「装饰」，服务端必须是唯一权威。授权/license 门控经负责人确认为**有意的客户端软门控、非安全边界**（详见 §3.1，不计缺陷）。
- **复制粘贴主导的结构债**：四个 `*ZuXun` 训练变体、`telegram`/`datagram` teaching 子树、`equipment`/`equipmentOperate` 近乎整树复制；已出现**跨域污染**（`telexZuXun/.../datagramTrain.js` 导入 electronKey API）与**同键碰撞**（telex/datagram `student.vue` 都写 `'datagramZuXun'+trainId`）。一处修复需改 N 份，且副本已开始分叉。
- **双状态管理**：vuex（`config/store/index.js`，持 `router/permissions/online`）与 pinia（`config/pinia/*`，持 theme/traffic）同时注册于 `main.js:97,100`。vuex 大部分是死代码（`setRouter/getRouter/setOnline/getOnline` 从未使用，`online` 永为 `{}` → `Room.js` 读到 `undefined`），仅 `setPermissions` 活跃且仅在 HJJ/LJ 主题提交。
- **Vue2 残留与死代码**：`main.js` 用 Vue2 指令钩子 `bind/update/unbind`（Vue3 永不触发 → `waves` 的 `mouseover` 监听泄漏）、`app.config.productionTip`（Vue3 no-op）；`Waves.vue` 逻辑整段注释却仍 `import three`；`ShortcutMenu.vue` 递归定时器无卸载清理。
- **死 POST 序列化分支（多域独立发现）**：`common/http/index.js:20` 的 `config.method === 'POST'` 因 axios 先把 method 转小写而**永不命中**；当前靠 axios 自动序列化侥幸正确，但若有人「修正」为 `'post'`，所有 POST 体将被双重 `JSON.stringify` 而静默损坏。
- **无 lint、无测试、无 i18n、无 a11y**：`.eslintrc.js` 引用的 eslint/prettier 插件均未安装（lint 跑不起来）；无任何测试；全量硬编码中文且 `<html lang="en">`；全树零 `role/tabindex/aria-*`。

---

## 3. 分域详述

### 3.1 安全（0 CRITICAL / 3 HIGH / 5 MEDIUM / 3 LOW / 1 INFO）

#### [已接受·非问题] 授权/license 校验为客户端软门控（经负责人确认非安全边界）
- **结论**：评审初稿曾将「授权校验纯客户端、可离线伪造、内置万能绕过码 `wjkj2025~` + 硬编码 AES-ECB 密钥 `wisdom23`」列为 CRITICAL。经项目负责人确认：该门控是**有意的**客户端软性激活/提示，不作为安全边界，故**不计为缺陷**（类比后端 fastjson 1.2.78 的既定接受）。
- **事实留存**：`common/utils/VerifyLicense.js:6-12`、`VerifyLicenseDB.js:11-17` 硬编码 `testCode='wjkj2025~'`/`aseKey='wisdom23'`/`mode.ECB`；`<VerifyLicense>` 仅门控 DOM 插槽，不门控任何后端能力。
- **成立前提（务必保持）**：真正的能力/数据访问由后端鉴权（token/deviceId）强校验；`<VerifyLicense>` 不得被复用为任何安全用途。若将来授权需成为付费/合规硬边界，则此项回升为 CRITICAL，需改由服务端签发并校验。

#### [HIGH] 后端富文本经 `v-html`/`document.write` 未净化渲染 → 存储型 XSS
- **位置**：`equipment/equipmentIndex.vue:24`（`v-html` sink）、`basicTheory/study/basic/details/js/useDetails.js:50-95`（`iframe.document.write` 写入后端 `knowledgeSwfs[i].content`）、`components/common/NipUEditor.vue`（产出存储 HTML 的编辑器）
- **问题**：设备说明、课程/知识内容由 TinyMCE/UEditor 编写并存后端，前端以原始 HTML 直插，无净化，注入脚本会在其他用户的同源会话中执行。
- **证据**（已复核）：`<div v-html="deviceList[seeDeviceIndex].descriptions[seeDeviceDescIndex].content">`；`iframe.document.write(...)` 系列。仓库内存在 `public/UEditor/third-party/xss.min.js` 但**未**用于这些渲染路径。
- **修复**：对所有后端 HTML 走白名单净化（DOMPurify/已有 `xss.min.js`）后再渲染；`document.write` 改为净化后的 `srcdoc`/受控 DOM。CWE-79。

#### [HIGH] 全量会话态（含 token）存 localStorage，无 CSP
- **位置**：`login/useLogin.js:122-126`（写入 token/userInfo/userRole/userRouter）、`common/http/index.js:15-18`（读取并作为请求头回放）、`config/router/guards.js:58-81`（用 localStorage `userRouter` 重建路由）
- **问题**：全部鉴权物料对任意同源脚本可读；叠加上面的存储型 XSS 与无 CSP，一次注入即可外带 token+deviceId 完成账号接管；`userRouter/userRole` 亦可被客户端篡改。
- **修复**：token 走 `HttpOnly`+`Secure` cookie（或 Electron 安全存储）；补 CSP；`userRole/userRouter` 由后端在每次请求鉴权，不作为授权依据。CWE-522/1004/79。

#### [HIGH] 自动登录明文持久化凭据；系统内置弱默认口令
- **位置**：`login/useLogin.js:127-134`（明文写 `autoLoginInfo`）、`:14-20`（回填）；`systemManage/structure/js/useStructure.js:88-90,258-260`（硬编码 `123456`/`a123456`）
- **问题**：勾选自动登录即把明文用户名+密码写入 localforage（IndexedDB），并回填登录框；用户配置内置可猜默认口令。
- **证据**（已复核）：`localforage.setItem('autoLoginInfo', { username, password })`。
- **修复**：改存服务端签发的长效 refresh token，绝不落地明文密码；移除硬编码默认口令、强制首登改密。CWE-256/798/312。

#### [INFO] AES-ECB + 硬编码短密钥（仅服务于上述客户端软门控）
- **位置**：`common/utils/VerifyLicense.js:7-12`、`VerifyLicenseDB.js:12-17`
- **说明**：ECB 模式 + 随包 8 字节密钥本身是弱加密；但其唯一消费者是被接受为「非安全边界」的授权软门控，故风险随之降级为提示。**仅当**该 license 将来成为真实安全/合规边界时，才需改 AEAD（如 AES-GCM）+ 安全密钥管理。CWE-327/329/798。

#### [MEDIUM] 缺少 Content-Security-Policy
- **位置**：`index.html:1-12`（head 无 CSP meta；服务端头亦未观察到）
- **问题**：多个 `v-html`/`innerHTML`/`document.write` sink 缺少纵深防御，注入脚本可自由执行。
- **修复**：加严格 CSP（禁 inline script、限定源），配合净化。CWE-1021/693。

#### [MEDIUM] 依赖存在已知安全 CVE
- **位置**：`package.json:28` axios `^0.26.1`、`:36` crypto-js `^4.1.1`、`:43` mockjs（放在 dependencies）、`:60` xlsx `^0.18.4`
- **问题**：axios 0.26 早于 CVE-2023-45857（跨域凭据泄露）修复；xlsx 0.18.4 有原型污染 CVE-2023-30533 与 ReDoS CVE-2024-22363 且修复版**未发布到 npm**（`^0.18.4` 永远无法自愈）；crypto-js 4.1.1 → CVE-2023-46233（弱 PBKDF2）；mockjs 作为运行时依赖。
- **修复**：axios→1.x；crypto-js→4.2.0；xlsx 改用 SheetJS 官方 CDN 定版；mockjs 移到 devDependencies 或删除。

#### [MEDIUM] 敏感/基础设施信息进控制台与源码
- **位置**：`common/http/index.js:4`（打印后端 URL）、`VerifyLicenseDB.js:210-248`（打印机器码/密文/解密载荷）、`VerifyLicense.js:71-96`、`login/useLogin.js:121`（打印菜单树）、`index.html:19-27`（硬编码内网 IP/端口）
- **修复**：移除生产日志；配置信息运行时注入而非硬编码。CWE-532/200。

#### [MEDIUM] 无 HTTPS/TLS 强制，默认走明文 HTTP
- **位置**：`common/http/index.js:5-11`（无 scheme 时强制 `http://`）、`index.html:82-94`（`http://`/`ws://` 拼接）
- **问题**：token/凭据在网络上明文传输，暴露于局域网 MITM。
- **修复**：强制 `https`/`wss`；配置侧禁止明文回退。CWE-319。

#### [MEDIUM] Electron 渲染进程暗示不安全主进程配置 `[INFERENCE]`
- **位置**：`electron/ipcRenderer.js:1-29`（`window.require('electron')`+`ipc.sendSync`）、`index.html:58-66`、`VerifyLicenseDB.js:211`
- **问题**：`window.require('electron')`/`ipc.sendSync` 仅在 `nodeIntegration:true` 且 `contextIsolation:false` 下可用 → 渲染进程 XSS 可触达 Node/IPC 实现本地 RCE。主进程配置不在本仓库，故为推断（低置信）。
- **修复**：开启 `contextIsolation`、关闭 `nodeIntegration`，用 `contextBridge` 暴露最小 IPC 面。CWE-829/16。

#### [LOW] 客户端授权门控（按钮/路由）+ 反直觉的判定语义
- **位置**：`config/directive/ButtonPermission.js:1-25`、`config/store/index.js:6-33`、`config/router/guards.js:58-104`
- **问题**：UI 授权完全来自客户端 vuex permissions 与 localStorage userRouter，可篡改绕过；`indexOf(value)===-1` 时置 `isExist=true` 的判定令人误解。安全依赖后端。

#### [LOW] 其余较低风险 HTML sink
- **位置**：`common/mixin/useNotification.js:22-28`（`innerHTML` 邻近 WS 数据）、`components/common/VerifyLicense.vue:43-44`、`components/danmaku/Danmaku.vue:231-233`、`common/utils/useNumRain.js:16-18`
- **修复**：改文本插值或净化，尤其是与 WS 载荷相邻的通知 sink。

---

### 3.2 架构 / 构建工具 / 依赖健康 / 代码组织（4 HIGH / 6 MEDIUM / 4 LOW / 1 INFO）

#### [HIGH → 部分已闭合] 271MB `node_modules.zip`；无 lockfile → 构建不可复现
- **位置**：`frontend/node_modules.zip`（271MB，**在工作树但未入库**）；`.gitignore:2` 忽略 `node_modules` 目录但不匹配 `.zip`；`.gitignore:8` 忽略了 `package-lock.json`
- **问题**：唯一的 lockfile 被忽略 → 每次 `npm install` 重解析 `^` 区间，构建不可复现、可能悄悄漂移到含漏洞版本。
- **2026-09-08 更正**：本条原判「已跟踪」并要求用 `git-filter-repo` 清历史 —— 经核实该 zip **从未入库**：`git log --all -- frontend/node_modules.zip` 零提交、`git ls-files` 零命中。前端首次提交前它已被排除（GitHub 硬拒单文件 >100MB，否则直接推不上去），同时 `frontend/.gitignore:3` 补了 `node_modules.zip`。**历史无需重写。**
- **仍待修**：提交 lockfile —— 从 `.gitignore:8` 去掉 `package-lock.json`。

#### [HIGH] 依赖存在已知漏洞/EOL 的定版
- **位置**：`package.json:28` axios、`:60` xlsx、`:36` crypto-js、`:44` moment `^2.29.1`、`:27` ant-design-vue `^2.2.8`
- **问题**：见 §3.1 的 CVE；moment 2.29.1（CVE-2022-24785/31129，2.29.4 修复）且已维护模式、被 64+ 文件引用；ant-design-vue 2.x 与 Vue 3.5 搭配已长期 EOL（现行 4.x）。
- **修复**：升级 axios/crypto-js/moment（或迁 dayjs，已在 CDN 加载）；规划 AntDV 2→4 迁移或显式冻结并记录理由。

#### [HIGH] `manualChunks` 每个 npm 包一个 chunk
- **位置**：`vite.config.js:69-77`
- **问题**：返回 `node_modules/` 后第一段路径作为 chunk 名 → 每个（含大量传递）包各成一 chunk，产出海量小 chunk、加载瀑布、压缩率差、缓存抖动；`chunkSizeWarningLimit:1500`（`:53`）又掩盖了真正的大 chunk。
- **修复**：改为按大库分组的白名单（echarts、@babylonjs、ant-design-vue、three…），其余交给 Rollup 默认。

#### [HIGH] 复制粘贴子树重复 + 跨域污染
- **位置**：`views/manage/organization/{datagram,telex,handkey,electronKey}ZuXun/train/student/js/`；`preJob/{datagram,telegram}/teaching/**`
- **问题**：四个「组训」变体近乎相同；每个 `trainScore.js` 都导出叫 `telegramList()` 的函数（不论实际域）；`telexZuXun/.../datagramTrain.js:9-11` 竟导入 **electronKey** API；telex/datagram 的 `student.vue:257/258` 都写同一 localStorage 键 `'datagramZuXun'+trainId` → 数据碰撞；teaching 子树结构相同且各带一份 763KB `unpkg.js`。
- **修复**：抽 `useTrainScore(domainApi)`/`useTrainStudent(domain)` 共享 composable，localStorage 按真实域分键；teaching 合并为带 `lesson` prop 的单组件。

#### [MEDIUM] 死 Tauri 集成 + Electron 外壳缺席 → 误导性双目标
- **位置**：`package.json:8-10`（tauri 脚本）、`index.html:99-108`（`tauri.invoke`）；无 `src-tauri/`、无 `@tauri-apps/*`、无 `electron` 依赖；渲染侧 IPC 在 `src/electron/*`
- **问题**：Tauri 路径纯残留；应用实际跑在 Electron 下（12+ 组件用 `ipcRenderer.ipc.*`），但**本仓库无 electron 依赖与主进程代码**，外壳在仓外且无文档。
- **修复**：删 Tauri 脚本与死 `tauri.invoke`，或真正补 `src-tauri`；在 README 记录外部 Electron 宿主。

#### [MEDIUM] `@`/`@assets` 别名解析到文件系统根 `/src`（潜在 bug，当前因未使用而无害）
- **位置**：`vite.config.js:21-22`
- **问题**：`path.resolve(__dirname, '/src')` 因第二参是绝对路径而丢弃 `__dirname`，得到 OS 根 `/src`；只因**全仓 0 处 `@/` 导入**才没炸。
- **修复**：改 `path.resolve(__dirname, 'src')` 并推广 `@/` 以消灭深层相对路径。

#### [MEDIUM] 双状态管理：vuex 与 pinia 均在用
- **位置**：vuex `config/store/index.js`（`main.js:100`）、pinia `config/pinia/*`（`main.js:97`）；活跃点见 §2
- **修复**：统一到 pinia，迁移 `router/permissions/online` 后移除 vuex 依赖。

#### [MEDIUM] 冗余/重叠与死依赖（一个功能多套引擎）
- **位置**：`package.json:12-63`
- **问题**：3D×3（three 仅 `Waves.vue`、@babylonjs 教学页、`vue-unity-webgl` **0 引用**）；表格×3+CDN（xlsx；exceljs、@vue-office/excel **0 引用**；luckysheet 走 CDN）；富文本×2（tinymce + vue-ueditor-wrap）；docx×3（docx/mammoth/@vue-office/docx）；工具×2（lodash-es+ramda）、ID×3（uuid/uuid-umd/shortid）；`mockjs` 在 dependencies 且 0 引用、`vite-plugin-mock` 未接线；浏览器包里混入 Node 服务端库（body-parser/compression/formidable/compressing/bluebird）。
- **修复**：删死依赖、每类只保留一套、mockjs 移 dev。可显著减小安装与包体。

#### [MEDIUM] 调试 GUI `dat.gui` 进了生产教学页
- **位置**：`package.json:37`；`preJob/{datagram,telegram,receive}/teaching/Index.vue`(+`Index1.vue`) 如 `datagram/teaching/Index.vue:44`
- **修复**：从生产组件移除或以 dev flag 包裹。

#### [MEDIUM] 环境相关配置硬编码在 `index.html`
- **位置**：`index.html:18-27`（`window.serverConfig` 局域网 IP）、`:196`（chrome.exe 下载 URL）
- **修复**：端点改运行时注入（env/服务端 JSON），收敛三处解析分支。

#### [LOW] `Nip*` 前缀 + 中英/拼音命名不一致
- **位置**：13 个 `Nip*` 组件；拼音目录 `*ZuXun`（组训）与 `zuoshouzhou` 等标识
- **修复**：按职责重命名（如 `NipSerial`→`SerialPortPanel`），统一英文领域词。

#### [LOW] 拼写错误被固化进模块边界与导入
- **位置**：`common/api/broaddcastTeacheingApi.js`（broadcast/Teaching 拼错）、`gradingRule/{Datagram,Exam,Telex}GardRule.vue`（Gard→Guard/Grade）
- **修复**：在去重时一并改名并更新引用方。

#### [LOW] vendored 大文件位于 `src/`（被构建反复解析）
- **位置**：`common/mqtt/paho-mqtt.js`(~97KB)、`common/utils/{fontBank.js,Homophone.js,wb_color.js}`、`preJob/*/teaching/js/unpkg.js`(763KB×2)、三份 `table.js`(278KB)
- **修复**：真 vendor 库移 `public/` 或 npm 安装；`unpkg.js`/`table.js` 去重为单一共享资源。

#### [LOW] `main.js`/`index.html` 死代码
- **位置**：`main.js:28-39`（Vue2 `bind/unbind`）、`:95`（`productionTip`）、`index.html:63-74,97`（注释 ipc/`initJS`）
- **修复**：删除 Vue2-only 钩子与注释加载路径。

#### [INFO] ESLint/Prettier 配置引用未安装插件；anime.js 是真库（非 stub）
- **位置**：`.eslintrc.js:6-10,17` vs `package.json:65-105`（无 eslint/prettier/eslint-plugin-vue/eslint-plugin-prettier）
- **说明**：lint 跑不起来（实为死配置）。修正任务假设：`public/js/animejs/anime.min.js` 是真实 anime.js v3.1.0；`browserslist` 与 `vite.config.js:56` target 重复定义。
- **修复**：装齐 lint 工具链并加 `lint` 脚本，或删死配置；浏览器目标取单一真源。

**做得好**：Vite 构建硬化合理（`sourcemap:false`、terser `drop_console/drop_debugger`、`cssCodeSplit`、现代 target）；AntDV 按需加载（`unplugin-*`+`AntDesignVueResolver`）；pinia store 干净小巧，是收敛 vuex 的好目标；`index.html` 非 Chrome 提示遮罩与 `img-fallback` 防循环指令。

---

### 3.3 网络 / 数据层（4 HIGH / 6 MEDIUM / 3 LOW / 2 INFO）

#### [HIGH] axios 无 timeout 且静默吞掉网络/传输层错误
- **位置**：`common/http/index.js:7`（timeout 被注释）、`:70-111`（错误处理）
- **问题**：无 timeout → 后端/连接卡住则请求永久 pending，loading 永不结束；错误处理仅在 `error.response && error.response.status` 为真时提示，纯传输失败（连接拒绝/DNS/CORS/abort）直落 `:111` 的 `reject` 且**无任何 toast**。
- **修复**：设置真实 `timeout`；错误处理补 `!error.response` 的网络异常提示分支。

#### [HIGH] WebSocket 重连无退避/无上限/无心跳
- **位置**：`ws/Ws.js:74-81`、`ws/PublicSocket.js:32-37`、`ws/MessageWebSocket.js:103-107`、`unionJob/js/UnionWs.js:109-116`
- **问题**：固定 `setTimeout`（3000ms，PublicSocket 甚至 1000ms）无条件重拨；后端宕机时无限重连打爆服务端；无应用层心跳（`ws/` 内无 `setInterval|heartbeat|ping`）→ NAT/代理下的半开连接永不被发现。
- **修复**：抽共享重连助手（指数退避+抖动+上限）+ 心跳看门狗强关死连接。

#### [HIGH] 部分 socket 用 `window.httpUrl` 而非 `window.wsUrl` 拼 URL → Web 部署下畸形
- **位置**：`unionJob/js/UnionWs.js:41`、`unionJob/disturbCode/js/train.js:224`、`unionJob/lineNotify/components/ListenIn.vue:300`、`unionJob/lineNotify/js/Issue.js:62`（对照正确用法 `PublicSocket.js:10`、`Ws.js:18`、`useBroadStudent.js:112`、`useBroadTeacher.js:104`）
- **问题**：Web 分支 `index.html:81` 把 `httpUrl` 设为 `${protocol}//…/data`，于是 `ws://${window.httpUrl}` 变成 `ws://https://…/data`，被 WebSocket 构造器拒绝 → 网络协同训练 WS 在非 Electron 部署下失效。
- **修复**：所有 socket 统一走 `window.wsUrl`，删除 `ws://${httpUrl}` 变体。

#### [HIGH] 音频 worklet 门控把「毫秒」与「采样数」混用；采样递减被注释
- **位置**：`public/processor.js:170-198`（`handleAutomatedGain`）、`:172`、`:177`（递减注释）、`:191-192`、`:255-262`
- **问题**：`datumSamples` 以采样计（`criterion*ratio`）赋给 `currentRemainingSamples`，但唯一结束符号的判定是墙钟 `startTime + currentRemainingSamples < Date.now()`（ms 加采样数）；`currentRemainingSamples--` 被注释 → 时序不再来自音频时钟，点/划时长错误且随采样率错向变化。
- **修复**：用采样累加器驱动门控（每次 `process()` 按 `channel.length` 递减）并移除 `Date.now()` 比较；或统一换算为 ms。二选一。

#### [MEDIUM] 请求拦截器 POST 分支为死代码（潜藏双重 stringify 陷阱）
- **位置**：`common/http/index.js:20-22`（经 `common/http/axios.js:17`）
- **问题**：axios 先把 method 转小写 → `=== 'POST'` 永不命中；今天靠 axios 自动序列化侥幸正确，一旦被「修正」为 `'post'`，POST 体将被双重 `JSON.stringify` 静默损坏。
- **修复**：删除该分支，交由 axios 的 JSON transform。

#### [MEDIUM] 无集中式业务码处理；HTTP 状态 switch 基本是死代码
- **位置**：`common/http/index.js:29-69`（业务码）、`:74-107`（HTTP 状态）
- **问题**：后端恒 200、状态在 `data.code`；拦截器只集中处理鉴权码 203/204/205/206，业务失败（`code:500`/校验错）原样透传，各调用方需自行判 `res.code===200`（多半漏判 → 静默 no-op）；`:74-107` 的 400–505 分支几乎永不触发；且 203/204 在登录页 `:32` `return undefined` 破坏了信封契约。
- **修复**：加默认业务错误分支（带 opt-out）统一 toast 非 200 `code`；裁剪不可达的 HTTP 状态映射。

#### [MEDIUM] MessageWebSocket：模块级单例、导入期建 store/composable、硬编码 socket
- **位置**：`ws/MessageWebSocket.js:9-23,82`
- **问题**：`trafficStore/trafficDataStore` 在**模块导入期**解析（Pinia 未必就绪）；`webSerial/ws` 模块级单例被所有组件共享（两视图争一串口/一 socket）；`useDocumentVisibility()` 在 setup 外调用；socket URL 硬编码 `ws://localhost:18765/echo` 忽略 `window.wsUrl`；`beforeunload` 监听从不移除。
- **修复**：store/composable/socket 建于导出的 setup 内；URL 取自配置；`onUnmounted` 移除监听。

#### [MEDIUM] 串口握手状态为模块全局，跨实例共享
- **位置**：`common/utils/electronSerial.js:9-12,22`（另 `WebSerial.js:12-15` 为每实例）
- **问题**：`key_lock/liftTimer/cacheArr/isFirst` 为模块级 `let`，所有 Electron 实例共享一份握手状态；`isFirst` 从不重置 → `handlePort` 进程内只跑一次；类名 `WebSerial` 与另一个无关的 Web-Serial 类重名。
- **修复**：状态移到 `this`；teardown 重置 `isFirst`；重命名为 `ElectronSerial`。

#### [MEDIUM] WS 单例从不拆除；`sendData` 无 readyState 守卫
- **位置**：`ws/Ws.js:15-31,67-72`、`unionJob/js/UnionWs.js:38-54,89-96,118-121`
- **问题**：进程级单例的静态 `instance` 从不清空；`UnionWs.exit()` 关了 socket 却留着 `instance`+`onmessage` 闭包 → 下次 `getInstance()` 拿到 `flag=false` 的陈旧实例、掉线不自动重连；`Ws` 构造期建 `useNotification()` 永久持有；`sendData` 不判 `readyState` → open 前/掉线后发送抛 `InvalidStateError`。
- **修复**：`exit()`/close 时置空 `instance`；`send` 前判 `readyState===OPEN`，否则缓冲/丢弃。

#### [MEDIUM] 两处组件绕过共享 axios 实例
- **位置**：`basicTheory/test/questionBank/js/knowledgeTabel.js:9,588-601`、`preJob/receive/explain/Index.vue:119`
- **问题**：直接 `import axios`（非 `../http/axios.js`）→ 无 baseURL、无 token/deviceId 注入、无鉴权码拦截；`knowledgeTabel.js` 手工补头并硬编码 URL，与实例逻辑漂移（缺 `:6` 的协议归一）。
- **修复**：统一走共享实例（如需 blob 下载在 `axios.js` 加 `responseType` 透传）。

#### [MEDIUM] `WebSerial.resetPort` 引用未定义 `callback`；`open()` 错误被吞
- **位置**：`common/utils/WebSerial.js:41-45,47-56`
- **问题**：`resetPort()` 调 `this.handlePort(callback)` 但 `callback` 既非参数也不在作用域 → 必抛 `ReferenceError`；`handlePort` 中 `port.open()` 失败被空 catch 吞掉后仍继续 `this.port.readable.getReader()` → 在未打开的端口上抛错且 `keepReading=true`。
- **修复**：`resetPort` 接收并转发 callback；`open()` 失败即 `callback({code:-1})` 返回，不再 `getReader()`。

#### [LOW] 串口层硬编码路由名白名单，非白名单页丢数据
- **位置**：`common/utils/WebSerial.js:66-83`、`electronSerial.js:34-48`
- **问题**：仅当 `location.hash` 页名在硬编码约 10 个训练路由名数组中才处理串口字节；新增训练页需在两文件各加字符串（列表已分叉：`WebSerial` 里 `lineNotifyTrain` 重复两次）；表现层路由塞进传输层是分层违规。
- **修复**：消费者自行 `PubSub` 订阅/退订；从串口读取器移除路由名门控。

#### [LOW] API 模块：重复端点 & GET 约定不一致
- **位置**：`common/api/UserApi.js:11-24`（`addSignin` 与 `userSignIn` 都 POST `/api/user/signin`）、`ReceiveApi.js:197-222`、`TelegramApi.js:59-64`
- **修复**：去重导出；GET 统一 `data`→`params`，停止手拼查询串。

#### [LOW] baseURL/httpUrl 在导入期读取，可致启动崩溃
- **位置**：`common/http/index.js:4-6`
- **问题**：`window.httpUrl.indexOf("http")` 在模块加载期执行；若 `httpUrl` 尚未由 `index.html` 的异步配置赋值则 `undefined.indexOf` 抛错、整包中止；并有生产 `console.log`。
- **修复**：`(window.httpUrl || '')` 守卫；`baseURL` 惰性计算或启动断言配置存在；删 log。

#### [INFO] MQTT(paho)：vendored 大文件、硬编码 broker、清理薄弱
- **位置**：调用方 `equipment/trainScore/Index.vue:309-310` 与近乎相同的 `equipmentOperate/trainScore/Index.vue:309-310`；库 `common/mqtt/paho-mqtt.js`（97KB，未逐行审）
- **问题**：仅两处重复视图使用；broker 硬编码、clientId 为空（无法可靠恢复会话）、用户名/密码空；无 `onUnmounted` 断连、无 `onConnectionLost` 重连 → 离开页面可能泄漏连接。
- **修复**：抽单一 MQTT composable（去重两视图）；给稳定 clientId；`onUnmounted` 断连；加 `onConnectionLost`。

#### [INFO] processor.js：音频线程内调试日志；节点从不拆除
- **位置**：`public/processor.js:56,148-165,167`
- **问题**：`addCode` 消息处理在音频线程 `console.log`；`process()` 恒 `return true` → 节点常驻且无断开路径；`delete this.morseCode[i]` 造成稀疏数组使 `.length` 与进度计算失真。
- **修复**：移除音频线程日志；用 `splice` 替代 `delete`；暴露 stop/close 供卸载时断开。

**做得好**：共享 axios 实例集中了 token/deviceId 注入与 203/204/205/206 跳登录流程；`PublicSocket.js` 正确以 `readyState==1` 守卫 send 并在 close 清定时器（其他 socket 应效仿）；API 模块统一薄封装便于清点；AudioWorklet 离主线程合成 Morse 是正确架构。

---

### 3.4 路由 / 状态管理 / 鉴权流程（4 HIGH / 5 MEDIUM / 4 LOW / 2 INFO）

#### [HIGH] `useTraffic` 泄漏 pinia 订阅并跨消费者共享模块级状态
- **位置**：`common/mixin/useTraffic.js:6-12,16,33,40-47`（消费者 `NipSerial.vue:53`、`PreviewHJ.vue:493`、`PreviewHJJ_LJ.vue:246`、4× `useControl.js`）
- **问题**：`$subscribe(...)` 返回的退订函数从不捕获/调用；`onUnmounted` 只 `clear()` 不退订 → 每次挂载都新增订阅、回调随挂/卸周期累积成倍执行；`wsOnline/devOnline/knockData/voiceFreq` 为模块级 ref → 所有调用方共享、状态互撞。
- **修复**：`const stop = store.$subscribe(...)` 并在 `onUnmounted` 调 `stop()`；共享 ref 移入工厂函数内。

#### [HIGH] 双状态层（vuex+pinia）；vuex 多为死代码且 `online` 从不赋值
- **位置**：`config/store/index.js:1-34`、`main.js:97,100`、`config/pinia/index.js:1-4`；提交点 `PreviewHJJ_LJ.vue:360`（`PreviewHJ.vue:667` 已注释）；读取点 `unionJob/unionTrain/js/Room.js:15-16`
- **问题**：`setRouter/setOnline` 从不提交、`getRouter/getOnline` 从不读取（死代码）；`state.online` 永为 `{}` → `Room.js` 读到 `undefined`（隐性 bug）；仅 `setPermissions` 活跃且仅 HJJ/LJ 主题提交。
- **修复**：删死 vuex 状态/getter，统一到 pinia（traffic store 已有 `linkStatus/devStatus`），`Room.js` 改指 pinia。

#### [HIGH] `v-per` 权限门控：纯客户端、失败即放行、非响应式、主题不一致
- **位置**：`config/directive/ButtonPermission.js:1-25`；`PreviewHJJ_LJ.vue:353-361` 填充、`PreviewHJ.vue:660-668` 停用；用法如 `equipment/trainList/Index.vue:7-8`
- **问题**：(1) permissions 为空时 `hasPermission` 返回 false → 受控元素被**保留**（失败即放行）；(2) `isExist` 语义倒置且名不副实；(3) 只在 `mounted` 运行 → 后续 `setPermissions` 不会重新隐藏/显示已挂载按钮（非响应式）；(4) HJ 主题从不提交 permissions → `getPermissions` 恒 `[]` → HJ 下**所有** `v-per` 元素被移除，而 HJJ/LJ 正常；(5) 纯客户端信任、可绕过。
- **修复**：后端对每个受控动作强校验；跨主题统一填充；用响应式 store 绑定（`v-if`）替代 mount-only 指令；重命名助手。

#### [HIGH] 鉴权守卫只信 token「存在」；过期/无效 token 不主动清理
- **位置**：`config/router/guards.js:22-36`；拦截器 `common/http/index.js:29-68`
- **问题**：守卫仅凭 `getItem('token') !== null` 放行（`:23,:31`）；按后端契约 token 过期 `getUserByToken` 返回 null（码 203/204/205/206），但守卫无「有效性」概念 → 过期 token 仍渲染整壳；恢复只在首个 API 命中拦截器后 `location.href='#/login'` 被动触发；且 203/204 在登录页 `:31-33` `return undefined`，调用方读 `res.code` 抛错。
- **修复**：受保护导航时校验 token（或调轻量 `me` 端点）；集中「会话失效 → 清存储 + 跳登录」单一路径；登录页短路返回规范化/拒绝的 promise。

#### [MEDIUM] 登出从不使服务端 token 失效
- **位置**：`common/api/UserApi.js:32-36`（`userLoginOut`→POST `/api/user/userOut`），`App.vue:41` 导入但**从不调用**；实际登出仅 `router.replace('/login')`（`PreviewHJJ_LJ.vue:387`、`PreviewHJ.vue:691`）→ 守卫清 localStorage
- **问题**：用户登出只清客户端；服务端 token 至自然过期前仍有效 → 被截获 token 登出后仍可用。
- **修复**：登出先 `await userLoginOut()` 再清存储/跳转；若确废弃则删导入。

#### [MEDIUM] 守卫就地改 `router.options.routes` 并经共享 PubSub `flag` 延迟 `next()`
- **位置**：`config/router/guards.js:17-18,42,59,79,82`；`skipGuards.js:3-18,20-31`
- **问题**：`handleRouter` 就地改活动路由数组并按 `name` 重复 `addRoute`（脆弱地耦合 vue-router 内部）；`handleSkip` 未 await `nestedPatDown(to).then()` 就 `next()`（竞态）；`skipGuards` 用**模块级 `flag`** 并把 `skip()/next()` 延迟到某 PubSub 回调到达；若无订阅者发布回调则 `next()` 永不调用 → **导航永久挂起**；并发导航共享 `flag` → 竞态。
- **修复**：从源数组构建动态路由并 `addRoute(parentName, child)`；await `nestedPatDown` 再 `next()`；`skipGuards` 改每次调用独立状态 + 超时兜底。

#### [MEDIUM] 动态路由构建对畸形 `userRouter` 载荷抛错
- **位置**：`config/router/guards.js:97-104`（`handlePermissions`，由 65/72/88 调用）
- **问题**：`if (r.permissions !== null)` 在 `undefined` 时也为真 → `r.permissions.forEach` 抛错；`r.meta.permissions=[]` 在 `r.meta` 缺失时抛错；整个构建在 `beforeEach` 内无 try/catch → 一个坏节点即锁死全部导航。
- **修复**：用 `Array.isArray(r.permissions)` 守卫并确保 `r.meta` 存在；构建包 try/catch 回退 `/login` 或 `/404`。

#### [MEDIUM] `waves` 全局指令用 Vue2 钩子名 → 监听泄漏，且实为死代码
- **位置**：`main.js:5,23-38`；`config/directive/waves/waves.js`；`waves/index.js`（全注释）；模板 `v-waves` 全注释
- **修复**：整体删除，或将钩子迁 Vue3（`mounted/updated/unmounted`）并删死文件。

#### [MEDIUM] 自动登录明文持久化凭据
- **位置**：`login/useLogin.js:127-134`（写）、`14-20`（读）
- **说明**：与 §3.1 重叠，此处从鉴权流程完整性角度记录。
- **修复**：改存服务端 refresh token；至少加密。

#### [LOW] `to.path.endsWith("login")` 作为登出/清理触发器过于脆弱
- **位置**：`config/router/guards.js:10` —— 任何以 "login" 结尾的路径都会清全部鉴权存储。
- **修复**：改 `to.name === 'Login'` 或 `to.path === '/login'`。

#### [LOW] 请求拦截器 POST 序列化分支为死代码
- **位置**：`common/http/index.js:20-22`（与 §3.3 同因）。

#### [LOW] `trafficDataStore.$subscribe` 假设 `message.d` 存在
- **位置**：`common/mixin/useTraffic.js:33-38` —— `state.message.d[0]` 在默认 `{}` 时抛 `TypeError`。
- **修复**：`state.message?.d?.[0]`。

#### [LOW] `NipPagePermission` 页名索引错配、订阅泄漏、列表重复项
- **位置**：`components/common/NipPagePermission.vue:95,122,111-117,39-61`
- **问题**：`pageName` 用两个不同数组求索引（含 `/` 查询串时越界得 `undefined`）；`PubSub.subscribe('receiveProcessData')` 仅在回调内 `status==='initialized'` 时退订、无 `onUnmounted` 兜底 → 泄漏；`voiceList` 重复多项。
- **修复**：`pageName` 取 `useRoute().name`；`onUnmounted` 退订；去重 `voiceList`。

#### [INFO] 登录 role→roles 迁移隐患（代码内已标注）
- **位置**：`login/useLogin.js:125` —— 客户端存 `data.role`，V2 后端返回 `roles` 将静默破坏 `userRole` 消费方。

#### [INFO] `handleRouter` 会话内不重建路由
- **位置**：`config/router/guards.js:22,58-59,82` —— 动态路由每次页面加载只建一次，服务端权限/菜单变更需整页刷新才生效（桌面应用可接受，建议文档化）。

**做得好**：拦截器集中注入 token+deviceId 并把 203/204/205/206 汇入单一 modal→登录流；`useTable/useUpload/useFontSize` 等小 composable 干净且正确返回；动态路由的 `-1/0` 哨兵映射到 `TransitionPage/ExcessPage` 是合理且有注释的扁平化方案；pinia `global` store 集中主题并派生 `leftWidth`。

---

### 3.5 组件 / 代码质量（2 HIGH / 4 MEDIUM / 2 LOW / 3 INFO）

> 总体：逻辑大多已抽到 composable，故超大 `.vue` 多是「模板+内联 LESS」重而非「上帝逻辑」重。教师→学生消息类型 1–4 全覆盖，无跨界丢消息。真正的功能 bug 是广播训练的双定时器。

#### [HIGH] 双 `setInterval` 导致时间双倍计数并泄漏定时器
- **位置**：`components/BroadcastTeachTrain/js/useBroadStudent.js:139-149`
- **问题**：教师「继续」（`d.type==3`）且 `codeIndex===0` 时，`:140` 建一个定时器赋给 `trainTimer.value`，`:146` 又建一个相同 `setInterval` 重新赋给同一 ref → 第一个句柄丢失，`clearInterval(trainTimer.value)` 永远停不掉它：两个 interval 同时触发，`storage.value.totalTime` 每秒 +2，孤儿 interval 直到页面卸载才停。
- **修复**：恢复时只建一次 interval（删冗余的 `:140`，或每次重赋前先 `clearInterval`）。

#### [HIGH] 去重约 1.3MB 字节级完全相同的入库源文件
- **位置**：`equipment/trainScore/js/table.js`（278,591B）与 `equipmentOperate/trainScore/js/table.js` 相同；`preJob/{telegram,datagram}/teaching/js/unpkg.js`（763,553B）相同；`wb_colork.js` 与 `hanzi/wubi/practice/js/wbColor.js`（251,184B，diff=0）；teaching `jsonData.js`（9,070B）×2。（均经 md5 复核一致）
- **问题**：一处修复要改 N 份，两份 278KB `table.js` 易静默分叉。
- **修复**：各抽单一共享模块，两处 import。

#### [MEDIUM] 合并已开始分叉的整文件重复组件
- **位置**：`gradingRule/Telex.vue` vs `IndexDelete.vue`（501 行仅差 16 行：组件名、一处 import 顺序、激活项配色）；`gradingRule/DatagramGardRule.vue` vs `TelexGardRule.vue`（444 行仅差 16 行，且 TelexGardRule 的 v-for 加了 `:key="index"` 而 Datagram 仍缺）
- **问题**：复制兄弟已分叉 → 在一处修的 bug 到不了另一处。
- **修复**：各合并为带 `mode/theme` prop 的单组件并删副本。

#### [MEDIUM] 给缺失 `:key` 的 `v-for` 补 key（约 270 处，跨 94 文件）
- **位置**：如 `gradingRule/DatagramGardRule.vue:6`（`v-for="(r, index) in ruleList"` 无 key）
- **问题**：缺 `:key` 触发 Vue 就地复用启发式；源数组重排/增删时组件状态/DOM 可能绑错行（陈旧输入、激活样式错位）；兄弟 `TelexGardRule.vue` 已加 `:key` 佐证这是缺陷非风格。
- **修复**：为每个 `v-for` 加稳定 `:key`（优先领域 id 而非数组下标）。

#### [MEDIUM] 用命名常量替代数字魔法状态比较
- **位置**：`useBroadStudent.js:122-167`（及全仓约 467 处 `.status == N`）
- **问题**：训练状态（0/1/2）与消息类型（`d.type==1|2|3|4`、`bwType`、`bdType`）在模板与 composable 间散布、无与后端契约对齐的共享枚举 → 改值/差一即静默错路由。
- **修复**：定义共享 `TRAIN_STATUS`/`BROADCAST_MSG` 枚举，生产/消费两端引用。

#### [MEDIUM] 拆分超大单文件组件（模板 + 内嵌 LESS）
- **位置**：`basicTheory/test/test/studentAddTest/Index.vue`（2517 行；`</template>` 在 155，`<style scoped>` 从 180 起 → 约 2337 行内嵌样式）；`questionBank/Index.vue`(1748)、`addTest/Index.vue`(1570)、`analyze/Index.vue`(1294)、`BroadStudent.vue`(1298)；`components/preJob/hanzi/SelfKeyboard.vue`(1627，且全逻辑内联 Options API 未抽 composable)
- **修复**：内嵌 LESS 外移到同名 `.less`（examTrain 已如此）；`SelfKeyboard` 逻辑抽 composable。

#### [LOW] 清除约 253 处遗留 `console.log/debug`
- **位置**：`common/utils/VerifyLicenseDB.js`(14)、`WebSerial.js`(13)、`useBroadTeacher.js`(7)、`examTrain.js`(7) 等约 50 文件
- **说明**：虽 terser `drop_console` 生产会移除，但含 license/串口模块的调试日志仍不应留在源码。
- **修复**：删除或以 debug flag 门控。

#### [LOW] 重复内联样式移入 scoped 类
- **位置**：`SelfKeyboard.vue`（68 处内联 style）、`studentAddTest/Index.vue`（35 处）、gradingRule 内联定位
- **修复**：重复内联样式提升为 scoped class（也是 Datagram/Telex GardRule 分叉之源）。

#### [INFO] 生产源码树剔除 demo/scratch 代码
- **位置**：`views/demo/photo/index.js`（584,270B）、`views/demo/photo/Index.vue`、`views/demo/Index.vue`
- **修复**：从构建排除或删除 `views/demo`。

#### [INFO] `common/ActionBtn` 与 Electron 解耦并用响应式 `interfaceStyle`
- **位置**：`components/common/ActionBtn.vue:104`（直接 import ipcRenderer）、`:125`（`const interfaceStyle = window.interfaceStyle` 非响应式）
- **修复**：IPC 抽象为运行时探测适配器；`interfaceStyle` 响应式取值。

#### [INFO] 删除死代码：空 script 块与注释的重连逻辑
- **位置**：`BroadStudent.vue:63`（`<script></script>` 空块）；`useBroadStudent.js:117`（`// login()` on onclose）、`:158-159`（注释 `playCodeInfo()/operation()`）
- **修复**：删死块；若确需重连则显式实现。

**做得好**：领域逻辑普遍抽入 composable；`examTrain` 已把 scoped LESS 外移到 `css/*`（可推广模板）；教师/学生消息类型全覆盖。

---

### 3.6 性能 / 打包 / 资源 / 可访问性 / 国际化（6 HIGH / 5 MEDIUM / 1 LOW / 3 INFO）

#### [HIGH] 为一个完全死掉的组件打包 three.js + 同时装两套 3D 引擎
- **位置**：`components/common/Waves.vue:14`（`import * as THREE`，但 `:19-195` 逻辑全注释）；消费者 `login/components/LoginHJ.vue:248`；Babylon 在 `preJob/*/teaching/Index.vue`（如 `datagram/teaching/Index.vue:43-45`）
- **问题**：`Waves.vue` 只渲染空 `<div>`，却仍激活 `import * as THREE` → 整个 three.js（~600KB raw）被拉进登录 chunk 做零功能；Babylon 才是教学页真正引擎 → 两套 3D 引擎，其一纯死重。
- **修复**：删 `Waves.vue` 及其在 `LoginHJ.vue` 的引用、从 `package.json` 移除 `three`；Babylon 在教学路由 chunk 内懒加载。

#### [HIGH] `index.html` 里全局、渲染阻塞的编辑器/库在每页加载
- **位置**：`index.html:252-254`（`dayjs/anime/tinymce` 的 body 末 `<script>`）、`:13-16`（head 的 localforage）；UEditor 全局注册 `main.js:9,102`
- **问题**：TinyMCE（~500KB+）在**每次**应用加载时同步引入（仅少数编辑页用到），dayjs/anime 同理，且是经典阻塞 `<script>`；UEditor（`ueditor.all.js` 1.1MB）也全局接线。
- **修复**：移除三个 `<script>`，TinyMCE 仅在编辑组件内动态 import（deps 已有 npm 版）；`VueUeditorWrap` 仅在需要的路由注册；至少加 `defer`/`type=module`。

#### [HIGH] 15+ 文件 `import * as echarts`（全量）—— 无 tree-shaking
- **位置**：`components/personal/js/personal.js:7`、`test/testAnalyse/testAnalyse.vue:51`、`studyManage/**/js/{useChart,...}.js`、`organization/**/js/{trainScore,teacher,...}.js` 等约 15+ 处；`package.json:38` echarts 5.4.2
- **问题**：`import * as echarts` 拉入整个 ECharts（~1MB raw / ~330KB gz）而非可 tree-shake 的 `echarts/core` + 按需注册。
- **修复**：改 `echarts/core` 只注册用到的图表/组件/渲染器，chunk 可减约 60–70%。

#### [HIGH] `manualChunks` 每包一 chunk —— chunk 爆炸 + 循环初始化风险
- **位置**：`vite.config.js:69-77`
- **问题**：每个顶级包各成一 chunk → 数百小 chunk、请求多、压缩差；跨包边界切分循环依赖有「Cannot access X before initialization」运行时风险 `[INFERENCE]`（chunk 爆炸是确定的）。
- **修复**：改按大库分组（vendor-vue/antd/charts/3d/office）+ 默认 vendor chunk。

#### [HIGH] 仓库臃肿：vendored React blob 入 `src`（`node_modules.zip` 未入库）
- **位置**：`preJob/{datagram,telegram}/teaching/js/unpkg.js`(各 745.7KB)；`frontend/node_modules.zip` 只在工作树，未进 VCS
- **问题**：`unpkg.js` 是 vendored **React 16.13.1 + scheduler** 生产包（两份约 1.5MB），入库进一个 Vue 应用的源码 —— 若被引入则多带整套第二框架，若未用则是撑树的死文件。
- **证据**：`unpkg.js:32 @license React v16.13.1`。
- **修复**：确认 teaching 是否 import `unpkg.js`，用则换正规依赖、不用则删两份；有 npm 等价物的大文件去 vendor 化。`node_modules.zip` 已被 `frontend/.gitignore:3` 排除，无需再处理。

#### [HIGH] 可访问性：全树无 ARIA、可点击 `<div>`、禁缩放、`lang` 错误
- **位置**：`index.html:2`（`<html lang="en">` on 中文 UI）、`:6-9`（`user-scalable=no`）；可点击非语义元素如 `common/utils/ocr/OcrComp.vue:14,22,24,32,56-58,91-95`（`<div class="operBtn" @click>`）
- **问题**：全 `src` 对 `role=`/`tabindex`/`aria-*` grep **零命中**；交互控件是 `<div @click>`（不可聚焦、键盘不可操作、无可访问名）；`user-scalable=no` 违反 WCAG 1.4.4；`lang="en"` 向读屏/拼写误报语言。
- **修复**：`<html lang="zh-CN">`；去 `user-scalable=no/maximum-scale`；动作按钮改 `<button>`（或 `role="button"`+`tabindex`+keydown）；有意义图片加 `alt/aria-label`；优先改共享组件。

#### [MEDIUM] 五套重复主题资源树 —— 数百 PNG + 重量级动图
- **位置**：`src/assets/{HJ,HJJ,LJ,KJ,GD}/**`（同套图各一份）；重货 `KJ/bar.gif`(311KB)、`{LJ,HJJ}/menu.png`(375KB)、`GD/tab-header.png`(90.6KB)、`HJ/train/cs.gif`
- **问题**：`menu.png`(375KB) 驱动 `animation: icon2 1.5s steps(45) infinite`（`dashboard/NipMenus.vue:121-122`）—— 375KB 精灵图无限动画；`bar.gif`(311KB) 作重复背景（`styles/HJ/common.css:4`）；无 WebP/AVIF、无精灵合并。
- **修复**：大 PNG 精灵/GIF 转 WebP（或 `menu.png` 改 CSS/SVG 动画）；去重跨主题相同资源；`bar.gif` 改 CSS 渐变/边框。

#### [MEDIUM] 构建插件已装未接线 —— 无压缩、无 PWA、无 HTML 压缩
- **位置**：`vite.config.js:10-18`（仅 `vue/AutoImport/Components`）vs `package.json:96-103`
- **问题**：`vite-plugin-compression/pwa/html/mock/svg-icons/style-import/theme/windicss/purge-icons` 全在 devDeps 但**均未注册** → 无 gzip/brotli 预压缩、无图标 purge（`vite.config.js:45` 注释提及 `@purge-icons/generated` 却未接线）。
- **修复**：至少接 `vite-plugin-compression`（brotli+gzip）；删未用插件。

#### [MEDIUM] 大表 `:pagination="false"` 全量渲染且无虚拟化
- **位置**：约 20+ 列表视图，如 `study/basic/manage/Index.vue:39-41`、`studyManage/classHours/Index.vue:34`、`*ZuXun/list/Index.vue:18`、`dept/member/Member.vue:16`
- **问题**：`a-table`（AntDV 2.x 无内建行虚拟化）以 `:pagination="false"` 直绑全量数组 + 手写「共 N 条」→ 大数据集一次性全渲染，挂载慢、内存高、滚动卡。
- **修复**：启用分页或改虚拟化表；至少限制渲染行数。

#### [MEDIUM] `@antv/g2plot` 仅为一个 `log()` 工具被引入（含启动路径）
- **位置**：`common/api/UserApi.js:1`（`import { log } from '@antv/g2plot/lib/utils/invariant.js'`）、`useDetails.js:10`、`postJob/telex/js/trainScore.js:9`、`hanzi/pinyin/practice/js/parctice.js:10`、`telegram/examTrain/ExamBasicTrain.vue:166`；整库引入 `dashboard/NipTop.vue:93`
- **问题**：仅为拿 `log` 而深引图表库；`UserApi.js` 在启动路径（`App.vue` 从中导入 `userLoginOut`）→ 有把 g2plot 拉进入口 chunk 的风险 `[INFERENCE]`。
- **修复**：`log` 换 `console`/本地工具；非图表模块移除 g2plot；g2plot 仅在真出图处懒加载。

#### [MEDIUM] 递归 `setTimeout`/interval 无卸载清理
- **位置**：`components/common/ShortcutMenu.vue:84-105`（`startServerTiming/startLocalTiming` 每秒自递归，setup 作用域启动，无句柄无 `onUnmounted`）；`preJob/telegram/addTelex/Index.vue:44-55`（罗盘 `setInterval`）；`preJob/telegram/train/js/basicTrain.js:110-112`（FPS RAF）
- **问题**：`ShortcutMenu` 启两个无限 1s 递归定时器且无拆除 → 卸载后仍触发，泄漏定时器与响应式更新。
- **修复**：存句柄并 `onUnmounted` 清理；优先 vueuse `useIntervalFn`（自动停）。

#### [LOW] `assetsInlineLimit:4096` 内联大量小图；导入 0 字节死 `tailwind.css`
- **位置**：`vite.config.js:52`；空文件 `styles/{HJ,HJJ,LJ,KJ,GD}/tailwind.css`(0B) 于 `main.js:2` 导入
- **问题**：<4KB 主题 PNG 被 base64 内联进 JS/CSS，撑大 chunk 且失去缓存；`tailwind.css` 为 0 字节（Tailwind 实际经各主题 `index.less` 的 `@tailwind` 指令交付）→ `main.js:2` 导入死空文件。
- **修复**：降 `assetsInlineLimit`（如 2048）或排除小 UI PNG；删空 `tailwind.css` 及 `main.js:2` 导入。

#### [INFO] 无 i18n 框架 —— 全量硬编码中文、`zhCN` 固定
- **位置**：`App.vue:20,25`；`index.html:127` 标题；`package.json` 无 `vue-i18n`
- **说明**：单语内销产品可接受（故 INFO），但与 `<html lang="en">`（见 a11y HIGH）冲突，当前向辅助技术误报语言。
- **修复**：无需补语言覆盖；若将来多语再引 `vue-i18n`；无论如何先修 `lang`。

#### [INFO] Vuex 与 Pinia 同时打包安装（此处仅计包体影响，归属见 §3.4）

**做得好**：路由组件与主题样式组件经动态 `import()`/`defineAsyncComponent` 正确分包；terser `drop_console/drop_debugger` + 现代 target + `cssCodeSplit`；定时器/监听清理纪律总体良好（142 文件成对 `onUnmounted`/`clearInterval`）；音频走 `AudioWorklet` 而非废弃的 `ScriptProcessorNode`。

---

## 4. 修复优先级路线图

**P0（安全，尽快）**
1. 后端富文本统一净化后再渲染（DOMPurify / 复用 `xss.min.js`）——§3.1 HIGH。
2. token 迁 HttpOnly cookie / Electron 安全存储 + 加 CSP；停止明文持久化密码——§3.1/§3.4 HIGH。
3. 登出调用后端失效 token；守卫校验 token 有效性——§3.4 HIGH。
4. Electron 主进程开 `contextIsolation`/关 `nodeIntegration`（需仓外外壳配合）——§3.1 MEDIUM。

**P1（稳定性/正确性）**
5. axios 加 timeout + 网络错误提示；业务码集中处理——§3.3 HIGH/MEDIUM。
6. WebSocket 统一 `wsUrl` + 退避/上限/心跳 + `readyState` 守卫 + 单例拆除——§3.3 HIGH/MEDIUM。
7. 修 `useBroadStudent` 双定时器、`useTraffic` 订阅泄漏、`ShortcutMenu` 定时器泄漏、`WebSerial.resetPort` 引用错误——§3.5/§3.4/§3.6/§3.3。
8. 守卫 `handlePermissions` 加健壮性 + try/catch；`skipGuards` 加超时兜底——§3.4 MEDIUM。

**P2（工程债/性能）**
9. 提交 lockfile、修 `@` 别名——§3.2 HIGH/MEDIUM（`node_modules.zip` 已排除入库，不再是待办）。
10. 去重（`table.js`/`unpkg.js`/`wb_color*`/组件对/ZuXun 子树），修同键碰撞——§3.5/§3.2 HIGH。
11. 统一到 pinia、删 vuex 与死依赖（three/exceljs/vue-unity-webgl/mockjs…）——§3.2/§3.4 MEDIUM。
12. `echarts/core` 按需、`manualChunks` 分组、TinyMCE/UEditor 懒加载、接 compression 插件——§3.6 HIGH/MEDIUM。
13. 升级 axios/crypto-js/moment/xlsx——§3.2/§3.1。

**P3（可维护性/可访问性）**
14. 修 `<html lang>`、去禁缩放、动作元素语义化、共享组件补 ARIA——§3.6 HIGH。
15. 装齐 lint 工具链或删死配置；魔法数字改枚举；补 `:key`；清 `console.log`；剔除 `views/demo`——§3.2/§3.5。

---

## 5. 附录：方法与验证

- 6 个只读子代理并行覆盖上述 6 域，各按统一格式产出，父代理合并。
- 父代理独立复核（`read`/`grep`/`md5sum`）的关键事实：
  - `md5sum` 确认 `unpkg.js`/`table.js`/`wb_color*.js` 跨路径**字节相同**；`Telex.vue`/`IndexDelete.vue` 为近似分叉（hash 不同）。
  - vuex 与 pinia 均在用：`useStore`/`vuex` 引用 4 处，pinia 引用 31 处；`config/store/index.js` 为 `createStore`（vuex），持 `router/permissions/online`。
  - 拦截器对 203/204/205/206 弹窗后仍 `return response.data`（`index.js:69`）；登录页 203/204 `return undefined`（`:32`）；`config.method === 'POST'`（`:20`）为死分支。
  - 授权软门控（经负责人确认非安全边界、不计缺陷）：`testCode='wjkj2025~'`、`aseKey='wisdom23'`、`mode.ECB` 在 `VerifyLicense.js` 与 `VerifyLicenseDB.js` **均**存在。
  - XSS：`equipmentIndex.vue:24` `v-html` 后端 `content`；`useDetails.js:50-70` `iframe.document.write`。
  - 明文口令：`login/useLogin.js:128-131` 写 `autoLoginInfo`，`:18` 回填。
- 未覆盖/推断项：无 `src-tauri/`，Electron 主进程配置不在本仓库（§3.1 MEDIUM 为推断）；vendored 大 blob（UEditor/tinymce/paho-mqtt/unpkg/fontBank）未逐行审逻辑，仅记录其存在/大小/重复。
- 局限：未执行 `npm run build`/未安装依赖（只读评审）；行号基于评审时快照，后续改动会偏移。
