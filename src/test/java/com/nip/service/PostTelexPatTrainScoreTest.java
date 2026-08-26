package com.nip.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 3.2 P1-21：countScore 的五三码规整必须与 convertCodeAll 同语义：
 * "23456 789" -> "2345 6789"（旧实现产出 "2345 7895"，丢 6 多 5 顺序反）。
 */
class PostTelexPatTrainScoreTest {

  @Test
  void fiveThreeNormalizationMovesLastCharToNextGroupHead() {
    String[] groups = {"23456", "789"};
    int count = PostTelexPatTrainService.normalizeAdjacentGroups(groups);
    assertArrayEquals(new String[]{"2345", "6789"}, groups);
    assertEquals(1, count);
  }

  @Test
  void threeFiveNormalizationMovesNextGroupHeadToTail() {
    String[] groups = {"234", "56789"};
    int count = PostTelexPatTrainService.normalizeAdjacentGroups(groups);
    assertArrayEquals(new String[]{"2345", "6789"}, groups);
    assertEquals(1, count);
  }

  @Test
  void regularGroupsUntouched() {
    String[] groups = {"2345", "6789"};
    int count = PostTelexPatTrainService.normalizeAdjacentGroups(groups);
    assertArrayEquals(new String[]{"2345", "6789"}, groups);
    assertEquals(0, count);
  }
}
