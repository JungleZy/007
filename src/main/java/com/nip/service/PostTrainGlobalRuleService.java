package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostTrainGlobalRuleDao;
import com.nip.dto.vo.PostTrainGlobalRuleVO;
import com.nip.entity.PostTrainGlobalRuleEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PostTrainGlobalRuleService {
  private final PostTrainGlobalRuleDao ruleDao;

  @Inject
  public PostTrainGlobalRuleService(PostTrainGlobalRuleDao ruleDao) {
    this.ruleDao = ruleDao;
  }

  /**
   * 添加评分规则
   *
   * @param vo 评分对象
   * @return 评分对象
   */
  @Transactional
  public List<PostTrainGlobalRuleVO> addRule(List<PostTrainGlobalRuleVO> vo) {
    List<PostTrainGlobalRuleEntity> postTrainGlobalRuleEntity = PojoUtils.convert(vo, PostTrainGlobalRuleEntity.class);
    List<PostTrainGlobalRuleEntity> entities = new ArrayList<>();
    postTrainGlobalRuleEntity.forEach(entity -> {
      if (entity.getId() == null) {
        entity.setCreateTime(LocalDateTime.now());
        PostTrainGlobalRuleEntity save = ruleDao.save(entity);
        entities.add(save);
      } else {
        ruleDao.update("type=?1,level=?2,accuracy=?3,description=?4 where id=?5",
            entity.getType(), entity.getLevel(), entity.getAccuracy(), entity.getDescription(), entity.getId());
        entities.add(ruleDao.find("id", entity.getId()).singleResult());
      }
    });
    return PojoUtils.convert(entities, PostTrainGlobalRuleVO.class);
  }

  /**
   * 根据类型查询评分对象
   *
   * @param vo 评分对象
   * @return 评分对象
   */
  public List<PostTrainGlobalRuleVO> findByType(PostTrainGlobalRuleVO vo) {
    Integer type = vo.getType();
    List<PostTrainGlobalRuleEntity> entityList = ruleDao.find("type = ?1", Sort.by("createTime").ascending(), type).list();
    return PojoUtils.convert(entityList, PostTrainGlobalRuleVO.class);
  }

  /**
   * 删除评分
   *
   * @param vo 删除评分
   */
  @Transactional
  public void deleteById(PostTrainGlobalRuleVO vo) {
    Integer id = vo.getId();
    if (id != null) {
      ruleDao.delete("id", id);
    }
  }
}
