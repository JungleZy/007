package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.TelexPatTrainDto;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.dto.vo.TelexPatTrainVO;
import com.nip.dto.vo.param.TelexPatTrainQueryParam;
import com.nip.entity.TelexPatTrainEntity;
import com.nip.service.TelexPatTrainService;
import com.nip.service.TelexPatTrainStatisticalService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 10:03
 */
@JWT
@Path("/telexPat")
@ApplicationScoped
@Tag(name = "岗前训练-发报训练-电传拍发")
public class TexPatTrainController {
  private final TelexPatTrainService telexPatTrainService;
  private final TelexPatTrainStatisticalService statisticalService;

  @Inject
  public TexPatTrainController(TelexPatTrainService telexPatTrainService, TelexPatTrainStatisticalService statisticalService) {
    this.telexPatTrainService = telexPatTrainService;
    this.statisticalService = statisticalService;
  }

  @POST
  @Path("/saveTexPatTrain")
  public Response<TelexPatTrainEntity> saveTexPatTrain(@RestHeader(TOKEN) String token, TelexPatTrainDto dto) {
    return telexPatTrainService.saveTexPatTrain(token, dto);
  }

  @POST
  @Path("/findTexPatTrainByToken")
  public Response<List<TelexPatTrainEntity>> findTexPatTrainByToken(@RestHeader(TOKEN) String token) {
    return telexPatTrainService.findTexPatTrainByToken(token);
  }

  //  @PostMapping("/deleteTexPatTrainByToken")
//  public Response deleteTexPatTrainByToken(@RestHeader(BaseConstants.TOKEN) String token,  Map<String,String> dto) {
//    return telexPatTrainService.deleteTexPatTrainByToken(token,Integer.valueOf(dto.get("type")));
//  }
  @Path("/findTexPatTrainById")
  @POST
  public Response<TelexPatTrainEntity> findTexPatTrainById(Map<String, String> dto) {
    return telexPatTrainService.findTexPatTrainById(dto.get(ID));
  }

  @Path("/statisticalPage")
  @POST
  @Operation(summary = "统计页面")
  public Response<List<TelexPatTrainStatisticalVO>> statisticalPage(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(statisticalService.statisticalPage(token));
  }

  @Path("/lastPatTrain")
  @POST
  @Operation(summary = "查询最后一次训练")
  public Response<TelexPatTrainVO> lastPatTrain(@RestHeader(TOKEN) String token, TelexPatTrainQueryParam param) {
    return ResponseResult.success(telexPatTrainService.lastPatTrain(token, param.getType()));
  }

  @Path("/deleteById")
  @POST
  @Operation(summary = "删除训练")
  public Response<Void> delete(TelexPatTrainVO vo) {
    telexPatTrainService.deleteById(vo.getId());
    return ResponseResult.success();
  }
}
