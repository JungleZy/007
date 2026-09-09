package com.nip.common.interceptor;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.ResponseResult;
import com.nip.dao.RoleDao;
import com.nip.entity.UserEntity;
import com.nip.service.UserService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;

import static com.nip.common.constants.BaseConstants.TOKEN;

@RequireAdmin
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
public class RequireAdminInterceptor {
  @Context
  HttpServerRequest request;

  @Inject
  UserService userService;

  @Inject
  RoleDao roleDao;

  @AroundInvoke
  Object execute(InvocationContext context) throws Exception {
    String token = request.getHeader(TOKEN);
    if (StringUtils.isEmpty(token)) {
      token = request.getParam(TOKEN);
    }
    UserEntity user = userService.getUserByToken(token);
    if (!roleDao.existsAdminRoleByUserId(user.getId())) {
      throw new WebApplicationException(
          jakarta.ws.rs.core.Response.ok(ResponseResult.error(ResponseCode.CODE_207))
              .type(MediaType.APPLICATION_JSON)
              .build());
    }
    return context.proceed();
  }
}
