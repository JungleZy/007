/**
 * 升级包验签公钥与运行时基线。
 *
 * 这两项只从安装目录里的出厂程序读取，不随升级包更新：升级包若能替换公钥或基线，
 * 签名与「运行时变更必须走完整安装包」的约束就都失效了。
 *
 * PUBLIC_KEYS 允许并列多把，用于轮换：新安装包同时信任新旧公钥，旧私钥停用后再移除。
 * RUNTIME_BASELINE 在更换 Electron、JRE、MySQL 或启动引导本身时加一，并发完整安装包。
 */
const PUBLIC_KEYS = [
`-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAwJiCfz1Ig19unadQ/1FcIkxRvf5nGShRu/T+EvtKhlg=
-----END PUBLIC KEY-----
`
];

const RUNTIME_BASELINE = 1;

module.exports = {PUBLIC_KEYS, RUNTIME_BASELINE};
