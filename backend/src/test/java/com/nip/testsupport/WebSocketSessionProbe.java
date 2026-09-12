package com.nip.testsupport;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 记录出站帧的 {@link Session} 桩。
 *
 * <p>生产端点对 Session 只用到 {@code isOpen()} / {@code getAsyncRemote()} /
 * {@code getBasicRemote()} / {@code close()}，其余方法一律返回类型默认值，
 * 因此用 JDK 动态代理即可，不需要容器或 mock 框架。
 *
 * <p>{@link #failing(String)} 造出「连接还在、写出去会炸」的接收方：Undertow 在通道损坏或
 * 并发写时正是抛 {@link IllegalStateException}。生产出站入口
 * （{@code WebSocketService.send}）以 {@code catch (Exception)} 兜住单个接收方的失败，
 * 该变体就是用来验证这条隔离契约的。
 */
public final class WebSocketSessionProbe {

  private final AtomicBoolean open = new AtomicBoolean(true);
  private final List<String> outbound = new CopyOnWriteArrayList<>();
  private final Session session;

  private WebSocketSessionProbe(String id, boolean failOnSend) {
    RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
        RemoteEndpoint.Async.class.getClassLoader(),
        new Class<?>[]{RemoteEndpoint.Async.class},
        (proxy, method, args) -> recordFrame(method.getName(), args, method.getReturnType(), failOnSend));
    RemoteEndpoint.Basic basic = (RemoteEndpoint.Basic) Proxy.newProxyInstance(
        RemoteEndpoint.Basic.class.getClassLoader(),
        new Class<?>[]{RemoteEndpoint.Basic.class},
        (proxy, method, args) -> recordFrame(method.getName(), args, method.getReturnType(), failOnSend));
    this.session = (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(),
        new Class<?>[]{Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getId" -> id;
          case "isOpen" -> open.get();
          case "getAsyncRemote" -> async;
          case "getBasicRemote" -> basic;
          case "close" -> {
            open.set(false);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Session[" + id + "]";
          default -> defaultValue(method.getReturnType());
        });
  }

  /** 正常在线的接收方。 */
  public static WebSocketSessionProbe open(String id) {
    return new WebSocketSessionProbe(id, false);
  }

  /** 在线但写出即抛 {@link IllegalStateException} 的接收方。 */
  public static WebSocketSessionProbe failing(String id) {
    return new WebSocketSessionProbe(id, true);
  }

  public Session session() {
    return session;
  }

  /** 该接收方实际收到的出站帧，按发送顺序。 */
  public List<String> outbound() {
    return outbound;
  }

  private Object recordFrame(String method, Object[] args, Class<?> returnType, boolean failOnSend) {
    if ("sendText".equals(method) && args != null && args.length > 0) {
      if (failOnSend) {
        throw new IllegalStateException("通道已损坏");
      }
      outbound.add(String.valueOf(args[0]));
    }
    return defaultValue(returnType);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    return 0D;
  }
}
