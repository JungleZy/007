package com.nip.controller;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.Page;
import com.nip.dto.PostTelexPatTrainDto;
import com.nip.dto.vo.PostTelexPatTrainPageInfoVO;
import com.nip.dto.vo.PostTelexPatTrainPageValueVO;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
import com.nip.dto.vo.param.PostTelexPatTrainParam;
import com.nip.service.PostTelexPatTrainService;
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

import static com.nip.common.constants.BaseConstants.*;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 14:18
 * @Description:
 */
@Tag(name = "岗位训练-发报训练-电传拍发")
@ApplicationScoped
@Path("/postTelexPatTrain")
@JWT
public class PostTelexPatTrainController {

  private final PostTelexPatTrainService postTelexPatTrainService;

  @Inject
  public PostTelexPatTrainController(PostTelexPatTrainService postTelexPatTrainService) {
    this.postTelexPatTrainService = postTelexPatTrainService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "新增训练")
  public Response<PostTelexPatTrainVO> save(PostTelexPatTrainDto dto, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.save(dto, token));
  }

  @POST
  @Path("/findAll")
  @Operation(summary = "根据类型查询该用户所有训练")
  //@ApiImplicitParam(name = "trainType",value = "训练类型 0 电传拍发 1 数据报拍发")
  public Response<PageInfo<PostTelexPatTrainVO>> findAll(@RestHeader(TOKEN) String token, @RestQuery(TRAIN_TYPE) Integer trainType, @RequestBody Page page) {
    return ResponseResult.success(postTelexPatTrainService.findAll(token, trainType,page));
  }

  @POST
  @Path("/detail")
  @Operation(summary = "详情")
  public Response<PostTelexPatTrainVO> detail(PostTelexPatTrainParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.detail(param, token));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<PostTelexPatTrainVO> begin(PostTelexPatTrainParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.begin(param, token));
  }

  @POST
  @Path("/pause")
  @Operation(summary = "暂停训练")
  public Response<PostTelexPatTrainVO> pause(PostTelexPatTrainParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.pause(param, token));
  }

  @POST
  @Path("/resume")
  @Operation(summary = "恢复训练")
  public Response<PostTelexPatTrainVO> resume(PostTelexPatTrainParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.resume(param, token));
  }

  @POST
  @Path("/reset")
  @Operation(summary = "重置训练轮次并保留报底")
  public Response<PostTelexPatTrainVO> reset(PostTelexPatTrainParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.reset(param, token));
  }

  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<PostTelexPatTrainVO> finish(PostTelexPatTrainFinishParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.finish(param, token));
  }

  @GET
  @Path("/getPage")
  @Operation(summary = "查询指定页的内容")
  public Response<PostTelexPatTrainPageInfoVO> getPage(@RestQuery(TRAIN_ID) String trainId, @RestQuery(PAGE_NUMBER) Integer pageNumber, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.getPage(trainId, pageNumber, token));
  }

  @POST
  @Path("/finishPage")
  @Operation(summary = "上传每页拍发内容")
  public Response<PostTelexPatTrainPageInfoVO> finishPage(@RequestBody PostTelexPatTrainPageValueVO vo, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.finishPage(vo, token));
  }

  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelexPatTrainService.delete(trainId, token));
  }
}
