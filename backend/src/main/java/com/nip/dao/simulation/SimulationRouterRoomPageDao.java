package com.nip.dao.simulation;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.router.SimulationRouterRoomPageEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SimulationRouterRoomPageDao extends BaseRepository<SimulationRouterRoomPageEntity, String> {

  public List<SimulationRouterRoomPageEntity> findByRoomIdAndPageNumberOrderBySort(Integer roomId, Integer pageNumber) {
    return find("roomId = ?1 and pageNumber = ?2 order by sort", roomId, pageNumber).list();
  }
  public Integer findMaxPageNumber(Integer roomId) {
    return entityManager.createQuery(
        "select max(pageNumber) from simulation_router_room_page where roomId =:roomId",
        Integer.class
    ).setParameter("roomId", roomId).setMaxResults(1).getSingleResult();
  }

}
