package com.nip.rehearsal;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 迁移演练用的实体 schema **手动导出入口**，不是回归测试：它没有被测契约，也没有正确性 oracle。
 * 作用是把 Hibernate 依实体 DDL 建出的规范化表/列元数据转成 TSV，交给
 * {@code scripts/rehearse-migrations.sh} 与快照库做 validate 等价差分——判定权在那份差分
 * （必须为 0 字节），不在本类。消费方另见 {@code backend/database/rehearsal/} 各日期目录下的 README.md。
 *
 * <p>调用方式：
 * <pre>
 * JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test
 * bash scripts/rehearse-migrations.sh   # 自动取用 target/migration-rehearsal/entity-schema.tsv
 * </pre>
 *
 * <p>类名刻意不以 {@code Test} 结尾，Surefire 默认 include 不匹配，因此不进 CI、不计入 verify 门禁。
 * 不要改 Surefire include 把它纳入执行：它要 DevServices 起 mysql:8.0 并 drop-and-create 整库
 * （%test profile 的 {@code generation: drop-and-create}），耗时与语义都不属于门禁，
 * 把导出器升成门禁只会得到一条永真的断言。
 *
 * <p>只写表/列结构元数据，绝不写业务数据行或凭据。导出失败抛带上下文的异常（库名、落盘绝对路径），
 * 不用断言。
 */
@QuarkusTest
class EntitySchemaSnapshotRehearsal {

  @Inject
  DataSource dataSource;

  @Test
  void exportCanonicalSchema() throws Exception {
    Path output = Path.of("target/migration-rehearsal/entity-schema.tsv");
    Files.createDirectories(output.getParent());

    int rows = 0;
    String catalog = null;
    try (Connection c = dataSource.getConnection();
         PreparedStatement ps = c.prepareStatement("""
             select table_name, column_name, ordinal_position, data_type, is_nullable,
                    coalesce(column_default, '<NULL>')
             from information_schema.columns
             where table_schema = database()
             order by table_name, ordinal_position
             """);
         ResultSet rs = ps.executeQuery();
         BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {

      catalog = c.getCatalog();
      w.write("table_name\tcolumn_name\tordinal_position\tdata_type\tis_nullable\tcolumn_default");
      w.newLine();
      while (rs.next()) {
        w.write(rs.getString(1));
        w.write('\t');
        w.write(rs.getString(2));
        w.write('\t');
        w.write(Integer.toString(rs.getInt(3)));
        w.write('\t');
        w.write(rs.getString(4));
        w.write('\t');
        w.write(rs.getString(5));
        w.write('\t');
        w.write(rs.getString(6));
        w.newLine();
        rows++;
      }
    }

    if (rows == 0) {
      throw new IllegalStateException("实体 schema 导出为空：库 " + catalog
          + " 的 information_schema.columns 无可见列，DevServices 的 drop-and-create 可能未生效；"
          + "已落盘（仅表头）" + output.toAbsolutePath());
    }
  }
}
