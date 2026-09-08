# 理论考试 / 题库导入导出 / 文件与富文本传输（前后端联合评审分片）

- 日期：2026-09-08 / 范围：理论考试·题库导入导出·文件上传下载·富文本(HTML)链路 的跨栈契约面 / 方法：只读取证（read/grep）
- 编号前缀：`TK` / 定级口径：J-P0..J-P3（见父任务「定级口径」）
- 前端相对路径以 `frontend/` 为根；后端以 `backend/` 为根。
- 重要前提：本轮实测**后端源码已相对 2026-09-07 单侧评审改动**（`uploadFileToNip`/`exportTemplate`/`saveBatch`/自测快照均已重写），前端**未改**。本片只报「两侧一起看才成立」的缺陷。

## 0. 分片结论与计数

| 定级 | 条数 | 责任分布(FE/BE/双侧) |
|---|---|---|
| J-P0 | 0 | — |
| J-P1 | 1 | 双侧 1 |
| J-P2 | 3 | 双侧 3（其中 1 条 `[已接受风险口径]` 不计入 P0/P1） |
| J-P3 | 1 | FE 1 |
| 合计 | 5 | FE 1 / BE 0 / 双侧 4 |

一句话结论：**题库导出/自测快照两条契约已一致（后端修复到位）；但「文档导入→富文本编辑器」这条契约在后端收窄+前端未跟随下已端到端失效（J-P1）**；导出模板、批量导入两条端点已成孤儿（后端建了、前端不调，J-P2×2）；富文本存储型 XSS 联合定责（J-P2 `[已接受风险口径]`）。

---

## 1. 契约清单（覆盖率）

### 1.1 上传/下载端点对账表

| 端点 | 后端形态 (file:line) | 前端调用 (file:line) | Content-Type / responseType | 结论 |
|---|---|---|---|---|
| `POST /theoryKnowledge/uploadFileToNip` | `@RestForm("file") FileUpload`→`readDocumentContent`，**仅 txt/md/csv**，非法即抛 `IllegalArgumentException`；成功返回 `VO{type=2,wordContent,imgUrls=[]}`（`controller/TheoryKnowledgeController.java:176-182`；`service/TheoryKnowledgeClassifyService.java:107-134`） | `a-upload name="file" accept=".doc,.docx,.pptx"`→`uploadChange`（`equipment/equipmentIndex.vue:203-207,245-249,336-347`；`basicTheory/study/basic/edit/Index.vue:168-170,203-215`） | 上传：a-upload 原生 multipart（**不过共享 axios**，字段名 `file` 与 `@RestForm("file")` 一致）；FE 无 responseType | **断裂 → TK-J-P1-01** |
| `POST /theoryKnowledgeQuestion/exportTemplate` | `Response<List<TemplateColumnVO>>`（**JSON 列规格**）（`controller/TheoryKnowledgeQuestionController.java:94-99`；`service/TheoryKnowledgeQuestionService.java:227-241`） | 活跃「导出模板」按钮走**静态文件** `window.fileUrl+'/006/题库-模板.docx'`（`test/questionBank/js/knowledgeTabel.js:648-654`）；`downloadTemplate(responseType:'blob')` 为**死导入**（`common/api/TheoryQuestionBankApi.js:44-51`，`knowledgeTabel.js:6`） | FE `downloadTemplate` responseType=blob（死）↔ BE JSON | **孤儿+脆弱 → TK-J-P2-01** |
| `POST /theoryKnowledgeQuestion/exportQuestionByLevelId` | `Response<List<QuestionEntity>>`（JSON 数据）（`TheoryKnowledgeQuestionController.java:79-84`；`Service:174-176`） | `exportQuestionBank`→客户端 `docx` 生成（`TheoryQuestionBankApi.js:52-57`；`knowledgeTabel.js:656-671,675-751`） | FE 无 responseType（收 JSON）；BE JSON | **一致** |
| `POST /theoryKnowledgeQuestion/saveBatch` | `List<QuestionDto>` **整批单事务+行级校验**（`TheoryKnowledgeQuestionController.java:86-92`；`Service:185-218`） | **无任何前端调用**；前端导入走逐行 `saveTheoryKnowledgeQuestion`（`knowledgeTabel.js:556-560`） | — | **孤儿 → TK-J-P2-02** |
| `POST /theoryKnowledgeQuestion/saveTheoryKnowledgeQuestion` | `Response<QuestionEntity>`（`Service:58-84`） | 手动出题 `bornTest`（`knowledgeTabel.js:158`）+ 导入逐行（`:558`） | JSON 一致 | **一致（但导入侧吞失败，见 TK-J-P2-02）** |
| `POST /theoryKnowledgeExam/saveTheoryKnowledgeExamSelfTesting` | 快照 `snap.setId(null)` 新建、五题型 `nullToEmpty`（`service/TheoryKnowledgeExamService.java:221-257`） | `studentAddTest/js/addTest.js:157` | JSON 一致 | **一致（TU-P1-01/TU-P3-03 已后端修复，见 §4）** |
| `POST /theoryKnowledgeExam/findTheoryKnowledgeExamById` · `examineAnalyse` | 见 `TheoryKnowledgeExamService.java:307-322`（`examineAnalyse` 对 `testPaperEntity.getTotal()` 无 null 兜底） | 多处消费无 null 兜底（`addTest/js/addTest.js:57-61`、`startGrade/js/startGrade.js:70-73` 等） | JSON | **残余脆弱 → TK-J-P3-01** |

