package com.nip.testsupport;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

public class MySqlResource implements QuarkusTestResourceLifecycleManager {
  static {
    // Docker Engine 29+ 最低支持 API 1.44；Testcontainers 1.19.3 未配置时硬编码回退到 1.32，
    // 会被 daemon 拒绝（"client version 1.32 is too old"）。升级 testcontainers 后可删。
    System.setProperty("api.version", "1.44");
  }

  private static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0");

  @Override
  public Map<String, String> start() {
    DB.start();
    return Map.of(
        "quarkus.datasource.jdbc.url",
        DB.getJdbcUrl() + "?rewriteBatchedStatements=true&characterEncoding=utf8&allowPublicKeyRetrieval=true",
        "quarkus.datasource.username", DB.getUsername(),
        "quarkus.datasource.password", DB.getPassword());
  }

  @Override
  public void stop() {
    DB.stop();
  }
}
