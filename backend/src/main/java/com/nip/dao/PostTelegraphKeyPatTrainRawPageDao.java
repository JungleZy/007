package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelegraphKeyPatTrainRawPageEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostTelegraphKeyPatTrainRawPageDao extends BaseRepository<PostTelegraphKeyPatTrainRawPageEntity, String> {
  public List<PostTelegraphKeyPatTrainRawPageEntity> findPages(String trainId) {
    return find("trainId", Sort.by("pageNumber"), trainId).list();
  }

  public PostTelegraphKeyPatTrainRawPageEntity findPage(String trainId, int pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2", trainId, pageNumber).firstResult();
  }
}
