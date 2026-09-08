package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.EnteringExerciseVO;
import com.nip.dto.vo.EnteringExerciseWordStockVO;
import com.nip.dto.vo.EnteringStatisticalVO;
import com.nip.dto.vo.param.*;
import com.nip.service.EnteringExerciseService;
import com.nip.service.EnteringExerciseWordStockService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:50
 * @Description:
 */
@JWT
@Path("/enteringExercise")
@ApplicationScoped
@Tag(name = "汉字录入-训练")
public class EnteringExerciseController {

  private final EnteringExerciseService enteringExerciseService;
  private final EnteringExerciseWordStockService stockService;

  @Inject
  public EnteringExerciseController(EnteringExerciseService enteringExerciseService, EnteringExerciseWordStockService stockService) {
    this.enteringExerciseService = enteringExerciseService;
    this.stockService = stockService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练")
  public Response<EnteringExerciseVO> add(@RestHeader(TOKEN) String token, EnteringExerciseAddParam addParam) {
    return ResponseResult.success(enteringExerciseService.add(addParam, token));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "分页")
  public Response<List<EnteringExerciseVO>> listPage(@RestHeader(TOKEN) String token, EnteringExercisePageParam param) {
    return ResponseResult.success(enteringExerciseService.listPage(param, token));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<Object> begin(EnteringExerciseUpdateParam param) {
    try {
      enteringExerciseService.begin(param);
      return ResponseResult.success();
    } catch (Exception e) {
      return ResponseResult.error(e.getMessage());
    }
  }

  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<Void> finish(EnteringExerciseFinishParam param) {
    enteringExerciseService.finish(param);
    return ResponseResult.success();
  }

  @POST
  @Path("/pause")
  @Operation(summary = "暂停训练")
  public Response<Void> pause(EnteringExerciseFinishParam param) {
    enteringExerciseService.pause(param);
    return ResponseResult.success();
  }

  @POST
  @Path("/goTo")
  @Operation(summary = "继续训练")
  public Response<Void> goTo(EnteringExerciseUpdateParam param) {
    enteringExerciseService.goTo(param);
    return ResponseResult.success();
  }

  @POST
  @Path("/getWordStock")
  @Operation(summary = "根据类型获取字库")
  public Response<EnteringExerciseWordStockVO> getWordStock(EnteringExerciseWordStockQueryParam param) {
    return ResponseResult.success(stockService.findByType(param.getType()));
  }

  @POST
  @Path("/getById")
  @Operation(summary = "根据Id查询训练")
  public Response<EnteringExerciseVO> getById(EnteringExerciseUpdateParam param) {
    return ResponseResult.success(enteringExerciseService.getById(param.getId()));
  }

  @POST
  @Path("/statisticalPage")
  @Operation(summary = "统计页面")
  public Response<List<EnteringStatisticalVO>> statisticalPage(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(enteringExerciseService.statisticalPage(token, 0));
  }

  @POST
  @Path("/lastTrain")
  @Operation(summary = "查询最后一次训练")
  public Response<EnteringExerciseVO> lastTran(@RestHeader(TOKEN) String token, EnteringStatisticalVO vo) {
    return ResponseResult.success(enteringExerciseService.lastTrain(token, vo.getType()));
  }
}
