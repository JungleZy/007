package com.nip.service;


import com.nip.common.constants.PostTelexPatTrainStatusEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TelexPatTrainDao;
import com.nip.dto.TelexPatTrainDto;
import com.nip.dto.vo.TelexPatTrainVO;
import com.nip.entity.TelexPatTrainEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.FINISH;
import static com.nip.common.constants.PostTelexPatTrainStatusEnum.NOT_STARTED;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 9:56
 */
@ApplicationScoped
@Slf4j
public class TelexPatTrainService {
  private final TelexPatTrainDao telexPatTrainDao;
  private final UserService userService;
  private final TelexPatTrainStatisticalService statisticalService;
  private final ManagedExecutor managedExecutor;

  @Inject
  public TelexPatTrainService(TelexPatTrainDao telexPatTrainDao, UserService userService, TelexPatTrainStatisticalService statisticalService, ManagedExecutor managedExecutor) {
    this.telexPatTrainDao = telexPatTrainDao;
    this.userService = userService;
    this.statisticalService = statisticalService;
    this.managedExecutor = managedExecutor;
  }

  /**
   * 保存训练记录
   *
   * @param token 用户令牌，用于识别和验证用户身份
   * @param dto   包含训练记录信息的数据传输对象
   * @return 返回保存结果的响应实体
   */
  @Transactional
  public Response<TelexPatTrainEntity> saveTexPatTrain(String token, TelexPatTrainDto dto) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      TelexPatTrainEntity telexPatTrainEntity = JSONUtils.fromJson(JSONUtils.toJson(dto), TelexPatTrainEntity.class);
      if (telexPatTrainEntity != null && telexPatTrainEntity.getId() != null) {
        TelexPatTrainEntity byId = telexPatTrainDao.findById(telexPatTrainEntity.getId());
        if (byId == null) {
          return ResponseResult.success();
        }
      }
      telexPatTrainEntity.setCreateUserId(userEntity.getId());
      if (dto.getStatus().compareTo(NOT_STARTED.getStatus()) == 0) {
        //未开始的训练，查询上一条训练记录，如果状态也是未开始，则删除上一条训练记录
        TelexPatTrainVO telexPatTrainVO = lastPatTrain(token, dto.getType());
        if (telexPatTrainVO != null && (telexPatTrainVO.getStatus().compareTo(NOT_STARTED.getStatus()) == 0)) {
          telexPatTrainDao.deleteById(telexPatTrainVO.getId());
        } else if (telexPatTrainVO != null && (telexPatTrainVO.getStatus().compareTo(PostTelexPatTrainStatusEnum.PAUSE.getStatus()) == 0)) {
          //如上一条训练状态是暂停，则视为自动完成训练。
          telexPatTrainDao.updateStatus(telexPatTrainVO.getId(), FINISH.getStatus());
          CompletableFuture.runAsync(() -> statisticalService.statistical(userEntity.getId(), dto.getType() + 1, PojoUtils.convertOne(telexPatTrainVO, TelexPatTrainEntity.class)), managedExecutor);
        }
      }

      TelexPatTrainEntity save = telexPatTrainDao.save(telexPatTrainEntity);
      //如果是完成训练，
      if (dto.getStatus().compareTo(FINISH.getStatus()) == 0) {
        statisticalService.statistical(userEntity.getId(), dto.getType() + 1, save);
      }
      return ResponseResult.success(save);
    } catch (Exception e) {
      log.error("保存训练记录失败：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  public Response<List<TelexPatTrainEntity>> findTexPatTrainByToken(String token) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      return ResponseResult.success(telexPatTrainDao.findAllByCreateUserId(userEntity.getId()));
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  public Response<TelexPatTrainEntity> findTexPatTrainById(String id) {
    try {
      return ResponseResult.success(telexPatTrainDao.findById(id));
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  /**
   * 根据token 和type查询最后一次训练记录
   *
   * @param: token
   * @param: type
   */
  public TelexPatTrainVO lastPatTrain(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelexPatTrainEntity telexPatTrainEntity = telexPatTrainDao.lastTration(userEntity.getId(), type);
    if (telexPatTrainEntity == null) {
      return null;
    }
    return PojoUtils.convertOne(telexPatTrainEntity, TelexPatTrainVO.class);
  }

  /**
   * 根据id删除训练
   *
   * @param: id
   */
  public void deleteById(String id) {
    telexPatTrainDao.deleteById(id);
  }
}
