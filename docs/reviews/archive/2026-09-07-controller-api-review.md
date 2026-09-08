# 结论：API 层（controller + dto）共 38 个问题 —— P0 1 / P1 7 / P2 22 / P3 8（已接受安全项入附录，不计入统计；CA-P2-23 经 prod jar 运行实测证伪，条目保留但不再计数）

| 项目 | 内容 |
|---|---|
| 审查范围 | `src/main/java/com/nip/controller/**`（62 个 .java，含 free/general/simulation/test 子包）、`src/main/java/com/nip/dto/**`（287 个 .java）、`src/main/java/com/nip/common/MainApplication.java`（@ApplicationPath 类实际在 common/ 包）|
| 审查日期 | 2026-09-07 |
| 审查方式 | 静态阅读 + grep 取证（未运行构建/测试）|
| 计数 | P0 1 / P1 7 / P2 22 / P3 8（原 P2 23，CA-P2-23 运行实测证伪后出账）|

## 0. 统计口径（本次 grep 实测值）
- Controller 总数 = 62（主包 48 + simulation 5 + free 2 + general 6 + test 1）。
- 标 @JWT 的类 = 52（grep @JWT 命中 52 文件，均类级注解）。
- 未标 @JWT 的类 = 10（上一轮 A-4 的 free/DemoController 已删除，故 11→10）。

| 未标 @JWT 的 Controller | 路径前缀 | 写接口暴露 |
|---|---|---|
| CableController.java:22 | /cable | POST /save(:53)、/delete(:60) 无鉴权写 |
| CableTypeController.java:17 | /cable/type | /save(:36)、/delete(:43) 无鉴权写 |
| CableFloorController.java:16 | /cable/floor | 仅 /find(:28) 只读 |
| DeviceController.java:29 | /device | save(:41)/delete(:56)/addDeviceDescription(:64)；save/addDesc 取 header token(:44,:69) 但类无 @JWT，token 从未校验 |
| PostTickerTapeTrainSettingController.java:24 | /postTickerTapeTrainSetting | /addOrUpdate(:43) 无鉴权写 |
| PostTrainGlobalRuleController.java:18 | /postTrainGlobalRule | /addRule(:29)、/deleteById(:43) 无鉴权写 |
| TelegraphKeyPatTrainSyntheticalController.java:28 | /telegraphKeyPatTrainSynthetical | save(:48)/begin(:56)/stop(:63)/goTo(:70)/finish(:77) 无鉴权写；save/lastTrain 取 header token 不校验 |
| test/TestController.java:18 | /test | 见 CA-P0-01 |
| free/ToolsController.java | /tools | 只读工具（有意匿名）|
| free/UserController.java:26 | /user | login/signin/test 等（有意匿名，见 CA-P2-11、附录）|

鉴权缺口的安全后果按内网口径入附录 A-1，不计数。

## 1. P0
| 编号 | 位置(file:line) | 触发条件 | 后果 | 证据 |
|---|---|---|---|---|
| CA-P0-01 | test/TestController.java:18,28-46 | 任意请求 GET /api/test/start（类无 @JWT）| 无条件对写死 ID 02bfee8b-a01f-479f-a1a7-1d081734c952 依次 begin→pause→goOn→finish（JPQL 批量 UPDATE），覆盖 status/startTime/endTime/validTime/mark/schedule 为测试值；:42-44 三行 System.out 绕日志 | 类级无 @JWT；@GET @Path("/start")(:28-29)。上一轮 P0-1/汇总#23，代码原样未修复。**主代理运行复核（2026-09-07）**：该 UUID 在当前库不存在，实测本次调用 0 行受影响；`t_ticker_tape_train` 为 InnoDB（非 MyISAM），故原文「MyISAM 不可回滚 → 永久数据丢失」不成立，严重级由本片 P0 下调为汇总口径 P1，详见汇总报告 §2.1 |

