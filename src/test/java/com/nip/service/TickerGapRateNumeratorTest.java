package com.nip.service;

import com.nip.dao.UserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainResetParam;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainErrorInfoVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainStatisticVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Task 3.4 的服务级回归锁：组间隔虚/粗占比的**分子必须是各自的计数**。
 *
 * util 级 {@code CalculateRateTest} 只锁 {@code PatTrainStatisticsUtil.calculateRate(count,total)}
 * 的两参契约（total==0 / count==0 / 取整），对「调用点把哪个变量当分子」零防护。旧实现
 * {@code GeneralTickerPatService:737} 曾写成 {@code calculateRate(groupGapMin, groupGapMax, groupTotal)}
 * ——三参守卫看首参 {@code min}，于是 {@code groupGapMin==0 && groupGapMax>0} 时组间隔粗被短路成 0%。
 * 迁到两参后该行成为 {@code calculateRate(groupGapMax, groupTotal)}，bug 消失；本测试就是把这个
 * 「首参 = 自己的分子」钉住：任何把 GroupGapMax 的首参改回 {@code groupGapMin}（或把两行参数对调）
 * 的回退都会让这里变红。
 *
 * 测的是公开入口 {@link GeneralTickerPatService#statistic}（→ 私有
 * {@code statisticsScoreAndDotLineGapRate} → {@code GeneralTickerPatTrainErrorInfoVO}），不用反射：
 * 该私有方法的唯一输入是 {@code general_ticker_pat_train_user} 里 role=0 的行，其点划间隔计数全部来自
 * {@code statistic_info} 的 JSON —— 直接落一行造好的记录即可精确控制 groupMin/groupMax/groupTotal，
 * 无需完整拍发轨迹与结算。
 */
@QuarkusTest
class TickerGapRateNumeratorTest {

  @Inject GeneralTickerPatService service;
  @Inject GeneralTickerPatTrainUserDao trainUserDao;
  @Inject UserDao userDao;

  /**
   * 落一行 role=0 的参训记录，其 statistic_info 让 groupTotal = min+max+perfect。
   *
   * @return 该记录的 trainId
   */
  private int seedTrain(int groupMinNumber, int groupMaxNumber, int groupPerfectNumber) {
    UserEntity user = Fixtures.user(userDao, "t34-gaprate-" + UUID.randomUUID());

    // 只写这三个字段：%test 下 generation=drop-and-create 把 String 映射成 varchar(255)，
    // 而 GeneralTickerPatTrainStatisticsVO 的完整 20 字段 JSON 会超长（Data too long for
    // column 'statistic_info'）。Gson 走 VO 的无参构造，未出现的字段保留字段初始值 0。
    String statisticInfo = String.format(
        "{\"groupMinNumber\":%d,\"groupMaxNumber\":%d,\"groupPerfectNumber\":%d}",
        groupMinNumber, groupMaxNumber, groupPerfectNumber);

    // trainId 只被 findByTrainIdAndRole 当过滤条件用，无外键；随机取值以隔离其它用例的数据
    int trainId = ThreadLocalRandom.current().nextInt(900_000, 1_000_000);
    GeneralTickerPatTrainUserEntity trainUser = new GeneralTickerPatTrainUserEntity();
    trainUser.setTrainId(trainId);
    trainUser.setUserId(user.getId());
    trainUser.setRole(0);
    trainUser.setScore(new BigDecimal("80"));
    trainUser.setStatisticInfo(statisticInfo);
    trainUserDao.save(trainUser);

    return trainId;
  }

  private GeneralTickerPatTrainErrorInfoVO errorInfoOf(int trainId) {
    GeneralTickerPatTrainResetParam param = new GeneralTickerPatTrainResetParam();
    param.setId(trainId);
    GeneralTickerPatTrainStatisticVO statisticVO = service.statistic(param);
    assertNotNull(statisticVO.getErrorInfoVO(), "统计结果必须带错情分布");
    return statisticVO.getErrorInfoVO();
  }

  /**
   * groupMin=0 / groupMax=5 / total=10：组间隔粗必须是 50%，组间隔虚必须是 0%。
   * 这是旧三参实现的原始 bug 输入 —— 首参若退回 groupGapMin，这里会拿到 0%。
   */
  @Test
  void groupGapMaxRateIsDrivenByGroupMaxNumberEvenWhenGroupMinIsZero() {
    GeneralTickerPatTrainErrorInfoVO errorInfo = errorInfoOf(seedTrain(0, 5, 5));

    assertEquals(0, new BigDecimal("50").compareTo(errorInfo.getGroupGapMax()),
        "组间隔粗 = groupMaxNumber/groupTotal = 5/10 = 50%；拿到 "
            + errorInfo.getGroupGapMax() + " 说明分子不是 groupGapMax");
    assertEquals(0, BigDecimal.ZERO.compareTo(errorInfo.getGroupGapMin()),
        "组间隔虚 = groupMinNumber/groupTotal = 0/10 = 0%；拿到 "
            + errorInfo.getGroupGapMin() + " 说明两行的分子被对调");
  }

  /**
   * 反向输入 groupMin=3 / groupMax=0 / total=10：组间隔虚 30%、组间隔粗 0%。
   * 与上一例合起来，两行的分子身份各自唯一确定，参数对调必红。
   */
  @Test
  void groupGapMinRateIsDrivenByGroupMinNumberEvenWhenGroupMaxIsZero() {
    GeneralTickerPatTrainErrorInfoVO errorInfo = errorInfoOf(seedTrain(3, 0, 7));

    assertEquals(0, new BigDecimal("30").compareTo(errorInfo.getGroupGapMin()),
        "组间隔虚 = groupMinNumber/groupTotal = 3/10 = 30%；拿到 " + errorInfo.getGroupGapMin());
    assertEquals(0, BigDecimal.ZERO.compareTo(errorInfo.getGroupGapMax()),
        "组间隔粗 = groupMaxNumber/groupTotal = 0/10 = 0%；拿到 " + errorInfo.getGroupGapMax());
  }
}
