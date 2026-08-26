package com.nip.ws.service.simulation;

import com.nip.ws.WebSocketSimulationService;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 全局房间表。value 一律是 {@link java.util.concurrent.CopyOnWriteArrayList}，
 * 且只能通过 {@code computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>())} 创建——
 * 禁止 get→new ArrayList→put 的 check-then-act 写法（并发 onOpen 会互相覆盖）。
 */
public class SimulationGlobal {
  public static final ConcurrentMap<Integer, List<WebSocketSimulationService>> routerRoom = new ConcurrentHashMap<>();
  /**
   * 快速干扰房间
   */
  public static final ConcurrentMap<Integer, List<WebSocketSimulationService>> disturbRoom = new ConcurrentHashMap<>();
  /**
   * 通报教学房间
   */
  public static final ConcurrentMap<Integer, List<WebSocketSimulationService>> reportRoom = new ConcurrentHashMap<>();
}
