package com.nip.service;

import com.nip.dao.UserDao;
import com.nip.dao.general.telex.GeneralTelexPatUserDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatUserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GeneralTelexCollationTest {
  @Inject DataSource dataSource;
  @Inject UserDao users;
  @Inject GeneralTelexPatUserDao members;

  @Test
  void rosterProjectionsJoinLegacyUserIdsWithDifferentCollations() throws Exception {
    String trainId = UUID.randomUUID().toString();
    UserEntity owner = Fixtures.user(users, "collation-" + UUID.randomUUID());
    owner.setUserName("Collation User");
    users.save(owner);
    try (Connection connection = dataSource.getConnection()) {
      String original = collation(connection, "general_telex_pat_user", "user_id");
      String userCollation = collation(connection, "t_user", "id");
      String different = userCollation.equals("utf8mb4_general_ci")
          ? "utf8mb4_0900_ai_ci" : "utf8mb4_general_ci";
      try (var statement = connection.createStatement()) {
        try {
          statement.execute("ALTER TABLE general_telex_pat_user MODIFY user_id VARCHAR(255) "
              + "CHARACTER SET utf8mb4 COLLATE " + different + " NULL");
          members.save(new GeneralTelexPatUserEntity()
              .setTrainId(trainId).setUserId(owner.getId()).setRole(0));
          QuarkusTransaction.requiringNew().run(() -> {
            var detailed = members.findByTrainIdToMap(trainId);
            assertEquals(1, detailed.size());
            assertEquals(owner.getId(), detailed.getFirst().getUserId());
            assertEquals("Collation User", detailed.getFirst().getUserName());
            var summary = members.findByTrainIdToMapSimple(trainId);
            assertEquals(1, summary.size());
            assertEquals(owner.getId(), summary.getFirst().getUserId());
            assertEquals("Collation User", summary.getFirst().getUserName());
          });
        } finally {
          try {
            QuarkusTransaction.requiringNew().run(() -> members.delete("trainId", trainId));
          } finally {
            statement.execute("ALTER TABLE general_telex_pat_user MODIFY user_id VARCHAR(255) "
                + "CHARACTER SET utf8mb4 COLLATE " + original + " NULL");
          }
        }
      }
    } finally {
      QuarkusTransaction.requiringNew().run(() -> users.deleteById(owner.getId()));
    }
  }

  private String collation(Connection connection, String table, String column) throws SQLException {
    try (var statement = connection.prepareStatement("SELECT COLLATION_NAME FROM information_schema.COLUMNS "
        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
      statement.setString(1, table);
      statement.setString(2, column);
      try (var result = statement.executeQuery()) {
        assertTrue(result.next(), "fixture column must exist");
        return result.getString(1);
      }
    }
  }
}
