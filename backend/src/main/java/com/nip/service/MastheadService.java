package com.nip.service;

import com.nip.common.exception.ForbiddenException;
import com.nip.dao.MastheadDao;
import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.entity.MastheadEntity;
import com.nip.entity.PostTickerTapeTrainEntity;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.service.simulation.SimulationRoomAccess;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.Objects;

@ApplicationScoped
public class MastheadService {
  private final MastheadDao mastheadDao;
  private final PostTickerTapeTrainDao trainDao;
  private final UserService userService;
  private final TrainWriteAccess trainWriteAccess;
  @Inject SimulationRoomAccess roomAccess;
  @Inject SimulationRouterRoomDao roomDao;

  @Inject
  public MastheadService(MastheadDao mastheadDao, PostTickerTapeTrainDao trainDao,
      UserService userService, TrainWriteAccess trainWriteAccess) {
    this.mastheadDao = mastheadDao;
    this.trainDao = trainDao;
    this.userService = userService;
    this.trainWriteAccess = trainWriteAccess;
  }

  @Transactional
  public MastheadEntity save(String token, MastheadEntity entity) {
    String actor = userService.getUserByToken(token).getId();
    if (entity == null || entity.getTrainId() == null || entity.getTrainId().isBlank()) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    PostTickerTapeTrainEntity train = trainDao.findByIdOptional(entity.getTrainId(), LockModeType.PESSIMISTIC_WRITE)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(actor, train.getUserId(), "收报训练报头 " + entity.getTrainId());
    MastheadEntity byId = entity.getId() == null || entity.getId().isBlank() ? null
        : mastheadDao.findByIdOptional(entity.getId(), LockModeType.PESSIMISTIC_WRITE)
            .orElseThrow(() -> new IllegalArgumentException("报头不存在"));
    if (byId != null && !Objects.equals(byId.getTrainId(), entity.getTrainId())) {
      throw new ForbiddenException("报头编号与训练不匹配");
    }
    MastheadEntity byTrain = mastheadDao.find("trainId", entity.getTrainId())
        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    if (byTrain != null && byId != null && !Objects.equals(byTrain.getId(), byId.getId())) {
      throw new ForbiddenException("该训练已有其他报头");
    }
    MastheadEntity target = byTrain == null ? new MastheadEntity() : byTrain;
    target.setTrainId(entity.getTrainId());
    target.setContent(entity.getContent());
    return mastheadDao.save(target);
  }

  public MastheadEntity findByTrainId(String token, String trainId) {
    PostTickerTapeTrainEntity train = trainDao.findByIdOptional(trainId)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    String actor = userService.getUserByToken(token).getId();
    trainWriteAccess.requireTrainOwner(actor, train.getUserId(), "收报训练报头 " + trainId);
    return mastheadDao.findByTrainId(trainId);
  }

  public MastheadEntity findByRoomId(HttpServerRequest request, Integer roomId) {
    requireRoomId(roomId);
    roomAccess.requireMember(request, roomId);
    return mastheadDao.findByTrainId(roomId.toString());
  }

  @Transactional
  public MastheadEntity saveRoom(HttpServerRequest request, Integer roomId, String content) {
    requireRoomId(roomId);
    roomDao.lockRoom(roomId);
    roomAccess.requireControl(request, roomId);
    // 房间历史报头以十进制房间编号存储；岗位收报训练使用 UUID。
    String trainId = roomId.toString();
    MastheadEntity target = mastheadDao.find("trainId", trainId)
        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    if (target == null) target = new MastheadEntity();
    target.setTrainId(trainId);
    target.setContent(content);
    return mastheadDao.save(target);
  }

  private static void requireRoomId(Integer roomId) {
    if (roomId == null || roomId <= 0) throw new IllegalArgumentException("房间编号不正确");
  }
}
