# SDD ledger — plan: docs/plans/2026-08-26-fix-plan.md

branch: fix/2026-08-26-p0 (base: main@2036ad5)
Task 0.1: complete (commits a0f0180..a18849d, review clean)
Task 0.1: minor (deferred): api.version=1.44 pin 反向牺牲旧 Docker 兼容；follow-up 升级 testcontainers ≥1.21 后删 static 块
Task 0.2: complete (commits a18849d..16090a8, review clean)
Task 1.1: complete (commits 16090a8..d0bf56c, review clean)
Task 1.2: fix round 1/5 (1 addressed, 0 open — knowledgeTestContents 归一; commits 9a26708..1fa3578)
Task 1.2: complete (commits d0bf56c..1fa3578, review clean)
Task 1.2: minor (deferred): listEquals 分支(:271-272)无专项回归测试（代码已修，仅覆盖缺口）
Task 1.3: complete (commits 1fa3578..7a7c126, review clean)
Task 1.3: minor (deferred): 编辑不存在菜单 id 时 findById null NPE（既有风险，删除前失败无数据丢失）
Task 1.4: complete (commits 7a7c126..bd551a5, review clean)
Task 1.4: note: #19 实际运行后果为"创建即 StaleObjectState 异常"而非"静默跨场删除"（Hibernate6 实测）；审计文档后果描述待勘误（并入 Task 1.11 docs 勘误）
Task 1.5: complete (commits bd551a5..5921151, review clean)
Task 1.6: fix round 1/5 (1 addressed, 0 open — resolveClient 会话身份校验; commits 16964f6..801b193)
Task 1.6: complete (commits 5921151..801b193, review clean)
Task 1.6: note: 出站统一 asyncRemote（防 basic 并发写崩溃）；入站 data Gson 二次序列化怪癖为既有缺陷未动（Phase 2 备选）
Task 1.7: complete (commits 801b193..fcfc902, review clean)
Task 1.7: note: P1-3 addRoom* get→new→put 覆盖竞态留 Phase 2 Task 2.1（评审确认合规）；kickOutOld 同 userId 误删场景属 1.6/2.5 范畴既存问题
Task 1.8: complete (commits fcfc902..d43eb4a, review clean)
Task 1.9: complete (commits d43eb4a..d14e62a, review clean)
Task 1.9: minor (deferred): EnteringExerciseService.finish 对不存在 id 会 persist 全空新行（convertOne(null) 返空实体+save 走 persist），:109 notNull 形同死代码——真实机理经评审更正（非 NPE），归 Phase 7 findById 家族
Task 1.10: complete (commits d14e62a..42540c1, review clean)
Task 1.10: note: P1-43"自调用绕过@Transactional"被证伪并经评审独立确认（Quarkus ArC 子类拦截，自调用照样生效）——Phase 7 P2-A 家族按"注解是否存在"复核而非"自调用绕过"
Task 1.10: minor (deferred): MilitaryTermDataServiceTest:17-21 类注释残留已证伪的 Spring 代理机制描述，待更正
Task 1.10: minor (deferred): excelHanle:227 提前 return（首个新建父类型后丢弃剩余行）独立缺陷，入 backlog
Task 1.11: complete (commits 42540c1..1eab44f, review clean) — Phase 1 全绿：Tests run 23/0 失败
Wave2 BASE: 1eab44f — Phase 2/3/5/6 并行（域互斥），Phase 4 合流后执行
Phase 2: fix round 1/5 (1 addressed — quitRoom* computeIfPresent 原子移除; commit 79b251f) — 复审中
Phase 2: complete (commits 859ece7..79b251f, 6+1 提交) — 泄漏测试压出并修复 RoomModel CME（评审未收录缺陷）
Phase 3: complete (commits 5fb76f1..4d46edb, review clean) — minor (deferred): checkDotLineGap 四分支 r/l 方向需产品裁决后统一（10x 量级敏感）；TelegraphKey totalTime 单位按秒统一待前端确认
Phase 5: complete (commits 6635da5..f823a33, review clean) — 迁移演练 105 表/0 MyISAM + prod validate 真启动零错；注记：迁移01 非幂等（重跑 1060 可忽略）、旧基线 int→varchar 转换未演练、新发现 t_post_ticker_tape_train.is_start_sign 已并入迁移
Phase 6: fix round 1/5 (1 addressed — release 资产按架构重命名; commits e2008b9,90f1538, 复审 ADDRESSED)
Phase 6: complete (commits 1fc4dab..e2008b9, review clean) — 注记：ARM64 native 需首次 tag push 实测；release 产物名含架构后缀（下游布局变化）
Phase 2-6 遗留汇入 Phase 7 附加清单：P1-1 countScore parallelStream（ws评审建议升级）、P1-13 static ThreadLocalRandom、P2-4 全局锁、P2-8 REST delete 不关 session、GeneralPatTrainRoomUserDto.joinUser 裸 ArrayList
Phase 2 fix round 1: 复审 ADDRESSED（computeIfPresent 三处原子化）；观察项：addRoom* 取表→add 间隙的既有窗口非本批引入，记档
