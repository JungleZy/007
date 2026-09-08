package com.nip.dao;


import com.nip.common.repository.BaseRepository;
import com.nip.entity.TestPaperEntity;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:03
 */
@ApplicationScoped
public class TestPaperDao extends BaseRepository<TestPaperEntity, String> {
  public List<TestPaperEntity> findAllByNameLike(String name) {
    return find("name like :name", Parameters.with("name", name)).list();
  }

  public List<TestPaperEntity> findAllByLevelIdIn(List<String> levelIds) {
    return find("levelId in (?1)", levelIds).list();
  }

  public List<TestPaperEntity> findAllByLevelIdInAndNameLike(List<String> levelIds, String name) {
    return find(
      "levelId in (:levelIds) and name like :name",
      Parameters.with("levelIds", levelIds).and("name", name)
    ).list();
  }
}
