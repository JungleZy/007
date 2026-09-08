package com.nip.dao.simulation;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.router.SimulationRouterRoomPageValueEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SimulationRouterRoomPageValueDao extends BaseRepository<SimulationRouterRoomPageValueEntity, Integer> {

  public SimulationRouterRoomPageValueEntity findByRoomIdAndPageNumberAndUserId(Integer roomId, Integer pageNumber, String userId) {
    return find("roomId = ?1 and pageNumber = ?2 and userId = ?3", roomId, pageNumber, userId).firstResult();
  }

  /**
   * 统计已填报页数
   *
   * @param userId 用户id
   * @param roomId 房间id
   * @return 页数
   */
  public long countByUserIdAndRoomId(String userId, Integer roomId) {
    return count("userId = ?1 and roomId = ?2", userId, roomId);
  }
}
