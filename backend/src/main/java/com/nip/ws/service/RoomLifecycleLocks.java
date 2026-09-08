package com.nip.ws.service;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class RoomLifecycleLocks {
  private static final int STRIPE_COUNT = 64;
  private static final Lock[] UNION_USERS = stripes();
  private static final Lock[] SIMULATION_ROOMS = stripes();
  private static final Lock[] GENERAL_KEY_ROOMS = stripes();
  private static final Lock[] GENERAL_TELEX_ROOMS = stripes();
  private static final Lock[] GENERAL_TICKER_ROOMS = stripes();

  private RoomLifecycleLocks() {
  }

  public static Lock unionUser(Object sid) {
    return stripe(UNION_USERS, sid);
  }

  public static Lock simulationRoom(Object roomId) {
    return stripe(SIMULATION_ROOMS, roomId);
  }

  public static Lock generalKeyRoom(Object roomId) {
    return stripe(GENERAL_KEY_ROOMS, roomId);
  }

  public static Lock generalTelexRoom(Object roomId) {
    return stripe(GENERAL_TELEX_ROOMS, roomId);
  }

  public static Lock generalTickerRoom(Object roomId) {
    return stripe(GENERAL_TICKER_ROOMS, roomId);
  }

  private static Lock[] stripes() {
    Lock[] locks = new Lock[STRIPE_COUNT];
    for (int index = 0; index < locks.length; index++) {
      locks[index] = new ReentrantLock();
    }
    return locks;
  }

  private static Lock stripe(Lock[] locks, Object key) {
    int hash = key == null ? 0 : key.hashCode();
    hash ^= hash >>> 16;
    return locks[hash & (locks.length - 1)];
  }
}