## 2. P1
| 编号 | 位置(file:line) | 触发+后果+证据 |
|---|---|---|
| CA-P1-01 | simulation/SimulationReceptRoomController.java:55 | @RestQuery("roomgId") 拼错（多 g），前端传 ?roomId= 得 null 下传 service→空详情/异常；同类 delete(:62) 用常量 ROOM_ID |
| CA-P1-02 | simulation/SimulationReportRoomController.java:56 | 同上 roomgId 拼错 |
| CA-P1-03 | simulation/SimulationRouterRoomController.java:73 | 同上 roomgId 拼错 |
| CA-P1-04 | dto/Page.java:19 + common/utils/Page.java:17（消费方 GroupNetTrainService:79、PostTelexPatTrainService:175、PostTickerTapeTrainService:125、TickerTapeTrainService:98、general/GeneralKeyPatService:314、GeneralTelexPatService:191、GeneralTickerPatService:363）| 默认 page=0 而全部消费方 getPage()-1→传 -1 越界（现被 ValidationExceptionMapper 兜 200/CODE_500 仍失败）；rows=0 除零；rows 无上限→setMaxResults(大数) 整表载入 OOM。两 Page 类默认相同且无钳制 |
| CA-P1-05 | TheoryKnowledgeExamUserController.java:54,59 | `.getData()` 丢弃内层错误 Response，:63 success(ret) 重包→失败伪装成 code=200 exam=null；map.get("state")(Boolean) 传 boolean(Service:38) 缺参拆箱 NPE。type 拆箱已修，此两处仍在 |
| CA-P1-06 | TheoryKnowledgeController.java:176-181 + TheoryKnowledgeClassifyService.java:89-93 | uploadFileToNip 不用文件、返回全 null 空 VO 包 code=200；FileUpload 缺 @RestForm（对照 PostEnteringExerciseWordStockController:52）。假成功，未修复 |
| CA-P1-07 | TheoryKnowledgeQuestionController.java:86-91 + TheoryKnowledgeQuestionService.java:174-175 | exportTemplate 返回 void（全层唯一非 Response<T>，客户端 204），service 空方法体→功能不存在破坏封装。未修复 |

