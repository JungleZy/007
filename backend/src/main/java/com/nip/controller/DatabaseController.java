package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.interceptor.RequireAdmin;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.service.DatabaseTransferService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.FILE;
import static com.nip.common.constants.BaseConstants.CONFIRMATION;

@JWT
@RequireAdmin
@Path("/database")
@ApplicationScoped
public class DatabaseController {
  private final DatabaseTransferService service;

  @Inject
  public DatabaseController(DatabaseTransferService service) {
    this.service = service;
  }

  @GET
  @Path("/status")
  public Response<Map<String, Object>> status() {
    return ResponseResult.success(service.status());
  }

  @POST
  @Path("/backup")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public jakarta.ws.rs.core.Response backup() throws IOException {
    var backup = service.backup();
    StreamingOutput body = output -> {
      try {
        Files.copy(backup.path(), output);
      } finally {
        Files.deleteIfExists(backup.path());
      }
    };
    return jakarta.ws.rs.core.Response.ok(body)
        .header("Content-Disposition", "attachment; filename=\"" + backup.filename() + "\"")
        .header("Content-Length", Files.size(backup.path()))
        .header("Cache-Control", "no-store")
        .build();
  }

  @POST
  @Path("/import")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response<Boolean> restore(@RestForm(FILE) FileUpload file, @RestForm(CONFIRMATION) String confirmation)
      throws IOException {
    service.restore(file, confirmation);
    return ResponseResult.success(true);
  }
}
