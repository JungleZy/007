package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TestPaperQuestionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:03
 */
@ApplicationScoped
public class TestPaperQuestionDao extends BaseRepository<TestPaperQuestionEntity, String> {
  public List<TestPaperQuestionEntity> findAllByTestPaperId(String testPaperId) {
    return find("testPaperId", testPaperId).list();
  }

  @Transactional
  public void deleteAllByTestPaperId(String testPaperId) {
    delete("testPaperId", testPaperId);
  }
}