### 1.2 富文本(HTML)链路

| 写入侧 (BE 存) | 回读侧 (BE 返) | 渲染 sink (FE) | 净化 |
|---|---|---|---|
| `saveTheoryKnowledge`（知识 `content`）、`saveDeviceEquipmentDescMsg`（设备说明 `descriptions[].content`） | `getByIdAndToken`、`getDeviceTypeEquipment` 原样返回 | `useDetails.js:71` `iframe.document.write(content)`；`equipmentIndex.vue:24` `v-html` | **两侧皆无**（`backend/src/main/java` grep `sanitiz/jsoup/escapeHtml/xss/Whitelist` = 0 命中） → TK-J-P2-03 |

### 1.3 覆盖率说明
- **已查**：`uploadFileToNip`、`exportTemplate`、`exportQuestionByLevelId`、`saveBatch`、`saveTheoryKnowledgeQuestion(+Level)`、自测保存/完成/列表、`findTheoryKnowledgeExamById`/`examineAnalyse`、题库导入解析(mammoth)、题库导出(docx 生成)、富文本 v-html/iframe sink、后端全量 HTML 净化 grep。
- **未深入（越界或非本片契约）**：图片/媒体上传走 `window.uploadFileUrl`（独立文件服务端点，非后端 `/api` 契约）；`NipUEditor`/TinyMCE 内嵌图片上传同走文件服务；`study/basic/details` 其余渲染分支；题库导出 Word 排版细节。均不影响本片结论。

---

## 2. 缺陷条目

### TK-J-P1-01 — 「文档导入→富文本编辑器」端到端失效（后端收窄契约 + 前端未跟随）
- **结论一句话**：设备说明/理论课件编辑器的「导入文档」按钮，选 Word/PPT 必失败、选纯文本也永不落内容——两侧判别口径与格式白名单不再对齐。
- **前端证据**：`frontend/src/views/manage/equipment/equipmentIndex.vue:203-207`（`a-upload accept=".doc,.docx,.pptx"` → `uploadChange`）、`:336-347`（`uploadChange`：`if(e.file.response.data.imgUrls){...}else{...data.wordContent.split("\r")...}`）；同构第二处 `frontend/src/views/manage/basicTheory/study/basic/edit/Index.vue:168`（accept）、`:203-215`（uploadChange）。
- **后端证据**：`backend/src/main/java/com/nip/service/TheoryKnowledgeClassifyService.java:116-119`（**仅 `txt/md/csv`**，其余 `throw new IllegalArgumentException`）、`:129-132`（成功恒 `vo.setImgUrls(List.of())`，即 `[]`）；无 `IllegalArgumentExceptionMapper`（`backend/.../common/exception/` 无该文件）→ 落 `GlobalExceptionMapper.java:19-25` → **HTTP 500 + `{code:500,message:"服务器错误",data:null}`**（具体原因「仅支持纯文本文档」被吞）。
- **触发 → 后果**：
  1. **格式白名单断裂**：前端 `accept=".doc,.docx,.pptx"` 引导用户选 Word/PPT；后端只收 txt/md/csv → 恒 500、`data=null`。`uploadChange` 命中 `if(e.file.response)` 后 `e.file.response.data.imgUrls` 对 `null` 解引用 → `TypeError`（若 a-upload 未回填错误响应体则为静默 no-op）。无论哪种，编辑器都拿不到内容，且后端错因不可见。
  2. **判别器断裂**：即便用户强选 `txt/md/csv`（后端会成功），返回 `imgUrls:[]`；JS 中**空数组为真值**，`if(e.file.response.data.imgUrls)` 恒真 → 走「图片分支」，`wordContent` 插入分支（`else`）永不执行 → 编辑器仍无内容。
