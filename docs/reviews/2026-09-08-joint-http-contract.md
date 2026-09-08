# HttpContractDiff（前后端联合评审分片）— HTTP 端点契约全量对账
- 日期：2026-09-08 / 范围：前端所有 HTTP 请求点 ↔ 后端所有 *Controller REST 端点 / 方法：只读取证（read/grep）

注：本分片无写文件工具（write 仅接受 xd://），按任务约定「把完整 Markdown 作为最终输出返回」。报告应落 `docs/reviews/2026-09-08-joint-http-contract.md`，由父代理落盘。

## 0. 分片结论与计数
| 定级 | 条数 | 责任分布(FE/BE/双侧) |
|---|---|---|
| J-P0 | 0 | — |
| J-P1 | 2 | FE 2（其一根因在 BE 单侧修复未联动，实修在 FE）|
| J-P2 | 1 | 双侧协同 1 |
| J-P3 | 3 | FE 3（均为死导出，接入即炸）|
| 合计 | 6 | FE 5 / 双侧 1 |

编号：HC-J-P1-01(roomgId)、HC-J-P1-02(deleteThroyKnowledgeById 末尾空格)、HC-J-P2-03(rows:999 vs 200 钳制)、HC-J-P3-04(getAllUserByContent GET/POST)、HC-J-P3-05(editStatus 不存在)、HC-J-P3-06(addUser 不存在)。计数与上方 findings 一一对应。

## 1. 契约清单与覆盖率
- 前端请求点：`frontend/src/common/api/*.js` **28/28 模块全量枚举**（100%），共约 170 个请求函数；请求形态全部经共享包装 `common/http/axios.js`（GET/DELETE 把 `data`→`params`、POST/PUT 把 `data`→body），故「GET 参数放 body 不发出」类问题在包装层被消解。
- 绕过共享实例的直连点：**2 个已逐一核实**。(a) `frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:583-601` 的 raw-axios `exportTemplate1`（含 token/deviceId 头）**是死函数**——UI 按钮(`questionBank/Index.vue:117,121`)绑定的是 `exportTemplate`(:643)，type0 走静态下载 `window.fileUrl+/006/题库-模板.docx`、type1 走 `exportQuestionByLevelId`(JSON)后客户端 docx 生成，均不调 raw-axios。(b) `frontend/src/views/manage/preJob/receive/explain/Index.vue:119` 虽 `import axios`，但唯一用法(`:133-139`)整段注释——**无活跃直连**。
- 后端端点：`backend/src/main/java/com/nip/controller/**/*Controller.java` **61 个 controller 全量枚举**，约 250 个 `@GET/@POST` 端点（含 free/、general/、simulation/ 三个子包与两处共享 `@Path("/user")`、两处共享 `@Path("/telexPat")`，均因子路径不同而合法）。
- 已对账 K：所有活跃前端请求点均逐一映射到后端端点；`/api` 前缀无重复/缺失；对账出 6 类不一致（见 §2）。硬编码查询串（`?id=`、`?trainId=`、`?trainType=`）与后端 `@RestQuery` 键均已抽查一致。
- 漏查/抽样说明：各「详情/删除」GET 端点的 query key（除 roomgId 外的 deviceId/id/trainId/userId）按调用点**抽样**核对为一致，未 100% 遍历每个调用点；multipart(`@RestForm`) 端点(`postEnteringExerciseWordStock/view`、`theoryKnowledgeQuestion/`(无)、`theoryKnowledge/uploadFileToNip`)未发现活跃前端 JSON 误发调用者。

