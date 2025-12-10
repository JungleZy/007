package com.nip.controller;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.TelegraphKeyPatSyntheticalDto;
import com.nip.dto.vo.TelegraphKeyPatSyntheticalVO;
import com.nip.dto.vo.TelegraphKeyTrainStatisticalVO;
import com.nip.service.TelegraphKeyPatSyntheticalService;
import com.nip.service.TelegraphKeyTrainStatisticalService;
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
 * @Data: 2022-06-09 10:23
 * @Description:
 */
@ApplicationScoped
@Path("/telegraphKeyPatTrainSynthetical")
@Tag(name = "岗前训练-电子键拍发-综合训练")
public class TelegraphKeyPatTrainSyntheticalController {
  private final TelegraphKeyPatSyntheticalService syntheticalService;
  private final TelegraphKeyTrainStatisticalService statisticalService;

  @Inject
  public TelegraphKeyPatTrainSyntheticalController(TelegraphKeyPatSyntheticalService syntheticalService, TelegraphKeyTrainStatisticalService statisticalService) {
    this.syntheticalService = syntheticalService;
    this.statisticalService = statisticalService;
  }


  @Path("/findAll")
  @POST
  @Operation(summary = "查询该用户所有训练")
  public Response<List<TelegraphKeyPatSyntheticalVO>> findAll(@RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(syntheticalService.findAll(token));
  }

  @Path("/save")
  @POST
  @Operation(summary = "添加训练")
  public Response<TelegraphKeyPatSyntheticalVO> save(@RestHeader(TOKEN) String token,
                                                     TelegraphKeyPatSyntheticalDto dto) {
    return ResponseResult.success(syntheticalService.save(token, dto));
  }

  @Path("/begin")
  @POST
  @Operation(summary = "开始")
  public Response<TelegraphKeyPatSyntheticalVO> begin(TelegraphKeyPatSyntheticalDto dto) {
    return ResponseResult.success(syntheticalService.begin(dto.getId()));
  }

  @Path("/stop")
  @POST
  @Operation(summary = "暂停")
  public Response<TelegraphKeyPatSyntheticalVO> stop(TelegraphKeyPatSyntheticalDto dto) {
    return ResponseResult.success(syntheticalService.stop(dto));
  }

  @Path("/goTo")
  @POST
  @Operation(summary = "继续训练")
  public Response<TelegraphKeyPatSyntheticalVO> goTo(TelegraphKeyPatSyntheticalDto dto) {
    return ResponseResult.success(syntheticalService.goTo(dto.getId()));
  }

  @Path("/finish")
  @POST
  @Operation(summary = "完成训练")
  public Response<TelegraphKeyPatSyntheticalVO> finish(TelegraphKeyPatSyntheticalDto dto) {
    return ResponseResult.success(syntheticalService.finish(dto));
  }

  @Path("/findById")
  @POST
  @Operation(summary = "根据id查询")
  public Response<TelegraphKeyPatSyntheticalVO> findById(TelegraphKeyPatSyntheticalDto dto) {
    return ResponseResult.success(syntheticalService.findById(dto));
  }

  @Path("/lastTrain")
  @POST
  @Operation(summary = "查询最后一次训练")
  public Response<TelegraphKeyPatSyntheticalVO> lastTrain(@RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(syntheticalService.lastTrain(token));
  }

  @Path("/statisticalPage")
  @POST
  @Operation(summary = "统计页面")
  public Response<List<TelegraphKeyTrainStatisticalVO>> statisticalPage(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(statisticalService.statisticalPage(token));
  }
}
