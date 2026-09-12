package com.nip.service.general;

import com.nip.common.constants.CodeConstants;
import com.nip.ws.WebSocketService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GeneralPatResultNotifier {
  @Inject Event<Result> results;

  public void publish(String type, Integer trainId, String userId, List<String> recipients) {
    results.fire(new Result(type, trainId, userId, List.copyOf(recipients)));
  }

  void afterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) Result result) {
    ResponseModel message = new ResponseModel(CodeConstants.NOTIFICATION_TRAIN_RESULT.getCode(),
        Map.of("type", result.type(), "userId", result.userId(), "trainId", result.trainId()));
    for (String recipient : result.recipients()) {
      WebSocketService.sendInfo(recipient, message);
    }
  }

  record Result(String type, Integer trainId, String userId, List<String> recipients) {}
}
