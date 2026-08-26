package com.nip.service;

import com.nip.dao.MilitaryTermDataDao;
import com.nip.dto.MilitaryTermDto;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 改级#18 + P1-43：Excel 导入入口 saveBatch（:202）无 @Transactional，
 * 自调用 excelHanle 绕过其 @Transactional；dao.save 各自独立提交，
 * 批中后行抛异常时前半已落库行不回滚。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class MilitaryTermDataServiceTest {
  @Inject MilitaryTermDataService service;
  @Inject MilitaryTermDataDao dao;

  private static MilitaryTermDto dto(String parentName, String childName, String content) {
    MilitaryTermDto d = new MilitaryTermDto();
    d.setParentName(parentName);
    d.setChildName(childName);
    d.setContent(content);
    return d;
  }

  @Test
  void importRollsBackWholeBatchWhenARowFails() {
    // 种子：已提交的父类型 + 一个已有子项（避免 excelHanle:227 提前 return 与 :233 maxSort NPE）
    MilitaryTermDataEntity parent = dao.save(
        new MilitaryTermDataEntity().setParentId("0").setKey("军语导入-通信类").setSort(99));
    dao.save(new MilitaryTermDataEntity()
        .setParentId(parent.getId()).setKey("已有子项").setValue("旧内容").setSort(0));

    List<MilitaryTermDto> batch = new ArrayList<>();
    batch.add(dto("军语导入-通信类", "新子项A", "内容A"));
    batch.add(dto("军语导入-通信类", "新子项B", "内容B"));
    batch.add(null); // 非法行：excelHanle:212 取字段时抛 NullPointerException

    assertThrows(RuntimeException.class, () -> service.saveBatch(batch), "非法行必须使导入失败");

    assertNull(dao.findByParentIdAndKey(parent.getId(), "新子项A"),
        "整批必须回滚：异常前已处理的行不得残留");
    assertNull(dao.findByParentIdAndKey(parent.getId(), "新子项B"),
        "整批必须回滚：异常前已处理的行不得残留");
  }

  @Test
  void emptyBatchIsRejected() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.saveBatch(new ArrayList<>()), "空集合必须在入口被拒");
    assertEquals("导入数据为空或格式不完整", ex.getMessage());
  }

  @Test
  void nullBatchIsRejected() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.saveBatch(null), "null 集合必须在入口被拒");
    assertEquals("导入数据为空或格式不完整", ex.getMessage());
  }
}
