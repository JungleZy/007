package com.nip.dao.general.telex;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.telex.GeneralTelexPatPageEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class GeneralTelexPatPageDao extends BaseRepository<GeneralTelexPatPageEntity, String> {
  public Integer findMaxPageNumber(String trainId) {
    return entityManager.createQuery(
        "select max(pageNumber) from general_telex_pat_page where trainId =:trainId",
        Integer.class
    ).setParameter("trainId", trainId).setMaxResults(1).getSingleResult();
  }
  public List<Integer> countPageNumber(String trainId) {
    return find("trainId = ?1 group by pageNumber order by pageNumber", trainId).list()
        .stream().map(GeneralTelexPatPageEntity::getPageNumber).toList();
  }
  public List<GeneralTelexPatPageEntity> findTwoPage(String id) {
    return find("id = ?1 and (pageNumber = 1 or pageNumber =2)", Sort.by("pageNumber").ascending(), id).list();
  }
  public List<GeneralTelexPatPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId, Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2", Sort.by("sort").ascending(), trainId, pageNumber).list();
  }
}
