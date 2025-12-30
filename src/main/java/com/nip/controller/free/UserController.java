package com.nip.controller.free;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.UserInfoDto;
import com.nip.dto.vo.HandKeyRecentTrainVO;
import com.nip.dto.vo.UserTrainDurationStatVO;
import com.nip.entity.UserEntity;
import com.nip.service.UserService;
import com.nip.service.UserTrainStatisticsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.USER_ID;

@Path("/user")
@ApplicationScoped
@Tag(name = "用户管理接口-无拦截")
public class UserController {

  @Inject
  UserService userService;

  @Inject
  UserTrainStatisticsService userTrainStatisticsService;

  @POST
  @Path("/login")
  public Response<UserInfoDto> login(Map<String, String> data) {
    String userAccount = data.get("userAccount");
    String password = data.get("password");
    String deviceId = data.get(DEVICE_ID);
    if (StringUtils.isEmpty(userAccount) || StringUtils.isEmpty(password) || StringUtils.isEmpty(deviceId)) {
      return ResponseResult.error(ResponseCode.NULL_ERROR);
    }
    return userService.login(userAccount, password, deviceId);
  }

  @POST
  @Path("/signin")
  public Response<Object> signin(UserEntity entity) {
    return userService.addUser(entity, true);
  }

  @POST
  @Path("/test")
  public Response<UserEntity> test() {
    return ResponseResult.success(userService.getUserById(""));
  }

  @POST
  @Path("/getUserTrainDurationStat")
  @Operation(summary = "统计当前用户训练时长")
  public Response<UserTrainDurationStatVO> getUserTrainDurationStat(Map<String, String> map) {
    return ResponseResult.success(
        userTrainStatisticsService.getUserTrainDurationStat(
            map.get(USER_ID),
            map.getOrDefault("startTime", null),
            map.getOrDefault("endTime", null)));
  }

  @POST
  @Path("/getRecentHandKeyTrains")
  @Operation(summary = "最近十次手键拍发数据（训练时间、得分、速率）")
  public Response<List<HandKeyRecentTrainVO>> getRecentHandKeyTrains(Map<String, String> map) {
    return ResponseResult.success(userTrainStatisticsService.getRecentHandKeyTrains(map.get(USER_ID)));
  }

  @POST
  @Path("/getRecentElectronicKeyTrains")
  @Operation(summary = "最近十次电子键拍发数据（训练开始时间、训练时间、得分、速率）")
  public Response<List<HandKeyRecentTrainVO>> getRecentElectronicKeyTrains(Map<String, String> map) {
    return ResponseResult.success(userTrainStatisticsService.getRecentElectronicKeyTrains(map.get(USER_ID)));
  }
}