## 3. P2
| 编号 | 位置(file:line) | 触发+后果+证据（简）|
|---|---|---|
| CA-P2-01 | general/GeneralKeyPatTrainController.java:29,31 | 路径 {trainId} 但用 @RestQuery→路径段被忽略，缺 query 时 trainId=null，ROOM(ConcurrentHashMap ws/WebSocketGeneralKeyPatService:40).get(null) NPE（现兜 500）；上轮 P1-1，已补空判但 get(null) 在其前 |
| CA-P2-02 | general/GeneralTickerPatTrainController.java:39 | PAT_ROOM.get(null)（ConcurrentHashMap ws/WebSocketGeneralTickerPatService:38）NPE，缺前置判空；上轮 P2-7；现兜 500 |
| CA-P2-03 | TheoryKnowledgeExamController.java:74-75 | Map<String,Boolean>→boolean(Service:108) 缺 state 拆箱 NPE→现兜 500 非参数码；上轮 P1-3 入参未改 |
| CA-P2-04 | 11 处转换：TheoryKnowledgeController:74,90,145,151；TheoryKnowledgeExamController:93,106；UserController:83；GradingRuleController:62,69；TelexPatController:39；TelegramTrainController:99 | parseInt/(Integer)/(int)/(String)/.toString() 未校验：缺失 NPE→500，非数字 NumberFormatException→ValidationExceptionMapper 200/CODE_500，类型不符 ClassCastException→500。封装破口已由 mapper 修（原 P1-6 核心），但仍无入参校验、错落 CODE_500 非 202 |
| CA-P2-05 | PostTelegramTrainController.java:123-125 | finish catch(Exception)→error("服务器错误") 吞异常不打日志（写成绩静默失败）；上轮 P2-1 已补入参守卫，catch-all 无日志仍在 |
| CA-P2-06 | TickerTapeTrainController.java:106-108 | saveBaseTrain catch→error() 无参无日志；上轮 P2-2 原样 |
| CA-P2-07 | EnteringExerciseController.java:65-67 | error(e.getMessage()) NPE 时 message=null 且泄内部文本、无日志；上轮 P2-3 |
| CA-P2-08 | EnteringTelexPatController.java:50-52,62-64 | 两处 error(e.getMessage())；上轮 P2-3 |
| CA-P2-09 | 20 GET 写：UserController:166,173；PostEnteringExercise:92；PostMilitaryTermTrain:78；PostRadiotelephoneTrain:98；PostTelegramTrain:143；PostTelegraphKeyPatTrain:96；PostTelexPatTrain:97；PostTickerTapeTrain:104；DeviceScoringRule:47；general/GeneralKeyPat:172,180；GeneralTelexPat:126,134；GeneralTickerPat:105,113；simulation/SimulationDisturd:104；SimulationRecept:59；SimulationReport:61；SimulationRouter:100 | GET 做删除/改状态/重置密码；虽在 @JWT 类，但 JWTInterceptor:62,71 允许 token/deviceId 走 query，?token= 的 GET 落日志即可重放执行写。CableController:60 删除用 POST 证明不一致；上轮 P2-5 原样 |
| CA-P2-10 | common/annotation/RequestPass.java + general/GeneralTickerSocketController.java:36,44 | JWTInterceptor 全文不检查 RequestPass，类有 @JWT→"放行"端点仍强制 token，调用方收 CODE_203。失效安全注解；上轮 P2-6 |
| CA-P2-11 | free/UserController.java:55-59 | POST /user/test（free 无 @JWT）空串查用户返回 UserEntity(含 password/token)；调试端点残留；上轮 P2-8 |
| CA-P2-12 | PostTelegramTrainController.java:150-155 | GET /test 调试端点 + @Operation(:152)"删除训练" 描述错（复制自 delete）；上轮 P2-9 |
| CA-P2-13 | DeviceScoringRuleController.java:50、GroupNetTrainController.java:62、SimulationRouterRoomController:80,94 等 | 必填 @RestQuery Integer 缺失→null 下传 service Hibernate 异常；非数字→JAX-RS 404 非封装。controller 无必填校验；上轮 P2-11 |
| CA-P2-14 | common/response/ResponseResult.java:29-55 + GlobalExceptionMapper.java:22 + ValidationExceptionMapper.java:17-18 | error() 序列化 HTTP200 体内码；未捕获→GlobalMapper HTTP500 信封；IllegalArgumentException→ValidationMapper HTTP200+CODE_500 且回显 e.getMessage()（内部文本外泄+语义错位）。三口径并存；上轮 P2-12 封装已收口但口径仍乱 |
| CA-P2-15 | common/constants/ResponseCode.java:10-19 | 同码多义：SUCCESS/CODE_200(200)、PARAMS_ERROR"参数错误"/CODE_202"授权过期"(202)、NULL_ERROR"参数为空"/CODE_204"设备标识"(204)、SYSTEM_ERROR/CODE_500(500)；202 无法区分参数错误与跳登录；上轮 P2-13 |
| CA-P2-16 | dto/Page.java 与 common/utils/Page.java | 两字段/默认值完全相同 Page 并存（仅 dto 多 @RegisterForReflection），import 分裂将来漂移；上轮 P2-14 |
| CA-P2-17 | TelexPatController.java:25 与 TexPatTrainController.java:33 | 两类同 @Path("/telexPat")，类名/方法名仅差一字母易混，Quarkus 新版重复端点检测可能构建失败；上轮 P2-15 |
| CA-P2-18 | general/GeneralTickerPatTrainController.java:35 | @Path("/综合训练-手键拍发Api") 中文路径需 percent-encoding，日志/抓包不一致；上轮 P2-16 |
| CA-P2-19 | simulation/SimulationRouterRoomController.java:15,58 | import jakarta.websocket.server.PathParam（REST 不认），靠参数名 roomId=={roomId} 侥幸工作，重命名/去 -parameters 即静默退化 null；上轮 P2-17 |
| CA-P2-20 | TheoryKnowledgeController.java:78-90 | getBasicTheoryOpen 无 map.isEmpty() 兜底（相邻 getBasicTheory:60 有），直接 parseInt(map.get(TYPE))；且 getBasicTheory 兜底也不全；两同义接口异常输入行为不一致；上轮 P2-18 |
| CA-P2-21 | dto/vo/PostTickerTapeTrainSettingVO.java:36 | @JsonFormat "yyyy-MM-dd hh:mm:ss" 用 12 小时 hh 无 AM/PM，14:30 显示 02:30→展示时间错误。新发现 |
| CA-P2-22 | dto/vo/TickerTapeTrainSettingVO.java:36 | 同 CA-P2-21 另一 VO hh:mm:ss。新发现 |
| CA-P2-23 | dto/MenusMetaDto.java:20-21(isMenu/isBread)、dto/general/GeneralTickerPatTrainVO.java:35/55/62、dto/vo/simulation/tickerPat/GeneralTickerPatTrainVO.java:53/61、dto/vo/PostTelegramTrainVO.java:34/52、dto/vo/param/PostTelegramTrainAddParam.java:28/53/70、dto/vo/param/simulation/tickerPat/GeneralTickerPatTrainAddParam.java:25/51/64、entity/simulation/router/SimulationRouterRoomEntity.java:82 | **不成立（误报，2026-09-07 运行实测证伪）**。原判断假设「实体侧存在 `boolean isRandom` → `isRandom()` → 属性 `random`」的一侧，实际不存在：这些字段一律是包装类型（`Boolean`/`Integer`），Lombok `@Data` 生成的是 `getIsXxx()` 而非 `isXxx()`，Jackson bean 命名因此保留前缀，DTO 与实体两侧同构。prod jar（1.1.0，端口 18002）`/q/openapi` 共 447 个 schema，其中 25 个含相关属性，属性名**全部保留 `is` 前缀**（`isRandom`/`isAverage`/`isCable`），无一个被削成 `random`/`average`/`cable`；样例 `GeneralTickerPatTrainVO: ['isAverage','isCable','isRandom']`、`PostTelegramTrainVO: ['isCable','isRandom']`、`SimulationRouterRoomEntity: ['isCable']`。结论：不需要 `@JsonProperty`，代码不改 |

