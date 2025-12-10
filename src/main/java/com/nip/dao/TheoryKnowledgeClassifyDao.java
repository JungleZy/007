package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeClassifyEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 09:07
 * @Description:
 */
@ApplicationScoped
public class TheoryKnowledgeClassifyDao extends BaseRepository<TheoryKnowledgeClassifyEntity, String> {

  public List<TheoryKnowledgeClassifyEntity> findAllByType(Integer type) {
    return find(TYPE, type).list();
  }
}
