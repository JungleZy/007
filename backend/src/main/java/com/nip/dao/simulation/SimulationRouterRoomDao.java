package com.nip.dao.simulation;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.SimulationRouterRoomDto;
import com.nip.dto.SimulationRouterRoomSimpDto;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class SimulationRouterRoomDao extends BaseRepository<SimulationRouterRoomEntity, Integer> {

  /** All page mutations acquire the parent row before any child rows. */
  public SimulationRouterRoomEntity lockRoom(Integer roomId) {
    if (roomId == null || roomId <= 0) {
      throw new IllegalArgumentException("房间编号不正确");
    }
    SimulationRouterRoomEntity room = findById(roomId, LockModeType.PESSIMISTIC_WRITE);
    if (room == null) {
      throw new IllegalArgumentException("未查询到该房间");
    }
    return room;
  }

  public List<SimulationRouterRoomDto> findAllByUserId(String userId) {
    return entityManager.createNamedQuery("find_simulation_router_room_dto", SimulationRouterRoomDto.class)
        .setParameter(1, userId).getResultList();
  }
  public List<SimulationRouterRoomDto> findAllByUserIdRecept(String userId) {
    return entityManager.createNamedQuery("find_simulation_recept_room_dto", SimulationRouterRoomDto.class)
        .setParameter(1, userId).getResultList();
  }

  public List<SimulationRouterRoomSimpDto> findAllByUserIdSimp(String userId) {
    return entityManager.createNamedQuery("find_simulation_router_room_simp_dto", SimulationRouterRoomSimpDto.class)
        .setParameter(1, userId).getResultList();
  }

  public SimulationRouterRoomEntity findByIds(Integer roomId) {
    return find("id = ?1", roomId).firstResult();
  }

  @Transactional
  public void updateStatsToGoing(int roomId) {
    update("stats = 1,playStatus = 1 where id = ?1", roomId);
  }

  @Transactional
  public void updateStatsToFinish(int roomId, Integer totalTime) {
    update("stats = 2,totalTime=?2 where id = ?1", roomId, totalTime);
  }
}
