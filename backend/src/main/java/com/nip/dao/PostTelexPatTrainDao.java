package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.entity.PostTelexPatTrainEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:13
 * @Description:
 */
@ApplicationScoped
public class PostTelexPatTrainDao extends BaseRepository<PostTelexPatTrainEntity, String> {
  public List<PostTelexPatTrainVO> findTrainList(Integer type, String userId) {
    return entityManager.createNamedQuery("find_train_list", PostTelexPatTrainVO.class)
        .setParameter("trainType", type)
        .setParameter("userId", userId).getResultList();
  }
}
