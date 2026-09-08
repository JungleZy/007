package com.nip.common;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * LifecycleApplication
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-27 10:39
 */
@ApplicationScoped
public class LifecycleApplication {
  private static final Logger LOG = Logger.getLogger("Application");

  /** 存储引擎自检 SQL：列出当前库里所有 MyISAM 表 */
  private static final String MYISAM_TABLES_SQL =
      "select table_name from information_schema.tables"
          + " where table_schema = database() and engine = 'MyISAM' order by table_name";

  /** 迁移脚本路径（相对仓库根，2026-09-08 起全仓文档收口到根 `docs/`），出现在自检失败信息里，保证报错可操作 */
  private static final String ENGINE_MIGRATION_SQL =
      "backend/database/migrations/2026-08-26-02-engine-innodb.sql";

  @Inject
  DataSource dataSource;

  void onStart(@Observes StartupEvent event) {
    LOG.info("The Application Is Starting...");
    try {
      InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("banner.txt");
      if (is != null) {
        String banner = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        String version = ConfigProvider.getConfig().getOptionalValue("version", String.class).orElse("");
        banner = banner.replace("${version}", version);
        LOG.info("\n" + banner);
      }
    } catch (Exception e) {
      LOG.warn("banner 打印失败", e);
    }
    checkStorageEngine();
  }

  /**
   * P2-2.2 启动期存储引擎自检。
   * MyISAM 不支持事务，@Transactional 的回滚在这些表上是空操作，结算类「先删后插」一旦中断即永久丢数据。
   * 生产（LaunchMode.NORMAL）下直接抛异常阻断启动，与 %prod 的 generation=validate fail-fast 语义一致；
   * dev/test 下只告警，不影响 DevServices（实体建表全是 InnoDB）。
   */
  private void checkStorageEngine() {
    List<String> myisamTables = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(MYISAM_TABLES_SQL);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        myisamTables.add(rs.getString(1));
      }
    } catch (SQLException e) {
      LOG.warn("存储引擎自检查询失败，本次跳过检查", e);
      return;
    }
    if (myisamTables.isEmpty()) {
      return;
    }
    String message = "检测到 " + myisamTables.size() + " 张 MyISAM 表，事务无法回滚，请先执行 "
        + ENGINE_MIGRATION_SQL + "：" + String.join(", ", myisamTables);
    if (LaunchMode.current().isDevOrTest()) {
      LOG.warn(message);
      return;
    }
    throw new IllegalStateException(message);
  }

  void onStop(@Observes ShutdownEvent event) {
    LOG.info("The Application Is Stopping...");
  }
}
