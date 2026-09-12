package com.nip.service;


import com.nip.common.PageInfo;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.DeviceDao;
import com.nip.dao.DeviceScoringRuleDao;
import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import com.nip.entity.DeviceScoringRuleEntity;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  private final DeviceScoringRuleDao ruleDao;

  @Inject
  public GroupNetTrainService(GroupNetTrainDao trainDao, UserService userService, DeviceDao deviceDao, DeviceTypeDao deviceTypeDao, DeviceScoringRuleDao ruleDao) {
    this.trainDao = trainDao;
    this.userService = userService;
    this.deviceDao = deviceDao;
    this.deviceTypeDao = deviceTypeDao;
    this.ruleDao = ruleDao;
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
    if (trainDto == null || trainDto.getDeviceId() == null) throw new IllegalArgumentException("设备不能为空");
    DeviceEntity device = Optional.ofNullable(deviceDao.findById(trainDto.getDeviceId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到设备"));
    if (!Objects.equals(device.getDeviceTypeId(), trainDto.getDeviceType())) {
      throw new IllegalArgumentException("设备与设备分类不匹配");
    }
    if (!"J210-742".equals(device.getDeviceNumber())) throw new IllegalArgumentException("该设备尚无综合组网题目与评分映射");
    List<DeviceScoringRuleEntity> rules = ruleDao.list("deviceId = ?1", device.getId());
    if (rules.size() != 1) throw new IllegalArgumentException("该设备必须配置唯一的有效评分规则");
    GroupNetTrainEntity groupNetTrainEntity = new GroupNetTrainEntity();
    groupNetTrainEntity.setDeviceId(device.getId());
    groupNetTrainEntity.setDeviceType(device.getDeviceTypeId());
    groupNetTrainEntity.setTopic(trainDto.getTopic());
    groupNetTrainEntity.setScoringRuleContent(GroupNetScoring.freeze(device.getId(), rules.getFirst().getRuleContent(), trainDto.getTopic()));
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
    List<GroupNetTrainEntity> entities = pageQuery.list();
    Set<Integer> deviceIds = entities.stream()
        .map(GroupNetTrainEntity::getDeviceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Set<Integer> typeIds = entities.stream()
        .map(GroupNetTrainEntity::getDeviceType)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Integer, DeviceEntity> devices = deviceDao.list("id in ?1", deviceIds).stream()
        .collect(Collectors.toMap(DeviceEntity::getId, Function.identity()));
    Map<Integer, DeviceTypeEntity> types = deviceTypeDao.findAllByIdIn(typeIds).stream()
        .collect(Collectors.toMap(DeviceTypeEntity::getId, Function.identity()));
    List<GroupNetTrainListPageVO> data = PojoUtils.convert(entities,
        GroupNetTrainListPageVO.class, (entity, vo) -> {
          vo.setDeviceName(Optional.ofNullable(devices.get(entity.getDeviceId()))
              .map(DeviceEntity::getDeviceName).orElse(null));
          vo.setDeviceTypeName(Optional.ofNullable(types.get(entity.getDeviceType()))
              .map(DeviceTypeEntity::getTypeName).orElse(null));
        });
    PageInfo<GroupNetTrainListPageVO> result = new PageInfo<>();
    result.setData(data);
    result.setPageSize(pageQuery.page().size);
    result.setTotalNumber(pageQuery.count());
    result.setCurrentPage(pageQuery.page().index);
    result.setTotalPage(pageQuery.pageCount());
    return result;
  }

  /**
   * 根据id查询训练详情
   *
   * @param id id
   * @return 详情
   */
  public GroupNetTrainDetailsVO detail(Integer id, String token) {
    GroupNetTrainEntity trainEntity = Optional.ofNullable(trainDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    requireOwner(trainEntity, token);
    return PojoUtils.convertOne(trainEntity, GroupNetTrainDetailsVO.class);
  }

  /**
   * 提交答案
   *
   * @param submitAnswerDto 答案
   */
  @Transactional(rollbackOn = Exception.class)
  public GroupNetTrainDetailsVO submitAnswer(GroupNetTrainSubmitAnswerDto submitAnswerDto, String token) {
    if (submitAnswerDto == null || submitAnswerDto.getId() == null) throw new IllegalArgumentException("训练id不能为空");
    submitAnswerDto.validateFields();
    GroupNetTrainEntity trainEntity = Optional.ofNullable(trainDao.findById(submitAnswerDto.getId(), LockModeType.PESSIMISTIC_WRITE))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    requireOwner(trainEntity, token);
    if (trainEntity.getScore() != null) {
      if (!GroupNetScoring.sameAnswer(trainEntity.getAnswer(), submitAnswerDto.getAnswer())) {
        throw new TerminalStateException("训练已提交，不能修改答案或成绩");
      }
      return PojoUtils.convertOne(trainEntity, GroupNetTrainDetailsVO.class);
    }
    if (trainEntity.getScoringRuleContent() == null || trainEntity.getScoringRuleContent().isBlank()) {
      throw new IllegalArgumentException("旧训练没有冻结评分规则，请重新创建训练；历史成绩不会重算");
    }
    GroupNetScoring.Result result = GroupNetScoring.calculate(trainEntity.getDeviceId(), trainEntity.getTopic(),
        trainEntity.getScoringRuleContent(), submitAnswerDto.getAnswer());
    trainEntity.setAnswer(submitAnswerDto.getAnswer());
    trainEntity.setScore(result.score());
    trainEntity.setContent(result.details());
    trainDao.flush();
    return PojoUtils.convertOne(trainEntity, GroupNetTrainDetailsVO.class);
  }

  private void requireOwner(GroupNetTrainEntity train, String token) {
    if (!Objects.equals(train.getCreateUser(), userService.getUserByToken(token).getId())) {
      throw new WebApplicationException(jakarta.ws.rs.core.Response.ok(ResponseResult.error(ResponseCode.CODE_207))
          .type(MediaType.APPLICATION_JSON).build());
    }
  }
}
