package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.RadiotelephoneTermDataDao;
import com.nip.dto.PostRadiotelephoneTermDataDto;
import com.nip.dto.vo.PostRadiotelephoneTermDataVO;
import com.nip.entity.PostRadiotelephoneTermDataEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 14:48
 * @Description:
 */
@ApplicationScoped
public class RadiotelephoneTermDataService {

  private final RadiotelephoneTermDataDao dataDao;

  @Inject
  public RadiotelephoneTermDataService(RadiotelephoneTermDataDao dataDao) {
    this.dataDao = dataDao;
  }

  public List<PostRadiotelephoneTermDataVO> findByType(Integer type) {
    List<PostRadiotelephoneTermDataEntity> entityList;
    if (type == null) {
      entityList = dataDao.findAll(Sort.by("key")).list();
    } else {
      entityList = dataDao.findByTypeOrderByKey(type);
    }
    return PojoUtils.convert(entityList, PostRadiotelephoneTermDataVO.class);
  }

  public List<PostRadiotelephoneTermDataVO> findByTypeAndRandom(PostRadiotelephoneTermDataDto dto) {
    List<PostRadiotelephoneTermDataEntity> entityList = dataDao.findByTypeOrderByKey(dto.getType());
    List<PostRadiotelephoneTermDataVO> ret = new ArrayList<>();
    Random random = new Random();
    for (int i = 0; i < dto.getNumber(); i++) {
      int index = random.nextInt(entityList.size() - 1);
      PostRadiotelephoneTermDataEntity entity = entityList.get(index);
      PostRadiotelephoneTermDataVO vo = PojoUtils.convertOne(entity, PostRadiotelephoneTermDataVO.class);
      ret.add(vo);
    }
    return ret;
  }

  @Transactional(rollbackOn = Exception.class)
  public PostRadiotelephoneTermDataVO add(PostRadiotelephoneTermDataVO vo) {
    PostRadiotelephoneTermDataEntity entity = PojoUtils.convertOne(vo, PostRadiotelephoneTermDataEntity.class);
    PostRadiotelephoneTermDataEntity save = dataDao.save(entity);
    vo.setId(save.getId());
    return vo;
  }

  @Transactional
  public void delete(PostRadiotelephoneTermDataVO vo) {
    dataDao.deleteById(vo.getId());
  }

  @Transactional
  public PostRadiotelephoneTermDataVO update(PostRadiotelephoneTermDataVO vo) {
    PostRadiotelephoneTermDataEntity byId = dataDao.findById(vo.getId());
    byId.setKey(vo.getKey());
    byId.setType(vo.getType());
    byId.setValue(vo.getValue());
    byId.setSort(vo.getSort());
    dataDao.save(byId);
    return vo;
  }
}