- **责任归属**：`双侧协同`。这是单侧 `CA-P1-06`/`TU-P3-02`（旧「后端假成功、不用上传文件」）被后端修复后**引入的新跨栈断裂**（后端改成「Word/PPT 由前端解析后提交内容」，但前端从未实现客户端 Word/PPT 解析，仍指望后端返回 `wordContent`）。
- **最小修复**：前端为主——把 `accept` 改成 `.txt,.md,.csv`，并把判别改为 `if (data && Array.isArray(data.imgUrls) && data.imgUrls.length)`、`wordContent` 分支加 `data?.wordContent` 兜底、按 `res.code` 提示后端错因；若仍要支持 Word/PPT，则前端按题库导入的成熟做法用 `mammoth` 客户端解析后走文本提交。后端可选纵深修复：文本内容时 `imgUrls` 置 `null`（复活前端判别器）并给 `IllegalArgumentException` 配 200+message 的 Mapper（让错因透传）。

### TK-J-P2-01 — 「导出模板」端点孤儿 + blob/JSON 死导入陷阱 + 静态文件带外依赖
- **结论一句话**：后端把 `exportTemplate` 重写成 JSON 列规格，前端「导出模板」却下载带外静态 docx，后端端点从未被调用；同时残留一条 `responseType:'blob'` 死导入，一旦被接线即产出「JSON 字节冒充 .docx」的损坏文件。
- **前端证据**：`frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:648-654`（`type==0` 走 `window.fileUrl+'/006/题库-模板.docx'` 静态下载，返回体见 `:764` 导出的 `exportTemplate`）；死导入 `frontend/src/common/api/TheoryQuestionBankApi.js:44-51`（`downloadTemplate` `responseType:'blob'`）在 `knowledgeTabel.js:6` 导入却全文未调用；死函数 `knowledgeTabel.js:583-642`（`exportTemplate1`，硬编码 URL + blob + `.docx` 文件名，未在 `:752-777` 导出）。
- **后端证据**：`backend/src/main/java/com/nip/controller/TheoryKnowledgeQuestionController.java:94-99` + `service/TheoryKnowledgeQuestionService.java:227-241`（返回 `List<TheoryKnowledgeQuestionTemplateColumnVO>` JSON 列规格）。
- **触发 → 后果**：模板按钮功能正确性依赖文件服务上存在 `006/题库-模板.docx`（缺失即下载 404/失败），与后端「列规格」端点无关；后端端点长期零调用；`downloadTemplate` 的 blob↔JSON 形态矛盾是脆弱陷阱（谁把按钮改回调 `downloadTemplate` 就得到损坏模板）。
- **责任归属**：`双侧协同`（对齐/清理）。
- **最小修复**：前端「导出模板」改调 JSON `exportTemplate` 端点并客户端生成 `.xlsx`（与后端注释设想一致），删除死代码 `exportTemplate1` 与死导入 `downloadTemplate`；或若维持静态文件方案，则后端删除孤儿 `exportTemplate` 端点与 `downloadTemplate` API，避免形态矛盾。

