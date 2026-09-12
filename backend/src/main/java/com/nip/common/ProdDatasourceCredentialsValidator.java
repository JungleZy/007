package com.nip.common;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.SmallRyeConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * SEC-12 生产数据源凭据启动自检。
 *
 * <p>{@code application.yml} 的 {@code %prod} 段已把库凭据改成 {@code ${DB_USER}} / {@code ${DB_PASSWORD}}，
 * 但仅靠配置表达式**挡不住漏注入**：{@code quarkus.datasource.username} 是可选配置，未展开的
 * {@code ${DB_USER}} 不会让 Quarkus 报错，而是被当作「未配置」，MySQL 驱动随即回退到操作系统用户名去连库。
 * 结果是日志里只有一行 {@code Access denied for user 'xxx'@...}，而进程照常启动并对外提供 HTTP——
 * 运维会以为凭据已经生效。所以必须在启动期把这条失败显式化。
 *
 * <p>校验只在 {@code %prod} 生效：{@code %dev} 仍用字面 {@code root/root}，{@code %test} 用 DevServices
 * 动态生成凭据，两者都不该因为本类启动失败。判定用激活 profile 而不是 {@code LaunchMode}，
 * 与配置文件里的 {@code "%prod":} 段严格同口径。
 *
 * <p>观察者优先级压到最低值，保证它先于 {@link LifecycleApplication#onStart} 的数据源自检执行——
 * 否则先炸出来的是一条含糊的连接异常，而不是这里能直接指出变量名的信息。
 */
@ApplicationScoped
public class ProdDatasourceCredentialsValidator {

  /** 生产 profile 名，与 {@code application.yml} 的 {@code "%prod"} 段一致 */
  static final String PROD_PROFILE = "prod";

  static final String USERNAME_KEY = "quarkus.datasource.username";
  static final String PASSWORD_KEY = "quarkus.datasource.password";

  /** 部署时必须注入的环境变量名，出现在失败信息里，保证报错可操作 */
  static final String USERNAME_ENV = "DB_USER";
  static final String PASSWORD_ENV = "DB_PASSWORD";

  void onStart(@Observes @Priority(1) StartupEvent event) {
    Config config = ConfigProvider.getConfig();
    if (!config.unwrap(SmallRyeConfig.class).getProfiles().contains(PROD_PROFILE)) {
      return;
    }
    requireCredentials(resolve(config, USERNAME_KEY), resolve(config, PASSWORD_KEY));
  }

  /**
   * 取配置值，把「表达式展开失败」归一成「未配置」。
   * SmallRye 在 {@code ${DB_USER}} 无来源时抛 {@link NoSuchElementException}，
   * 与 key 缺失是同一种运维错误，不该在这里变成另一条看不懂的堆栈。
   */
  private static String resolve(Config config, String key) {
    try {
      return config.getOptionalValue(key, String.class).orElse(null);
    } catch (NoSuchElementException | IllegalArgumentException unresolved) {
      return null;
    }
  }

  /**
   * 凭据非空校验。与配置读取分离，便于直接对「缺哪个变量、报什么文案」下断言。
   *
   * @throws IllegalStateException 任一凭据为空白时抛出，信息里逐个点名缺失的环境变量
   */
  static void requireCredentials(String username, String password) {
    List<String> missing = new ArrayList<>(2);
    if (isBlank(username)) {
      missing.add(USERNAME_ENV);
    }
    if (isBlank(password)) {
      missing.add(PASSWORD_ENV);
    }
    if (missing.isEmpty()) {
      return;
    }
    throw new IllegalStateException("生产环境（%prod）数据源凭据缺失，未注入的环境变量："
        + String.join(", ", missing)
        + "。application.yml 的 %prod 段用配置表达式读取且刻意不带默认值；漏注入时 Quarkus 会把未展开的"
        + "表达式当作未配置，驱动回退到操作系统用户名连库而进程照常启动，因此这里在启动期直接失败。");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
