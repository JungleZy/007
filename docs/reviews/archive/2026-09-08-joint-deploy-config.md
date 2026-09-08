# 部署/配置/网络与运行时形态（前后端联合评审分片）
- 日期：2026-09-08 / 范围：部署形态 × HTTP base/WS/CORS/TLS/上传/构建交付/版本/配置真源 / 方法：只读取证

## 0. 分片结论与计数
| 定级 | 条数 | 责任分布(FE/BE/双侧) |
| --- | --- | --- |
| J-P0 | 0 | — |
| J-P1 | 1 | FE 1（+需BE/运维反代该形态才成立） |
| J-P2 | 1 | 双侧 1 |
| J-P3 | 3 | FE 1（DC-J-P3-03）、双侧 2（DC-J-P3-01 版本、DC-J-P3-02 CI） |
| 合计 | 5 | — |
条目：DC-J-P1-01、DC-J-P2-01、DC-J-P3-01、DC-J-P3-02、DC-J-P3-03（与 findings 一一对应）。

## 1. 契约清单与覆盖率
覆盖的跨栈接口面：
1) 前端三分支 base 拼装（Electron/index.html:62-74；Web-https/index.html:76-81；Web-http/index.html:82-88；tauri 死路径 index.html:99-108）× 后端 /api 前缀 + 18001（application.yml:10、MainApplication @ApplicationPath /api、Dockerfile.jvm:91）——已查。
2) baseURL 组装与 /api 重复（http/index.js:6 + api 模块 28 个抽样，CableApi/UserApi/TelegramApi… 均写 /api/...）——已查，无重复。
3) CORS（application.yml:11-15 + JWTInterceptor.java:45-51）——已查，实测证据见下。
4) TLS/WSS（application.yml:32-33 ssl.native=false，全仓无 ssl-port/证书；前端 index.html:38 ws/wss 跟随协议，Electron 强制 http/ws index.html:68-73）——已查。
5) 上传体积/超时链（application.yml 无 quarkus.http.limits；ant Upload action 6 处；http/index.js:7 timeout 注释）——已查后端与前端两端；反代层缺配置=[INFERENCE]。
6) 构建交付（.github/workflows/build-quarkus-native.yml 唯一 workflow）+ 版本（pom.xml:7 / package.json:2 / application.yml:1 / git tag）——已查。
7) 配置真源分裂（index.html:19-27,196；MessageWebSocket.js:82；demo 3 处；Electron 走仓外 shell settings）——已查。
漏/未查（归属其它分片，见 §5）：后端 @ServerEndpoint 路径与前端 WS path 逐一匹配（WebSocketContract）；SSE /Sse/connect、union /simulation//websocketUnion 的后端端点是否存在（HttpContractDiff/WebSocketContract）；仓外 Electron 外壳 settings 与 nginx 反代实际配置（仓外，无法证）。

## 2. 缺陷条目
见 findings：DC-J-P1-01（Web+反代形态上传/SSE/导出/协同WS 全畸形失效）、DC-J-P2-01（上传体积链不一致）、DC-J-P3-01（版本三分裂）、DC-J-P3-02（CI 仅后端 + 前端交付无文档）、DC-J-P3-03（配置真源分裂量化 + 换环境改文件清单）。

### 部署形态矩阵（形态 × 维度 → 是否可用 / 证据）
| 维度 | Electron 直连 | Web+反代(https) | 本地 dev(vite) |
| --- | --- | --- | --- |
| HTTP base | 可用：shell settings→http://host:port（index.html:67）+ axios /api（http/index.js:6）| 可用：axios https://host/data/api/...（index.html:81 + http/index.js:6，反代去 /data，[INFERENCE]）| 可用：无 proxy（vite.config.js:25-27），base=serverConfig.httpUrl 192.168.1.193:18001（index.html:20），跨源到 :18001 |
| WS URL | 可用：ws://host:port/...（Ws.js:18 经 wsUrl:73；UnionWs.js:41 用 httpUrl 无协议也巧合可用）| 部分失效：wsUrl 路径 wss://host/push/...（Ws.js:18/PublicSocket.js:10）可用；但 UnionWs.js:41/train.js:224/ListenIn.vue:300/Issue.js:62 用 ws://${httpUrl}=ws://https://.../data ✗ |
| CORS | 不涉/放行：origins:* 通配（application.yml:11-15）| 同源免预检（页面与 /data 同 host）| 可用：跨源 18000→18001，预检经 origins:*,headers:* 放行 token/deviceId（application.yml:11-15）|
| TLS | 明文 http/ws（index.html:68-73）[已接受风险口径] | 仅靠反代终止 TLS；后端无 TLS 监听（application.yml:32-33，无 ssl-port）[INFERENCE 反代] | 明文 http（跨源到 :18001）[已接受风险口径] |
| 上传限制 | http://host:port/api/...upload 可用；后端默认 10MB（application.yml 无 limits）| ✗ action=http://https://.../data/...upload 畸形失效（见 DC-J-P1-01）；且体积链 BE10MB vs 反代[INFERENCE] | 跨源上传，CORS 放行，后端 10MB |

