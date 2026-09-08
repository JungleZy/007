package com.nip;

import com.nip.dao.TestPaperDao;


import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest

class SmokeTest {
  @Inject TestPaperDao testPaperDao;

  @Inject org.eclipse.microprofile.config.Config config;

  @Test
  void schemaBoots() {
    assertDoesNotThrow(() -> testPaperDao.count());
  }

  @Test
  void testProfileUsesDedicatedDevServiceDatabase() {
    String jdbcUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);
    assertTrue(jdbcUrl.contains("/project006_test"), jdbcUrl);
    assertFalse(jdbcUrl.contains("localhost:3306/project006"), jdbcUrl);
  }
}