## 2. 缺陷条目
- **HC-J-P1-01｜roomgId 契约反转（后端单侧修复引入回归）**：前端 7 处以 `{roomgId}` 调 getRoomDetail（FE：`UserApi.js:126-132`+`lineNotify/js/Issue.js:149,290`+`lineNotify/components/ListenIn.vue:404,483`；`broaddcastTeacheingApi.js:38-42`+`components/BroadcastTeachTrain/js/useBroadStudent.js:265`、`useBroadTeacher.js:288`、`unionJob/broadcastTeacheing/js/useBroadcastTrain.js:21`），后端三处现读 `ROOM_ID="roomId"`（BE：`SimulationRouterRoomController.java:70`、`SimulationReportRoomController.java:56`、`SimulationReceptRoomController.java:55`；`BaseConstants.java:12`），grep 全仓 BE 无 roomgId。触发→后果：进入房间详情即 `?roomgId=`→后端 roomId=null→router 走 CODE_500 信封、report/recept 得空详情。责任：**双侧协同**（实修 FE→roomId）。
- **HC-J-P1-02｜deleteThroyKnowledgeById URL 末尾空格恒 404**：FE `TestApi.js:61` url 末尾多一个空格；BE `TheoryKnowledgeController.java:135-139` `@Path("/deleteThroyKnowledgeById")` 无空格；活跃调用 `basicTheory/study/basic/edit/Index.vue:295`、`.../edit/js/useForm.js:109`。空格被编码为 %20→真 404→删除/关闭回调静默不触发。责任：**FE**（删空格）。
- **HC-J-P2-03｜rows:999 被后端钳到 200 静默截断**：FE `organization/telexZuXun/list/js/list.js:138` 传 `{page:1,rows:999}`；BE `common/utils/Page.java:43-44` `getRows()` 钳 [1,200]，消费方 `GeneralKeyPatService.java:334`。>200 条时列表静默丢数据且无分页可翻。责任：**双侧协同**。
- **HC-J-P3-04/05/06｜三个死导出指向破损契约**：`StructureApi.js:11-16` getAllUserByContent(GET) vs BE `UserController.java:95-101`(POST)→405；`UnionApi.js:44-48` editStatus→BE 无此端点(404)；`UserApi.js:25-31` addUser→BE 无 `/user/addUser`(404，注册实际走 `signin`)。均无活跃调用者，接入即炸。责任：**FE**（删除或对齐）。

## 3. 已核实为「一致/无问题」的关键契约
- **分页基准一致**：前端 listPage 一律 1-based（`{page:1,...}`，如 `organization/*/list/js/list.js:137/148`、`network/js/telegram.js:70`、`postJob/**/telex.js:102`），后端 `Page.getPage()=Math.max(page,1)`、偏移 `getPage()-1`（`common/utils/Page.java:36-38` + 7 消费方 `page.getPage()-1`）→page=1→offset0，无 off-by-one。
- **listPge 拼写两侧一致**：FE `postWording.js:13` 与 BE `PostRadiotelephoneTrainController.java:70` **同样误拼** `listPge`，故仍可用（非缺陷，勿单侧改）。
- **socket 状态端点一致**：FE `handkeyZuXun.js:34` `/api/socket/generalTickerPatTrain/updateTrainStatus` ↔ BE `GeneralTickerSocketController.java:23,42`。
- **GET startTrain / 路径参数一致**：FE `handkeyZuXun.js:69` `startTrain?trainId=` ↔ BE `GeneralTickerPatController.java:106`；FE `UserApi.js:119-124` `getRoomUserList/{roomId}` ↔ BE `SimulationRouterRoomController.java:56`(@PathParam)。
- **postTelexPatTrain/findAll 混合传参一致**：FE `TelegramApi.js:62` `?trainType=`+body page ↔ BE `PostTelexPatTrainController.java:57`(@RestQuery TRAIN_TYPE + @RequestBody Page)。
- **危险测试端点已清除**：上一轮提到的 `GET /api/test/start`、`GET /postTelegramTrain/test` 现仓内**不存在**（grep 无 test/start 路径）；仅存合法的 `startTrain` GET。

## 4. 与单侧评审的定级变化
- **CA-P1-01/02/03（roomgId）→ HC-J-P1-01**：单侧后端评审判为「后端拼写错误 roomgId(P1)、前端传 roomId」，后端据此已改为 roomId。联合实测：前端实为 roomgId、后端已无 roomgId → **责任归属反转**（BE→双侧协同，实修在 FE），定级维持 P1；这是「后端单侧修复未联动前端反而把原本一致的契约改断」的典型。
- **CA-P1-04（分页越界/rows OOM）→ 部分沿用 + 新增 HC-J-P2-03**：后端已修（getPage 钳 1、getRows 钳 200）。联合新增：该 200 上限与前端 `rows:999` 的「一次拉全量」约定冲突，形成潜在静默截断。

