package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.entity.ReceiveKeyPointsEntity;
import com.nip.service.ReceiveKeyPointsService;
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
 * @Data: 2022-04-11 09:25
 * @Description:
 */
@JWT
@Path("/receiveKeyPoints")
@ApplicationScoped
@Tag(name = "收报训练-要点讲解")
public class ReceiveKeyPointsController {

  private final ReceiveKeyPointsService keyPointsService;

  @Inject
  public ReceiveKeyPointsController(ReceiveKeyPointsService keyPointsService) {
    this.keyPointsService = keyPointsService;
  }


  @POST
  @Path("/save")
  @Operation(summary = "保存要点")
  public Response<ReceiveKeyPointsEntity> save(ReceiveKeyPointsEntity param) {
    return ResponseResult.success(keyPointsService.save(param));
  }

  @POST
  @Path("/getByType")
  @Operation(summary = "查询要点")
  public Response<ReceiveKeyPointsEntity> getByType(Map<String, Integer> type) {
    return ResponseResult.success(keyPointsService.getByType(type.get(TYPE)));
  }

}
