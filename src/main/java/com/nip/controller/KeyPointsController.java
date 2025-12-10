package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.entity.KeyPointsEntity;
import com.nip.service.KeyPointsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/23 15:55
 */
@JWT
@Path("/keyPoints")
@ApplicationScoped
@Tag(name = "手键报训练-要点讲解")
public class KeyPointsController {
  private final KeyPointsService keyPointsService;

  @Inject
  public KeyPointsController(KeyPointsService keyPointsService) {
    this.keyPointsService = keyPointsService;
  }

  @POST
  @Path("/saveKeyPoints")
  @Operation(summary = "保存要点讲解")
  public Response<KeyPointsEntity> saveKeyPoints(KeyPointsEntity entity) {
    return ResponseResult.success(keyPointsService.saveKeyPoints(entity));
  }

  @POST
  @Path("/findKeyPointsByType")
  @Operation(summary = "查询要点讲解")
  public Response<KeyPointsEntity> findKeyPointsByType(Map<String, Integer> type) {
    return ResponseResult.success(keyPointsService.findById(type.get(TYPE)));
  }
}
