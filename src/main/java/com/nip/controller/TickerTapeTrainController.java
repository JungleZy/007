package com.nip.controller;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.Page;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.dto.vo.TickerTapeTrainVo;
import com.nip.dto.vo.param.TickerTapeBaseTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainQueryParam;
import com.nip.dto.vo.param.TickerTapeTrainUpdateParam;
import com.nip.service.TickerTapeTrainService;
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
 * @Data: 2022-04-06 16:46
 * @Description:
 */
@JWT
@Path("/tickerTapeTrain")
@ApplicationScoped
@Tag(name = "收报管理-训练")
public class TickerTapeTrainController {

  private final TickerTapeTrainService trainService;

  @Inject
  public TickerTapeTrainController(TickerTapeTrainService trainService) {
    this.trainService = trainService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练")
  public Response<TickerTapeTrainAddParam> add(TickerTapeTrainAddParam param, @RestHeader(TOKEN) String token)
      throws Exception {
    return ResponseResult.success(trainService.add(param, token));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "分页查询")
  public Response<PageInfo<TickerTapeTrainVo>> listPage(Page page, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(trainService.listPage(page, token));
  }

  @POST
  @Path("/getById")
  @Operation(summary = "根据id获取训练")
  public Response<TickerTapeTrainVo> getById(TickerTapeTrainQueryParam param) {
    return ResponseResult.success(trainService.getById(param.getId()));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<Void> begin(TickerTapeTrainQueryParam param) {
    trainService.begin(param.getId());
    return ResponseResult.success();
  }

  @POST
  @Path("/pause")
  @Operation(summary = "暂停")
  public Response<Void> pause(TickerTapeTrainUpdateParam updateParam) {
    trainService.pause(updateParam);
    return ResponseResult.success();
  }

  @POST
  @Path("/goOn")
  @Operation(summary = "继续")
  public Response<Void> goOn(TickerTapeTrainQueryParam param) {
    trainService.goOn(param.getId());
    return ResponseResult.success();
  }

  @POST
  @Path("/finish")
  @Operation(summary = "结束")
  public Response<Void> finish(TickerTapeTrainUpdateParam updateParam) {
    trainService.finish(updateParam);
    return ResponseResult.success();
  }

  @POST
  @Path("/saveBaseTrain")
  @Operation(summary = "添加基础训练/科式训练")
  public Response<Void> saveBaseTrain(TickerTapeBaseTrainAddParam param, @RestHeader(TOKEN) String token) {
    try {
      trainService.saveBaseTrain(param, token);
      return ResponseResult.success();
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  @POST
  @Path("/statisticalPage")
  @Operation(summary = "统计页面")
  public Response<List<TelexPatTrainStatisticalVO>> statisticalPage(@RestHeader(TOKEN) String token)
      throws Exception {
    return ResponseResult.success(trainService.statisticalPage(token));
  }

  @POST
  @Path("/lastTrain")
  @Operation(summary = "查询最后一次训练状态")
  public Response<TickerTapeTrainVo> lastTrain(@RestHeader(TOKEN) String token, TickerTapeBaseTrainAddParam param)
      throws Exception {
    return ResponseResult.success(trainService.lastTrain(token, param.getType()));
  }

}
