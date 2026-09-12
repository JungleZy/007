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
    // 取「该训练有哪些页号」。原写法 find("... group by pageNumber") 会 select 实体全部列却只按
    // pageNumber 分组，在 MySQL 8 默认的 only_full_group_by 下直接 SQLSyntaxErrorException
    // （该域 finish 路径此前无测试覆盖，故一直未暴露）。改为与同类 DAO 一致的 distinct 投影。
    return entityManager.createQuery(
            "select distinct pageNumber from general_telex_pat_page where trainId = ?1 order by pageNumber",
            Integer.class)
        .setParameter(1, trainId)
        .getResultList();
  }
  public List<GeneralTelexPatPageEntity> findTwoPage(String trainId) {
    return find("trainId = ?1 and (pageNumber = 1 or pageNumber = 2)",
        Sort.by("pageNumber").ascending().and("sort"), trainId).list();
  }
  public List<GeneralTelexPatPageEntity> findByTrainIdAndPageNumberOrderBySort(String trainId, Integer pageNumber) {
    return find("trainId = ?1 and pageNumber = ?2", Sort.by("sort").ascending(), trainId, pageNumber).list();
  }
}
