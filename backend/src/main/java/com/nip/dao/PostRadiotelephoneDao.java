package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostRadiotelephoneTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostRadiotelephoneDao extends BaseRepository<PostRadiotelephoneTrainEntity, String> {

  public List<PostRadiotelephoneTrainEntity> findByUserIdOrderByCreateTimeDesc(String userId) {
    return find("userId = ?1 order by createTime desc", userId).list();
  }
}
