package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.EnteringStatisticalVO;
import com.nip.dto.vo.EnteringTelexPatVO;
import com.nip.dto.vo.param.EnteringTelexPatQueryParam;
import com.nip.dto.vo.param.EnteringTelexPatSaveParam;
import com.nip.service.EnteringExerciseService;
import com.nip.service.EnteringTelexPatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 10:36
 * @Description:
 */
@JWT
@Path("/enteringTelexPat")
@ApplicationScoped
@Tag(name = "汉字录入-五笔训练")
public class EnteringTelexPatController {

  private final EnteringTelexPatService telexPatService;
  private final EnteringExerciseService exerciseService;

  @Inject
  public EnteringTelexPatController(EnteringTelexPatService telexPatService, EnteringExerciseService exerciseService) {
    this.telexPatService = telexPatService;
    this.exerciseService = exerciseService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "添加/修改训练")
  public Response<EnteringTelexPatVO> save(@RestHeader(TOKEN) String token, EnteringTelexPatSaveParam param) {
    try {
      return ResponseResult.success(telexPatService.save(token, param));
    } catch (Exception e) {
      return ResponseResult.error(e.getMessage());
    }
  }

  @POST
  @Path("/findByUserIdAndType")
  @Operation(summary = "根据用户和类型查询训练")
  public Response<EnteringTelexPatVO> findByUserIdAndType(EnteringTelexPatQueryParam param,
                                                          @RestHeader(TOKEN) String token) {
    try {
      return ResponseResult.success(telexPatService.findByUserIdAndType(token, param.getType()));
    } catch (Exception e) {
      return ResponseResult.error(e.getMessage());
    }
  }

  @POST
  @Path("/clear")
  @Operation(summary = "清空训练")
  public Response<EnteringTelexPatVO> clear(EnteringTelexPatQueryParam param, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(telexPatService.clear(param, token));
  }

  @POST
  @Path("/statisticalPage")
  public Response<List<EnteringStatisticalVO>> statisticalPage(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(exerciseService.statisticalPage(token, 1));
  }
}
