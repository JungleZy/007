package com.nip.controller.test;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dao.TickerTapeTrainDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * @Author: wushilin
 * @Data: 2023-07-27 11:29
 * @Description:
 */
@Path("/test")
@ApplicationScoped
@Tag(name = "测试接口")
public class TestController {


  @Inject
  TickerTapeTrainDao trainDao;


  @GET()
  @Path("/start")
  @Operation(summary = "接口测试")
  public Response<?> start(){

    trainDao.begin("02bfee8b-a01f-479f-a1a7-1d081734c952");
    trainDao.pause("02bfee8b-a01f-479f-a1a7-1d081734c952","10","1",1);
    trainDao.goOn("02bfee8b-a01f-479f-a1a7-1d081734c952");
    trainDao.finish(
            "02bfee8b-a01f-479f-a1a7-1d081734c952",
              "10",
            "1",
            10
            );
    System.out.println(trainDao.countBaseTrain("5", 1));
    System.out.println(trainDao.countLetterTrain("5"));
    System.out.println(trainDao.lastTrain("5", 1));
    return ResponseResult.success("bigDecimal");
  }

}
