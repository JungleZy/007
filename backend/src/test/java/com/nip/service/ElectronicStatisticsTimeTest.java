package com.nip.service;

import com.nip.dao.TelegraphKeyPatSyntheticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.TelegraphKeyPatSyntheticalDto;
import com.nip.dto.TelegraphKeyPatTrainDto;
import com.nip.dto.vo.TelegraphKeyTrainStatisticalVO;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ElectronicStatisticsTimeTest {
  private static final String SOURCE = "[{\"text\":\"1234\",\"value\":\"\",\"type\":true,\"isFocus\":false}]";
  private static final String ANSWER = "[{\"text\":\"1234\",\"value\":\"1234\",\"isFocus\":true}]";

  @Inject UserDao users;
  @Inject TelegraphKeyPatSyntheticalDao trains;
  @Inject TelegraphKeyPatSyntheticalService comprehensive;
  @Inject TelegraphKeyPatTrainService basic;
  @Inject TelegraphKeyTrainStatisticalService statistics;

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = 0)
  void completedHistoryKeepsLegacyMillisecondsAndCountsAlongsideAuthoritativeTime(Integer protocol) {
    UserEntity owner = actor();
    TelegraphKeyPatSyntheticalEntity legacy = trains.save(new TelegraphKeyPatSyntheticalEntity()
        .setCreateUserId(owner.getId()).setStatus(3).setProtocolVersion(protocol)
        .setDuration("60000").setAccumulatedActiveMillis(999999L).setSpeed("60").setAccuracy(73d));
    trains.save(new TelegraphKeyPatSyntheticalEntity().setCreateUserId(owner.getId())
        .setStatus(1).setDuration("999999").setSpeed("999"));

    String id = comprehensive.save(owner.getToken(), new TelegraphKeyPatSyntheticalDto()
        .setTitle("millisecond-regression").setMessageType(0).setContent(SOURCE)).getId();
    comprehensive.begin(owner.getToken(), id);
    TelegraphKeyPatSyntheticalDto finish = new TelegraphKeyPatSyntheticalDto().setId(id).setContent(ANSWER);
    comprehensive.stop(owner.getToken(), finish);
    // A paused capture has no running wall clock: settlement must retain its exact 2250ms.
    QuarkusTransaction.requiringNew().run(() -> trains.findById(id)
        .setAccumulatedActiveMillis(2250L).setDuration("2").setSpeed("106.67"));
    comprehensive.finish(owner.getToken(), finish);
    comprehensive.finish(owner.getToken(), finish);

    assertStatistics(owner, 2, "62250", 2, "83.34");
    TelegraphKeyPatSyntheticalEntity history = trains.findById(legacy.getId());
    assertEquals("60000", history.getDuration());
    assertEquals(protocol, history.getProtocolVersion());
    assertEquals("60", history.getSpeed());
    assertEquals(73d, history.getAccuracy());
    assertEquals("2", trains.findById(id).getDuration());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void basicAndSingleCharacterRatesUseMillisecondsAndClearAllStatistics(int type) {
    UserEntity owner = actor();
    TelegraphKeyPatTrainDto capture = new TelegraphKeyPatTrainDto();
    capture.setType(type);
    capture.setTotalTime(60000);
    capture.setTotalNum(60);
    capture.setTotalError(2);
    basic.save(owner.getToken(), capture);
    assertStatistics(owner, type, "60000", 1, "60");

    capture.setId(basic.findByUserIdAndType(owner.getToken(), type).getId());
    capture.setTotalTime(120000);
    capture.setTotalNum(120);
    basic.save(owner.getToken(), capture);
    assertStatistics(owner, type, "120000", 2, "60");

    basic.clear(owner.getToken(), type);
    assertStatistics(owner, type, "0", 0, "0");
    var cleared = basic.findByUserIdAndType(owner.getToken(), type);
    assertEquals(0, cleared.getTotalTime());
    assertEquals(0, cleared.getTotalNum());
    assertEquals(0, cleared.getTotalError());
  }

  @Test
  void clearingWithoutExistingStatisticsStillExposesZeroTotals() {
    UserEntity owner = actor();
    basic.clear(owner.getToken(), 0);
    assertStatistics(owner, 0, "0", 0, "0");
  }

  private void assertStatistics(UserEntity owner, int type, String millis, int count, String speed) {
    TelegraphKeyTrainStatisticalVO result = statistics.statisticalPage(owner.getToken()).stream()
        .filter(row -> row.getType() == type).findFirst().orElseThrow();
    assertEquals(0, new BigDecimal(millis).compareTo(new BigDecimal(result.getTotalTime())));
    assertEquals(count, result.getTotalCount());
    assertEquals(0, new BigDecimal(speed).compareTo(result.getAvgSpeed()));
  }

  private UserEntity actor() {
    return Fixtures.user(users, "electronic-statistics-" + UUID.randomUUID());
  }
}