### TK-J-P2-02 — 题库批量导入分工漂移：`saveBatch` 孤儿 / Word≠Excel / 逐行 fire-and-forget「假成功」
- **结论一句话**：后端建了「整批单事务+行级校验」的 `saveBatch` 代替文件导入，前端从不调用；前端仍以 `mammoth` 解析 **Word**（后端注释却写「Excel」），逐行 fire-and-forget 调 `saveTheoryKnowledgeQuestion`，2 秒后无条件报「上传成功」。
- **前端证据**：`frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:567-582`（`uploadChange` 用 `mammoth.extractRawText` 解析 Word）、`:556-564`（`for (let v of arrObj) saveTheoryKnowledgeQuestion(v)` 不 await + `setTimeout(()=>message.success("上传成功！"),2000)` 恒成功）。
- **后端证据**：`backend/src/main/java/com/nip/controller/TheoryKnowledgeQuestionController.java:86-92`（`saveBatch`，`@Operation` 注释「Excel 由前端解析后提交 JSON 行」）+ `service/TheoryKnowledgeQuestionService.java:185-218`（整批单事务、逐行校验缺题干/类型/levelId 即抛错回滚）。
- **触发 → 后果**：后端的「整批原子性 + 行级校验反馈」能力完全闲置；前端逐行保存对单行失败（如后端校验/鉴权码≠200）无任何提示，失败静默、可能部分导入；格式契约也漂移（前端 Word ↔ 后端注释 Excel）。注：解析对象已带 `levelId:activeAction.value.key`（`knowledgeTabel.js:333/377/424/443/461`），故不产生 levelId 孤儿；危害集中在「假成功 + 无原子性」。
- **责任归属**：`双侧协同`。
- **最小修复**：前端导入改为收集 `arrObj` 后一次 `saveBatch(arrObj)`，按返回码/异常提示真实结果；或后端删除 `saveBatch` 孤儿端点并把注释「Excel」更正为「Word」。二选一但必须两侧口径统一。

### TK-J-P2-03 `[已接受风险口径]` — 富文本存储型 XSS：联合定责（净化应落渲染侧）
- **结论一句话**：知识/设备说明富文本 HTML **写入侧后端零净化、渲染侧前端原样直插**，构成存储型 XSS（持久污染）；联合视角确认最小修复应落在**前端渲染侧**。
- **前端证据**：`frontend/src/views/manage/equipment/equipmentIndex.vue:24`（`v-html="deviceList[...].descriptions[...].content"`）、`frontend/src/views/manage/basicTheory/study/basic/details/js/useDetails.js:71`（`iframe.document.write(data.value.knowledgeSwfs[i].content)`）。
- **后端证据**：`backend/src/main/java` 全量 grep `sanitiz|jsoup|escapeHtml|htmlEscape|Whitelist|xss|StringEscapeUtils` = **0 命中**；`service/TheoryKnowledgeService.saveTheoryKnowledge`、`EquipmentService.saveDeviceEquipmentDescMsg` 原样入库，`getByIdAndToken` 原样返回。
- **触发 → 后果**：拥有出题/建设备说明权限者写入 `<script>`/事件属性 → 在其它教员/学员同源会话中执行（存储型持久污染）。按内网口径**不计 J-P0/J-P1**，但**存在数据性后果**（持久污染多用户），故列 J-P2 记录在案。
- **责任归属**：`双侧协同`，**最小修复落前端渲染侧**。理由：(1) 内容是 UEditor/TinyMCE 合法富文本，写入侧白名单净化易误删合法标记且须与编辑器能力持续同步；(2) 存在多个渲染 sink（`v-html` + `iframe.write` + 导出 Word 复用同一 `content`），渲染侧 `DOMPurify`（或仓内已有 `public/UEditor/third-party/xss.min.js`）是唯一收口点；(3) 后端把 `content` 当不透明存储，多消费方共用。后端写入侧净化仅作纵深防御（可选）。
- **与单侧关系**：前端评审 §3.1 HIGH#1 已列该 XSS 但只说「走白名单净化」；本条为**联合定责细化**——确认后端写入侧亦零净化，并明确「最小修复侧 = FE 渲染侧」。

### TK-J-P3-01 — 考试取卷/分析前端消费点缺 null 兜底（残余脆弱）
- **结论一句话**：后端 `examineAnalyse`/`findTheoryKnowledgeExamById` 在缺快照时会走出错误信封（`data=null`），前端多处消费直接解引用 `res.data.paper.*` 无兜底，将白屏/JS 报错。
- **前端证据**：`frontend/src/views/manage/basicTheory/test/test/addTest/js/addTest.js:57-61`（`res.data.paper.completion = JSON.parse(res.data.paper.completionList)` 无 null 检查），同构见 `startGrade/js/startGrade.js:70-73`、`studentGradeDetails/js/startGrade.js:109-112`、`startTest/js/startTest.js:124-127`。
- **后端证据**：`backend/src/main/java/com/nip/service/TheoryKnowledgeExamService.java:319-322`（`examineAnalyse` 对 `theoryKnowledgeExamTestPaperDao.findAllByExamId(examId)` 返回值不判空即 `.getTotal()`，缺快照→NPE→`GlobalExceptionMapper` HTTP 500 `data=null`）。
- **触发 → 后果**：正常路径不触发（自测/主路径均建快照，见 §4）；仅当某考试快照缺失/被删或鉴权码≠200 时，前端取卷/分析页 `res.data.paper` 解引用崩溃。属残余脆弱。
- **责任归属**：`FE`（渲染侧加 `res.code===200 && res.data?.paper` 兜底）。
- **最小修复**：前端消费点统一按信封判 `res.code`、对 `res.data`/`res.data.paper` 加可选链与失败提示。

