package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelegraphKeyPatTrainMoreEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostTelegraphKeyPatTrainMoreEntityDao
  extends BaseRepository<PostTelegraphKeyPatTrainMoreEntity, String> {

  public List<PostTelegraphKeyPatTrainMoreEntity> getTop2ByTrainId(String trainId) {
    return find("trainId = ?1 and (pageNumber = 1 or pageNumber = 2) order by pageNumber", trainId).list();
  }

  public PostTelegraphKeyPatTrainMoreEntity findByTrainIdAndPageNumber(String trainId, Integer pageNumber) {
    return find("trainId = ?1 and pageNumber =?2", trainId, pageNumber).firstResult();
  }
}