## 5. 未能验证的部分
- `window.httpUrl` 的注入值是否含 scheme（影响 `knowledgeTabel.js:588` `'http://'+httpUrl` 是否双前缀）——属 DeployConfig 分片；且该 raw-axios 为死函数，故不计入活跃缺陷。
- 后端 `theoryKnowledgeQuestion/exportTemplate`（返回 JSON 列规格）**无活跃前端调用者**（live 走静态下载 + 客户端生成），故 blob/JSON 形态差异非活跃缺陷，未计入——若后续启用 `exportTemplate1`/api 模块 `exportTemplate(responseType:blob)` 则需重估。
- 未逐一遍历全部 GET 详情端点每个调用点的 query key（除 roomgId 已全查外为抽样）。

## 附录 A：结构化缺陷条目（6 条，子代理原始输出）

### [J-P1] 前端发送 roomgId 而后端已改读 roomId，getRoomDetail 全线失效

- 锚点：`frontend/src/views/manage/unionJob/lineNotify/js/Issue.js:148-150`（置信度 0.95）

联合视角发现的最严重一条，且是后端单侧修复引入的回归。前端所有房间详情调用仍以查询键 roomgId(多一个 g)传参：apiSimulationRouterRoomDetail(GET /api/simulation/router/getRoomDetail, frontend/src/common/api/UserApi.js:126-132) 的调用点 frontend/src/views/manage/unionJob/lineNotify/js/Issue.js:149、:290 与 frontend/src/views/manage/unionJob/lineNotify/components/ListenIn.vue:404、:483 均传 {roomgId: ...}；getRoomDetail(GET /api/simulation/report/getRoomDetail, frontend/src/common/api/broaddcastTeacheingApi.js:38-42) 的调用点 frontend/src/components/BroadcastTeachTrain/js/useBroadStudent.js:265、useBroadTeacher.js:288、frontend/src/views/manage/unionJob/broadcastTeacheing/js/useBroadcastTrain.js:21 也传 {roomgId:...}。后端三处 getRoomDetail 现在读常量 ROOM_ID="roomId"(backend/.../common/constants/BaseConstants.java:12)：SimulationRouterRoomController.java:70、SimulationReportRoomController.java:56、SimulationReceptRoomController.java:55(@RestQuery(ROOM_ID) Integer roomId)。已 grep 全仓 backend/src/main/java 无任何 roomgId(结果为空)。触发条件→后果：任意进入房间详情，查询串为 ?roomgId=NN，后端读 roomId=null，router 路走 findByIdOptional(null).orElseThrow→HTTP200+CODE_500，report/recept 路得空详情。与单侧评审的定级变化：后端 CA-P1-01/02/03 把此判为「后端拼写错误 roomgId(P1) 待修」，其前提「前端传 roomId」是错的——前端实为 roomgId；后端据此已把 roomgId 改成 roomId，反而把原本两侧一致的契约改断。责任归属由 BE 反转为 双侧协同，实修落在 FE。最小修复：FE 把上述 7 处 roomgId 改为 roomId(与已正确使用 roomId 的 findPage 调用一致)；后端保持不变。

### [J-P1] deleteThroyKnowledgeById URL 末尾多一个空格，删除调用恒 404

- 锚点：`frontend/src/common/api/TestApi.js:58-63`（置信度 0.9）

前端 TestApi.js:61 的 url 字符串为 "/api/theoryKnowledge/deleteThroyKnowledgeById "（:raw 已确认末尾含一个空格），method post（frontend/src/common/api/TestApi.js:58-64）。后端对应端点路径为 @Path("/deleteThroyKnowledgeById")（无空格，backend/src/main/java/com/nip/controller/TheoryKnowledgeController.java:135-139）。该函数是活跃调用：frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:295 与 frontend/src/views/manage/basicTheory/study/basic/edit/js/useForm.js:109 均在 route.query.type==0 时调 deleteThroyKnowledgeById({id})。触发条件→后果：浏览器/axios 将路径末尾空格编码为 %20，请求目标变为 /api/theoryKnowledge/deleteThroyKnowledgeById%20，RESTEasy Reactive 精确路由不匹配→真正 HTTP 404（非 200 信封）；.then(res=>{PubSub.publishSync(...close)}) 无 catch，Promise reject 后 .then 不执行，删除/关闭回调从不触发，删除静默失败。单侧评审未列（前端评审 grep 无 deleteThroyKnowledgeById）。责任归属：FE。最小修复：删掉 TestApi.js:61 url 末尾的空格（后端不动）。

