package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.TelegramBaseTrainDto;
import com.nip.dto.TelegramTrainDto;
import com.nip.dto.vo.TelegramTrainStatisticalVO;
import com.nip.entity.TelegramTrainEntity;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainLogEntity;
import com.nip.entity.TelegramTrainSettingEntity;
import com.nip.service.TelegramTrainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.*;

/**
 * TelegramTrainController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-08 15:27
 */
@JWT
@Path("/telegramTrain")
@ApplicationScoped
@Tag(name = "拍发训练")
public class TelegramTrainController {

  private final TelegramTrainService telegramTrainService;

  @Inject
  public TelegramTrainController(TelegramTrainService telegramTrainService) {
    this.telegramTrainService = telegramTrainService;
  }

  /**
   * 获取当前人所有训练
   *
   * @param token
   * @return
   */
  @Path("/getAll")
  @POST
  public Response<List<TelegramTrainEntity>> getAll(@RestHeader(TOKEN) String token) {
    return telegramTrainService.getAll(token);
  }

  /**
   * 根据训练编号获取训练
   *
   * @param map
   * @return
   */
  @Path("/getById")
  @POST
  public Response<TelegramTrainDto> getById(Map<String, String> map) {
    return telegramTrainService.getById(map.get(TRAIN_ID));
  }

  /**
   * 根据报底编号获取报文内容
   *
   * @param ids
   * @return
   */
  @Path("/getFloorContentByFloorId")
  @POST
  public Response<Map<String, List<TelegramTrainFloorContentEntity>>> getFloorContentByFloorId(List<String> ids) {
    return telegramTrainService.getFloorContentByFloorId(ids);
  }

  /**
   * 根据报底编号获取报文内容-异步
   *
   * @param token
   * @param map
   * @return
   */
  @Path("/getFloorContentByFloorIdAsync")
  @POST
  public Response<Void> getFloorContentByFloorId(@RestHeader(TOKEN) String token, Map<String, String> map) {
    return telegramTrainService.getFloorContentByFloorIdAsync(token, map.get(TRAIN_ID));
  }

  @Path("/getFloorContentByFloor")
  @POST
  public Response<Void> getFloorContentByFloor(@RestHeader(TOKEN) String token, Map<String, Object> map) {
    return telegramTrainService.getFloorContentByFloorIdAsync(token, (String) map.get(TRAIN_ID), (Integer) map.get(PAGE_NUMBER));
  }

  /**
   * 更新单个报文信息
   *
   * @param map
   * @return
   */
  @Path("/saveFloorContent")
  @POST
  public Response<Void> saveFloorContent(Map<String, String> map) {
    return telegramTrainService.saveFloorContent(map);
  }

  /**
   * 开始训练
   *
   * @param trainDto
   * @return
   */
  @Path("/startTelegramTrain")
  @POST
  public Response<TelegramTrainEntity> startTelegramTrain(TelegramTrainDto trainDto) {
    return telegramTrainService.controlTelegramTrain(0, trainDto);
  }

  /**
   * 暂停训练
   *
   * @param trainDto
   * @return
   */
  @Path("/pauseTelegramTrain")
  @POST
  public Response<TelegramTrainEntity> pauseTelegramTrain(TelegramTrainDto trainDto) {
    return telegramTrainService.controlTelegramTrain(1, trainDto);
  }

  /**
   * 结束训练
   *
   * @param trainDto
   * @return
   */
  @Path("/endTelegramTrain")
  @POST
  public Response<TelegramTrainEntity> endTelegramTrain(TelegramTrainDto trainDto) {
    return telegramTrainService.controlTelegramTrain(2, trainDto);
  }

  /**
   * 保存训练
   *
   * @param token
   * @param trainDto
   * @return
   */
  @Path("/saveTelegramTrain")
  @POST
  public Response<TelegramTrainEntity> saveTelegramTrain(@RestHeader(TOKEN) String token, TelegramTrainDto trainDto) {
    return telegramTrainService.save(token, trainDto);
  }

  /**
   * 根据训练ID获取发报记录
   *
   * @param train
   * @return
   */
  @Path("/getTelegramTrainLog")
  @POST
  public Response<List<TelegramTrainLogEntity>> saveTelegramTrain(TelegramTrainEntity train) {
    return telegramTrainService.getTelegramTrainLogByTelegramTrainId(train.getId());
  }

  @Path("/getSetting")
  @POST
  public Response<List<TelegramTrainSettingEntity>> getSetting() {
    return telegramTrainService.getSetting();
  }

  @Path("/saveSetting")
  @POST
  public Response<List<TelegramTrainSettingEntity>> saveSetting(List<TelegramTrainSettingEntity> list) {
    return telegramTrainService.saveSetting(list);
  }

  @Path("/saveBaseTrain")
  @Operation(summary = "保存基础训练")
  @POST
  public Response<TelegramTrainEntity> saveBaseTrain(@RestHeader(TOKEN) String token, TelegramBaseTrainDto dto) throws Exception {
    return ResponseResult.success(telegramTrainService.saveBaseTrain(dto, token));
  }

  @Path("/statisticalPage")
  @Operation(summary = "统计页面")
  @POST
  public Response<List<TelegramTrainStatisticalVO>> statisticalPage(@RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(telegramTrainService.statisticalPage(token));
  }

  @Path("/lastTrain")
  @POST
  @Operation(summary = "根据类型查询最后一场训练")
  public Response<TelegramTrainEntity> lastTrain(@RestHeader(TOKEN) String token, TelegramBaseTrainDto dto) throws Exception {
    return ResponseResult.success(telegramTrainService.lastTrain(token, dto.getType()));
  }
}