单元格 dev(vite) 的 WS/CORS/TLS/上传合并说明：dev 与 Web-http 直连同构（window.httpUrl='host:18001' 无协议），协同WS/上传因 'http://'/'ws://'+host:18001 合法而可用；仅 Web-https 形态触发畸形。

## 3. 已核实为「一致/无问题」的关键契约（防止误改）
1) CORS 单源且可用（实测证据，满足验收）：application.yml:11-15 配置 quarkus.http.cors=true、headers:'*'、origins:'*'、methods:OPTIONS,GET,POST,DELETE,PUT,PATCH；JWTInterceptor.java:45-46 注释明示「CORS 一律由 quarkus.http.cors 配置承担，手写版已删除（Phase 7 Task 7.2）」，:47-51 对 OPTIONS 预检直接 return 200 不做 token 校验。结论：跨源形态（dev 18000→18001、Web-http 直连）预检可通过，自定义头 token/deviceId 被 headers:'*' 放行（无 credentials，* 为真通配）。勿再向后端加第二套手写 CORS。
2) 无 /api 重复：baseURL=window.httpUrl（http/index.js:6，绝不含 /api），api 模块统一写 /api/...（抽样 28 模块全部如此）→ 与后端 @ApplicationPath("/api") 恰好一层，无双写。勿把 /api 移进 baseURL。
3) 端口一致：18001 在 application.yml:10、Dockerfile.jvm:91、前端 Electron/Web-http 字面量三处一致。
4) 无 vite proxy 冲突：vite.config.js:25-27 仅设 server.port=18000，无 proxy → 不存在「两套 base 机制并存」，dev 仅靠 window.httpUrl 单机制（经开放 CORS 直连）。
5) base:'./'（vite.config.js:9）产出相对资源路径，契合 Electron file:// 加载。

## 4. 与单侧评审的定级变化
- 后端 CI-P2-03「JWTInterceptor 手写 CORS 与配置择一」→【已解决】：JWTInterceptor.java:45-46 手写 CORS 已删除，现单源。联合状态：非缺陷（后端已据前次评审改代码）。
- 前端 FE#6/HIGH「部分 socket 用 httpUrl 拼 URL → Web 畸形」→【联合扩面，责任/范围变化】：同根因不止 WS。DC-J-P1-01 新增证据把失效面扩到 HTTP 侧上传/SSE/导出（edit/Index.vue:198,230、questionBank/Index.vue:307、equipmentIndex.vue:272、articleManage:120、militaryDeploy:119、systemManage/equipment:112、knowledgeTabel.js:588），并结合「后端无 TLS 监听（application.yml:32-33）」这一后端事实，把整个 Web+反代形态判为不可用 → 联合维持 J-P1，责任 FE，前提是 BE/运维提供反代该形态。
- 前端 FE#5（axios 无 timeout）、FE MEDIUM(173/234/250)（index.html/MessageWebSocket/knowledgeTabel 硬编码）：不重报单点；仅在 DC-J-P2-01（作为链路一环）与 DC-J-P3-03（作为量化汇总）中引用，未改单侧定级。

