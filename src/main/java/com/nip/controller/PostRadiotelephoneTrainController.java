package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostRadiotelephoneDto;
import com.nip.dto.PostRadiotelephoneTermDataDto;
import com.nip.dto.vo.PostRadiotelephoneTermDataVO;
import com.nip.dto.vo.PostRadiotelephoneVO;
import com.nip.service.PostRadiotelephoneService;
import com.nip.service.RadiotelephoneTermDataService;
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

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 14:54
 * @Description:
 */
@JWT
@Path("/postRadiotelephoneTrain")
@ApplicationScoped
@Tag(name = "岗位训练-报话训练")
public class PostRadiotelephoneTrainController {
  private final RadiotelephoneTermDataService dataService;
  private final PostRadiotelephoneService service;

  @Inject
  public PostRadiotelephoneTrainController(RadiotelephoneTermDataService dataService,
                                           PostRadiotelephoneService service) {
    this.dataService = dataService;
    this.service = service;
  }

  @POST
  @Path("/findByType")
  @Operation(summary = "根据类型查询")
  public Response<List<PostRadiotelephoneTermDataVO>> findByType(PostRadiotelephoneTermDataDto dto) {
    return ResponseResult.success(dataService.findByType(dto.getType()));
  }

  @POST
  @Path("/findByTypeAndRandom")
  @Operation(summary = "根据类型查询并随机获取指定个数")
  public Response<List<PostRadiotelephoneTermDataVO>> findByTypeAndRandom(PostRadiotelephoneTermDataDto dto) {
    return ResponseResult.success(dataService.findByTypeAndRandom(dto));
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练")
  public Response<PostRadiotelephoneVO> add(@RestHeader(TOKEN) String token, PostRadiotelephoneDto dto)
      throws Exception {
    return ResponseResult.success(service.add(token, dto));
  }

  @POST
  @Path("/listPge")
  @Operation(summary = "列表")
  public Response<List<PostRadiotelephoneVO>> listPage(@RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(service.listPage(token));
  }

  @POST
  @Path("/begin")
  @Operation(summary = "开始训练")
  public Response<Void> begin(PostRadiotelephoneVO vo) {
    service.begin(vo);
    return ResponseResult.success();
  }

  @POST
  @Path("/finish")
  @Operation(summary = "结束训练")
  public Response<Void> finish(PostRadiotelephoneVO vo) {
    service.finish(vo);
    return ResponseResult.success();
  }

  @POST
  @Path("/details")
  @Operation(summary = "详情")
  public Response<PostRadiotelephoneVO> details(PostRadiotelephoneVO vo) {
    return ResponseResult.success(service.details(vo));
  }
  @GET
  @Path(value = "delete")
  @Operation(summary = "删除训练")
  public Response<Boolean> delete(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(service.delete(trainId));
  }
}
