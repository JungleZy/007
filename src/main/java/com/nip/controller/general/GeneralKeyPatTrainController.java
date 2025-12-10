package com.nip.controller.general;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.PojoUtils;
import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.ws.WebSocketGeneralKeyPatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.ArrayList;
import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@JWT
@Path("/GeneralKeyPatTrain")
@ApplicationScoped
@Tag(name = "综合组训-电子键")
public class GeneralKeyPatTrainController {
  @GET
  @Path("/getOneline/{trainId}")
  @Operation(summary = "获取在线人数")
  public Response<List<GeneralPatTrainUserDto>> getOneLien(@RestQuery(TRAIN_ID) Integer trainId) {
    GeneralPatTrainRoomUserDto trainRoomUser = WebSocketGeneralKeyPatService.ROOM.get(trainId);
    if (trainRoomUser == null) {
      return ResponseResult.success(new ArrayList<>());
    }
    List<GeneralPatTrainUserModelDto> joinUser = new ArrayList<>(trainRoomUser.getJoinUser());
    if (trainRoomUser.getGroupUser() != null) {
      joinUser.add(trainRoomUser.getGroupUser());
    }
    return ResponseResult.success(PojoUtils.convert(joinUser, GeneralPatTrainUserDto.class));
  }
}