## 5. 未能验证的部分（缺什么前提）
- 反代配置：仓库内无 nginx/traefik 配置 → /data、/push、/file 前缀映射、client_max_body_size、代理超时、TLS 终止均 [INFERENCE]。Web+反代形态是否真部署无法从仓内证实，但 index.html:78-81 的 /data、/push 前缀是其存在的强设计意图证据。
- Electron 外壳：controller.system.getConfig 返回的 dataUrl.port 是否=18001、外壳是否强制 http、外壳如何打包前端 dist——外壳在仓外且无文档，无法证。
- 后端端点路径匹配：SSE /Sse/connect 与 union /simulation//websocketUnion 是否存在对应后端端点属 HttpContractDiff/WebSocketContract 分片；本片只验 URL 拼装形态，未逐一核对后端 @ServerEndpoint/@Path。
- CORS 预检未做运行时抓包，仅据 application.yml 配置文本与 Quarkus 语义推断放行（headers:'*' 覆盖 token/deviceId、无 credentials 时 * 为真通配）。

## 附录 A：结构化缺陷条目（5 条，子代理原始输出）

### [J-P1] Web+反代(https)形态下上传/SSE/导出/协同WS 全部畸形失效

- 锚点：`frontend/index.html:80-88`（置信度 0.82）

DC-J-P1-01（联合视角扩面自前端单侧 FE#6/HIGH → 责任面与部署矩阵结论改变）。

一句话结论：后端是纯 API 服务（无 TLS 监听），唯一能承载 HTTPS 的部署形态是「Web+反代」，而恰恰是该形态下，前端所有『手工拼 base』的调用点都会产出畸形 URL，导致文件上传、SSE、Excel 导出、网络协同 WS 整类功能不可用。

机理：`frontend/index.html:81` 在非 http 页面把 `window.httpUrl` 设成绝对 URL `${protocol}//${serverConfig.httpUrl}/data`（即 `https://192.168.1.193/data`）。共享 axios 实例对此是协议感知的（`frontend/src/common/http/index.js:6` 用 `indexOf('http')` 判断），因此 `/api/...` 请求正确拼成 `https://host/data/api/...`（反代去 `/data`）——这一条可用。但所有绕过共享实例、自己拼 base 的点都写死了 `'http://' + window.httpUrl`：上传 `frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:198`、`.../test/questionBank/Index.vue:307`、`.../equipment/equipmentIndex.vue:272`、`.../postJob/hanzi/articleManage/Index.vue:120`、`.../preJob/ditto/militaryDeploy/Index.vue:119`、`.../systemManage/basic/equipment/Index.vue:112`；SSE `.../study/basic/edit/Index.vue:230` 的 `new EventSource('http://' + window.httpUrl + '/Sse/connect')`；导出 `.../questionBank/js/knowledgeTabel.js:588`。在 https 形态这些全部变成 `http://https://192.168.1.193/data/...`（畸形且混合内容）。协同 WS `frontend/src/views/**/unionJob/js/UnionWs.js:41`、`unionJob/disturbCode/js/train.js:224`、`unionJob/lineNotify/components/ListenIn.vue:300`、`unionJob/lineNotify/js/Issue.js:62` 同理变成 `ws://https://.../data/...`。

后端侧证据（本形态成立所依赖但仓内缺失/不支持）：`backend/src/main/resources/application.yml:8-15` 后端只在 18001 起明文 HTTP，`:32-33` 仅 `quarkus.ssl.native:false`、全仓无 `quarkus.http.ssl-port`/证书配置 → 后端不能直供 wss/https，https 形态强依赖仓外反代把 `/data`、`/push` 前缀回源到 `:18001`（仓库内无任何反代配置，[INFERENCE]）。

触发条件 → 后果：一旦以「浏览器 https + 反代」形态部署（`/data`/`/push` 前缀即为此设计），上传/SSE/导出/协同训练 WS 静默失效；Electron 直连与 Web-http 直连（`window.httpUrl='host:18001'` 无协议）因 `'http://'+host:18001` 恰好合法而侥幸可用。

责任归属：修复侧 = FE（把这些点统一走共享 axios 实例 / `window.wsUrl`，删除 `'http://'+httpUrl` 变体，使 base 协议感知）；BE/运维需提供反代该形态才存在。单侧 FE#6 仅覆盖 WS 子集且定级 HIGH；联合视角把同根因扩到上传/SSE/导出（新增证据），并因『后端无 TLS 监听』这一后端事实把整个 Web+反代形态判为不可用 → 维持 J-P1。

