package com.nip.ws.service.simulation;

import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.UserEntity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SimulationSocketService {
  @Inject
  SimulationRouterRoomUserDao roomUserDao;
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  UserDao userDao;

  public Uni<UserEntity> findById(String id) {
    return Uni.createFrom().item(() -> userDao.findById(id)).runSubscriptionOn(Infrastructure.getDefaultExecutor());
  }


}
