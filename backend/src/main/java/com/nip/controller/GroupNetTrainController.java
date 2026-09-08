package com.nip.controller;

import com.nip.common.PageInfo;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.Page;
import com.nip.dto.GroupNetTrainDto;
import com.nip.dto.GroupNetTrainSubmitAnswerDto;
import com.nip.dto.vo.GroupNetTrainDetailsVO;
import com.nip.dto.vo.GroupNetTrainListPageVO;
import com.nip.service.GroupNetTrainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 11:42
 * @Description:
 */
@ApplicationScoped
@JWT
@Path("/groupNetTrain")
@Tag(name = "组网运用")
public class GroupNetTrainController {
  private final GroupNetTrainService trainService;

  @Inject
  public GroupNetTrainController(GroupNetTrainService trainService) {
    this.trainService = trainService;
  }

  @POST
  @Path("/saveTrain")
  @Operation(summary = "添加训练")
  public Response<GroupNetTrainDetailsVO> saveTrain(GroupNetTrainDto dto, @RestHeader(TOKEN) String token) {

    return ResponseResult.success(trainService.save(dto, token));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "分页查询")
  public Response<PageInfo<GroupNetTrainListPageVO>> listPage(Page page, @RestHeader(TOKEN) String token) {

    return ResponseResult.success(trainService.listPage(page, token));
  }

  @GET
  @Path("/details")
  @Operation(summary = "详情")
  public Response<GroupNetTrainDetailsVO> details(@RestQuery(value = ID) Integer id) {
    return ResponseResult.success(trainService.detail(id));
  }


  @POST
  @Path("submitAnswer")
  @Operation(summary = "提交答案")
  public Response<?> submitAnswer(GroupNetTrainSubmitAnswerDto answerDto) {
    trainService.submitAnswer(answerDto);
    return ResponseResult.success();
  }


}
