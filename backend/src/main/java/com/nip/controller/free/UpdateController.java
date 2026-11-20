package com.nip.controller.free;

import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.UpdateInfoDto;
import com.nip.service.UpdatePackageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.nio.file.Files;

/**
 * 软件升级的匿名端点（docs/specs/2026-11-19-software-update-spec.md U07）。
 *
 * <p>放在 free 包的理由：组网客户端在登录之前就要比对版本，版本不一致时根本不允许登录；
 * 升级包不含机密，其可信性由客户端用内置公钥验签保证，不依赖本端点的访问控制。
 */
@Path("/update")
@ApplicationScoped
@Tag(name = "软件升级接口")
public class UpdateController {
  private final UpdatePackageService service;

  @Inject
  public UpdateController(UpdatePackageService service) {
    this.service = service;
  }

  @GET
  @Path("/info")
  @Operation(summary = "本机产品版本与可分发的升级包")
  public Response<UpdateInfoDto> info() {
    return ResponseResult.success(service.info());
  }

  @GET
  @Path("/package")
  @Operation(summary = "按平台与风格下载升级包")
  public jakarta.ws.rs.core.Response download(@RestQuery("target") String target, @RestQuery("style") String style) {
    if (!UpdatePackageService.supported(target, style)) {
      return envelope(ResponseResult.error(ResponseCode.PARAMS_ERROR));
    }
    var stored = service.find(target, style);
    if (stored.isEmpty()) {
      return envelope(ResponseResult.error(ResponseCode.PARAMS_ERROR.getCode(), "没有适用于该平台与风格的升级包"));
    }
    var file = stored.get().file();
    StreamingOutput body = output -> Files.copy(file, output);
    return jakarta.ws.rs.core.Response.ok(body, MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"" + stored.get().info().getFileName() + "\"")
        .header("Content-Length", stored.get().info().getSize())
        .header("Cache-Control", "no-store")
        .build();
  }

  // 业务拒绝仍是 HTTP200 信封；客户端据 Content-Type 区分信封与包文件。
  private static jakarta.ws.rs.core.Response envelope(Response<Object> body) {
    return jakarta.ws.rs.core.Response.ok(body, MediaType.APPLICATION_JSON).build();
  }
}