## 4. P3
| 编号 | 位置(file:line) | 触发+后果+证据（简）|
|---|---|---|
| CA-P3-01 | general/GeneralKeyPatController.java:71-72 | catch(Exception)→throw new RuntimeException(e) 无意义包装；已对 IllegalArgument/State 重抛(:69-70)交专用 Mapper，仅剩通用包装噪音；上轮 P2-4 降级 |
| CA-P3-02 | TheoryKnowledgeQuestionController.java:79-84 | upLoadFile 空方法返 success（"新框架不做上传"）却留假成功端点；上轮 P3-1 |
| CA-P3-03 | 裸/通配 Response：UserController:98(裸)、GroupNetTrainController:70、general/GeneralKeyPatController:86,94、GeneralTelexPatController:78、GeneralTickerSocketController:45、test/TestController:31(Response<?>) | schema 退化为 object 前端无类型；上轮 P3-2 |
| CA-P3-04 | @Operation 不符：PostTelegramTrainController:152(test 标"删除训练")、general/GeneralKeyPatController:65,78(detail/patDetail 同"查询训练详情")、GeneralTickerPatTrainController:54(findTrainInfo 标房间人员实返回全部房间) | Swagger 唯一文档源，描述错即文档错；上轮 P3-3 |
| CA-P3-05 | TheoryKnowledgeQuestionController.java:94-99 + TheoryKnowledgeQuestionService.java:177 | exportQuestionByLevelId 的 HttpServerResponse 一路不用（改前端导出后遗留），返回实体列表非 VO；上轮 P3-4 |
| CA-P3-06 | PostRadiotelephoneTrainController.java:70(/listPge 方法名 listPage)、TheoryKnowledgeController.java:111,117(getById 重载 /getById 与 /getByIdAndToken)、SimulationRouterRoomController(sendFinish POST 无 body) | 命名/约定不一致；上轮 P3-6 |
| CA-P3-07 | dto/vo/PostTickerTapeTrainVo.java:50,57,64 与 dto/vo/TickerTapeTrainVo.java:52,59,66 | @JsonFormat "yyy-MM-dd" 年份 yyy 笔误（DateTimeFormatter 下最少 3 位、2026 仍 4 位，待运行验证）。新发现 |
| CA-P3-08 | dto/vo/PostTelexPatTrainVO.java:119 | 输出 VO createTime = LocalDateTime.now() 默认值，未赋值时静默展示当前时间误导前端。新发现 |