### [J-P2] 前端 listPage 传 rows:999 被后端 getRows() 钳到 200，超 200 条静默截断

- 锚点：`frontend/src/views/manage/organization/telexZuXun/list/js/list.js:137-139`（置信度 0.75）

frontend/src/views/manage/organization/telexZuXun/list/js/list.js:138 调 getElectronKeyZuXunList({page:1, rows:999})（单页拉全量，无分页），经 electronKeyZuXun api → POST /api/generalKeyPat/findAll(@RequestBody Page)。后端 Page.getRows() 将 rows 钳制到 [1,200]（backend/src/main/java/com/nip/common/utils/Page.java:43-44），消费方 GeneralKeyPatService 用 page.getPage()-1, page.getRows()（backend/.../service/general/GeneralKeyPatService.java:334）。触发条件→后果：当该用户的电子键组训训练记录 >200 条时，前端想一次拿 999 条但后端只返 200 条，列表静默丢失余下记录且无分页可翻。与单侧定级变化：后端 CA-P1-04 已修（getRows() 新增 200 上限防 OOM），联合视角新增：该上限与前端 rows:999 的“一次拉全”约定冲突，形成潜在静默截断。责任归属：双侧协同（后端钳制正确且必要；前端应改为真分页或将 rows 降至 ≤200）。最小修复：FE 改用真分页（rows≤20 且随页拉取）或确认上限后钳至 200 内。

### [J-P3] getAllUserByContent 前端 GET / 后端 POST 方法不匹配（死导出，接入即碍）

- 锚点：`frontend/src/common/api/StructureApi.js:11-16`（置信度 0.85）

frontend/src/common/api/StructureApi.js:11-16 定义 getAllUserByContent(data) method:'get' → GET /api/user/getAllUserByContent。后端只有 @POST：backend/src/main/java/com/nip/controller/UserController.java:95-101 为 @POST @Path("/getAllUserByContent")(@RestQuery userName,@RestQuery userAccount)。GET 命中 POST 端点→RESTEasy 返 405 Method Not Allowed（非 200 信封）。当前 grep frontend/src 全仓无任何 import/调用 getAllUserByContent（仅定义），为死导出，故影响潜伏：一旦有人接入该接口即立刻 405。责任归属：FE（改为 method:'post' 与后端一致）或删除死导出。单侧评审均未列（后端 controller-api-review grep 无 getAllUserByContent，前端评审亦无）。

### [J-P3] UnionApi.editStatus 指向后端不存在的 routerRoomContent/editStatus（死导出）

- 锚点：`frontend/src/common/api/UnionApi.js:44-48`（置信度 0.85）

frontend/src/common/api/UnionApi.js:44-48 定义 editStatus（method:'post', url:'/api/simulation/routerRoomContent/editStatus'）。后端 @Path("/simulation/routerRoomContent") 的 SimulationDisturdController(backend/src/main/java/com/nip/controller/simulation/SimulationDisturdController.java:31) 子路径仅有 addRoomAndContent/addStudent/findAlls/findById/findTrainUser/uploadResult/saveSetting/delete，无 editStatus；grep 全仓 backend/src/main/java 无 editStatus（结果为空）。前端 GET/import editStatus 无调用点（grep frontend/src 仅定义），为死导出→潜伏：接入即 404。责任归属：FE（删除或对齐到真实状态变更端点，如 editStatus 应走 uploadResult/saveSetting 或新增后端端点）。

### [J-P3] UserApi.addUser 指向后端不存在的 /api/user/addUser（死导出）

- 锚点：`frontend/src/common/api/UserApi.js:25-31`（置信度 0.85）

frontend/src/common/api/UserApi.js:25-31 定义 addUser（method:'post', url:'/api/user/addUser'）。后端两个 @Path("/user") 控制器均无 addUser：主 UserController 有 saveUser(backend/.../controller/UserController.java:43-44)，free/UserController 有 signin(backend/.../controller/free/UserController.java:49-50)；grep @Path addUser 全仓为空。前端 import {addUser} from UserApi 无匹配（登录页的 addUser 为 useLogin.js 内局部函数，非此 API），为死导出→潜伏：接入即 404。注意注册实际走 userSignIn→POST /api/user/signin（已验证存在）。责任归属：FE（删除死导出，或若需保留则改指 /api/user/signin 或 /api/user/saveUser）。
