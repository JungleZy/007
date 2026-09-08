package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.entity.EnteringKeyPointsEntity;
import com.nip.service.EnteringKeyPointsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 09:25
 * @Description:
 */
@JWT
@Path("/enteringKeyPoints")
@ApplicationScoped
@Tag(name = "汉字录入-要点讲解")
public class EnteringKeyPointsController {

  private final EnteringKeyPointsService keyPointsService;

  @Inject
  public EnteringKeyPointsController(EnteringKeyPointsService keyPointsService) {
    this.keyPointsService = keyPointsService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "保存要点")
  public Response<EnteringKeyPointsEntity> save(EnteringKeyPointsEntity entity) {
    return ResponseResult.success(keyPointsService.save(entity));
  }

  @POST
  @Path("/getByType")
  @Operation(summary = "查询要点")
  public Response<EnteringKeyPointsEntity> getByType(Map<String, Integer> type) {
    return ResponseResult.success(keyPointsService.getByType(type.get(TYPE)));
  }

}
