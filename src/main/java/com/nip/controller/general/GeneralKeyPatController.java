package com.nip.controller.general;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.Page;
import com.nip.dto.general.*;
import com.nip.dto.general.statistic.GeneralKeyPatTrainStatisticVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageVO;
import com.nip.service.general.GeneralKeyPatService;
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

import java.math.BigDecimal;
import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@JWT
@Path("/generalKeyPat")
@ApplicationScoped
@Tag(name = "综合组训-电子键组训")
public class GeneralKeyPatController {
  private final GeneralKeyPatService patTrainService;

  @Inject
  public GeneralKeyPatController(GeneralKeyPatService patTrainService) {
    this.patTrainService = patTrainService;
  }

  @POST
  @Path("add")
  @Operation(summary = "添加训练")
  public Response<GeneralKeyPatTrainVO> add(@RequestBody GeneralKeyPatAddParamDto param, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(patTrainService.add(param, token));
  }

  @POST
  @Path("findAll")
  @Operation(summary = "查询请求用户的所有训练")
  public Response<PageInfo<GeneralKeyPatTrainVO>> findAll(@RequestBody Page page, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.findAll(page, token));
  }

  @POST
  @Path("findPage")
  @Operation(summary = "按页查询报底")
  public Response<GeneralKeyPatPageDto> findPage(@RequestBody GeneralKeyPatPageParamDto param) {
    return ResponseResult.success(patTrainService.findMessageBody(param));
  }

  @POST
  @Path("detail")
  @Operation(summary = "查询训练详情")
  public Response<GeneralKeyPatTrainVO> detail(@RequestBody GeneralKeyPatPageParamDto param) {
    try {
      return ResponseResult.success(patTrainService.detail(param));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @POST
  @Path("patDetail")
  @Operation(summary = "查询训练详情")
  public Response<GeneralKeyPatUserInfoVO> patDetail(@RequestBody GeneralKeyPatPageParamDto param) {
    return ResponseResult.success(patTrainService.patDetail(param));
  }

  @POST
  @Path("uploadResult")
  @Operation(summary = "上传提交结果")
  public Response<?> uploadResult(@RequestBody GeneralKeyPatPageSubmitDto vo, @RestHeader(TOKEN) String token) {
    patTrainService.saveContentValue(vo, token);
    return ResponseResult.success("");
  }

  @POST
  @Path("updateTrainStatus")
  @Operation(summary = "修改训练状态")
  public Response<?> updateTrainStatus(@RequestBody GeneralKeyPathUpdateStatusParam param) {
    patTrainService.updateStatus(param.getTrainId(), param.getStatus());
    return ResponseResult.success("");
  }

  @POST
  @Path("finish")
  @Operation(summary = "完成训练")
  public Response<List<GeneralKeyPatUserInfoVO>> finish(@RequestBody GeneralKeyPatFinishDto vo) {
    return ResponseResult.success(patTrainService.finish(vo));
  }

  @POST
  @Path("/getPage")
  @Operation(summary = "获取指定页得content")
  public Response<PostTelegraphKeyPatTrainPageVO> getPage(@RequestBody GeneralKeyPatPageParamDto param) {
    return ResponseResult.success(patTrainService.getPage(param.getTrainId(), param.getPageNumber(), param.getUserId()));
  }

  @POST
  @Path("/getPatValue")
  @Operation(summary = "获取指定用户拍发的报文")
  public Response<List<GeneralKeyPatTrainUserValueVO>> getPatValue(@RequestBody GeneralKeyPatPageParamDto param) {
    return ResponseResult.success(patTrainService.getPatValue(param));
  }

  @POST
  @Path("statistics")
  @Operation(summary = "统计信息")
  public Response<GeneralKeyPatTrainStatisticVO> statistic(@RequestBody GeneralKeyPatPageParamDto param) {
    return ResponseResult.success(patTrainService.statistic(param.getTrainId()));
  }

  /**
   * 成绩
   *
   * @param
   * @return
   */
  @POST
  @Path("getScore")
  @Operation(summary = "获取成绩")
  public Response<List<BigDecimal>> score() {
    return ResponseResult.success(patTrainService.score());
  }

  @GET
  @Path("getTrainInfo")
  @Operation(summary = "获取训练信息用来导入到本地库")
  public Response<GeneralKeyPatTrainDto> fetchTrainInfo(@RestQuery(TRAIN_ID) Integer trainId) {

    return ResponseResult.success(patTrainService.getTrainInfo(trainId));
  }

  @POST
  @Path("importTrainInfo")
  @Operation(summary = "导入到本地库")
  public Response<Void> importTrainInfo(@RequestBody GeneralKeyPatTrainDto dto) {
    patTrainService.importTrainInfo(dto);
    return ResponseResult.success();
  }

  @GET
  @Path("getTrainInfoBatch")
  @Operation(summary = "获取训练信息用来导入到本地库")
  public Response<List<GeneralKeyPatTrainDto>> fetchTrainInfoBatch(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.getTrainInfoBatch(token));
  }

  @POST
  @Path("importTrainInfoBatch")
  @Operation(summary = "导入到本地库")
  public Response<Void> importTrainInfoBatch(@RequestBody List<GeneralKeyPatTrainDto> dto) {
    patTrainService.importTrainInfoBatch(dto);
    return ResponseResult.success();
  }

  @GET
  @Path("/startTrain")
  @Operation(summary = "用户开始训练")
  public Response<Void> startTrain(@RestQuery(TRAIN_ID) Integer trainId, @RestHeader(TOKEN) String token) {
    patTrainService.startTrain(trainId, token);
    return ResponseResult.success();
  }
}
