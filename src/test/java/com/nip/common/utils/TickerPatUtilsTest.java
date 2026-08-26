package com.nip.common.utils;

import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TickerPatUtilsTest {

  @Test
  void corruptedPatLogsThrowsInsteadOfSilentEmpty() {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
    item.setPatLogs("{corrupted-json");
    item.setMoresTime("[[1,2]]");
    item.setMoresValue("[[1,2]]");
    assertThrows(IllegalStateException.class,
        () -> TickerPatUtils.handleMessageBody(List.of(item)));
  }

  @Test
  void corruptedMoresTimeThrowsInsteadOfSilentEmpty() {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
    item.setPatLogs("[[]]");
    item.setMoresTime("{corrupted-json");
    item.setMoresValue("[[1,2]]");
    assertThrows(IllegalStateException.class,
        () -> TickerPatUtils.handleMessageBody(List.of(item)));
  }

  @Test
  void corruptedMoresValueThrowsInsteadOfSilentEmpty() {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys("[\"a\",\"b\",\"c\",\"d\"]");
    item.setPatLogs("[[]]");
    item.setMoresTime("[[1,2]]");
    item.setMoresValue("{corrupted-json");
    assertThrows(IllegalStateException.class,
        () -> TickerPatUtils.handleMessageBody(List.of(item)));
  }
}
