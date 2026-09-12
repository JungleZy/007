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

  @Test
  void rawCountRetainsOriginalAndReplacementBodiesButNotCorrectionMarks() {
    assertEquals(25, PostTelexPatTrainService.characterCount(
        "1234 //// 5678 9// 0123 4567/8901 2345-2/1", 0));
  }

  @Test
  void rawCountExcludesCommandsAndSelectorsButCountsInsertedBodies() {
    assertEquals(24, PostTelexPatTrainService.characterCount(
        "1234 QTA 1 ADD 2 ABCD\n20 EFGH\n1P 3 IJKL\nADD 2---3 MNOP QRST", 0));
  }

  @Test
  void rawCountPreservesEmbeddedUnknownAndIncompleteCommands() {
    assertEquals(16, PostTelexPatTrainService.characterCount("XQTA PREADD QTA ADD", 0));
    assertEquals(6, PostTelexPatTrainService.characterCount("WORD-X", 0));
    assertEquals(4, PostTelexPatTrainService.characterCount("////", 0));
    assertEquals(4, PostTelexPatTrainService.characterCount("QTA 1 ADD 2 1234-1", 0));
    assertEquals(12, PostTelexPatTrainService.characterCount("QTA 1 ADD 2 1234-1", 4));
  }
}
