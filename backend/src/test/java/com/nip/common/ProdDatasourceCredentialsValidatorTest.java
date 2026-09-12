package com.nip.common;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.nip.common.ProdDatasourceCredentialsValidator.requireCredentials;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEC-12 回归测试：生产数据源凭据必须来自环境变量，漏注入时启动即失败。
 *
 * <p>守两条可观察契约：
 * <ol>
 *   <li>缺凭据时抛出的异常**点名了具体缺哪个环境变量**——含糊的「配置错误」对运维不可操作，
 *       而这条失败的全部价值就在于能直接指出要注入什么；</li>
 *   <li>非 {@code %prod} profile 下这条校验完全惰性——它挂在 {@link StartupEvent} 上，
 *       profile 判定一旦写错，整个测试套件与本地 dev 都会启动失败。</li>
 * </ol>
 */
@QuarkusTest
class ProdDatasourceCredentialsValidatorTest {

  @Test
  void missingBothCredentialsNamesBothEnvVars() {
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> requireCredentials(null, null));
    assertTrue(error.getMessage().contains("DB_USER"), error.getMessage());
    assertTrue(error.getMessage().contains("DB_PASSWORD"), error.getMessage());
  }

  @Test
  void missingPasswordNamesOnlyThePasswordVar() {
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> requireCredentials("app", "  "));
    assertTrue(error.getMessage().contains("DB_PASSWORD"), error.getMessage());
    assertFalse(error.getMessage().contains("DB_USER"), error.getMessage());
  }

  @Test
  void missingUsernameNamesOnlyTheUsernameVar() {
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> requireCredentials("", "secret"));
    assertTrue(error.getMessage().contains("DB_USER"), error.getMessage());
    assertFalse(error.getMessage().contains("DB_PASSWORD"), error.getMessage());
  }

  @Test
  void bothCredentialsPresentPasses() {
    assertDoesNotThrow(() -> requireCredentials("app", "secret"));
  }

  /**
   * 当前 profile 是 {@code %test}（DevServices 动态凭据、环境里没有 DB_USER/DB_PASSWORD）。
   * 直接重放启动事件：profile 隔离一旦失效这里就会抛异常，而真实后果是整个应用起不来。
   * 不走注入是为了绕开 CDI 客户端代理对包级方法的可见性问题——本类无注入依赖，直接 new 即可。
   */
  @Test
  void startupCheckIsInertOutsideProdProfile() {
    assertDoesNotThrow(() -> new ProdDatasourceCredentialsValidator().onStart(new StartupEvent()));
  }
}