## 5. 上一轮 34 条核销
| 上轮 | 上轮结论 | 当前判定 | 证据 |
|---|---|---|---|
| P0-1 | /test/start 无鉴权覆盖记录 | 未修复 | test/TestController.java:18,28-46 原样；CA-P0-01 |
| 汇总#23 | GET /api/test/start（改级）| 仍在 | 端点/UUID/DAO/System.out 全原样 |
| P1-1 | getOneline 路径/参数不匹配 NPE | 部分修复(降P2) | GeneralKeyPatTrainController:29,31 仍 @RestQuery+{trainId}，已补空判但 get(null) 在前，现兜 500；CA-P2-01 |
| P1-2 | roomgId ×3 | 未修复 | Recept:55/Report:56/Router:73；CA-P1-01/02/03 |
| P1-3 | Map<String,Boolean> 拆箱 NPE | 部分修复(降P2) | TheoryKnowledgeExamController:74-75 未改，Service:108 仍 boolean，现兜 500；CA-P2-03 |
| P1-4 | 拆箱 NPE+内层错误丢弃 | 部分修复 | type 拆箱已修(:49,53)，.getData() 掩盖(:54,59)+state 拆箱仍在；CA-P1-05 |
| P1-5 | 分页越界/除零/无上限 | 未修复 | dto/Page:19、common/utils/Page:17 默认 page=0，7 消费方 getPage()-1 无钳制，rows OOM 未解；CA-P1-04 |
| P1-6 | 无全局 ExceptionMapper | 核心已修复(残余降P2) | 新增 common/exception 6 Mapper(Global/Validation/IllegalState/InvalidTitle/Unauthorized/WebApplication)，JWTInterceptor proceed() 移出 try(:89)；11 转换仍无校验落 CODE_500；CA-P2-04 |
| P1-7 | uploadFileToNip 丢文件返成功 | 未修复 | TheoryKnowledgeController:176-181 + ClassifyService:89-93；CA-P1-06 |
| P1-8 | exportTemplate void/注释 | 未修复 | Controller:86-91 void；Service:174-175 死实现改空方法体；CA-P1-07 |
| P2-1 | finish catch-all 不打日志 | 部分修复 | 已补入参守卫(:106-117)，:123-125 catch(Exception) 无日志仍在；CA-P2-05 |
| P2-2 | saveBaseTrain error() 丢原因 | 未修复 | TickerTapeTrainController:106-108；CA-P2-06 |
| P2-3 | e.getMessage() 可能 null ×3 | 未修复 | EnteringExerciseController:65-67、EnteringTelexPatController:50-52,62-64；CA-P2-07/08 |
| P2-4 | throw new RuntimeException(e) | 部分修复(降P3) | GeneralKeyPatController:69-72 已重抛 IllegalArgument/State，剩通用包装；CA-P3-01 |
| P2-5 | 20 GET 写 | 未修复 | 20 处原样含 4 simulation delete；CA-P2-09 |
| P2-6 | @RequestPass 死注解 | 未修复 | JWTInterceptor 不检查；GeneralTickerSocketController:36,44；CA-P2-10 |
| P2-7 | findUserInfo get(null) NPE | 部分修复(保持P2) | GeneralTickerPatTrainController:39 无前置判空，现兜 500；CA-P2-02 |
| P2-8 | /user/test 残留 | 未修复 | free/UserController:55-59；CA-P2-11 |
| P2-9 | /postTelegramTrain/test+描述错 | 未修复 | PostTelegramTrainController:150-155；CA-P2-12 |
| P2-10 | /demo/test DemoController | 已修复 | free/DemoController.java 已删除（glob 仅 Tools/User）|
| P2-11 | @RestQuery 缺失 null 下传 | 未修复 | DeviceScoringRuleController:50、GroupNetTrainController:62；CA-P2-13 |
| P2-12 | HTTP 恒 200 业务码在体内 | 部分修复(保持P2) | error() 仍 200；叠 mapper 后 200/500/CODE_500 并存回显异常文本；CA-P2-14 |
| P2-13 | ResponseCode 双套语义 | 未修复 | ResponseCode:10-19 原样；CA-P2-15 |
| P2-14 | 两 Page 类并存 | 未修复 | dto/Page、common/utils/Page 均在；CA-P2-16 |
| P2-15 | /telexPat 被两类占用 | 未修复 | TelexPatController:25、TexPatTrainController:33；CA-P2-17 |
| P2-16 | 中文路径 | 未修复 | GeneralTickerPatTrainController:35；CA-P2-18 |
| P2-17 | 错误 PathParam 导入 | 未修复 | SimulationRouterRoomController:15；CA-P2-19 |
| P2-18 | getBasicTheory(Open) 不一致 | 未修复 | TheoryKnowledgeController:60 vs 78-90；CA-P2-20 |
| P2-19 | 注入未使用依赖 | 已修复 | UserController:38-41 仅 UserService；Cable*/Device 单注入(CableController:29 等) |
| P3-1 | upLoadFile 假成功 | 未修复 | TheoryKnowledgeQuestionController:79-84；CA-P3-02 |
| P3-2 | 裸/通配 Response | 未修复 | UserController:98 等 7 处；CA-P3-03 |
| P3-3 | @Operation 不符 | 未修复 | CA-P3-04 |
| P3-4 | 未用 HttpServerResponse+返回实体 | 未修复 | TheoryKnowledgeQuestionController:94-99；CA-P3-05 |
| P3-5 | Test.java 实验类 | 已修复 | test/ 仅剩 TestController.java |
| P3-6 | listPge/sendFinish/getById 重载 | 未修复 | PostRadiotelephoneTrainController:70 等；CA-P3-06 |

