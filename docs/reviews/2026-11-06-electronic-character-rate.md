# 个人岗位电子键码率单位核对

## 现场证据

当前个人成绩 af31c88b-ec2e-4dd4-a81a-ff4aef78f136：speedNumber=59，前端乘4显示236码/分；冻结规则rateUnit=FOUR_CHARACTER_GROUPS_PER_MINUTE、base=150、r=l=1，实际按59−150扣91分。用户本轮明确要求基准150码/分。原规则编辑器及后端只支持四码组/分，与用户业务口径不符；成绩页又未展示冻结基准和单位，导致误解。

同时只读核对保存的两页及模拟器当前原文：40组＋句号＋60组。第一段6375至5402，第二段6831至7614。第二段逐组等于第一页第41—100组报底。本次少10行分别来自第一页尾部6行、第二页尾部4行；第二页前60组不匹配该页报底，另有第一页1错码和1对串组。未发现同位置漏行兼计错码；继续遵守句号页界，不跨页补齐。

## 改动与兼容

- 电子键规则编辑器新增规则默认CHARACTERS_PER_MINUTE，明确基准和系数按码/分；旧规则打开时提示核对并确认单位再保存。
- ScoringRuleValidation.electronic接受明确的码/分与历史四码组/分单位，拒绝未知/缺失单位。个人及组训从冻结规则单位和原始字符数、采集时长计算计分码率。
- 保留既有speed/speedNumber的四码组存储契约；deductInfo新增speedCharacters（原始字符数直接计算，避免先取整组速再乘4的误差）、scoringSpeedNumber、scoringRateUnit。
- 两成绩页优先使用speedCharacters；扣分依据展示同单位的实际速率、冻结基准、加扣系数，旧150四码组/分明确显示相当于600码/分。
- 不修改已有训练快照和已保存成绩。当前已保存的旧规则须在规则编辑器确认码/分并保存，后续训练才采用150码/分。

跨栈核对：TelegramApi.js的getPostExamTrainDetails与electronKeyZuXun.js详情消费均保持原端点/信封；两个trainScore.js解析deductInfo、ruleContent后传给明细。ExamGradeRule.vue与GradingRuleService的type=3校验一起支持新单位。两种结算同步处理共用规则，未把仅一侧改成码/分。组训旧统计按既有命名字段提取，新增元数据不改变旧字段类型。

## 验证

- JDK21：ElectronicKeyClassificationTest、ScoringConsistencyTest、ScoringRuleValidationTest共37项通过。覆盖236码/分对150加86、等于150为0、149扣1、历史四码组规则继续按旧单位计算。
- 前端依据/分类/音频13项通过，包含真实模板渲染的两种单位和历史换算解释。
- 个人结算集成新增240码/分对150保存+90的断言，已编译；Docker不可用，未运行数据库集成与完整训练验收。
- ESLint无错误，两个成绩页保留各一条原有未使用导入警告。
