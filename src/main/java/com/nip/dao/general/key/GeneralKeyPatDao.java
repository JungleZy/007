package com.nip.dao.general.key;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.GeneralKeyPatTrainScoreDto;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class GeneralKeyPatDao extends BaseRepository<GeneralKeyPatEntity, Integer> {

  public List<GeneralKeyPatTrainScoreDto> countClassResultRate(List<String> uidList, String startTime, String endTime) {
    return getEntityManager().createNamedQuery("find_general_key_pat_train_score_dto", GeneralKeyPatTrainScoreDto.class)
        .setParameter("uidList", uidList)
        .setParameter("startTime", startTime)
        .setParameter("endTime", endTime).getResultList();
  }
}
