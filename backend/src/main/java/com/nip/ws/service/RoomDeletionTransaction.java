package com.nip.ws.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.function.BooleanSupplier;

@ApplicationScoped
public class RoomDeletionTransaction {

  @Transactional(rollbackOn = Exception.class)
  public boolean run(BooleanSupplier deletion) {
    return deletion.getAsBoolean();
  }
}
