package com.nip.dao.general.key;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.key.GeneralKeyPatPageEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class GeneralKeyPatPageDao extends BaseRepository<GeneralKeyPatPageEntity, Integer> {

  public List<GeneralKeyPatPageEntity> findByPageNumberAndTrainIdOrderBySort(Integer pageNumber, Integer trainId) {
    return find("pageNumber = ?1 and trainId = ?2", Sort.by("sort").ascending(), pageNumber, trainId).list();
  }

  public List<GeneralKeyPatPageEntity> findByTrainIdAndPageNumberOrderBySort(Integer trainId, Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2", Sort.by("sort").ascending(), trainId, pageNumber).list();
  }

  public List<Integer> countPageNumber(Integer trainId) {
    return find("trainId = ?1 group by pageNumber", trainId).list()
        .stream().map(GeneralKeyPatPageEntity::getPageNumber).toList();
  }

  public List<GeneralKeyPatPageEntity> findTwoPage(Integer id) {
    return find("id = ?1 and (pageNumber = 1 or pageNumber =2)", Sort.by("pageNumber").ascending(), id).list();
  }

  public List<GeneralKeyPatPageEntity> findByTrainId(Integer trainId) {
    return find("trainId = ?1", trainId).list();
  }
  public Integer findMaxPageNumber(Integer trainId) {
    return entityManager.createQuery(
        "select max(pageNumber) from general_key_pat_page where trainId =:trainId",
        Integer.class
    ).setParameter("trainId", trainId).setMaxResults(1).getSingleResult();
  }
}
