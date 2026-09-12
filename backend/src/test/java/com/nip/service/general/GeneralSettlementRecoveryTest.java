package com.nip.service.general;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GeneralSettlementRecovery#recover()} 的「收尾扫描失败不外抛」契约（评审 CONC-01）。
 *
 * <p>{@code recover()} 同时挂在 {@code @Observes StartupEvent} 与 5s 定时器上：待收尾清单查询
 * 一旦把异常抛出方法之外，Quarkus 就据此中止整个应用启动——库瞬时不可达会升级成进程起不来。
 * 因此这里守的是启动流程与调度器可见的三件事：扫描失败不外抛、两域扫描互不连坐、
 * 单个训练收尾失败不吞掉同域其余训练（口径与 {@code PostTelexPatTrainRecovery} 一致）。
 *
 * <p>造「扫描抛异常」的手法：两个协作服务都是构造注入的重量级 bean，且工程内没有 Mockito，
 * 故用匿名子类覆写 {@code closingTrainIds()}/{@code settleExpired()}，其余协作者一律传 null
 * （两个构造器都只做字段赋值，本用例走不到它们），再借 {@code GeneralSettlementRecovery}
 * 本就是包内字段注入这一点直接装配。匿名类不会被 Arc 当作 bean，不会与生产 bean 争解析。
 */
@QuarkusTest
class GeneralSettlementRecoveryTest {

  /** 契约一：手键（ticker）扫描抛异常既不外抛，也不阻断电子键（key）域的扫描与收尾。 */
  @Test
  void tickerScanFailureNeitherEscapesNorSkipsTheKeyDomain() {
    List<Integer> keySettled = new ArrayList<>();
    GeneralSettlementRecovery recovery = new GeneralSettlementRecovery();
    recovery.ticker = new GeneralTickerPatService(null, null, null, null, null, null, null, null) {
      @Override
      public List<Integer> closingTrainIds() {
        throw new IllegalStateException("模拟手键待收尾清单查询失败");
      }
    };
    recovery.key = new GeneralKeyPatService(null, null, null, null, null, null, null, null, null, null) {
      @Override
      public List<Integer> closingTrainIds() {
        return List.of(7);
      }

      @Override
      public void settleExpired(Integer trainId) {
        keySettled.add(trainId);
      }
    };

    assertDoesNotThrow(recovery::recover, "扫描失败外抛会中断 StartupEvent，进而中止应用启动");
    assertEquals(List.of(7), keySettled, "手键扫描失败不得阻断电子键域的收尾");
  }

  /** 契约二：单个训练收尾失败只跳过它自己，其余训练照收；另一域扫描同时失败也不外抛。 */
  @Test
  void oneFailedSettlementNeitherStopsTheRestNorEscapes() {
    List<Integer> attempted = new ArrayList<>();
    GeneralSettlementRecovery recovery = new GeneralSettlementRecovery();
    recovery.ticker = new GeneralTickerPatService(null, null, null, null, null, null, null, null) {
      @Override
      public List<Integer> closingTrainIds() {
        return List.of(1, 2, 3);
      }

      @Override
      public void settleExpired(Integer trainId) {
        attempted.add(trainId);
        if (trainId == 2) {
          throw new IllegalStateException("模拟 2 号训练收尾失败");
        }
      }
    };
    recovery.key = new GeneralKeyPatService(null, null, null, null, null, null, null, null, null, null) {
      @Override
      public List<Integer> closingTrainIds() {
        throw new IllegalStateException("模拟电子键待收尾清单查询失败");
      }
    };

    assertDoesNotThrow(recovery::recover, "电子键扫描失败外抛同样会中止应用启动");
    assertEquals(List.of(1, 2, 3), attempted, "单个训练收尾失败不得吞掉同域其余训练");
  }
}
