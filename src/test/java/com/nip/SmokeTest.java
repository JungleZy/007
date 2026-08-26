package com.nip;

import com.nip.dao.TestPaperDao;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class SmokeTest {
  @Inject TestPaperDao testPaperDao;

  @Test
  void schemaBoots() {
    assertDoesNotThrow(() -> testPaperDao.count());
  }
}
