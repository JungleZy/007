package com.nip.ws.service.simulation;

import com.nip.ws.model.SimulationSessionHolder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 全局房间表。成员和空房 key 生命周期只能通过 {@link SimulationRoomLifecycle}
 * 的单次 map compute 修改。
 */
public class SimulationGlobal {
  public static final ConcurrentMap<Integer, List<SimulationSessionHolder>> routerRoom = new ConcurrentHashMap<>();
  /**
   * 快速干扰房间
   */
  public static final ConcurrentMap<Integer, List<SimulationSessionHolder>> disturbRoom = new ConcurrentHashMap<>();
  /**
   * 通报教学房间
   */
  public static final ConcurrentMap<Integer, List<SimulationSessionHolder>> reportRoom = new ConcurrentHashMap<>();
}
