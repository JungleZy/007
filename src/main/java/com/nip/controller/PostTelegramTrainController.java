package com.nip.controller;


import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostTelegramTrainContentValueDto;
import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.vo.PostTelegramTrainAddContentValueVO;
import com.nip.dto.vo.PostTelegramTrainContentVO;
import com.nip.dto.vo.PostTelegramTrainVO;
import com.nip.dto.vo.param.PostTelegramTrainAddParam;
import com.nip.dto.vo.param.PostTelegramTrainFloorContentQueryParam;
import com.nip.dto.vo.param.PostTelegramTrainQueryParam;
import com.nip.service.PostTelegramTrainService;
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

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:45
 * @Description:
 */
@Path("/postTelegramTrain")
@Tag(name = "岗位训练-手键拍发")
@ApplicationScoped
@JWT
public class PostTelegramTrainController {
  private final PostTelegramTrainService postTelegramTrainService;

  @Inject
  public PostTelegramTrainController(PostTelegramTrainService postTelegramTrainService) {
    this.postTelegramTrainService = postTelegramTrainService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "添加训练")
  public Response<PostTelegramTrainVO> save(PostTelegramTrainAddParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(postTelegramTrainService.save(param, token));
  }

  @POST
  @Path("/findAll")
  @Operation(summary = "查询用户所有训练")
  public Response<List<PostTelegramTrainVO>> findAll(@RestHeader(TOKEN) String token) {
    return ResponseResult.success((postTelegramTrainService.findAll(token)));
  }

  @POST
  @Path("/detail")
  @Operation(summary = "详情")
  public Response<PostTelegramTrainVO> detail(PostTelegramTrainQueryParam param) {
    return ResponseResult.success((postTelegramTrainService.detail(param)));
  }

  @POST
  @Path("/findMessageBody")
  @Operation(summary = "根据训练id和报底编号查询报文内容")
  public Response<PostTelegramTrainContentVO> findMessageBody(PostTelegramTrainFloorContentQueryParam param) {
    return ResponseResult.success((postTelegramTrainService.findMessageBody(param)));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<PostTelegramTrainVO> begin(PostTelegramTrainQueryParam param) {
    return ResponseResult.success((postTelegramTrainService.begin(param.getId())));
  }

  @POST
  @Path("/printBottomReport")
  @Operation(summary = "打印报底——生成报底")
  public Response<List<String>> printBottomReport(@RequestBody PostTelegramTrainQueryParam param) {
    return ResponseResult.success(postTelegramTrainService.printBottomReport(param));
  }

  @POST
  @Path("/stop")
  @Operation(summary = "重置训练")
  public Response<Void> stop(PostTelegramTrainQueryParam param) {
    postTelegramTrainService.stop(param.getId());
    return ResponseResult.success();
  }


  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<PostTelegramTrainVO> finish(PostTelegramTrainFinishDto dto) {
    return ResponseResult.success((postTelegramTrainService.finish(dto)));
  }

  @POST
  @Path("/saveContentValue")
  @Operation(summary = "记录用户拍发时间")
  public Response<Void> saveContentValue(PostTelegramTrainContentValueDto dto) {
    postTelegramTrainService.saveContentValue(dto);
    return ResponseResult.success();
  }

  @POST
  @Path("/addContentValue")
  @Operation(summary = "追加content")
  public Response<List<Integer>> addContentValue(PostTelegramTrainAddContentValueVO vo) {
    return ResponseResult.success((postTelegramTrainService.addContentValue(vo)));
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(postTelegramTrainService.delete(trainId));
  }
}