核销小结：已修复 4（P1-6 核心/P2-10/P2-19/P3-5）、部分修复 6（P1-1/P1-4/P2-1/P2-4/P2-7/P2-12）、未修复 24；无原结论误报。P1-1、P1-3 因 mapper 落地降级 P2，P2-4 降级 P3。

## 6. 待运行验证清单
1. CA-P0-01：UUID 02bfee8b-a01f-479f-a1a7-1d081734c952 是否存在于生产库。（本机库已由主代理实测为不存在，0 行受影响）
2. CA-P1-04：{} 空 body 请求 7 分页接口的实际返回形态；rows=1000000 内存占用。
3. ~~CA-P2-23：Boolean isRandom/isAverage/isMenu/isBread 等 DTO 与实体 Jackson 属性名是否错配。~~ **已验证并证伪（2026-09-07）**：prod jar `/q/openapi` 447 个 schema、25 个命中，属性名全部保留 `is` 前缀，成因是包装类型 + Lombok `getIsXxx()`；详见 §3 的 CA-P2-23 行。
4. CA-P2-21/22、CA-P3-07：下午时段 createTime 是否输出 12 小时错误值、yyy 是否输出 4 位。
5. CA-P2-14：ValidationExceptionMapper 回显的 e.getMessage() 是否被前端/日志留存。

## 附录：已接受安全风险（内网口径，不计数）
- A-1 未标 @JWT 的 6 个业务 Controller（Cable/CableType/Device/PostTickerTapeTrainSetting/PostTrainGlobalRule/TelegraphKeyPatTrainSynthetical）含无鉴权写；DeviceController:44,69 与 TelegraphKeyPatTrainSyntheticalController:51,94 取 header token 但从不校验，可伪造 token 指定操作者。
- A-2 UserEntity（含 password/token/deviceId/idCard）直接作响应：UserController:91-92 getAllUser 导出全库凭据、:107,121,128,135,142 及 free/UserController:57；token+deviceId 即 JWTInterceptor:79 全部校验材料。UserInfoDto/RoleInfoDto/ComprehensiveVO 内嵌 UserEntity 扩大泄漏面。
- A-3 free 包 /user 匿名端点按 body userId 取数（free/UserController:64,76,83）无越权校验，且与受保护 /user(@JWT) 共前缀。
- A-4 CORS 全开（application.yml origins * + credentials）与 JWTInterceptor:50-54 二次手工 CORS（回显 Origin+credentials:true）并存；JWTInterceptor:62,71 允许 token/deviceId 走 query，放大 CA-P2-09 重放面。Swagger UI 在 prod jar 实测为 404（主代理运行复核），原文「生产开启 Swagger UI 放大 CA-P0-01」不成立。