---

## 3. 已核实为「一致/无问题」的关键契约（防止后续误改）

1. **上传字段名一致**：前端 `a-upload name="file"`（`equipmentIndex.vue:203/245`、`edit/Index.vue:168`）与后端 `@RestForm("file")`（`TheoryKnowledgeController.java:179`）匹配；上传走 a-upload 原生 multipart，**未**误用共享 axios（不会被默认 `application/json` 毁掉 boundary）——这是**正确**模式。
2. **题库导出（数据侧）一致**：`exportQuestionByLevelId` 返回 JSON、前端 `exportQuestionBank` 不设 `responseType`、客户端 `handleExportWordDataInfo` 生成 docx（`knowledgeTabel.js:656-751`），与后端「后端只给数据、前端生成文件」一致。
3. **自测快照契约一致（后端已修）**：`saveTheoryKnowledgeExamSelfTesting` 现 `snap.setId(null)`（`TheoryKnowledgeExamService.java:238`）+ 五题型 `nullToEmpty`（`:241-245`）+ 只按 `examId` 删本场快照/考生（`:233-234`）→ 单侧 `TU-P1-01`/`TU-P3-03` 的「跨用户快照覆盖 / 写侧不归一」**已不复现**。
4. **学员端 exam 信封透传一致（后端已修）**：`TheoryKnowledgeExamUserController.findAllTheoryKnowledgeExamUser` 现对 `state=null` 抛错（`:56-59`）、内层错误码透传（`:62-66`）→ 单侧 `CA-P1-05`「失败伪装成 code=200 + 拆箱 NPE」**已不复现**。
5. **题库导入 levelId 归属正确**：解析出的每道题都带 `levelId:activeAction.value.key`（`knowledgeTabel.js:333/377/424/443/461`），不会产生 levelId 为空的孤儿题。

---

## 4. 与单侧评审的定级变化

| 原单侧编号 | 原结论/定级 | 联合定级变化 + 理由 |
|---|---|---|
| 后端 `CA-P1-06` / `TU-P3-02`（`uploadFileToNip` 假成功、不用上传文件）| 后端 P1/P3 | **根因已由后端修复**（`readDocumentContent` 真读文件），但修复**引入新跨栈断裂** → 见 **TK-J-P1-01**（J-P1，双侧）。责任从「纯后端未实现」变为「双侧契约未对齐」。 |
| 后端 `CA-P1-07` / `TU-P3-01`（`exportTemplate` 返回 void / service 空体）| 后端 P1/P3 | **后端已实现为 JSON 列规格**；但前端不调用、按钮走静态文件 → 端点孤儿 + blob 死导入陷阱 → **TK-J-P2-01**（J-P2，双侧）。 |
| 后端 `TU-P1-01`（自测未 `setId(null)` 覆盖源试卷）| 后端 P1 | **后端已修**（`:238` `setId(null)`），联合复核不复现 → 降为 §3「一致」；前端残余为 **TK-J-P3-01**（缺 null 兜底，J-P3，FE）。 |
| 前端评审 §3.1 HIGH#1（后端富文本经 `v-html`/`document.write` 未净化 → XSS）| 前端 HIGH | **联合定责细化**：确认后端写入侧亦零净化，明确最小修复落**前端渲染侧** → **TK-J-P2-03**（J-P2 `[已接受风险口径]`，双侧）。 |
| 前端评审 §3.3 MEDIUM（`knowledgeTabel.js` 绕过共享 axios/硬编码 URL）| 前端 MEDIUM | 联合视角补充：其中 `exportTemplate1`（blob）实为**死代码**、`downloadTemplate` 为**死导入**、`saveBatch` 端点**孤儿** → 归入 **TK-J-P2-01/02**（契约漂移视角）。 |

---

