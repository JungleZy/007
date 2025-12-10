package com.nip.controller.general;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.Page;
import com.nip.dto.general.GeneralTickerPatTrainVO;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainPageParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainQueryParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainResetParam;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainContentVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainContentValueVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainFinishVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainStatisticVO;
import com.nip.service.general.GeneralTickerPatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@JWT
@Path(value = "/generalTickerPatTrain")
@ApplicationScoped
@Tag(name = "综合训练-手键拍发")
public class GeneralTickerPatController {
  private final GeneralTickerPatService patTrainService;

  @Inject
  public GeneralTickerPatController(GeneralTickerPatService patTrainService) {
    this.patTrainService = patTrainService;
  }

  @POST
  @Path("add")
  @Operation(summary = "添加")
  public Response<GeneralTickerPatTrainVO> add(@RequestBody GeneralTickerPatTrainAddParam param, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(patTrainService.add(param, token));
  }

  @POST
  @Path("findPage")
  @Operation(summary = "按页查询报低")
  public Response<GeneralTickerPatTrainContentVO> findPage(@RequestBody GeneralTickerPatTrainPageParam param, @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(patTrainService.findMessageBody(param, token));
  }

  @POST
  @Path("findAll")
  @Operation(summary = "查询所有训练")
  public Response<PageInfo<GeneralTickerPatTrainVO>> findAll(@RestHeader(TOKEN) String token, @RequestBody Page page) throws Exception {
    return ResponseResult.success(patTrainService.findAll(token, page));
  }

  @POST
  @Path("detail")
  @Operation(summary = "查询训练详情")
  public Response<GeneralTickerPatTrainVO> detail(@RequestBody GeneralTickerPatTrainQueryParam param) {
    return ResponseResult.success(patTrainService.detail(param));
  }

  @POST
  @Path("finish")
  @Operation(summary = "完成训练")
  public Response<GeneralTickerPatTrainVO> finish(@RequestBody GeneralTickerPatTrainFinishVO vo) {
    return ResponseResult.success(patTrainService.finish(vo));
  }

  @POST
  @Path("uploadResult")
  @Operation(summary = "上传提交结果")
  public Response<Void> uploadResult(@RequestBody GeneralTickerPatTrainContentValueVO vo) {
    patTrainService.saveContentValue(vo);
    return ResponseResult.success();
  }


  @POST
  @Path("reset")
  @Operation(summary = "重置训练")
  public Response<String> resetTrain(@RequestBody GeneralTickerPatTrainResetParam param) {
    patTrainService.reset(param);
    return ResponseResult.success("ok");
  }

  @POST
  @Path("statistics")
  @Operation(summary = "统计信息")
  public Response<GeneralTickerPatTrainStatisticVO> statistic(@RequestBody GeneralTickerPatTrainResetParam param) {
    return ResponseResult.success(patTrainService.statistic(param));
  }
  @GET
  @Path("/startTrain")
  @Operation(summary = "用户开始训练")
  public Response<Void> startTrain(@RestQuery(TRAIN_ID) Integer trainId, @RestHeader(TOKEN) String token) {
    patTrainService.startTrain(trainId,token);
    return ResponseResult.success();
  }
}
