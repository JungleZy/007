package com.nip.service;

import com.nip.common.exception.ForbiddenException;
import com.nip.dao.MastheadDao;
import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.entity.MastheadEntity;
import com.nip.entity.PostTickerTapeTrainEntity;
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
}
