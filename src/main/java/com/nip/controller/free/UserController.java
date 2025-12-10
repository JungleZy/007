package com.nip.controller.free;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.UserInfoDto;
import com.nip.entity.UserEntity;
import com.nip.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;

@Path("/user")
@ApplicationScoped
@Tag(name = "用户管理接口-无拦截")
public class UserController {

  @Inject
  UserService userService;

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
}
