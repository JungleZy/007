package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeTestFallibleEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TheoryKnowledgeTestFallibleDao extends BaseRepository<TheoryKnowledgeTestFallibleEntity, String> {

  public TheoryKnowledgeTestFallibleEntity findByUserId(String userId) {
    return find("userId", userId).firstResult();
  }
}
