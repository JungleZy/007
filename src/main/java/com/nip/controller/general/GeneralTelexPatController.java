package com.nip.controller.general;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.Page;
import com.nip.dto.general.*;
import com.nip.dto.general.statistic.GeneralTelexPatTrainStatisticVO;
import com.nip.dto.vo.PostTelegraphTelexPatTrainPageVO;
import com.nip.service.general.GeneralTelexPatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@JWT
@Path("/generalTelexPat")
@ApplicationScoped
@Tag(name = "综合组训-数据报组训")
public class GeneralTelexPatController {
  private final GeneralTelexPatService patTrainService;

  @Inject
  public GeneralTelexPatController(GeneralTelexPatService patTrainService) {
    this.patTrainService = patTrainService;
  }

  @POST
  @Path("add")
  @Operation(summary = "添加")
  public Response<GeneralTelexPatTrainVO> add(@RequestBody GeneralTelexPatAddParamDto param, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(patTrainService.add(param, token));
  }

  @POST
  @Path("findAll")
  @Operation(summary = "查询请求用户的所有训练")
  public Response<PageInfo<GeneralTelexPatTrainVO>> findAll(@RestHeader(TOKEN) String token, @RequestBody Page page) throws Exception {
    return ResponseResult.success(patTrainService.findAll(page, token));
  }

  @POST
  @Path("detail")
  @Operation(summary = "查询训练详情")
  public Response<GeneralTelexPatTrainVO> detail(@RequestBody GeneralTelexPatPageParamDto param) {
    return ResponseResult.success(patTrainService.detail(param));
  }

  @POST
  @Path("patDetail")
  @Operation(summary = "查询训练详情")
  public Response<GeneralTelexPatUserInfoVO> patDetail(@RequestBody GeneralTelexPatPageParamDto param) {
    return ResponseResult.success(patTrainService.patDetail(param));
  }

  @POST
  @Path("findPage")
  @Operation(summary = "按页查询报底")
  public Response<GeneralTelexPatPageDto> findPage(@RequestBody GeneralTelexPatPageParamDto param) throws Exception {
    return ResponseResult.success(patTrainService.findMessageBody(param));
  }

  @POST
  @Path("updateTrainStatus")
  @Operation(summary = "修改训练状态")
  public Response<?> updateTrainStatus(@RequestBody GeneralTelexPathUpdateStatusParam param) {
    patTrainService.updateStatus(param.getTrainId(), param.getStatus());
    return ResponseResult.success("");
  }

  @POST
  @Path("uploadResult")
  @Operation(summary = "上传提交结果")
  public Response<Void> uploadResult(@RequestBody GeneralTelexPatPageSubmitDto vo, @RestHeader(TOKEN) String token) {
    patTrainService.saveContentValue(vo, token);
    return ResponseResult.success();
  }

  @POST
  @Path("finish")
  @Operation(summary = "完成训练")
  public Response<List<GeneralTelexPatUserInfoVO>> finish(@RequestBody GeneralTelexPatFinishDto vo) {
    return ResponseResult.success(patTrainService.finish(vo));
  }

  @POST
  @Path("/getPage")
  @Operation(summary = "获取指定页得content")
  public Response<PostTelegraphTelexPatTrainPageVO> getPage(@RequestBody GeneralTelexPatPageParamDto param) {
    return ResponseResult.success(patTrainService.getPage(param.getTrainId(), param.getPageNumber(), param.getUserId()));
  }

  @POST
  @Path("/getPatValue")
  @Operation(summary = "获取指定用户拍发的报文")
  public Response<List<GeneralTelexPatTrainUserValueVO>> getPatValue(@RequestBody GeneralTelexPatPageParamDto param) {
    return ResponseResult.success(patTrainService.getPatValue(param));
  }

  @POST
  @Path("statistics")
  @Operation(summary = "统计信息")
  public Response<GeneralTelexPatTrainStatisticVO> statistic(@RequestBody GeneralTelexPatPageParamDto param) {
    return ResponseResult.success(patTrainService.statistic(param.getTrainId()));
  }

  @GET
  @Path("/getOnline")
  @Operation(summary = "获取在线人数")
  public Response<List<GeneralPatTrainUserDto>> getOnline(@RestQuery(TRAIN_ID) String trainId) {
    return patTrainService.getOnline(trainId);
  }

  @GET
  @Path("/startTrain")
  @Operation(summary = "用户开始训练")
  public Response<Void> startTrain(@RestQuery(TRAIN_ID) String trainId, @RestHeader(TOKEN) String token) {
    patTrainService.startTrain(trainId,token);
    return ResponseResult.success();
  }
}
