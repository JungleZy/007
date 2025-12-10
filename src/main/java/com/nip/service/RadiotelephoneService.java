package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.entity.RadiotelephoneEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:33
 * @Description:
 */
@ApplicationScoped
public class RadiotelephoneService {

  private final UserDao userDao;
  private final RadiotelephoneDao radiotelephoneDao;

  @Inject
  public RadiotelephoneService(UserDao userDao, RadiotelephoneDao radiotelephoneDao) {
    this.userDao = userDao;
    this.radiotelephoneDao = radiotelephoneDao;
  }

  @Transactional
  public List<RadiotelephoneVO> listPage(String token, RadiotelephoneDto dto) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    List<RadiotelephoneEntity> entityList = radiotelephoneDao.findAllByUserId(userEntity.getId());
    RadiotelephoneEntity byUserIdAndType = radiotelephoneDao.findByUserIdAndType(userEntity.getId(), dto.getType());
    if (byUserIdAndType == null) {
      RadiotelephoneEntity entity = new RadiotelephoneEntity();
      entity.setTotalTime("0");
      entity.setType(dto.getType());
      entity.setUserId(userEntity.getId());
      entity.setTotalCount(0);
      RadiotelephoneEntity save = radiotelephoneDao.save(entity);
      entityList.add(save);
    }
    List<RadiotelephoneVO> convert = PojoUtils.convert(entityList, RadiotelephoneVO.class);
    convert.sort(Comparator.comparingInt(RadiotelephoneVO::getType));
    return convert;
  }

  @Transactional(rollbackOn = Exception.class)
  public RadiotelephoneVO finish(RadiotelephoneDto dto, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    RadiotelephoneEntity entity = radiotelephoneDao.findByUserIdAndType(userEntity.getId(), dto.getType());
    entity.setTotalCount(entity.getTotalCount() + 1);
    entity.setTotalTime(String.valueOf(Integer.parseInt(entity.getTotalTime()) + dto.getTotalTime()));
    RadiotelephoneEntity save = radiotelephoneDao.save(entity);
    return PojoUtils.convertOne(save, RadiotelephoneVO.class);
  }
}
