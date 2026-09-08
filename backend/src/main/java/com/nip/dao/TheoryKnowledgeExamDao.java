package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.sql.FindAllExamByIdDto;
import com.nip.dto.sql.FindAllExamDto;
import com.nip.entity.TheoryKnowledgeExamEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ID;

/**
 * @version v1.0.01
 * @Author: BBB
 * @Date: Create 2022/2/22 16:50
 */
@ApplicationScoped
public class TheoryKnowledgeExamDao extends BaseRepository<TheoryKnowledgeExamEntity, String> {

  public List<FindAllExamDto> findAllExam(int type1, int type2) {
    return entityManager.createNamedQuery("find_all_exam", FindAllExamDto.class).setParameter("s1", type1)
                        .setParameter("s2", type2).getResultList();
  }

  public FindAllExamByIdDto findAllExamById(String id) {
    return entityManager.createNamedQuery("find_all_exam_by_id", FindAllExamByIdDto.class).setParameter(ID, id).setMaxResults(1)
                        .getSingleResult();
  }
}