### [J-P2] 上传体积链不一致：后端默认10MB、前端无体积守卫、反代默认更低

- 锚点：`backend/src/main/resources/application.yml:8-15`（置信度 0.6）

DC-J-P2-01。

一句话结论：上传体积在三层（前端→反代→后端）无任何一致阀值，具体上限取决于未写入仓库的默认值，大文件上传会在某一层以非业务信封的方式被截断。

后端证据：`backend/src/main/resources/application.yml:8-15` 的 `quarkus.http` 块未配置 `quarkus.http.limits.max-body-size`（全仓 grep `max-body-size|limits` 在 `src/main/resources` 零命中，仅命中 vendored 文档）→ 采用 Quarkus 默认 10MB。超限时 Quarkus 返回原生 HTTP 413（非后端 `code` 信封）。

前端证据：上传走 ant-design-vue Upload 的 `action`（如 `frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:198` `.../api/theoryKnowledge/uploadFileToNip`、`.../preJob/ditto/militaryDeploy/Index.vue:119` `/api/mtd/upload`），无任何前置体积校验；共享 axios 又将 `timeout` 注掉（`frontend/src/common/http/index.js:7`，此项单侧 FE#5 已报，此处仅作链路一环引用，不重报）。

反代层 [INFERENCE]：仓库内无 nginx/traefik 配置文件，若 Web+反代形态使用 nginx 默认 `client_max_body_size 1m`，则 1–10MB 的上传会在反代处先以 413 断掉，早于后端 10MB。

触发条件 → 后果：上传 >10MB（或反代限额）的题库 Excel/图片/词库时，请求被截断，前端既无 timeout 也无体积预校，用户只能看到不透明的失败（413 不进入业务码信封，且上传组件走自己的 XHR 不经共享拦截器）。

责任归属：双侧协同——BE 显式写定 `quarkus.http.limits.max-body-size` 并与反代对齐；FE 在上传前加体积校验与友好提示；若启用反代则同步调高 `client_max_body_size`。信封层面由 EnvelopeErrorContract 分片处理。

### [J-P3] 两工程版本号无单一真源：后端1.1.0 / 前端0.0.0 / application.yml 4.0.1

- 锚点：`frontend/package.json:2-2`（置信度 0.9）

DC-J-P3-01。

一句话结论：单仓双工程无跨项目的版本真源；后端 pom 与 git tag 已对齐（都是 1.1.0），但前端 `package.json` 永远停在 `0.0.0`，assembly 发布时一个 `v1.1.0` 产物内的前端自报 `0.0.0`，而 application.yml 又携第三个号 `4.0.1`。

前端证据：`frontend/package.json:2` `"version": "0.0.0"`（从未随发布递增）。

后端证据：`backend/pom.xml:7` `<version>1.1.0</version>`（= git tag `v1.1.0`，当前 HEAD `git describe` = `v1.1.0-7-g0d3bdea`）；git tags 实测仅 `v1.0.0`/`v1.1.0`；另 `backend/src/main/resources/application.yml:1` 顶层 `version: 4.0.1` 是与两者均无关的第三个标记。

触发条件 → 后果：发布/回滯时无法从单一版本号确认“前后端是否同一发布集”；前端 `0.0.0` 无法与任何 tag 关联，现场排障时无法定位前端实际版本。不造成运行时故障，故 J-P3。

责任归属：双侧协同——确立单一版本源（如发布时由 tag 驱动同步 pom.version 与 package.json.version，并把 application.yml 的 4.0.1 要么删除要么纳入同一体系）。两侧单侧评审均未跨项目对比版本，属新联合结论。

### [J-P3] CI 仅构建/发布后端；前端缺席流水线，前端→Electron 交付无文档

- 锚点：`.github/workflows/build-quarkus-native.yml:178-183`（置信度 0.85）

DC-J-P3-02。

一句话结论：单仓双工程但 CI 只管后端，CI 全绿≠前端可用（如 DC-J-P1-01 的 Web 形态破坏可静默随包发出而不被 CI 拦住），且前端产物如何进仓外 Electron 外壳无任何仓内文档/脚本。

