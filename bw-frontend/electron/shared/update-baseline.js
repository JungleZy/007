/**
 * 运行时基线。
 *
 * 只从安装目录里的出厂程序读取，不随升级包更新：升级包若能改基线，
 * 「运行时变更必须走完整安装包」的约束就失效了。
 *
 * 在更换 Electron、JRE、MySQL 或启动引导本身时加一，并发完整安装包。
 */
const RUNTIME_BASELINE = 1;

module.exports = {RUNTIME_BASELINE};
