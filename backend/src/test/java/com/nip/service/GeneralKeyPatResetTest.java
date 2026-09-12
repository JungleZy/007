package com.nip.service;

import com.nip.dao.UserDao;
import com.nip.service.general.GeneralKeyPatService;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.key.GeneralKeyPatPageDao;
import com.nip.dao.general.key.GeneralKeyPatTrainMoreDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.dao.general.key.GeneralKeyPatUserValueDao;
import com.nip.dao.general.key.GeneralKeyPatUserValueResolverDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import com.nip.entity.simulation.key.GeneralKeyPatPageEntity;
import com.nip.entity.simulation.key.GeneralKeyPatTrainMoreEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserValueEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserValueResolverEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class GeneralKeyPatResetTest {
  @Inject GeneralKeyPatService service;
  @Inject UserDao userDao;
  @Inject GeneralKeyPatDao trainDao;
  @Inject GeneralKeyPatPageDao pageDao;
  @Inject GeneralKeyPatUserDao userDaoByTrain;
  @Inject GeneralKeyPatUserValueDao valueDao;
  @Inject GeneralKeyPatUserValueResolverDao resolverDao;
  @Inject GeneralKeyPatTrainMoreDao moreDao;

  @Test
  void resetClearsOnlyCurrentUsersResultsAndKeepsGeneratedPages() {
    String token = "key-reset-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token);
    GeneralKeyPatEntity train = new GeneralKeyPatEntity();
    train.setTitle("reset-" + UUID.randomUUID());
    train.setCreateUser(user.getId());
    train.setStatus(1);
    train.setValidTime(42L);
    train = trainDao.saveAndFlush(train);

    GeneralKeyPatPageEntity page = new GeneralKeyPatPageEntity();
    page.setTrainId(train.getId());
    page.setPageNumber(1);
    page.setSort(0);
    page.setKey("[\"A\"]");
    page.setValue("[]");
    page.setTime("[]");
    page = pageDao.saveAndFlush(page);

    GeneralKeyPatUserEntity participant = new GeneralKeyPatUserEntity();
    participant.setTrainId(train.getId());
    participant.setUserId(user.getId());
    participant.setRole(0);
    participant.setIsFinish(1);
    participant.setAccuracy("88.00");
    participant.setSpeed("12");
    participant.setErrorNumber(3);
    participant.setScore(BigDecimal.TEN);
    participant.setDuration("42");
    participant = userDaoByTrain.saveAndFlush(participant);

    GeneralKeyPatUserValueEntity value = new GeneralKeyPatUserValueEntity();
    value.setTrainId(train.getId());
    value.setUserId(user.getId());
    value.setPageNumber(1);
    value.setSort(0);
    value.setKey("A");
    value.setValue("A");
    value.setTime("1");
    valueDao.saveAndFlush(value);

    GeneralKeyPatUserValueResolverEntity resolver = new GeneralKeyPatUserValueResolverEntity();
    resolver.setTrainId(train.getId());
    resolver.setUserId(user.getId());
    resolver.setPageNumber(1);
    resolver.setSort(0);
    resolver.setKey("A");
    resolver.setValue("A");
    resolver.setTime("1");
    resolverDao.saveAndFlush(resolver);

    GeneralKeyPatTrainMoreEntity more = new GeneralKeyPatTrainMoreEntity();
    more.setTrainId(train.getId());
    more.setUserId(user.getId());
    more.setPageNumber(1);
    more.setMoreGroup("[]");
    more.setMoreLine("[]");
    moreDao.saveAndFlush(more);

    service.reset(train.getId(), 0, token);

    GeneralKeyPatUserEntity reset = userDaoByTrain.findByUserIdAndTrainId(user.getId(), train.getId());
    assertEquals(0, reset.getIsFinish());
    assertEquals("0.00", reset.getAccuracy());
    assertEquals(0, reset.getErrorNumber());
    assertEquals(0, BigDecimal.ZERO.compareTo(reset.getScore()));
    assertNull(reset.getFinishTime());
    assertEquals(0, valueDao.count("trainId = ?1 and userId = ?2", train.getId(), user.getId()));
    assertEquals(0, resolverDao.count("trainId = ?1 and userId = ?2", train.getId(), user.getId()));
    assertEquals(0, moreDao.count("trainId = ?1 and userId = ?2", train.getId(), user.getId()));
    assertEquals(1, pageDao.count("trainId", train.getId()));
  }
}
