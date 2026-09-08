# 结论：本片 P0 0 / P1 2 / P2 8 / P3 3；上轮本片 P0 #1-#4 全部已修复（附回归测试），#19 部分修复、#15 未修复

| 项目 | 内容 |
|---|---|
| 审查范围 | service/TheoryKnowledgeService.java、TheoryKnowledgeExamService.java、TheoryKnowledgeTestService.java、TheoryKnowledgeQuestionService.java、TheoryKnowledgeClassifyService.java、TheoryKnowledgeExamUserService.java、TestPaperService.java、UserService.java、MenusService.java、RoleService.java、ComprehensiveService.java、UserTrainStatisticsService.java；旁证 UserDao、BaseRepository、PojoUtils、ListUtils、JSONUtils、common/exception/*、相关 Controller、src/test 对应用例、docs/database/project006.sql |
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 0 / P1 2 / P2 8 / P3 3（纯安全项不计入，见附录）|

> 系统性事实（已核实，供全表引用）：
> 1. `getUserByToken` 现在查无用户即抛 `UnauthorizedException`（`UserService.java:480-486`），不再返回 null。配套 6 个 `@Provider` ExceptionMapper（`common/exception/`：Unauthorized→200+code203、IllegalState/IllegalArgument/InvalidTitle→200+CODE_500+message、WebApplication 直通、Global→500）。`JWTInterceptor` 的 `context.proceed()` 已移出 try（`JWTInterceptor.java:46-47`），业务异常直达 Mapper。上轮“无 ExceptionMapper + 丢堆栈 + token 返回 null 传播 NPE”的系统性根因已消除。
> 2. `BaseRepository.save`（`BaseRepository.java:15-23`）：id 空→persist，id 非空→merge。`PojoUtils.convertOne`（`PojoUtils.java:74-83`）用 `BeanUtil.copyProperties(...setIgnoreError(true))` 复制含 `id` 的全部同名属性、不排除 id——是快照主键复用类问题的直接机理。
> 3. 表引擎：`t_` 前缀表均为 InnoDB；MyISAM 仅限 `general_*`/`simulation_router_room_page*`/`t_post_*` 明细等训练表。**主代理运行复核（2026-09-07，活库 `information_schema`）**：`t_theory_knowledge_test_paper_question`（本文早期草稿误写为 `t_test_paper_question`，该表名在活库与快照中均不存在）、`t_theory_knowledge_swf`、`t_theory_knowledge_exam_test_paper`、`t_theory_knowledge_exam_user` 实测**全为 InnoDB**，故本片涉及的先删后插路径在异常逸出时确实能回滚——上轮 P0 被真正修好的第二个前提成立，原「待运行验证」已消除。

---

## 1. P0

无。

上轮属于本片的 5 条确认 P0（#1/#2/#3/#4/#10）与 service-core/silent-failures 对应条目均已修复，详见第 5 节核销。

---

## 2. P1

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| TU-P1-01 | `service/TheoryKnowledgeExamService.java:242-250`（`saveTheoryKnowledgeExamSelfTesting`）| 以库中已存在的试卷 P（`TestPaperDto.id=P.id`）发起自测；随后任意用户再以同一试卷 P 发起自测 | 快照实体复用源试卷主键，第二次 `merge` 命中同一 `t_theory_knowledge_exam_test_paper` 主键行，把 examId 从前一场改写为后一场；前一场 `findAllByExamId` 变 null，其取卷/考核分析在 `TheoryKnowledgeExamService.java:323,326` `testPaperEntity.getTotal()` NPE。跨用户、跨考试的快照被覆盖 | `PojoUtils.convertOne(testPaper,…)`（:242-243）复制 `TestPaperDto.id`（`TestPaperDto.java:14`）→ 只 `setExamId`（:244）**未 `setId(null)`** → `BaseRepository.save` id 非空走 merge（:20-21）。对照主路径已加固：`saveTheoryKnowledgeExam` 的 `snap.setId(null)`（:86）。即 #19 仅修主路径、自测路径残留。运行期确切后果（merge 未命中时 insert vs 命中时 update、前端自测是否携带源 id）标 `待运行验证` |
| TU-P1-02 | `service/RoleService.java:63-65`（含级联 :56-62）| 经 `/api/role/addRole`（唯一角色保存入口，`RoleController.java:44-47`）编辑已有角色（body 带 id）| 角色标量字段（name/isAdmin/isDefault/备注）**永不落库**——`entity.getRole()` 是 detached 对象，`roleDao.save` 仅在 id 为空（新建）时执行；编辑仅重建了 role-menus 关联。级联：编辑当前默认角色且提交 `isAdmin==1 && isDefault==0` 时，:57-61 把含自身在内的所有默认角色改成 `isDefault=1` 并落库，而自身提交的 `isDefault=0` 因 :63 不执行而丢失 → 库中无任何 `isDefault=0` 角色 → `UserService.assignDefaultRole`（:255）`find("isDefault",0).firstResult()` 恒 null → 新建用户无角色 → 登录时 `login:391-392` `role.getIsAdmin()` NPE→ :399 catch→“数据异常”，永久登不上（管理员可手动补默认角色恢复）| `RoleService.java:63` `if (StringUtils.isEmpty(entity.getRole().getId())) roleDao.save(...)`；无任何 else/merge。#15 未修复。无对应回归测试 |

---

## 3. P2

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| TU-P2-01 | `service/TheoryKnowledgeExamUserService.java:56-65`（`teacherUploadScore`）| 教师批量上传成绩，payload 中任一 `user_id` 在本场考试无 `exam_user` 行 | `theoryKnowledgeExamUserDao.save(allByExamIdAndUserId)`（:65）在 null 判断**之外**，该项 `allByExamIdAndUserId` 为 null → `BaseRepository.save` 的 `Assert.notNull`（:16）抛 `IllegalArgumentException` → 方法 `@Transactional` 回滚 → 整批（含合法成绩）全部不落库，教师侧 200+CODE_500 | :56 `if (null != allByExamIdAndUserId){…}` 仅包住 setScore/setState/setContent（:57-63），:65 save 在 if 外 |
| TU-P2-02 | `service/TestPaperService.java:236-244,216-234` | 两个并发请求同时命中 `/api/test/findTestPaperByLevelIdAndName`（走 `findAllLevel`）| `List<String> ids` 是 `@ApplicationScoped` 单例的**可变实例字段**，递归 `findAllLevel` 向其累加、请求末尾 :232 重置；并发请求互相污染 level id 集合 → 返回错误试卷集，或 `ConcurrentModificationException` | :236 实例字段 `ids`；:239 `ids.add(id)`；:221 调用、:232 `ids = new ArrayList<>()`。数据非持久化、可恢复、需并发触发 |
| TU-P2-03 | `service/TheoryKnowledgeQuestionService.java:111-140` | 两个并发请求同时命中 `findAllQuestionByLevelId` | 同上反模式：单例可变实例字段 `ids`（:111）在 `findAllLevel`（:113-121）累加、:140 重置，并发下 level id 集合互相污染 → 返回错误题目集 | 与 TU-P2-02 同构，不同源码位置故独立计一条 |
| TU-P2-04 | `service/UserService.java:451-453,437-439`（`changePassword`）| 传入不存在的用户 id（或方法内任意非持久化异常）| :437 `userDao.findById(id)` 返回 null → :439 `user.getPassword()` NPE → 被 :451 `catch(Exception)` 吞成 `ResponseResult.success(DATA_EXCEPTION,false)`，**零日志**；仅按 `code` 判定的客户端会误读为“成功”，且失败无堆栈 | catch 用 `success(...)`（`ResponseResult` 恒 code=200）；与 :440/:444 的正常业务失败同结构。（早前“密码静默改成功”的升级已被 InnoDB 回滚 + Mapper 阻断，仅剩可观测性/信封语义缺陷）|
| TU-P2-05 | `service/UserService.java:418` 与 `dao/UserDao.java:91-98`（`userOut`/`updateUser`）| 登出时 `updateUser` 内部抛异常或影响 0 行 | `userOut` 调 `updateUser` 却忽略其布尔返回（:418）无条件 `return true`；`UserDao.updateUser` 又 `catch(Exception) return false` 且丢弃受影响行数 → 若清 token 失败，登出仍报成功而旧 token 未失效 | `UserService.java:414-419`；`UserDao.java:92-97`。#P1-8 部分修复（token 无效时 `userOut` 现返回 false，:420-421）|
| TU-P2-06 | `service/TheoryKnowledgeExamService.java:429-438`（`deleteTheoryKnowledgeExam`）| 三连删除中任一抛持久化异常 | `catch(Exception) return ResponseResult.error()` **零日志**；删除失败无诊断信息（回滚正确，非数据丢失）| :435-437 空信息 catch |
| TU-P2-07 | `service/TestPaperService.java:247-255`（`deleteTestPaper`）| 删除题目/试卷时抛持久化异常 | 同上零日志 catch，失败不可诊断 | :252-254 `catch(Exception) return ResponseResult.error()` |
| TU-P2-08 | `service/TheoryKnowledgeService.java:370-371`（`saveTheoryKnowledgeRecord`）| 上报学习记录时 `knowledgeId` 指向不存在的知识 | :370 `knowledgeDao.findById(...)` 返回 null → :371 `theoryKnowledgeEntity.getType()` NPE → GlobalExceptionMapper 500、回滚（无先删，无数据丢失）| 缺 null 校验；@Transactional 方法 :358 |

---

## 4. P3

| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| TU-P3-01 | `service/TheoryKnowledgeQuestionService.java:174-175`（`exportTemplate`）| 调用 `/api/…/exportTemplate`（`TheoryKnowledgeQuestionController.java:87-91`）| 方法体为空，导出模板无任何输出——未完成功能（memory 记录的遗留导出项仍在）| 方法体 `{ }` 空实现 |
| TU-P3-02 | `service/TheoryKnowledgeClassifyService.java:89-94`（`updateFileToNip`）| 调用 `/api/theoryKnowledge/uploadFileToNip`（`TheoryKnowledgeController.java:177-180`）| 取完 token/userId 后直接 `return new TheoryKnowledgeDocumentContentVO()`，文件未处理——空壳返回，未完成功能 | :92-93 未使用上传文件 |
| TU-P3-03 | `service/TheoryKnowledgeExamService.java:245-249`（`saveTheoryKnowledgeExamSelfTesting` 写侧）| 自测试卷某题型列表为 null | 直接 `JSONUtils.toJson(testPaper.getSingleChoice())` 未经 `ListUtils.nullToEmpty` 归一（对照主路径 :89-93 已归一）；null→toJson→空串入库，读侧 `examineAnalyse` 已用 nullToEmpty 兜住故不崩，仅存写侧不一致 | :245-249 无 nullToEmpty；主路径 :89-93 有 |

---

## 5. 上一轮遗留核销

| 上轮编号 | 上轮结论 | 当前判定 | 当前证据(file:line) |
|---|---|---|---|
| 汇总 P0 #1 | 理论课件/测验被静默清空（TheoryKnowledgeService）| 已修复 | 校验前置于删除：标题空/课件列表 null 直接 `throw`（`TheoryKnowledgeService.java:243-253`），删除在校验后（:256），版本1测验缺失 `throw IllegalStateException`（:280-282），全方法无吞异常 catch；回归 `TheoryKnowledgeServiceTest.editWithNullSwfListKeepsExistingSwfs`/`saveWithNullTestContentsDoesNotNpe` |
| 汇总 P0 #2 | 试卷题目被清空（TestPaperService）| 已修复 | 先用 `ListUtils.nullToEmpty` 组装五题型列表（:63-67），`deleteAllByTestPaperId` 移到组装后（:72），无吞异常 catch；`getUserByToken` 抛异常而非 null（:78）；回归 `TestPaperServiceTest.updateWithNullTypeListKeepsExistingQuestions` |
| 汇总 P0 #3 | 菜单按钮权限被永久删除（MenusService）| 已修复 | `permissions==null` 在删除前 `throw IllegalArgumentException`（`MenusService.java:98-100`），删除在其后（:120）；回归 `MenusServiceTest.addMenusWithNullPermissionsKeepsButtons` |
| 汇总 P0 #4 / service-core P0-10 | 编辑考试无条件删除全部考生答卷成绩 | 已修复 | 重建前 `count("examId=?1 and (state<>1 or score>0 or content 非空)")>0 即 throw`（`TheoryKnowledgeExamService.java:74-79`），只删本考试自身快照（:81）；回归 `editExamWithAnsweredUsersIsRejected`/`editExamWithContentOnlyAnswerIsRejectedWithoutDeletingRows` |
| 汇总改级 #14 / service-core P0-06 | 菜单编辑八行自赋值 | 已修复 | 改为从入参 `in=entity.getMenus()` 读取（`MenusService.java:111-118`）；回归 `MenusServiceTest.editMenusUpdatesMetadata` |
| 汇总改级 #15 / service-core P0-07 | 角色编辑不落库 + 抹默认角色 | 未修复 | `RoleService.java:63-65` 仍仅在 id 空时 save；见 TU-P1-02 |
| 汇总改级 #16 | 五笔训练重复校验写反 | 非本片 | 属 `EnteringTelexPatService`（不在本片清单，由 general/entering 分片核销）|
| 汇总改级 #19 / service-core P0-11 | 考试快照复用源试卷主键 | 部分修复 | 主路径 `snap.setId(null)`（:86）+ 回归 `twoExamsOnSamePaperKeepBothSnapshots`；自测路径未加固，见 TU-P1-01 |
| 汇总改级 #20 / service-core P0-12 | 试卷缺题型时 addAll(null) 崩 | 已修复 | 写侧主路径 nullToEmpty（:89-93）、读侧 `examineAnalyse` 每处 `ListUtils.nullToEmpty(JSONUtils.fromJson(...))`（:334-348）；回归 `analyseWithMissingTypeListDoesNotNPE` |
| 汇总改级 #21 | 统计清空 save(null) | 非本片 | 属 `TelegraphKeyPatTrainService`（不在本片清单）|
| service-core P2-77 | getMonth 月/日复用命名误导 | 已修复 | 重命名 `padTwoDigits` 并勘误 javadoc（`TheoryKnowledgeService.java:506-514`）|
| service-core P2-78 | 及格线硬编码 >=60 与分档分歧 | 已修复 | 统一走 `countPass`→`countExamPass`（:519-525），分档以每卷 `passMark` 动态计算 goodBoundary=(total-passMark)/2+passMark（:574-580）；回归 `gradeDistributionUsesPerPaperThresholds` |
| silent-failures P0-5 | 保存试卷先清空再 NPE 提交 | 已修复 | 同 #2；先删后插顺序纠正 + getUserByToken 抛异常 + 无吞异常 catch |
| silent-failures P0-6 | 保存理论知识先删再 NPE 提交 | 已修复 | 同 #1 |
| silent-failures P0-7 | 保存菜单先删再 NPE、零日志提交 | 已修复 | 同 #3 |
| silent-failures P1（根因）| getUserByToken 返回 null 传播 NPE | 已修复 | `UserService.java:480-486` 抛 `UnauthorizedException`→`UnauthorizedExceptionMapper`（200+code203）|
| silent-failures P1-1 | JWTInterceptor 丢堆栈 + 无 ExceptionMapper | 已修复 | 新增 6 个 `@Provider` Mapper（`common/exception/*`）；`JWTInterceptor.java:46-47` proceed 移出 try（本条主属 CommonInfra 分片，此处仅就本片鉴权链确认）|
| silent-failures P1-7 | 改密码把异常包成 success | 未修复（降级 P2）| `UserService.java:451-453` 仍吞成 `success(...,false)` 零日志；数据侧升级风险已被回滚+Mapper 阻断，见 TU-P2-04 |
| silent-failures P1-8 | 登出永远成功、token 可能仍有效 | 部分修复（降级 P2）| `userOut` 现对 null token 返回 false（:420-421），但仍忽略 `updateUser` 返回值（:418）、`UserDao.updateUser` 仍吞异常/丢行数（:91-98），见 TU-P2-05 |
| silent-failures P1-9 | 分配角色先删后插被吞 | 已修复（数据完整性）| `UserService.addUserRole`（:335-353）仍单事务 delete→insert，但异常逸出+InnoDB 回滚使不再部分提交；日志已带堆栈（:350 `log.error("addUserRole error", e)`）。残留仅“catch 返回 false 而非透传 Mapper”，无数据风险，不单列计数 |
| silent-failures P2-6 | 删除类零日志 catch（TestPaper:255、TheoryKnowledgeExam:425）| 未修复 | 见 TU-P2-06 / TU-P2-07 |

---

## 6. 待运行验证清单

1. TU-P1-01：自测前端 payload 是否携带源试卷 id；两场（跨用户）自测同一试卷时 `merge` 对不存在主键是 insert 还是先 SELECT 再 UPDATE，据此确认快照覆盖的确切时序与 `examineAnalyse(前一场)` 的 NPE。
2. ~~系统性事实 3 的 `ENGINE` 行~~ → 已由主代理活库实测消除：`t_theory_knowledge_test_paper_question`、`t_theory_knowledge_swf`、`t_theory_knowledge_exam_test_paper`、`t_theory_knowledge_exam_user` 均为 InnoDB。
3. TU-P2-02/03：并发命中同一分级查询接口时 `ids` 实例字段污染的真实表现（错误结果集 vs `ConcurrentModificationException`）。

memory 遗留项复核：`TheoryKnowledgeQuestionService.exportTemplate` 仍为空实现（TU-P3-01）；`TickerTapeTrainService.update`（`TickerTapeTrainService.java:109-112`，非本片文件）仍存在但无任何 Controller 端点暴露（`TickerTapeTrainController` 仅 pause/finish 等），为未接线的孤儿方法，二者遗留状态维持不变。

---

## 附录：已接受安全风险（内网口径，不计入计数）

- 鉴权仅凭 `token`+`deviceId` 请求头，`getUserByToken` 失效返回 200+code203（信封，非 401）——沿用既定契约，属已接受。
- 口令 MD5（`MD5Util`）、token AES 固定密钥（`AESUtil.UKDAI_AES_KEY`）、`resetPassword` 硬编码“123456”（`UserService.java:572-579`）、登录/查询返回含 password 等敏感字段的 `UserEntity`——均为上轮已列入附录的已接受风险，本片不重复计数。
- 本片新增：无新增纯安全项。
