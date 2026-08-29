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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手动运行的实体 schema 导出器（迁移演练用）。
 *
 * <p>类名刻意不以 {@code Test} 结尾，Surefire 默认排除，仅在显式指定时运行：
 * <pre>./mvnw -B -Dtest=EntitySchemaSnapshotRehearsal test</pre>
 *
 * <p>%test profile 使用 {@code generation: drop-and-create} + DevServices（mysql:8.0），
 * 因此连接的库中已由 Hibernate 实体 DDL 建好全部实体表。本类把 {@code information_schema.columns}
 * 中的规范化表/列元数据导出为 TSV，作为迁移演练脚本比对的权威期望（validate 的等价参照）。
 *
 * <p>只写表/列结构元数据，绝不写业务数据行或凭据。
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

    assertTrue(rows > 0, "entity schema export produced no rows");
    assertTrue(Files.exists(output), "entity-schema.tsv was not written");
  }
}
