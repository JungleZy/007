package com.nip.service;

import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainPageDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dao.simulation.SimulationRouterRoomContentDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomPageValueDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.dto.*;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: demo
 * @author: zc
 * @create: 2023-08-03 16:10
 */
@ApplicationScoped
public class DemoService {

//    @Inject
//    BaseService queryService;
//
//    //无参
//    public List<UserDto> findAllUser() {
//        String sql = "select id,user_name userName from t_user";
//        List<UserDto> res = queryService.queryListBySql(sql, UserDto.class, null);
//        return res;
//    }
//
//
//    //带参数
//    public List<UserDto> findOneUser() {
//        Map<String, Object> param = new HashMap<>();
//        param.put("id","1");
//        String sql = "select id,user_name userName from t_user where id=:id";
//        List<UserDto> res = queryService.queryListBySql(sql, UserDto.class, param);
//        return res;
//    }

  private final GeneralKeyPatUserDao generalKeyPatUserDao;
  private final SimulationRouterRoomDao simulationRouterRoomDao;
  private final SimulationRouterRoomContentDao simulationRouterRoomContentDao;
  private final SimulationRouterRoomPageValueDao simulationRouterRoomPageValueDao;
  private final SimulationRouterRoomUserDao simulationRouterRoomUserDao;
  private final GeneralTickerPatTrainDao generalTickerPatTrainDao;
  private final GeneralTickerPatTrainPageDao generalTickerPatTrainPageDao;
  private final GeneralTickerPatTrainUserDao generalTickerPatTrainUserDao;
  private final TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao;

  @Inject
  public DemoService(
      GeneralKeyPatUserDao generalKeyPatUserDao,
      SimulationRouterRoomDao simulationRouterRoomDao,
      SimulationRouterRoomContentDao simulationRouterRoomContentDao,
      SimulationRouterRoomPageValueDao simulationRouterRoomPageValueDao,
      SimulationRouterRoomUserDao simulationRouterRoomUserDao,
      GeneralTickerPatTrainDao generalTickerPatTrainDao,
      TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao,
      GeneralTickerPatTrainPageDao generalTickerPatTrainPageDao, GeneralTickerPatTrainUserDao generalTickerPatTrainUserDao) {
    this.generalKeyPatUserDao = generalKeyPatUserDao;
    this.simulationRouterRoomDao = simulationRouterRoomDao;
    this.simulationRouterRoomContentDao = simulationRouterRoomContentDao;
    this.simulationRouterRoomPageValueDao = simulationRouterRoomPageValueDao;
    this.simulationRouterRoomUserDao = simulationRouterRoomUserDao;
    this.generalTickerPatTrainDao = generalTickerPatTrainDao;
    this.generalTickerPatTrainPageDao = generalTickerPatTrainPageDao;
    this.generalTickerPatTrainUserDao = generalTickerPatTrainUserDao;
    this.theoryKnowledgeExamUserDao = theoryKnowledgeExamUserDao;
  }

  public void test() {
    List<BigDecimal> score = generalKeyPatUserDao.score();
    System.out.println(score);
    List<Integer> integers = generalKeyPatUserDao.queryRelatedTrainId("a9d77e22-717f-4037-8114-52dbc531d543");
    System.out.println(integers);
    List<BigDecimal> lastTwoResult = generalKeyPatUserDao.findLastTwoResult("a9d77e22-717f-4037-8114-52dbc531d543");
    System.out.println(lastTwoResult);
    List<String> trainUserIdsByTrainId = generalKeyPatUserDao.findTrainUserIdsByTrainId(74);
    System.out.println(trainUserIdsByTrainId);
    List<BigDecimal> byFistTwoScore = generalKeyPatUserDao.findByFistTwoScore("a9d77e22-717f-4037-8114-52dbc531d543", LocalDateTime.now());
    System.out.println(byFistTwoScore);
    List<SimulationRouterRoomSimpDto> allByUserId = simulationRouterRoomDao.findAllByUserIdSimp("83696303-c2a5-4376-bd2f-1ab20c8e059b");
    System.out.println(allByUserId);
    SimulationRouterRoomContentDto byRoomId = simulationRouterRoomContentDao.findByRoomIdReport(850);
    System.out.println(byRoomId);
    List<SimulationRouterRoomContentRecordDto> allRecord = simulationRouterRoomContentDao.findAllRecord();
    System.out.println(allRecord);
    SimulationRouterRoomContentMessageDto message = simulationRouterRoomContentDao.findMessage(850);
    System.out.println(message);
    long l = simulationRouterRoomPageValueDao.countByUserIdAndRoomId("b6187334-8124-4d0e-9c6e-a9375a42451c", 924);
    System.out.println(l);
    SimulationRouterRoomUserEntity byRoomIdAndUserId = simulationRouterRoomUserDao.findByUserIdAndRoomId("83696303-c2a5-4376-bd2f-1ab20c8e059b", 859);
    System.out.println(byRoomIdAndUserId);
    SimulationRouterRoomUserSimpDto byUserIdAndRoomId2Map = simulationRouterRoomUserDao.findByUserIdAndRoomId2Map("2", 1034);
    System.out.println(byUserIdAndRoomId2Map);
//    String byUserLastScore1 = generalTickerPatTrainDao.findByUserLastScore("a1035bfc-f2d9-475d-adf0-d6098e23d888", "2025");
//    System.out.println(byUserLastScore1);
//    String byUserLastScore2 = generalTickerPatTrainDao.countByUserTrainTime("a1035bfc-f2d9-475d-adf0-d6098e23d888", "2025");
//    System.out.println(byUserLastScore2);
//    List<String> byUserLastScore3 = generalTickerPatTrainDao.countByUserTrainYearScore("a1035bfc-f2d9-475d-adf0-d6098e23d888", "2025");
//    System.out.println(byUserLastScore3);
//    BigDecimal byUserIdAvgScore = theoryKnowledgeExamUserDao.findByUserIdAvgScore("a1035bfc-f2d9-475d-adf0-d6098e23d888");
//    System.out.println(byUserIdAvgScore);
//    List<Integer> byTrainIdCountFloor = generalTickerPatTrainPageDao.findByTrainIdCountFloor(29);
//    System.out.println(byTrainIdCountFloor);

  }

  public static void main(String[] args) {
    System.out.println(19/10+1);
  }
}
