package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
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
  public Response<Void> begin(PostTelegraphKeyPatTrainDto dto) {
    patTrainService.begin(dto);
    return ResponseResult.success();
  }

  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<PostTelegraphKeyPatTrainVO> finish(PostTelegraphKeyPatTrainDto dto) {
    return ResponseResult.success(patTrainService.finish(dto));
  }

  @POST
  @Path("/details")
  @Operation(summary = "详情")
  public Response<PostTelegraphKeyPatTrainVO> details(PostTelegraphKeyPatTrainDto dto) {
    return ResponseResult.success(patTrainService.details(dto.getId()));
  }

  @GET
  @Path("/getPage")
  @Operation(summary = "获取指定页得content")
  public Response<PostTelegraphKeyPatTrainPageVO> getPage(@RestQuery(TRAIN_ID) String trainId, @RestQuery(PAGE_NUMBER) Integer pageNumber) {
    return ResponseResult.success(patTrainService.getPage(trainId, pageNumber));
  }

  @POST
  @Path(value = "/finishPage")
  @Operation(summary = "提交当前完成页")
  public Response<Void> finishPage(List<PostTelegraphKeyPatTrainPageMessageVO> vos,
                                   @RestQuery(TRAIN_ID) String trainId,
                                   @RestQuery(PAGE_NUMBER) Integer pageNumber) {
    patTrainService.finishPage(vos, trainId, pageNumber);
    return ResponseResult.success();
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(patTrainService.delete(trainId));
  }
}
