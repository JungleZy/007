package com.nip.dao.simulation;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.SimulationRouterRoomContentDto;
import com.nip.dto.SimulationRouterRoomContentMessageDto;
import com.nip.dto.SimulationRouterRoomContentRecordDto;
import com.nip.entity.simulation.router.SimulationRouterRoomContentEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ROOM_ID;

@ApplicationScoped
public class SimulationRouterRoomContentDao extends BaseRepository<SimulationRouterRoomContentEntity, Integer> {

  public SimulationRouterRoomContentEntity findByRoomIdRouter(Integer roomId) {
    return find(ROOM_ID, roomId).firstResult();
  }

  public SimulationRouterRoomContentDto findByRoomIdReport(Integer roomId) {
    List<SimulationRouterRoomContentDto> findSimulationRouterRoomContentDto = entityManager.createNamedQuery("find_simulation_router_room_content_dto", SimulationRouterRoomContentDto.class)
        .setParameter(1, roomId).getResultList();
    return !findSimulationRouterRoomContentDto.isEmpty() ? findSimulationRouterRoomContentDto.getFirst() : null;
  }

  public List<SimulationRouterRoomContentRecordDto> findAllRecord() {
    return entityManager.createNamedQuery("find_simulation_router_room_content_record_dto", SimulationRouterRoomContentRecordDto.class)
        .getResultList();
  }

  public SimulationRouterRoomContentMessageDto findMessage(Integer roomId) {
    List<SimulationRouterRoomContentMessageDto> findSimulationRouterRoomContentMessageDto = entityManager.createNamedQuery("find_simulation_router_room_content_message_dto", SimulationRouterRoomContentMessageDto.class)
        .setParameter(1, roomId)
        .getResultList();
    return !findSimulationRouterRoomContentMessageDto.isEmpty() ? findSimulationRouterRoomContentMessageDto.getFirst() : null;
  }

}
