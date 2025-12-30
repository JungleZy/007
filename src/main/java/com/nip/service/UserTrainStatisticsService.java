package com.nip.service;

import com.nip.dao.PostEnteringExerciseDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.dao.TickerTapeTrainDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.dao.general.telex.GeneralTelexPatUserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dto.vo.HandKeyRecentTrainVO;
import com.nip.dto.vo.UserTrainDurationStatVO;
import com.nip.entity.PostEnteringExerciseEntity;
import com.nip.entity.PostTelexPatTrainEntity;
import com.nip.entity.PostTickerTapeTrainEntity;
import com.nip.entity.TickerTapeTrainEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatUserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class UserTrainStatisticsService {
  @Inject
  GeneralTickerPatTrainUserDao tickerUserDao;
  @Inject
  GeneralKeyPatUserDao keyUserDao;
  @Inject
  TickerTapeTrainDao tickerDao;
  @Inject
  PostTickerTapeTrainDao postTickerDao;
  @Inject
  PostTelexPatTrainDao postTelexDao;
  @Inject
  PostEnteringExerciseDao postEnteringExerciseDao;
  @Inject
  GeneralTelexPatUserDao generalTelexUserDao;

  public UserTrainDurationStatVO getUserTrainDurationStat(String userId) {
    return getUserTrainDurationStat(userId, null, null);
  }

  public UserTrainDurationStatVO getUserTrainDurationStat(String userId, String startTime, String endTime) {
    LocalDateTime start = parseTime(startTime);
    LocalDateTime end = parseTime(endTime);
    int handKey = sumHandKey(userId, start, end);
    int electronicKey = sumElectronicKey(userId, start, end);
    int receive = sumReceive(userId, start, end);
    int datagram = sumPostTelex(userId, 1, start, end);
    int telex = sumPostTelex(userId, 0, start, end) + sumGeneralTelex(userId, start, end);
    int pinyin = sumEnteringExercise(userId, 0, start, end);
    int wubi = sumEnteringExercise(userId, 2, start, end) + sumEnteringExercise(userId, 3, start, end);
    int english = sumEnteringExercise(userId, 4, start, end);
    return new UserTrainDurationStatVO()
        .setHandKeyDuration(handKey)
        .setElectronicKeyDuration(electronicKey)
        .setReceiveDuration(receive)
        .setDatagramDuration(datagram)
        .setTelexDuration(telex)
        .setPinyinDuration(pinyin)
        .setWubiDuration(wubi)
        .setEnglishDuration(english);
  }

  private LocalDateTime parseTime(String text) {
    if (text == null || text.isBlank())
      return null;
    try {
      return LocalDateTime.parse(text);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    } catch (DateTimeParseException ignored) {
    }
    return null;
  }

  private int sumHandKey(String userId, LocalDateTime start, LocalDateTime end) {
    List<GeneralTickerPatTrainUserEntity> list = tickerUserDao.find("userId = ?1 and role = 0", userId).list();
    long total = 0;
    for (GeneralTickerPatTrainUserEntity e : list) {
      if (Objects.equals(e.getIsFinish(), 1) && e.getCreateTime() != null && e.getFinishTime() != null) {
        if (within(e.getFinishTime(), start, end)) {
          total += Duration.between(e.getCreateTime(), e.getFinishTime()).getSeconds();
        }
      }
    }
    return (int) total;
  }

  private int sumElectronicKey(String userId, LocalDateTime start, LocalDateTime end) {
    List<GeneralKeyPatUserEntity> list = keyUserDao.find("userId = ?1 and role = 0 and isFinish = 1", userId).list();
    int total = 0;
    for (GeneralKeyPatUserEntity e : list) {
      try {
        if (within(e.getFinishTime(), start, end)) {
          total += Integer.parseInt(Objects.toString(e.getDuration(), "0"));
        }
      } catch (Exception ignored) {
      }
    }
    return total;
  }

  private int sumReceive(String userId, LocalDateTime start, LocalDateTime end) {
    List<TickerTapeTrainEntity> baseList = tickerDao.find("userId = ?1 and status = 3", userId).list();
    int total = 0;
    for (TickerTapeTrainEntity e : baseList) {
      try {
        if (within(e.getEndTime(), start, end)) {
          total += Integer.parseInt(Objects.toString(e.getValidTime(), "0"));
        }
      } catch (Exception ignored) {
      }
    }
    List<PostTickerTapeTrainEntity> postList = postTickerDao.find("userId = ?1 and status >= 2", userId).list();
    for (PostTickerTapeTrainEntity e : postList) {
      try {
        if (within(e.getEndTime(), start, end)) {
          total += Integer.parseInt(Objects.toString(e.getValidTime(), "0"));
        }
      } catch (Exception ignored) {
      }
    }
    return total;
  }

  private int sumPostTelex(String userId, int trainType, LocalDateTime start, LocalDateTime end) {
    List<PostTelexPatTrainEntity> list = postTelexDao
        .find("createUser = ?1 and trainType = ?2 and status = 3", userId, trainType).list();
    int total = 0;
    for (PostTelexPatTrainEntity e : list) {
      if (within(e.getEndTime(), start, end)) {
        total += Objects.requireNonNullElse(e.getValidTime(), 0);
      }
    }
    return total;
  }

  private int sumGeneralTelex(String userId, LocalDateTime start, LocalDateTime end) {
    List<GeneralTelexPatUserEntity> list = generalTelexUserDao.find("userId = ?1 and role = 0 and isFinish = 1", userId)
        .list();
    int total = 0;
    for (GeneralTelexPatUserEntity e : list) {
      if (within(e.getFinishTime(), start, end)) {
        total += Objects.requireNonNullElse(e.getValidTime(), 0);
      }
    }
    return total;
  }

  private int sumEnteringExercise(String userId, int type, LocalDateTime start, LocalDateTime end) {
    List<PostEnteringExerciseEntity> list = postEnteringExerciseDao
        .find("createUserId = ?1 and type = ?2 and status = 2", userId, type).list();
    int total = 0;
    for (PostEnteringExerciseEntity e : list) {
      if (within(e.getEndTime(), start, end)) {
        total += Objects.requireNonNullElse(e.getDuration(), 0);
      }
    }
    return total;
  }

  private boolean within(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
    if (time == null)
      return false;
    if (start != null && time.isBefore(start))
      return false;
    if (end != null && time.isAfter(end))
      return false;
    return true;
  }

  public List<HandKeyRecentTrainVO> getRecentHandKeyTrains(String userId) {
    List<GeneralTickerPatTrainUserEntity> list = tickerUserDao
        .find("userId = ?1 and role = 0 and isFinish = 1 ORDER BY finishTime DESC LIMIT 10", userId)
        .list();
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return list.stream().map(e -> {
      int trainTime = 0;
      if (e.getCreateTime() != null && e.getFinishTime() != null) {
        trainTime = (int) Duration.between(e.getCreateTime(), e.getFinishTime()).getSeconds();
      }
      return new HandKeyRecentTrainVO()
          .setStartTime(e.getFinishTime() == null ? e.getCreateTime().format(fmt) : e.getFinishTime().format(fmt))
          .setTrainTime(trainTime)
          .setScore(e.getScore())
          .setSpeed(e.getSpeed());
    }).toList();
  }

  public List<HandKeyRecentTrainVO> getRecentElectronicKeyTrains(String userId) {
    List<GeneralKeyPatUserEntity> list = keyUserDao
        .find("userId = ?1 and role = 0 and isFinish = 1 ORDER BY finishTime DESC LIMIT 10", userId)
        .list();
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return list.stream().map(e -> {
      int trainTime = 0;
      if (e.getCreateTime() != null && e.getFinishTime() != null) {
        trainTime = (int) Duration.between(e.getCreateTime(), e.getFinishTime()).getSeconds();
      }
      return new HandKeyRecentTrainVO()
          .setStartTime(e.getCreateTime() == null ? null : e.getCreateTime().format(fmt))
          .setTrainTime(trainTime)
          .setScore(e.getScore())
          .setSpeed(e.getSpeed());
    }).toList();
  }
}
