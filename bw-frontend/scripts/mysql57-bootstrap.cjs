// 32位兼容包仅转换其独立的初始化资产，不改写 MySQL8 快照或迁移原文件。
function mysql57Sql(sql) {
    return sql.replaceAll('utf8mb4_0900_ai_ci', 'utf8mb4_general_ci')
        .replaceAll("'capture_pages','longtext NOT NULL DEFAULT (''{}'')'", "'capture_pages','longtext NOT NULL'");
}
module.exports = {mysql57Sql};
