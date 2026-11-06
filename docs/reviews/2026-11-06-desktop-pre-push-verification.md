# 2026-11-06 桌面整合改动提交前验证

## 提交范围

提交本地累计的桌面整合运行、Windows ia32/MySQL5.7支持、Win7与麒麟打包脚本、CH340准备工具、WebGL和串口修复、排障工具、测试及文档。安装包、下载/组装的运行组件、设备仿真资源和现场诊断输出不进入本次提交。

新增忽略规则覆盖 `bw-frontend/frontend/public/equipment/`、`J210-742/`、本地缓存、整合运行目录、新增平台MySQL客户端目录及根目录诊断输出。此前已跟踪的Windows x64客户端保持现状；忽略规则不会自动移除Git已有文件。需要这些资源进行构建时按打包清单恢复。

## 验证环境与隔离方式

Windows JDK21与WSL Ubuntu Docker29.1.3。原工作目录执行 `mvnw.cmd -B clean verify` 时，正在运行的开发后端锁定 `target/quarkus-template-dev.jar`，clean无法删除；未停止开发服务。将当前后端源码、Maven配置和数据库资产复制到临时后端副本，并按pom的相对目录规则带入 `bw-frontend/frontend/src/common/utils/entering-codebook.json`。

测试进程通过临时、仅绑定127.0.0.1的Docker socket转发访问WSL Docker，使用Testcontainers的MySQL8.0与Ryuk；设置 `DOCKER_HOST`、`TESTCONTAINERS_HOST_OVERRIDE`、`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`，不修改仓库生产配置或Docker daemon配置。测试MySQL由容器提供，不连接开发库或客户数据库。

## 本次发现并处理的问题

- 第一轮临时副本缺少pom从前端读取的码表，导致5个岗前协议测试失败；补齐副本资源后这些测试通过，无需修改生产代码。
- 既有 `GeneralCaptureContractTest.electronicSubmittedEmptySecondPageStillReturnsReferenceBeforeAndAfterFinish` 有两处错误预期：直接比较Java Long生成的JsonNode与解析JSON生成的IntNode，以及将报底的空拍发字符串 `"[]"` 当成null。现按实际JSON契约比较采集区间，精确断言空拍发字符串。保留报底一致性、终态前后区间一致性和原始答案不被伪造的全部检查。
- 上述修正后，`GeneralCaptureContractTest` 21项和 `DatabaseTransferServiceTest` 7项定向测试全部通过。

## 已完成检查

- `bw-frontend/frontend/`：`npm test`，167项通过。
- `bw-frontend/`：`node --test electron/test/*.test.cjs`，36项通过。
- `bw-frontend/`：`python scripts/test-kylin-deb.py`，4项通过。
- JDK21、完整临时后端副本及WSL Docker：`mvnw.cmd -B clean verify`，577项测试，0失败、0错误、0跳过，最终 `BUILD SUCCESS`；总耗时4分08秒。
- 待提交文件检查：排除生成物后均为文本，未发现新增二进制、超过1MiB的单文件或常见私钥/token标记；默认管理员密码为用户指定的公开初始化值。

此验证针对本轮提交源码；不会追溯改变此前安装包构建时“未执行全量Docker测试”的历史记录，也不代替Win7/麒麟实机、图形驱动或串口设备验收。

检查期间新增的手键组训分页改动（`handKeyTrain.js`、对应`student.vue`、`groupHandPagination.test.mjs`）属于本轮开始后的并行工作，保留在工作区，不包含在此次桌面部署提交中。
