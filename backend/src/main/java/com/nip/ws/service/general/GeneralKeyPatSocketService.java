package com.nip.ws.service.general;


import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.service.general.GeneralKeyPatService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GeneralKeyPatSocketService {
  @Inject
  GeneralKeyPatService generalKeyPatService;

  public Uni<GeneralPatTrainUserDto> getTrainUserInfo(String uid, Integer trainId) {
    return Uni.createFrom().item(() -> generalKeyPatService.getTrainUserInfo(uid, trainId)).runSubscriptionOn(Infrastructure.getDefaultExecutor());
  }
}
