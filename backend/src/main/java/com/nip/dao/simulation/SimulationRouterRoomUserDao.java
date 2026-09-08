package com.nip.dao.simulation;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.SimulationRouterRoomUserDto;
import com.nip.dto.SimulationRouterRoomUserSimpDto;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SimulationRouterRoomUserDao extends BaseRepository<SimulationRouterRoomUserEntity, Integer> {

  public List<SimulationRouterRoomUserEntity> findByRoomId(Integer roomId) {
    return find("roomId = ?1", roomId).list();
  }

  public List<SimulationRouterRoomUserEntity> findAllByRoomIdAndChannelAndUserType(Integer roomId, Integer channel, Integer userType) {
    return find("roomId = ?1 and channel = ?2 and userType = ?3", roomId, channel, userType).list();
  }

  public void remove(Integer roomId, String userId) {
    delete("roomId = ?1 and userId = ?2", roomId, userId);
  }

  public long countByUserStatus(Integer roomId) {
    return count("roomId = ?1 and userType = 0 and userStatus = 0", roomId);
  }

  public SimulationRouterRoomUserEntity findByUserIdAndRoomId(String userId, Integer roomId) {
    return find("userId = ?1 and roomId = ?2", userId, roomId).firstResult();
  }

  public List<SimulationRouterRoomUserDto> findTranUser(Integer roomId) {
    return entityManager.createNamedQuery("find_simulation_router_room_user_dto", SimulationRouterRoomUserDto.class)
        .setParameter(1, roomId)
        .getResultList();
  }

  public SimulationRouterRoomUserSimpDto findByUserIdAndRoomId2Map(String userId, Integer roomId) {
    List<SimulationRouterRoomUserSimpDto> findSimulationRouterRoomUserSimpDto = entityManager.createNamedQuery("find_simulation_router_room_user_simp_dto", SimulationRouterRoomUserSimpDto.class)
        .setParameter(1, userId)
        .setParameter(2, roomId)
        .getResultList();
    return !findSimulationRouterRoomUserSimpDto.isEmpty() ? findSimulationRouterRoomUserSimpDto.getFirst() : null;
  }


}
