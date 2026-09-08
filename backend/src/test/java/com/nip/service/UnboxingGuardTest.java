package com.nip.service;

import com.nip.dao.MenusDao;
import com.nip.dao.PostTelegraphKeyPatTrainDao;
import com.nip.dao.UserDao;
import com.nip.dto.MenusDto;
import com.nip.dto.PostTelegraphKeyPatTrainDto;
import com.nip.entity.MenusEntity;
import com.nip.entity.PostTelegraphKeyPatTrainEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 7.4 Integer 拆箱家族边界测试（第二批抽样）：
 * 可空 Integer 列/入参上的裸 {@code ==} / {@code compareTo} / 算术会拆箱 NPE。
 * 读路径必须给出可用结果，写路径必须给出显式参数错误且不落脏行。
 */
@QuarkusTest

class UnboxingGuardTest {

  @Inject MenusService menusService;
  @Inject MenusDao menusDao;
  @Inject PostTelegraphKeyPatTrainService postTelegraphKeyPatTrainService;
  @Inject PostTelegraphKeyPatTrainDao postTelegraphKeyPatTrainDao;
  @Inject UserDao userDao;

  @Test
  void menuNodeWithNullIsMenuAndIsBreadStillRenders() {
    String key = "unbox-menu-" + UUID.randomUUID();
    MenusEntity root = new MenusEntity();
    // parentId 用唯一值：本行只用于直接校验节点渲染，不参与任何菜单树，避免污染其它用例
    root.setParentId(UUID.randomUUID().toString());
    root.setKey(key);
    root.setTitle("拆箱边界菜单");
    root.setSort(1);
    // 历史库里这两列可能是 NULL；修复前 getIsMenu() == 0 直接拆箱 NPE，菜单接口整体 500
    root.setIsMenu(null);
    root.setIsBread(null);
    MenusEntity saved = menusDao.saveAndFlush(root);

    MenusDto dto = menusService.handleMenusDto(saved);

    assertEquals(key, dto.getKey());
    assertFalse(dto.getMeta().getIsMenu(), "isMenu 为 NULL 时按「非菜单」呈现，不得 NPE");
    assertFalse(dto.getMeta().getIsBread(), "isBread 为 NULL 时按「不显示面包屑」呈现，不得 NPE");
  }

  @Test
  void addKeyPatTrainWithoutTotalNumberIsRejectedBeforeAnyWrite() {
    String token = "unbox-keypat-" + UUID.randomUUID();
    Fixtures.user(userDao, token);
    long before = postTelegraphKeyPatTrainDao.count();

    PostTelegraphKeyPatTrainDto dto = new PostTelegraphKeyPatTrainDto();
    dto.setTitle("拆箱边界训练");
    dto.setRuleId(UUID.randomUUID().toString());
    // isCable 默认 0（随机报），totalNumber 客户端未传：
    // 修复前 save.getTotalNumber() > 200 直接拆箱 NPE，且训练行已经落库
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> postTelegraphKeyPatTrainService.add(dto, token),
        "总组数缺失必须是显式参数错误而非 NPE");

    assertEquals("训练总组数不能为空", ex.getMessage());
    assertEquals(before, postTelegraphKeyPatTrainDao.count(),
        "校验必须发生在写库之前，不得留下无报底的孤儿训练行");
  }

  @Test
  void getPageWithoutPageNumberIsRejected() {
    // 修复前 pageNumber.compareTo(totalPage) 直接拆箱 NPE，读接口退化成 500
    String trainId = postTelegraphKeyPatTrainDao.saveAndFlush(
        new PostTelegraphKeyPatTrainEntity().setIsCable(0).setTotalNumber(200)).getId();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> postTelegraphKeyPatTrainService.getPage(trainId, null),
        "页码为空必须是显式参数错误而非 NPE");
    assertEquals("页码不能为空", ex.getMessage());
  }
}
