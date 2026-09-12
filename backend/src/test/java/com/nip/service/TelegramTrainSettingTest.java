package com.nip.service;

import com.nip.dao.TelegramTrainDao;
import com.nip.dto.TelegramTrainDto;
import com.nip.entity.TelegramTrainEntity;
import com.nip.dao.TelegramTrainSettingDao;
import com.nip.entity.TelegramTrainSettingEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class TelegramTrainSettingTest {
  @Inject TelegramTrainService service;
  @Inject TelegramTrainSettingDao dao;
  @Inject TelegramTrainDao trainDao;

  @Test
  @TestTransaction
  void startingAgainPreservesSavedMillisecondBoundaries() {
    TelegramTrainEntity train = new TelegramTrainEntity();
    train.setType(0);
    train.setStatus(0);
    trainDao.saveAndFlush(train);
    TelegramTrainEntity settings = new TelegramTrainEntity();
    settings.setId(train.getId());
    settings.setRateDotMinMs(40);
    settings.setRateDotMaxMs(80);
    settings.setRateLineMinMs(81);
    settings.setRateLineMaxMs(250);
    settings.setRateIntervalMinMs(80);
    settings.setRateIntervalMaxMs(330);
    settings.setBigIntervalMinMs(330);
    settings.setBigIntervalMaxMs(500);
    TelegramTrainDto request = new TelegramTrainDto();
    request.setTrain(settings);
    service.controlTelegramTrain(0, request);
    trainDao.flush();
    trainDao.getEntityManager().clear();
    TelegramTrainEntity loaded = trainDao.findById(train.getId());
    assertEquals(List.of(40, 80, 81, 250, 80, 330, 330, 500), boundaries(loaded));
    request.setTrain(loaded);
    service.controlTelegramTrain(0, request);
    trainDao.flush();
    trainDao.getEntityManager().clear();
    assertEquals(List.of(40, 80, 81, 250, 80, 330, 330, 500), boundaries(trainDao.findById(train.getId())));
  }

  private static List<Integer> boundaries(TelegramTrainEntity train) {
    return List.of(train.getRateDotMinMs(), train.getRateDotMaxMs(), train.getRateLineMinMs(),
        train.getRateLineMaxMs(), train.getRateIntervalMinMs(), train.getRateIntervalMaxMs(),
        train.getBigIntervalMinMs(), train.getBigIntervalMaxMs());
  }

  @Test
  @TestTransaction
  void malformedLastRowPreservesExistingSettings() {
    assertRejectedWithoutDataLoss(rows -> rows.getLast().setValue("{broken"));
  }

  @Test
  @TestTransaction
  void missingAbnormalIntervalPreservesExistingSettings() {
    assertRejectedWithoutDataLoss(rows -> rows.removeIf(row -> row.getKey().equals("1")
        && row.getValue().contains("\"type\":0")));
  }

  @Test
  @TestTransaction
  void noPositiveIntervalPreservesExistingSettings() {
    assertRejectedWithoutDataLoss(rows -> rows.removeIf(row -> row.getValue().contains("\"type\":1")));
  }

  @Test
  @TestTransaction
  void nonFiniteBoundaryPreservesExistingSettings() {
    assertRejectedWithoutDataLoss(rows -> rows.getLast().setValue(
        "{\"type\":1,\"name\":\"正常\",\"msg\":\"继续练习\",\"min\":\"80\",\"max\":\"Infinity\"}"));
  }

  @Test
  @TestTransaction
  void validReplacementKeepsFractionalBoundsAndAllGrades() {
    dao.deleteAll();
    dao.saveAndFlush(settings("80"));
    List<TelegramTrainSettingEntity> replacement = settings("80.5");
    service.saveSetting(replacement);
    dao.flush();
    dao.getEntityManager().clear();
    assertEquals(values(replacement), values(service.getSetting().getData()));
    service.saveSetting(service.getSetting().getData());
    dao.flush();
    dao.getEntityManager().clear();
    assertEquals(values(replacement), values(service.getSetting().getData()));
  }

  private void assertRejectedWithoutDataLoss(Consumer<List<TelegramTrainSettingEntity>> corrupt) {
    dao.deleteAll();
    dao.saveAndFlush(settings("80"));
    dao.getEntityManager().clear();
    Map<String, String> before = dao.listAll().stream()
        .collect(Collectors.toMap(TelegramTrainSettingEntity::getId, TelegramTrainSettingEntity::getValue));
    List<TelegramTrainSettingEntity> invalid = settings("80.5");
    corrupt.accept(invalid);
    assertThrows(IllegalArgumentException.class, () -> service.saveSetting(invalid));
    dao.getEntityManager().clear();
    assertEquals(before, dao.listAll().stream()
        .collect(Collectors.toMap(TelegramTrainSettingEntity::getId, TelegramTrainSettingEntity::getValue)));
  }

  private static Map<String, String> values(List<TelegramTrainSettingEntity> rows) {
    return rows.stream().collect(Collectors.toMap(row -> row.getKey() + ":" + row.getValue(),
        TelegramTrainSettingEntity::getValue));
  }

  private static List<TelegramTrainSettingEntity> settings(String max) {
    List<TelegramTrainSettingEntity> rows = new ArrayList<>();
    for (String key : List.of("0", "1")) {
      rows.add(new TelegramTrainSettingEntity(null, 0, key,
          "{\"type\":0,\"name\":\"异常\",\"msg\":\"继续练习\",\"min\":\"<10\",\"max\":\">" + max + "\"}"));
      rows.add(new TelegramTrainSettingEntity(null, 0, key,
          "{\"type\":1,\"name\":\"正常\",\"msg\":\"继续练习\",\"min\":\"10\",\"max\":\"" + max + "\"}"));
    }
    return rows;
  }
}
