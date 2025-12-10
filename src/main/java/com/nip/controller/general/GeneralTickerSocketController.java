package com.nip.controller.general;

import com.nip.common.annotation.RequestPass;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.dto.general.GeneralTickerPatTrainUpdateDto;
import com.nip.service.general.GeneralTickerPatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;
import static com.nip.common.constants.BaseConstants.USER_ID;

@JWT
@Path(value = "/socket/generalTickerPatTrain/")
@ApplicationScoped
public class GeneralTickerSocketController {
  private final GeneralTickerPatService trainService;

  @Inject
  public GeneralTickerSocketController(GeneralTickerPatService trainService) {
    this.trainService = trainService;
  }

  @GET
  @Path("getByTrainIdAndUserId")
  @Operation(summary = "查询人员信息根据用户id和训练id")
  @RequestPass
  public Response<GeneralPatTrainUserDto> getByTrainIdAndUserId(@RestQuery(TRAIN_ID) Integer trainId, @RestQuery(USER_ID) String userId) {
    return ResponseResult.success(trainService.getByTrainIdAndUserId(trainId, userId));
  }

  @POST
  @Path("updateTrainStatus")
  @Operation(summary = "修改训练状态")
  @RequestPass
  public Response<?> updateTrainStatus(@RequestBody GeneralTickerPatTrainUpdateDto dto) {
    trainService.updateStatus(dto);
    return ResponseResult.success("ok");
  }
}
