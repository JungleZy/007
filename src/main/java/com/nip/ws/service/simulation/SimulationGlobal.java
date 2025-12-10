package com.nip.ws.service.simulation;

import com.nip.ws.WebSocketSimulationService;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
