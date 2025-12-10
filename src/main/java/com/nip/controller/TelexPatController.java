package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.entity.TelexPatEntity;
import com.nip.service.TelexPatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.Map;

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/21 17:59
 */
@JWT
@Path("/telexPat")
@ApplicationScoped
@Tag(name = "发报训练")
public class TelexPatController {
  private final TelexPatService telexPatService;

  @Inject
  public TelexPatController(TelexPatService telexPatService) {
    this.telexPatService = telexPatService;
  }

  @Path("/saveTelexPat")
  @POST
  public Response<TelexPatEntity> saveTelexPat(@RestHeader(TOKEN) String token, Map<String, Object> map) {
    return telexPatService.saveTelexPat(token, (int) map.get("count"), (int) map.get("mistake"), (int) map.get(TYPE), String.valueOf(map.get("duration")));
  }

  @Path("/findTelexPatById")
  @POST
  public Response<TelexPatEntity> findTelexPatById(@RestHeader(TOKEN) String token, Map<String, Integer> map) {
    return telexPatService.findById(token, map.get(TYPE));
  }

  @Path("/deleteTexPatByToken")
  @POST
  public Response<Void> deleteTexPatByToken(@RestHeader(TOKEN) String token, Map<String, Integer> map) {
    return telexPatService.deleteTexPatByToken(token, map.get(TYPE));
  }
}
