package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.dto.PostTelegraphKeyPatTrainActionDto;
import com.nip.dto.PostTelegraphKeyPatTrainPageDto;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainVO;
import com.nip.service.PostTelegraphKeyPatTrainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

import static com.nip.common.constants.BaseConstants.*;

/**
 * @Author: wushilin
 * @Data: 2022-06-13 09:13
 * @Description:
 */
@ApplicationScoped
@Tag(name = "岗位训练-发报训练-电子键拍发")
@Path("/PostTelegraphKeyPatTrain")
@JWT
public class PostTelegraphKeyPatTrainController {

  private final PostTelegraphKeyPatTrainService patTrainService;

  @Inject
  public PostTelegraphKeyPatTrainController(PostTelegraphKeyPatTrainService patTrainService) {
    this.patTrainService = patTrainService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练")
  public Response<PostTelegraphKeyPatTrainVO> add(PostTelegraphKeyPatTrainDto dto,
                                                  @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.add(dto, token));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "列表")
  public Response<List<PostTelegraphKeyPatTrainVO>> listPage(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.listPage(token));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<PostTelegraphKeyPatTrainVO> begin(PostTelegraphKeyPatTrainActionDto dto,
      @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.begin(dto, token));
  }

  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<PostTelegraphKeyPatTrainVO> finish(PostTelegraphKeyPatTrainActionDto dto,
      @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.finish(dto, token));
  }

  @POST
  @Path("/details")
  @Operation(summary = "详情")
  public Response<PostTelegraphKeyPatTrainVO> details(PostTelegraphKeyPatTrainActionDto dto,
      @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.details(dto.getId(), token));
  }

  @GET
  @Path("/getPage")
  @Operation(summary = "获取指定页得content")
  public Response<PostTelegraphKeyPatTrainPageVO> getPage(@RestQuery(TRAIN_ID) String trainId,
      @RestQuery(PAGE_NUMBER) Integer pageNumber, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.getPage(trainId, pageNumber, token));
  }

  @POST
  @Path(value = "/finishPage")
  @Operation(summary = "提交当前完成页")
  public Response<PostTelegraphKeyPatTrainPageVO> finishPage(PostTelegraphKeyPatTrainPageDto dto,
      @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.finishPage(dto, token));
  }

  @POST
  @Path("/reset")
  @Operation(summary = "重置训练轮次，保留报底与冻结规则")
  public Response<PostTelegraphKeyPatTrainVO> reset(PostTelegraphKeyPatTrainActionDto dto,
      @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.reset(dto, token));
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(patTrainService.delete(trainId, token));
  }
}
