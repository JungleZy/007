package com.nip.controller.general;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.PojoUtils;
import com.nip.dto.GeneralTickerPatTrainUserDto;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.ws.WebSocketGeneralTickerPatService;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@JWT
@Path("/openApi/generalTickerPatTrain")
@ApplicationScoped
@Tag(name = "综合训练-手键拍发Api")
@Slf4j
public class GeneralTickerPatTrainController {

  @GET
  @Path("/综合训练-手键拍发Api")
  @Operation(summary = "查询在线学员信息")
  public Response<List<GeneralPatTrainUserDto>> findUserInfo(@RestQuery(TRAIN_ID) Integer trainId) {
    log.info("查询用户信息");
    GeneralTickerPatTrainRoomUserModel trainRoomUser = WebSocketGeneralTickerPatService.PAT_ROOM.get(trainId);
    if (trainRoomUser == null) {
      return ResponseResult.success(new ArrayList<>());
    }
    log.info("组训人已处理完");
    List<GeneralTickerPatTrainUserModel> joinUser = new ArrayList<>(trainRoomUser.getJoinUser());
    if (trainRoomUser.getGroupUser() != null) {
      joinUser.add(trainRoomUser.getGroupUser());
    }
    log.info("参训人已处理玩");
    return ResponseResult.success(PojoUtils.convert(joinUser, GeneralPatTrainUserDto.class));
  }

  @GET
  @Path("/findTrainInfo")
  @Operation(summary = "查询房间人员信息")
  public Response<Map<Integer, List<GeneralTickerPatTrainUserDto>>> findTrainInfo() {
    Map<Integer, List<GeneralTickerPatTrainUserDto>> ret = new HashMap<>();
    WebSocketGeneralTickerPatService.PAT_ROOM.forEach((key, value) -> {
      GeneralTickerPatTrainRoomUserModel trainRoomUser = WebSocketGeneralTickerPatService.PAT_ROOM.get(key);
      List<GeneralTickerPatTrainUserModel> joinUser = new ArrayList<>(trainRoomUser.getJoinUser());
      if (trainRoomUser.getGroupUser() != null) {
        joinUser.add(trainRoomUser.getGroupUser());
      }
      ret.put(key, PojoUtils.convert(joinUser, GeneralTickerPatTrainUserDto.class));
    });

    return ResponseResult.success(ret);
  }
}
