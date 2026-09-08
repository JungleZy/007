# 数据模型/字段类型/时间与数值表示（前后端联合评审分片）

- 日期：2026-09-08 / 方法：只读取证（reviewer 子代理）

## 0. 分片结论

数据模型联合评审结论：INCORRECT。计数 J-P1:1 / J-P2:2 / J-P3:3（共6条），责任分布 FE:1 / BE:1 / 双侧:4。§1覆盖：核对了日期/时间(格式+时区)、BigDecimal/数值、Boolean与0-1标志、包装类null、ID精度、枚举字典、字段命名7个维度的跨栈契约；抽样了entity/dto/vo全部@JsonFormat点、application.yml的jackson配置(实测无quarkus.jackson.*、全仓无ObjectMapperCustomizer)、@Id生成策略、4个Dockerfile的TZ、前端moment/dayjs解析点/toFixed/布尔判定/硬编码下拉/ID处理；未逐一验证每张训练表的accuracy落库量纲与容器运行期TZ。§3已核实一致(勿误改)：(a)ID精度无风险——全仓无Long/雪花主键，主键仅String-UUID(UserEntity.java:42)或Integer自增(DeviceEntity.java:29-30)，Integer≪2^53，前端数字id走Number()/*1、UUID走字符串===，无JS精度丢失；(b)null策略——Jackson默认输出null(无全局JsonInclude)，包装类null以`null`出现、字段仍在(TheoryKnowledgeSwfEntity.java:45 score=null)，前端多为真值判定，BE→FE方向不会因缺字段崩；(c)BigDecimal score序列化为JSON裸数字(非字符串)，前端Number()/直绑一致；(d)理论/报文域epoch毫秒串createTime↔前端Number()/*1解析自洽(useForm.js:24前端写epoch串↔后端new Date().getTime()串)。§4定级变化：后端§8"CA-P2-23 Boolean isXxx Jackson属性名错配"经联合核实对isAdmin/isDefault不成立——RoleEntity.java:28-29与GradingRuleEntity.java:28均为Integer(非Boolean)，不触发Jackson`is`前缀剥离，JSON键仍为isAdmin/isDefault、前端读取键一致；唯一真Boolean是TheoryKnowledgeSwfEntity.haveTest(getHaveTest()→键haveTest，前端details/Index.vue:24 v-if=checkItem.haveTest一致)，故该待验证项无实际危害。后端§8"CA-P2-21/22/P3-07 hh:mm:ss/yyy日期格式"经联合核实：抽样@JsonFormat全为标准yyyy-MM-dd HH:mm:ss、未见hh/yyy畸形样本，真正跨栈时间风险不是pattern笔误而是ISO-T形态(DM-J-P3-03)与timezone对LocalDateTime无效导致早8h(DM-J-P2-01)，责任从"格式笔误"转到"时区/形态契约"。§5未验证：容器实际TZ未运行验证→DM-J-P2-01的8h偏移为[INFERENCE]；设备type/trainType的后端权威取值域未定位到字典/常量真源；各训练类型accuracy的0..1 vs 0..100量纲未逐一核对落库值。说明：本代理write工具拒绝写工作树路径，无法落盘docs/reviews/2026-09-08-joint-datamodel.md，故完整报告经上述findings+本summary交付，父代理汇总即可。

## 1. 缺陷条目

### [J-P1] 对齐自测列表开始时间键名为 start_time

- 证据锚点：`frontend/src/views/manage/basicTheory/test/test/studentGradeList/Index.vue:31-32`（置信度 0.85）

后端 TheoryKnowledgeExamUserSelfVO.java:28-29 字段名是 snake_case `private String start_time`（Lombok→JSON 键 `start_time`），且该 VO 无任何 createTime/create_time 字段；端点 TheoryKnowledgeExamController.java:60 返回 List<TheoryKnowledgeExamUserSelfVO>。前端自测列表 studentGradeList/Index.vue:32 绑定 {{d.startTime}}（camelCase），其数据经 js/knowledgeTabel.js:13-15 的 listPageSelfTesting 取回后交 components/test/nodeTree/listSort.js:3 按 createTime/create_time 排序。触发条件：进入「自测列表」页即恒定发生——每张卡片「开始时间」读 d.startTime=undefined 显示空白；排序键 Number(undefined)=NaN 使 sort 比较恒返回 NaN，列表实际未按时间排序。后果：开始时间静默不显示 + 排序失效。责任归属=FE（同模块其余考试视图 grade/Index.vue:30、list/Index.vue:46、startGrade/Index.vue:34 均读 snake d['start_time']，本页是唯一读 camel 的离群点）。最小修复：前端把 d.startTime 改为 d.start_time；排序改用 VO 实有字段或移除 listSort 的时间排序。此为纯跨栈缺陷：单看前端无法判断键名对错，必须对照后端 VO 的 snake 键才能发现恒空。未在两侧单侧评审中出现。

### [J-P2] 统一容器时区并修正 LocalDateTime 上无效的 GMT+8 标注

- 证据锚点：`backend/src/main/java/com/nip/entity/EnteringExerciseEntity.java:43-45`（置信度 0.5）

后端自动生成的时间戳普遍用零时区 LocalDateTime.now()（EnteringExerciseEntity.java:45、DeviceEntity.java:65；TheoryKnowledgeExamService.java:252 的 LocalDateTime.now().format(...)），而 VO 上的 @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")（TickerTapeTrainVo.java:52 等）对 LocalDateTime 无效——Jackson 仅对带时区类型应用 timezone，LocalDateTime 被忽略，实际输出的是容器本地墙钟；Dockerfile.jvm/Dockerfile.native 均未设 TZ（基础镜像默认 UTC）。而 DateTimeUtil.java:25 另一处却强制 LocalDateTime.now(UTC_PLUS_8)，同库两套时区口径。前端 study/basic/details/Index.vue:103 对 epoch 毫秒串 dayjs(Number(createTime)) 渲染为浏览器本地（正确），但对后端格式化字符串时间（VO/实体）直接 moment 解析或直绑（verbatim）。触发条件[INFERENCE，容器 TZ 未运行验证]：容器为 UTC 时，后端自动生成并格式化的「创建/开始/结束时间」比北京时间早 8h，与 epoch 毫秒串字段同列并存导致同类时间显示不一致；同源两时间做差（如 startGrade.js:161 end-start）因同偏移相消不受影响。责任归属=BE。最小修复：容器设 TZ=Asia/Shanghai 或 JVM -Duser.timezone=Asia/Shanghai，并把实体时间统一走 DateTimeUtil.localNow()；或改用带偏移的 OffsetDateTime 输出，让前端可自行本地化。

### [J-P2] 统一 accuracy/speed 等数值字段的 wire 类型

- 证据锚点：`backend/src/main/java/com/nip/entity/TelegramTrainEntity.java:40-41`（置信度 0.68）

同名数值字段跨实体 wire 类型不一致：TelegramTrainEntity.java:40-41 的 accuracy/speed 为 String（JSON 带引号，如 "0"），TelexPatTrainEntity.java:41 的 duration 亦 String，而 EnteringExerciseEntity.java:50 的 accuracy 为 Double、:55 的 speed 为 Integer，PostRadiotelephoneTrainEntity.java:52/88/105 的 speed/accuracy/score 为 BigDecimal（Jackson 默认序列化为 JSON 裸数字）。同一概念一会儿是带引号 JSON 字符串、一会儿是数字。前端到处用 parseInt/parseFloat/.toFixed()*1 防御性强转掩盖之：NipTop.vue:139 parseInt(item.accuracy)、handKeyTrain.js:741 parseFloat((total-errorNumber)/total).toFixed(2)、datagramTrain.js:177 (...).toFixed(1)*1。触发条件：任一消费点若对 String 变体做 + 直接算术（求和/均值）→ 字符串拼接得错误结果（"0"+"5"="05"）；对 String 变体直接 .toFixed() 会抛 TypeError。当前未见已发作点，属契约脆弱：一侧改动即炸。责任归属=双侧协同。最小修复：后端把数值列统一为 BigDecimal/Integer（去掉以 String 存数字），前端相应移除 *1/parseFloat/toFixed 兜底强转。

### [J-P3] 统一 JSON 命名策略消除 snake/camel 分裂

- 证据锚点：`backend/src/main/java/com/nip/dto/vo/TheoryKnowledgeExamUserSelfVO.java:28-29`（置信度 0.85）

命名风格按端点分裂：考试模块的 native-query/自测 DTO 发 snake_case JSON 键——FindAllExamByIdDto.java:24-28（start_time/end_time/create_user_id/create_time）、TheoryKnowledgeExamUserSelfVO.java:29（start_time）、TheoryKnowledgeExamEntity.java:45-49（@ColumnResult 别名 snake）；而全站其余实体/VO 发 camelCase（如 EnteringExerciseEntity.java:35 startTime）。前端遂按端点分别硬编码：test/grade/Index.vue:30、test/list/Index.vue:46、startGrade/Index.vue:34、addTest.js:64 读 snake，其余读 camel。当前靠「逐端点记对」维持，一旦某侧规范化（如把 DTO 字段改驼峰）即静默 undefined——DM-J-P1-01（自测列表 startTime 恒空）就是该分裂已发作的实例。注：common/utils/CustomPhysicalNamingStrategy 只管库列名、不影响 JSON 键，勿混淆。责任归属=双侧协同。最小修复：选定 camelCase 为唯一 JSON 契约，后端把 snake DTO 字段改驼峰或加 @JsonProperty，前端同步；至少先消除离群读取点。属跨栈只能联合看到的契约脆弱（单看一侧都自洽）。

### [J-P3] 对齐前端硬编码字典与后端取值域

- 证据锚点：`frontend/src/views/manage/fixedMessage/details/Index.vue:20-22`（置信度 0.7）

枚举/字典全前端硬编码、无后端字典端点，多处与后端域漂移：(1) 报文类型 type：前端 fixedMessage/details/Index.vue:20-22 与组训 datagramZuXun/list/Index.vue:129-131 仅 0数码报/1字码报/2混合报，而后端 TelegramTrainEntity.java:36 域含 3.点划报 → type=3 报文前端无标签。(2) 题型：前端三套编码——questionBank/js/knowledgeTabel.js:67-71 id 为字符串 '1'..'5'、theNewTest.js:13-27 key 为数字 1/2/3、startTest.js:27-39 key 为字符串名 singleChoice，而后端 TheoryKnowledgeQuestionEntity.java:27 type 为 Integer → 字符串 id 与 Integer 靠 == 强转、=== 会失配。(3) 角色 isAdmin/isDefault：前端 role/Index.vue:55 text===0?'是':'否'、默认 useRoleForm.js:18 isAdmin:1，后端 RoleEntity.java:28-29 为 Integer、GradingRuleEntity.java:28 isDefault 注释「0，使，1，否」→ 反极性（0=是），isXxx 字段名误导。(4) isCable：后端 PostTelegraphKeyPatTrainVO.java:34,36 同字段两处注释自相矛盾（docstring 0固定/1随机 vs @Schema 0随机/1固定）。(5) 设备 trainType 前端 equipmentList/Index.vue:39-42 值 1/2/4（缺3），且 trainType 一名多义（设备 1/2/4 vs PostTelegraphKeyPatTrainVO:42 0个人/1考核）。触发：后端产出前端未覆盖的值时无标签/误判。责任归属=双侧协同。最小修复：后端提供字典端点或共享常量枚举，前端消除三套题型编码、补 type=3 与 trainType=3、统一 isCable/isDefault 极性注释。部分后端真源（设备 type/trainType 权威域）未定位到字典，标 [INFERENCE]。

### [J-P3] 统一 createTime 时间 wire 形态并建前端适配层

- 证据锚点：`backend/src/main/java/com/nip/entity/TelegramTrainEntity.java:54-55`（置信度 0.8）

同名 createTime 跨实体三种 wire 形态：epoch 毫秒串——TelegramTrainEntity.java:55、TelexPatTrainEntity.java:59、TheoryKnowledgeSwfEntity.java:40、TheoryKnowledgeTestUserEntity.java:58（new Date().getTime()+""）；空格格式串——CableEntity.java:44、TickerTapeTrainVo.java:52（@JsonFormat yyyy-MM-dd HH:mm:ss）；ISO-T 串——EnteringExerciseEntity.java:45/DeviceEntity.java:65 等无 @JsonFormat 的 LocalDateTime（Quarkus 默认 ISO-8601；实测 application.yml 无 quarkus.jackson.*、全仓无 ObjectMapperCustomizer）。前端每个视图各自匹配解析器：epoch 走 useList.js:86 createTime*1、NipTop.vue:126 parseInt、details/Index.vue:103 Number()；字符串走 addTest.js:64 moment(exam['start_time'])。触发：当前逐点靠巧合正确；一旦端点在 entity↔VO 间切换（wire 形态随之变）或跨域复用解析器（把 Number(createTime) 用到空格格式串→NaN，或把 moment(str) 用到 epoch 串→Invalid date）即静默失配。责任归属=双侧协同（长期）。最小修复：后端统一时间 wire 形态（推荐带偏移 ISO 或统一 epoch 毫秒数字），前端建单一时间适配层集中解析。
