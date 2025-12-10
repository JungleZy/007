package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@ApplicationScoped
public class PostMilitaryTermTrainTestPaperDao extends BaseRepository<PostMilitaryTermTrainTestPaperEntity, String> {

  public List<PostMilitaryTermTrainTestPaperEntity> findAllByTrainId(String trainId) {
    return find(TRAIN_ID, trainId).list();
  }
}
