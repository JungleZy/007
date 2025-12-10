package com.nip.service;


import com.nip.common.PageInfo;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.DeviceDao;
import com.nip.dao.DeviceTypeDao;
import com.nip.dao.GroupNetTrainDao;
import com.nip.dto.GroupNetTrainDto;
import com.nip.dto.GroupNetTrainSubmitAnswerDto;
import com.nip.dto.vo.GroupNetTrainDetailsVO;
import com.nip.dto.vo.GroupNetTrainListPageVO;
import com.nip.entity.DeviceEntity;
import com.nip.entity.DeviceTypeEntity;
import com.nip.entity.GroupNetTrainEntity;
import com.nip.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 10:34
 * @Description:
 */
@ApplicationScoped
public class GroupNetTrainService {

  private final GroupNetTrainDao trainDao;
  private final UserService userService;
  private final DeviceDao deviceDao;
  private final DeviceTypeDao deviceTypeDao;

  @Inject
  public GroupNetTrainService(GroupNetTrainDao trainDao, UserService userService, DeviceDao deviceDao, DeviceTypeDao deviceTypeDao) {
    this.trainDao = trainDao;
    this.userService = userService;
    this.deviceDao = deviceDao;
    this.deviceTypeDao = deviceTypeDao;
  }

  /**
   * 保存
   *
   * @param trainDto dto
   * @param token    token
   */
  @Transactional(rollbackOn = Exception.class)
  public GroupNetTrainDetailsVO save(GroupNetTrainDto trainDto, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    GroupNetTrainEntity groupNetTrainEntity = PojoUtils.convertOne(trainDto, GroupNetTrainEntity.class);
    groupNetTrainEntity.setCreateUser(userEntity.getId());
    trainDao.saveAndFlush(groupNetTrainEntity);
    return PojoUtils.convertOne(groupNetTrainEntity, GroupNetTrainDetailsVO.class);
  }

  /**
   * 分页查询
   *
   * @param page  分页对象
   * @param token token
   * @return 分页数据
   */
  public PageInfo<GroupNetTrainListPageVO> listPage(Page page, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    PanacheQuery<GroupNetTrainEntity> pageQuery = trainDao
        .find("createUser = ?1", Sort.by("createTime").descending(), userEntity.getId())
        .page(page.getPage() - 1, page.getRows());
    return PojoUtils.convertPage(pageQuery, GroupNetTrainListPageVO.class, (e, v) -> {
      DeviceEntity deviceEntity = Optional.ofNullable(deviceDao.findById(e.getDeviceId()))
          .orElseGet(DeviceEntity::new);
      DeviceTypeEntity deviceTypeEntity = Optional.ofNullable(deviceTypeDao.findById(e.getDeviceType()))
          .orElseGet(DeviceTypeEntity::new);
      v.setDeviceTypeName(deviceTypeEntity.getTypeName());
      v.setDeviceName(deviceEntity.getDeviceName());
    });
  }

  /**
   * 根据id查询训练详情
   *
   * @param id id
   * @return 详情
   */
  public GroupNetTrainDetailsVO detail(Integer id) {
    GroupNetTrainEntity trainEntity = Optional.ofNullable(trainDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    return PojoUtils.convertOne(trainEntity, GroupNetTrainDetailsVO.class);
  }

  /**
   * 提交答案
   *
   * @param submitAnswerDto 答案
   */
  @Transactional(rollbackOn = Exception.class)
  public void submitAnswer(GroupNetTrainSubmitAnswerDto submitAnswerDto) {
    GroupNetTrainEntity trainEntity = Optional.ofNullable(trainDao.findById(submitAnswerDto.getId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    trainEntity.setAnswer(submitAnswerDto.getAnswer());
    trainEntity.setScore(submitAnswerDto.getScore());
  }
}