## 5. 未能验证的部分（明确缺什么前提）

1. **a-upload 在 HTTP 500 时是否回填 `file.response`**：决定 TK-J-P1-01 的 Word/PPT 路径表现为「TypeError」还是「静默 no-op」。二者都使功能失效，结论不变；确切表现需运行期（Electron 内实测上传 .docx）确认。`[INFERENCE]` 部分仅限该表现二选一，功能失效本身有双侧源码硬证据。
2. **`window.fileUrl/006/题库-模板.docx` 是否真实存在于文件服务**：决定 TK-J-P2-01 的「导出模板」按钮当前是否可用；文件服务不在本仓，无法静态证实。
3. **`examineAnalyse` 缺快照的现网可达性**：TK-J-P3-01 正常路径不触发；是否有「快照被删/鉴权过期」的现网路径需运行期验证，故定 J-P3。

## 附录 A：结构化缺陷条目（5 条，子代理原始输出）

### [J-P1] 文档导入→富文本编辑器端到端失效（后端收窄契约+前端未跟随）

- 锚点：`frontend/src/views/manage/equipment/equipmentIndex.vue:336-347`（置信度 0.9）

设备说明/理论课件编辑器的「导入文档」按钮跨栈断裂。后端 uploadFileToNip 已重写为 readDocumentContent，仅接受 txt/md/csv，其余（含 .doc/.docx/.pptx）抛 IllegalArgumentException（backend/src/main/java/com/nip/service/TheoryKnowledgeClassifyService.java:116-119），且无 IllegalArgumentExceptionMapper → 落 GlobalExceptionMapper（GlobalExceptionMapper.java:19-25）→ HTTP 500 + {code:500,message:"服务器错误",data:null}（具体错因被吞）。前端 a-upload accept=".doc,.docx,.pptx"（equipmentIndex.vue:204、study/basic/edit/Index.vue:168）引导用户选 Word/PPT → 恒 500，uploadChange 内 e.file.response.data.imgUrls 对 null 解引用 → TypeError（若未回填响应体则静默 no-op）。第二重断裂：即便强选 txt/md/csv 后端成功，也恒返回 imgUrls=[]（TheoryKnowledgeClassifyService.java:132）；JS 中空数组为真值，if(e.file.response.data.imgUrls) 恒真 → 走图片分支，wordContent 插入分支（else）永不执行。两重叠加使导入按钮对用户完全无效，且后端错因不可见。这是单侧 CA-P1-06/TU-P3-02 被后端修复后引入的新跨栈断裂。责任=双侧协同；最小修复：前端把 accept 改 .txt,.md,.csv、判别改 if(data&&Array.isArray(data.imgUrls)&&data.imgUrls.length)、wordContent 分支加可选链、按 res.code 提示；或前端按题库做法用 mammoth 客户端解析 Word。后端可选：文本内容时 imgUrls 置 null 并给 IllegalArgumentException 配 200+message 的 Mapper。

### [J-P2] 导出模板后端端点成孤儿 + blob/JSON 死导入陷阱 + 静态文件带外依赖

- 锚点：`frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:648-654`（置信度 0.88）

后端把 exportTemplate 重写为返回 JSON 列规格（TheoryKnowledgeQuestionController.java:94-99 + TheoryKnowledgeQuestionService.java:227-241），但前端活跃「导出模板」按钮走带外静态文件 window.fileUrl+'/006/题库-模板.docx'（knowledgeTabel.js:648-654，导出于 :764），后端端点从未被调用。同时残留一条 responseType:'blob' 死导入 downloadTemplate（common/api/TheoryQuestionBankApi.js:44-51，于 knowledgeTabel.js:6 导入但全文未调）与死函数 exportTemplate1（knowledgeTabel.js:583-642，硬编码URL+blob+.docx名，未导出）——一旦被接线即产出「JSON 字节冒充 .docx」的损坏文件。后果：模板按钮正确性依赖文件服务上存在静态 docx（缺失即 404），与后端列规格端点无关；后端端点长期零调用；blob↔JSON 形态矛盾为脆弱陷阱。责任=双侧协同（对齐/清理）。最小修复：前端「导出模板」改调 JSON exportTemplate 并客户端生成 .xlsx，删 exportTemplate1/downloadTemplate 死代码；或若维持静态文件方案则后端删孤儿端点与 downloadTemplate API。

