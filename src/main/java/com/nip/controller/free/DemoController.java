package com.nip.controller.free;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.service.DemoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/demo")
@ApplicationScoped
public class DemoController {
  @Inject
  DemoService demoService;

  @GET
  @Path("/test")
  public Response<Void> test() {
    demoService.test();
    return ResponseResult.success();
  }
}
