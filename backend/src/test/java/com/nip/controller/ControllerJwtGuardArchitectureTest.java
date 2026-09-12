package com.nip.controller;

import com.nip.common.interceptor.JWT;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门禁覆盖率的架构断言：{@code com.nip.controller} 下除 {@code free} 子包（明确匿名可达）之外的
 * 每个控制器都必须带类级 {@link JWT}。
 *
 * <p>这是可观察的对外契约而不是注解细节：缺了类级 {@code @JWT} 的控制器，其全部端点都能被
 * 无 token 的匿名请求直接调用（本轮 P0-02 的四个控制器就是这么被发现的）。单端点回归测试只能
 * 覆盖写出来的那几条路径，新增控制器漏加门禁时没有任何东西会失败，所以用一条扫描全包的断言兜底。
 *
 * <p>新增匿名端点的唯一合法做法是放进 {@code controller.free} 包，让「这个端点匿名可达」在包结构上显式。
 */
class ControllerJwtGuardArchitectureTest {

  private static final String CONTROLLER_PACKAGE = "com.nip.controller";
  private static final String FREE_SUBPACKAGE = CONTROLLER_PACKAGE + ".free.";

  @Test
  void everyNonFreeControllerCarriesClassLevelJwt() throws Exception {
    List<Class<?>> controllers = scanControllerClasses();

    // 扫描本身失败（类路径布局变化导致一个类都没找到）必须报错，否则这条断言会静默空转。
    assertFalse(controllers.isEmpty(), "未扫描到任何控制器类，架构测试的类路径扫描已失效");
    assertTrue(controllers.contains(CableFloorController.class),
        "扫描结果缺少已知控制器 CableFloorController，架构测试的类路径扫描已失效");

    // 判据是「带 @Path 的 JAX-RS 资源类」：src/test 下的测试类与本类同包，会一并出现在
    // 扫描到的 com/nip/controller 目录里（target/test-classes 也是该包的一个类路径根），
    // 它们不是端点、不该要求 @JWT。用 @Path 而不是类名后缀判断，是因为「对外暴露端点」这件事
    // 由 @Path 定义，改类名不会绕过这条断言。
    List<String> missing = controllers.stream()
        .filter(type -> type.isAnnotationPresent(jakarta.ws.rs.Path.class))
        .filter(type -> !type.isAnnotationPresent(JWT.class))
        .map(Class::getName)
        .sorted()
        .toList();

    assertTrue(missing.isEmpty(),
        "以下控制器缺少类级 @JWT，其端点对匿名请求开放；"
            + "确需匿名可达请移入 com.nip.controller.free 包：" + missing);
  }

  private static List<Class<?>> scanControllerClasses() throws IOException, URISyntaxException {
    // 用本测试类自己的加载器：扫描出来的 Class 必须与 CableFloorController.class 同源，
    // 否则下面的 contains 身份比较会因加载器不同而假失败。
    ClassLoader loader = ControllerJwtGuardArchitectureTest.class.getClassLoader();
    Enumeration<URL> roots = loader.getResources(CONTROLLER_PACKAGE.replace('.', '/'));
    List<Class<?>> found = new ArrayList<>();
    while (roots.hasMoreElements()) {
      URL root = roots.nextElement();
      if (!"file".equals(root.getProtocol())) {
        continue;
      }
      Path dir = Path.of(root.toURI());
      try (Stream<Path> files = Files.walk(dir)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
          String relative = dir.relativize(file).toString()
              .replace(java.io.File.separatorChar, '.');
          String className = CONTROLLER_PACKAGE + "." + relative.substring(0, relative.length() - ".class".length());
          if (className.startsWith(FREE_SUBPACKAGE) || className.contains("$")) {
            continue;
          }
          Class<?> type = Class.forName(className, false, loader);
          if (type.isInterface() || type.isEnum() || type.isAnnotation()
              || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
            continue;
          }
          found.add(type);
        }
      } catch (ClassNotFoundException unreachable) {
        throw new IllegalStateException("控制器类在类路径上存在 .class 却无法加载", unreachable);
      }
    }
    return Collections.unmodifiableList(found);
  }
}
