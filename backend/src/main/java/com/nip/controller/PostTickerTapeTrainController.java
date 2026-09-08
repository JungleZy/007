package com.nip.controller;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.Page;
import com.nip.dto.vo.PostTickerTapeTrainPageValueVO;
import com.nip.dto.vo.PostTickerTapeTrainVo;
import com.nip.dto.vo.param.PostTickerTapeTrainAddParam;
import com.nip.dto.vo.param.PostTickerTapeTrainUpdateParam;
import com.nip.dto.vo.param.PostTickerTapeTrainUploadResultParam;
import com.nip.dto.vo.param.TickerTapeTrainQueryParam;
import com.nip.service.PostTickerTapeTrainService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.PAGE_NUMBER;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:46
 * @Description:
 */
@JWT
@ApplicationScoped
@Path("/postTickerTapeTrain")
@Tag(name = "岗位训练-收报管理-训练")
public class PostTickerTapeTrainController {

  private final PostTickerTapeTrainService trainService;

  @Inject
  public PostTickerTapeTrainController(PostTickerTapeTrainService trainService) {
    this.trainService = trainService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练")
  public Response<PostTickerTapeTrainAddParam> add(PostTickerTapeTrainAddParam param, HttpServerRequest request) {
    return ResponseResult.success(trainService.add(param, request));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "分页查询")
  public Response<PageInfo<PostTickerTapeTrainVo>> listPage(Page page, HttpServerRequest request) {
    return ResponseResult.success(trainService.listPage(page, request));
  }

  @POST
  @Path("/getById")
  @Operation(summary = "根据id获取训练")
  public Response<PostTickerTapeTrainVo> getById(TickerTapeTrainQueryParam param) {
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
  @Path("/finish")
  @Operation(summary = "结束")
  public Response<Void> finish(PostTickerTapeTrainUpdateParam updateParam) {
    trainService.finish(updateParam);
    return ResponseResult.success();
  }

  @POST
  @Path("/reset")
  @Operation(summary = "重置")
  public Response<Void> reset(TickerTapeTrainQueryParam param) {
    trainService.reset(param.getId());
    return ResponseResult.success();
  }

  @POST
  @Path("/upLoadResult")
  @Operation(summary = "上传结果")
  public Response<PostTickerTapeTrainVo> uploadResult(PostTickerTapeTrainUploadResultParam param) {
    return ResponseResult.success(trainService.uploadResult(param));
  }

  @GET
  @Path(value = "findPage")
  @Operation(summary = "查询页")
  public Response<PostTickerTapeTrainPageValueVO> findPage(@RestQuery(TRAIN_ID) String trainId, @RestQuery(PAGE_NUMBER) Integer pageNumber) {
    return ResponseResult.success(trainService.findPage(trainId, pageNumber));
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(trainService.delete(trainId));
  }

}
