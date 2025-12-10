package com.nip.controller;

import com.nip.common.constants.BaseConstants;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostEnteringExerciseWordStockDto;
import com.nip.dto.vo.PostEnteringExerciseVO;
import com.nip.dto.vo.param.*;
import com.nip.service.PostEnteringExerciseService;
import com.nip.service.PostEnteringExerciseWordStockService;
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
 * @Data: 2022-05-17 10:33
 * @Description:
 */
@JWT
@Path("/postEnteringExercise")
@ApplicationScoped
@Tag(name = "岗位-汉字录入-训练")
public class PostEnteringExerciseController {

  private final PostEnteringExerciseService enteringExerciseService;
  private final PostEnteringExerciseWordStockService stockService;

  @Inject
  public PostEnteringExerciseController(PostEnteringExerciseService enteringExerciseService,
                                        PostEnteringExerciseWordStockService stockService) {
    this.enteringExerciseService = enteringExerciseService;
    this.stockService = stockService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练")
  public Response<PostEnteringExerciseVO> add(PostEnteringExerciseAddParam addParam, @RestHeader(BaseConstants.TOKEN) String token) {
    return ResponseResult.success(enteringExerciseService.add(addParam, token));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "分页")
  public Response<List<PostEnteringExerciseVO>> listPage(PostEnteringExercisePageParam param,
                                                         @RestHeader(BaseConstants.TOKEN) String token) {
    return ResponseResult.success(enteringExerciseService.listPage(param, token));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<Void> begin(PostEnteringExerciseUpdateParam param) {
    enteringExerciseService.begin(param);
    return ResponseResult.success();
  }

  @POST
  @Path("/finish")
  @Operation(summary = "完成训练")
  public Response<Void> finish(PostEnteringExerciseFinishParam param) {
    enteringExerciseService.finish(param);
    return ResponseResult.success();
  }

  @POST
  @Path("/getWordStock")
  @Operation(summary = "根据类型获取字库-暂未使用")
  public Response<PostEnteringExerciseWordStockDto> getWordStock(EnteringExerciseWordStockQueryParam param) {
    return ResponseResult.success(stockService.findByType(param.getType()));
  }


  @POST
  @Path("/getById")
  @Operation(summary = "根据Id查询训练")
  public Response<PostEnteringExerciseVO> getById(PostEnteringExerciseUpdateParam param) {
    return ResponseResult.success(enteringExerciseService.getById(param.getId()));
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(enteringExerciseService.delete(trainId));
  }
}
