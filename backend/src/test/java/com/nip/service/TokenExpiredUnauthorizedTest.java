package com.nip.service;

import com.nip.common.exception.UnauthorizedException;
import com.nip.dto.PostMilitaryTermTrainAddDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DATA-03：凭据查无用户时的服务层契约 —— 必须抛 {@link UnauthorizedException}
 * （由 UnauthorizedExceptionMapper 映射为 HTTP 200 + {@code code:203}），
 * 而不是裸解引用 {@code null} 变成 NPE → {@code code:500}。
 *
 * <p>为什么断言在服务层而不是打端点：{@code JWTInterceptor} 会先用
 * {@code existsUserByTokenAndDeviceId} 把不存在的 token 拦成 {@code code:206}，
 * REST 层根本走不到服务里的「查无用户」分支（该分支只在凭据于鉴权后被清除的竞态里可达，
 * 以及被无 {@code @JWT} 端点复用时可达）。因此这里直接调服务方法，
 * 断言的正是那条分支的可观察行为：异常类型决定了客户端拿到 203 还是 500。
 */
@QuarkusTest
class TokenExpiredUnauthorizedTest {
  @Inject EnteringExerciseService enteringExerciseService;
  @Inject EnteringTelexPatService enteringTelexPatService;
  @Inject TickerTapeTrainService tickerTapeTrainService;
  @Inject PostRadiotelephoneService postRadiotelephoneService;
  @Inject PostMilitaryTermTrainService postMilitaryTermTrainService;

  /** 每次取一个绝不可能入库的 token，避免与其他用例的夹具串味。 */
  private static String staleToken() {
    return "stale-token-" + UUID.randomUUID();
  }

  @Test
  void enteringExerciseLastTrainRejectsStaleToken() {
    assertThrows(UnauthorizedException.class,
        () -> enteringExerciseService.lastTrain(staleToken(), 1));
  }

  @Test
  void enteringTelexPatQueryRejectsStaleToken() {
    assertThrows(UnauthorizedException.class,
        () -> enteringTelexPatService.findByUserIdAndType(staleToken(), 1));
  }

  @Test
  void tickerTapeLastTrainRejectsStaleToken() {
    assertThrows(UnauthorizedException.class,
        () -> tickerTapeTrainService.lastTrain(staleToken(), 1));
  }

  @Test
  void postRadiotelephoneListPageRejectsStaleToken() {
    assertThrows(UnauthorizedException.class,
        () -> postRadiotelephoneService.listPage(staleToken()));
  }

  /**
   * {@code add} 整个方法体裹在 {@code catch (Exception) -> new RuntimeException(e)} 里：
   * 若 UnauthorizedException 没被原样上抛，203 会被降级成 500。这条用例钉的就是那份放行名单。
   */
  @Test
  void postMilitaryTermTrainAddRejectsStaleTokenWithoutDowngrade() {
    PostMilitaryTermTrainAddDto dto = new PostMilitaryTermTrainAddDto();
    assertThrows(UnauthorizedException.class,
        () -> postMilitaryTermTrainService.add(dto, staleToken()));
  }
}
