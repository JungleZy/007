package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.service.RadiotelephoneService;
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
 * @Data: 2022-06-22 09:36
 * @Description:
 */
@JWT
@Path("/radiotelephone")
@ApplicationScoped
@Tag(name = "岗位训练-报话训练")
public class RadiotelephoneController {
  private final RadiotelephoneService radiotelephoneService;

  @Inject
  public RadiotelephoneController(RadiotelephoneService radiotelephoneService) {
    this.radiotelephoneService = radiotelephoneService;
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "统计页面")
  public Response<List<RadiotelephoneVO>> listPage(@RestHeader(TOKEN) String token, RadiotelephoneDto dto) throws Exception {
    List<RadiotelephoneVO> ret = radiotelephoneService.listPage(token, dto);
    return ResponseResult.success(ret);
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练并返回服务端会话标识")
  public Response<RadiotelephoneVO> begin(RadiotelephoneDto dto, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(radiotelephoneService.begin(dto, token));
  }

  @POST
  @Path("/finish")
  @Operation(summary = "结束训练")
  public Response<RadiotelephoneVO> finish(RadiotelephoneDto dto, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(radiotelephoneService.finish(dto, token));
  }

  @POST
  @Path("/pause")
  @Operation(summary = "暂停训练")
  public Response<RadiotelephoneVO> pause(RadiotelephoneDto dto, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(radiotelephoneService.pause(dto, token));
  }

  @POST
  @Path("/resume")
  @Operation(summary = "继续训练")
  public Response<RadiotelephoneVO> resume(RadiotelephoneDto dto, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(radiotelephoneService.resume(dto, token));
  }
}
