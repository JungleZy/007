package com.nip.service;

import com.nip.dao.PostTelegramTrainFloorContentDao;
import com.nip.entity.PostTelegramTrainFloorContentEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Slf4j
public class AsyncSavePostTelegramTrainService {
  private final PostTelegramTrainFloorContentDao floorContentDao;

  @Inject
  public AsyncSavePostTelegramTrainService(PostTelegramTrainFloorContentDao floorContentDao) {
    this.floorContentDao = floorContentDao;
  }

  @Transactional
  public CompletableFuture<List<PostTelegramTrainFloorContentEntity>> savePostTelegramTrain(List<PostTelegramTrainFloorContentEntity> list) {
    log.info("线程名称：" + Thread.currentThread().getName());
    List<PostTelegramTrainFloorContentEntity> save = floorContentDao.save(list);
    return CompletableFuture.completedFuture(save);
  }


  public CompletableFuture<List<PostTelegramTrainFloorContentEntity>> selectPostTelegramTrainFloorContent(String id, Integer number, Integer pageSize) {
    log.info("线程名称：" + Thread.currentThread().getName());
    List<PostTelegramTrainFloorContentEntity> contentEntities = floorContentDao.findAllByTrainIdOrderByFloorNumberAscSortAscLimit(id, number, pageSize);
    return CompletableFuture.completedFuture(contentEntities);
  }
}
