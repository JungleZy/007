package com.nip.controller;

import com.nip.common.constants.BaseConstants;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostMilitaryTermTrainAddDto;
import com.nip.dto.PostMilitaryTermTrainFinishDto;
import com.nip.dto.vo.PostMilitaryTermTrainVO;
import com.nip.service.PostMilitaryTermTrainService;
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

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 15:39
 * @Description:
 */
@JWT
@Path("/postMilitaryTermTrain")
@ApplicationScoped
@Tag(name = "岗位训练-报话训练-军语密语训练")
public class PostMilitaryTermTrainController {

  private final PostMilitaryTermTrainService postMilitaryTermTrainService;

  @Inject
  public PostMilitaryTermTrainController(PostMilitaryTermTrainService postMilitaryTermTrainService) {
    this.postMilitaryTermTrainService = postMilitaryTermTrainService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "新增训练")
  public Response<PostMilitaryTermTrainVO> add(PostMilitaryTermTrainAddDto dto, @RestHeader(BaseConstants.TOKEN) String token) {
    return ResponseResult.success(postMilitaryTermTrainService.add(dto, token));
  }


  @POST
  @Path("/listPage")
  @Operation(summary = "列表")
  public Response<List<PostMilitaryTermTrainVO>> listPage(@RestHeader(BaseConstants.TOKEN) String token) {
    return ResponseResult.success(postMilitaryTermTrainService.listPage(token));
  }

  @POST
  @Path("/details")
  @Operation(summary = "详情（只传入Id）")
  public Response<PostMilitaryTermTrainVO> details(PostMilitaryTermTrainVO vo) {
    return ResponseResult.success(postMilitaryTermTrainService.details(vo));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练（只传入ID）")
  public Response<PostMilitaryTermTrainVO> begin(PostMilitaryTermTrainVO vo) {
    return ResponseResult.success(postMilitaryTermTrainService.begin(vo.getId()));
  }

  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<PostMilitaryTermTrainVO> finish(PostMilitaryTermTrainFinishDto dto) {
    return ResponseResult.success(postMilitaryTermTrainService.finish(dto));
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(postMilitaryTermTrainService.delete(trainId));
  }
}
