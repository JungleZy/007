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
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:33
 * @Description:
 */
@ApplicationScoped
@Slf4j
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
    if (entity == null) {
      // 前端可以不经 listPage 直接结算：与 listPage:39-47 同口径懒建，避免裸解引用 NPE
      entity = new RadiotelephoneEntity();
      entity.setUserId(userEntity.getId());
      entity.setType(dto.getType());
      entity.setTotalCount(0);
      entity.setTotalTime("0");
    }
    entity.setTotalCount((entity.getTotalCount() == null ? 0 : entity.getTotalCount()) + 1);
    int increment = dto.getTotalTime() == null ? 0 : dto.getTotalTime();
    entity.setTotalTime(String.valueOf(parseTotalTime(entity.getTotalTime()) + increment));
    RadiotelephoneEntity save = radiotelephoneDao.save(entity);
    return PojoUtils.convertOne(save, RadiotelephoneVO.class);
  }

  /** 历史数据里 totalTime 是字符串列，非数字视为 0，不让脏数据把结算打成 500 */
  private int parseTotalTime(String totalTime) {
    try {
      return Integer.parseInt(totalTime);
    } catch (NumberFormatException e) {
      log.warn("话报训练累计时长不是数字，按 0 计算:{}", totalTime);
      return 0;
    }
  }
}
