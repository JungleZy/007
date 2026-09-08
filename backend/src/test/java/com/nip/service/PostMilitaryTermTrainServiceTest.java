package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.PostMilitaryTermTrainAddDto;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.entity.PostMilitaryTermTrainEntity;
import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0#22：generateTestPaper 纯内存装配，直接 new（dao 不参与该方法）。
 */
class PostMilitaryTermTrainServiceTest {

  private final PostMilitaryTermTrainService service =
      new PostMilitaryTermTrainService(null, null, null, null, null);

  /**
   * 4 条同类型、value 互异的候选。value 刻意不含数字/无线/出口/入口/干线/小时/
   * 线状/面状/接收/发射/战术/战役/三个以上顿号——checkKeyword 全路径不命中，
   * 无法合成干扰项。修复前 nextInt(size-1) 使末元素永不可选，随机路径最多凑出
   * 2 个干扰项，while (flag <= 3) 永不退出 → 测试超时（红）。
   */
  private static List<MilitaryTermDataEntity> fourPlainCandidates() {
    List<MilitaryTermDataEntity> list = new ArrayList<>();
    String[][] rows = {
        {"甲术语", "甲种密语内容说明"},
        {"乙术语", "乙种密语内容说明"},
        {"丙术语", "丙种密语内容说明"},
        {"丁术语", "丁种密语内容说明"},
    };
    for (String[] row : rows) {
      list.add(new MilitaryTermDataEntity().setKey(row[0]).setValue(row[1]));
    }
    return list;
  }

  @Test
  void generateTestPaperTerminatesWithExactlyFourCandidates() {
    Map<String, List<MilitaryTermDataEntity>> dataMap = Map.of("type1", fourPlainCandidates());
    PostMilitaryTermTrainAddDto dto = new PostMilitaryTermTrainAddDto();
    dto.setTypes(List.of("type1"));
    dto.setTotalNumber(10);
    List<PostMilitaryTermTrainTestPaperEntity> out = new ArrayList<>();

    assertTimeoutPreemptively(Duration.ofSeconds(2),
        () -> service.generateTestPaper(dto, new PostMilitaryTermTrainEntity(), dataMap, out));

    assertEquals(10, out.size());
    for (PostMilitaryTermTrainTestPaperEntity paper : out) {
      Map<String, String> optionMap =
          JSONUtils.fromJson(paper.getOption(), new TypeToken<LinkedHashMap<String, String>>() {});
      assertEquals(4, optionMap.size(), "每题必须 4 个选项: " + paper.getOption());
      assertEquals(4, new HashSet<>(optionMap.values()).size(),
          "4 个选项必须互异: " + paper.getOption());
      assertNotNull(paper.getCorrectAnswer());
      assertTrue(optionMap.containsKey(paper.getCorrectAnswer()),
          "correctAnswer 必须指向存在的选项: " + paper.getCorrectAnswer());
    }
  }

  @Test
  void generateTestPaperRejectsTypeWithFewerThanFourDistinctValues() {
    List<MilitaryTermDataEntity> three = fourPlainCandidates().subList(0, 3);
    Map<String, List<MilitaryTermDataEntity>> dataMap = Map.of("type1", new ArrayList<>(three));
    PostMilitaryTermTrainAddDto dto = new PostMilitaryTermTrainAddDto();
    dto.setTypes(List.of("type1"));
    dto.setTotalNumber(1);

    assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
        assertThrows(IllegalArgumentException.class,
            () -> service.generateTestPaper(dto, new PostMilitaryTermTrainEntity(), dataMap, new ArrayList<>())));
  }
}
