package com.nip.common.utils;

import com.google.gson.reflect.TypeToken;

import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
  @Test
  void malformedJsonShapedPatKeysIsRejectedWithBoundedContext() {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    String malformed = "[\"a\",broken" + "x".repeat(300);
    item.setPatKeys(malformed);
    item.setPatLogs("[[]]");
    item.setMoresTime("[[1,2]]");
    item.setMoresValue("[[1,2]]");

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> TickerPatUtils.handleMessageBody(List.of(item)));

    assertAll(
        () -> assertTrue(error.getMessage().contains("patKeys")),
        () -> assertTrue(error.getMessage().contains("index=0")),
        () -> assertTrue(error.getMessage().contains("[\"a\",broken")),
        () -> assertTrue(error.getMessage().length() < 220, "错误信息不得回显无界原始输入"),
        () -> assertFalse(error.getMessage().contains(malformed), "错误信息不得包含完整超长输入"));
  }

  @Test
  void plainTextPatKeysRemainsCompatible() {
    PostTelegramTrainContentAddParam item = new PostTelegramTrainContentAddParam();
    item.setPatKeys("ABCD");
    item.setPatLogs("[[]]");
    item.setMoresTime("[[1,2]]");
    item.setMoresValue("[[1,2]]");

    List<PostTelegramTrainContentAddParam> result = assertDoesNotThrow(
        () -> TickerPatUtils.handleMessageBody(List.of(item)));
    List<String> patKeys = JSONUtils.fromJson(result.get(0).getPatKeys(), new TypeToken<>() {
    });
    assertEquals(List.of("A", "B", "C", "D"), patKeys);
  }
}