### [J-P2] 题库批量导入分工漂移：saveBatch 孤儿 / Word≠Excel / 逐行 fire-and-forget 假成功

- 锚点：`frontend/src/views/manage/basicTheory/test/questionBank/js/knowledgeTabel.js:556-564`（置信度 0.85）

后端建了「整批单事务+行级校验」的 saveBatch 代替文件导入（TheoryKnowledgeQuestionController.java:86-92 + TheoryKnowledgeQuestionService.java:185-218，@Operation 注释写「Excel 由前端解析后提交 JSON 行」），前端却从不调用。前端仍以 mammoth 解析 Word（knowledgeTabel.js:567-582），逐行 fire-and-forget 调 saveTheoryKnowledgeQuestion 且不 await，2 秒后 setTimeout 无条件 message.success('上传成功！')（knowledgeTabel.js:556-564）。后果：后端整批原子性+行级校验反馈能力完全闲置；前端逐行保存对单行失败（后端校验/鉴权码≠0200）无任何提示，失败静默、可能部分导入；格式契约亦漂移（前端 Word ↔ 后端注释 Excel）。注：解析对象已带 levelId:activeAction.value.key（knowledgeTabel.js:333/377/424/443/461），不产生 levelId 孤儿；危害集中在假成功+无原子性。责任=双侧协同。最小修复：前端改为一次 saveBatch(arrObj) 并按返回码提示；或后端删 saveBatch 孤儿端点并把注释 Excel 更正为 Word。

### [J-P2] [已接受风险口径] 富文本存储型 XSS：联合定责（净化应落渲染侧/FE）

- 锚点：`frontend/src/views/manage/basicTheory/study/basic/details/js/useDetails.js:70-75`（置信度 0.9）

知识/设备说明富文本 HTML 写入侧后端零净化、渲染侧前端原样直插，构成存储型 XSS（持久污染）。前端 sink：equipmentIndex.vue:24 v-html="deviceList[..].descriptions[..].content"；useDetails.js:71 iframe.document.write(data.value.knowledgeSwfs[i].content)。后端：backend/src/main/java 全量 grep sanitiz|jsoup|escapeHtml|Whitelist|xss = 0 命中，saveTheoryKnowledge/saveDeviceEquipmentDescMsg 原样入库、getByIdAndToken 原样返回。按内网口径不计 J-P0/J-P1，但存在数据性后果（持久污染多用户）故列 J-P2 备案。联合定责：最小修复落前端渲染侧——(1)内容是 UEditor/TinyMCE 合法富文本，写入侧白名单易误删合法标记；(2)多个渲染 sink（v-html+iframe.write+导出 Word 复用同一 content），渲染侧 DOMPurify/已有 xss.min.js 是唯一收口点；(3)后端当不透明存储。后端写入侧净化仅作纵深防御（可选）。与单侧关系：前端评审 3.1 HIGH#1 已列该 XSS但只说「走白名单净化」；本条为联合定责细化（确认后端亦零净化，明确最小修复侧=FE 渲染侧）。

### [J-P3] 考试取卷/分析前端消费点缺 null 兜底（残余脆弱）

- 锚点：`frontend/src/views/manage/basicTheory/test/test/addTest/js/addTest.js:57-61`（置信度 0.8）

后端 examineAnalyse 对 theoryKnowledgeExamTestPaperDao.findAllByExamId(examId) 返回值不判空即 .getTotal()（backend/src/main/java/com/nip/service/TheoryKnowledgeExamService.java:319-322），缺快照时 NPE→GlobalExceptionMapper HTTP 500 data=null；findTheoryKnowledgeExamById 同类错误信封也会 data=null。前端多处消费直接解引用 res.data.paper.*：addTest/js/addTest.js:57-61 res.data.paper.completion=JSON.parse(res.data.paper.completionList) 无 null 检查，同构见 startGrade/js/startGrade.js:70-73、studentGradeDetails/js/startGrade.js:109-112、startTest/js/startTest.js:124-127。触发→后果：正常路径不触发（自测/主路径均建快照，见 §4）；仅当某考试快照缺失/被删或鉴权码≠0200 时，前端取卷/分析页 res.data.paper 解引用崩溃。属残余脆弱，根因 TU-P1-01 已后端修复（setId(null)，:238）。责任=FE。最小修复：前端消费点统一按信封判 res.code===200 && res.data?.paper 并加可选链/失败提示。