后端/CI 证据：`.github/workflows/build-quarkus-native.yml` 唯一 workflow，所有 job 的 `defaults.run.working-directory: backend`（`:15-17`、`:62-64`）；test job 仅 `mvn -B verify`（`:39`）；build job 仅后端 native；release 仅上传 `backend/target/*-runner*`（`:178-183`）。全程无任何 `npm`/`vite build`/前端 lint/测试步骤。

前端证据：`frontend/package.json:4-11` 有 `build`/`t:build` 脚本但从未被任何 workflow 调用；`frontend/index.html:66-67` 表明运行时靠外壳 `controller.system.getConfig` 注入后端地址，而外壳与其 build 均在仓外且无文档。

触发条件 → 后果：前端回归（包括跨栈契约破裂）无任何自动防护；发布物只含后端 binary，前端如何构建并打进 Electron 无可复现流程。J-P3（交付/过程缺口，不自发造成运行时故障）。

责任归属：双侧协同/CI——在同一 workflow 增前端 `build`+最小健性步骤并归档产物，并在 README 记录前端 dist → Electron 外壳的交付链。与前端单侧 [MEDIUM]（Electron 外壳缺席/无文档，`docs/reviews/2026-09-08-frontend-review.md:150-153`）部分重叠，但本条新角度 = CI 只覆盖后端这一跨栈交付缺口。

### [J-P3] 后端地址配置真源分裂：2 套机制 + 8+ 处硬编码，换环境需改多文件

- 锚点：`frontend/index.html:19-27`（置信度 0.9）

DC-J-P3-03（本条同时是验收要求的「换环境要改的文件清单」量化结论）。

一句话结论：同一后端地址在前端无单一真源，被 2 套互斥机制（Web/dev 走 index.html serverConfig；Electron 走仓外外壳 settings）+ 8+ 处硬编码字面表达，且已出现漂移（chrome 下载用了另一个 IP）。

后端证据（固定面）：后端端口固定 18001（`backend/src/main/resources/application.yml:10`、`backend/src/main/docker/Dockerfile.jvm:91` EXPOSE 18001）——前端每处需与之对齐。

前端证据（需改文件清单，逐个 file:line）：
1. `frontend/index.html:20` serverConfig.httpUrl `192.168.1.193`（驱动 Web/dev 的 HTTP base）
2. `frontend/index.html:26` serverConfig.wsUrl `192.168.1.193`
3. `frontend/index.html:21` serverConfig.fileUrl `192.168.43.183`
4. `frontend/index.html:22` serverConfig.ueditorUrl `192.168.43.160:8003`
5. `frontend/index.html:23` serverConfig.mqttUrl `192.168.43.50`
6. `frontend/index.html:24` serverConfig.ocrUrl `192.168.43.160:8080`
7. `frontend/index.html:196` chrome.exe 下载硬编码 `http://10.10.0.99:8000`（注意与 serverConfig 不同网段——真源已漂移的实证）
8. `frontend/src/common/ws/MessageWebSocket.js:82` 硬编码 `ws://localhost:18765/echo`（本机 echo 服务）
9. 演示页硬编码 IP：`frontend/src/views/demo/chil/IMDemo.vue:144` `ws://10.10.0.210:18766`、`frontend/src/views/demo/hanzi/Index.vue:13` `ws://10.10.0.210:3333`、`frontend/src/views/demo/vico/Index.vue:77` `ws://10.10.0.232:8081`

量化结论：针对主应用换后端地址，【Web/dev 形态】至少改 `index.html` 一个文件内 6–7 处字面（:20-26,196）；【Electron 形态】后端 host/port 完全不看仓内 index.html，而取自仓外外壳 `controller.system.getConfig`（`frontend/index.html:66-67`）→ 另一套独立配置源；若本地 echo 服务或演示页迁移还需改 `MessageWebSocket.js:82` 与 3 个 demo 文件。即同一部署环境需在 ≥ 2 套机制 + ≥ 1 个文件（主应用）~ 5 个文件（含 echo/demo）间手改，无环境变量注入。J-P3。

责任归属：修复侧 = FE（端点改运行时注入/服务端 JSON，收敛三分支 + 消除硬编码字面）。单侧前端评审已分别点名 index.html（MEDIUM :173-175）、MessageWebSocket（MEDIUM :234-237）、knowledgeTabel（MEDIUM :250-252）；本条不重报单点，而是验收要求的跨栈量化汇总（几套机制/几个文件/漂移实证）。
