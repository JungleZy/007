package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostEnteringExerciseEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PostEnteringExerciseDao extends BaseRepository<PostEnteringExerciseEntity, String> {

  @Transactional
  public void begin(String id, Integer status) {
    update("status = ?2, startTime = now() where id = ?1", id, status);
  }

  @Transactional
  public void finish(String id, Integer status, Double accuracy, Integer speed, Integer duration, String content,
                     Integer errorNum, Integer correctNum) {
    update(
      "status = ?2 ,endTime = now(), accuracy = ?3, speed = ?4, duration = ?5, content = ?6, errorNum = ?7, correctNum = ?8 where id = ?1",
      id, status, accuracy, speed, duration, content, errorNum, correctNum
    );
  }
}
