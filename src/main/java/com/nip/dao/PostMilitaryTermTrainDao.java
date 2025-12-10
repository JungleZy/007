package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostMilitaryTermTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostMilitaryTermTrainDao extends BaseRepository<PostMilitaryTermTrainEntity, String> {

  public List<PostMilitaryTermTrainEntity> findByUserIdOrderByCreateTimeDesc(String id) {
    return find("userId = ?1 order by createTime desc", id).list();
  }
}
