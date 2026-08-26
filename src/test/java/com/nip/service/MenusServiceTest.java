package com.nip.service;

import com.nip.dao.MenusButtonDao;
import com.nip.dao.MenusDao;
import com.nip.dto.MenusButtonDto;
import com.nip.entity.MenusButtonEntity;
import com.nip.entity.MenusEntity;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class MenusServiceTest {
  @Inject MenusService service;
  @Inject MenusDao menusDao;
  @Inject MenusButtonDao menusButtonDao;

  private MenusButtonDto menuWithOneButton(String title) {
    MenusEntity menus = new MenusEntity();
    menus.setTitle(title);
    menus.setKey("k-" + title);
    menus.setPath("/p/" + title);
    menus.setName("n-" + title);
    menus.setComponent("c");
    menus.setSort(1);

    MenusButtonEntity button = new MenusButtonEntity();
    button.setTitle("btn");
    button.setKey("btn-key");

    MenusButtonDto dto = new MenusButtonDto();
    dto.setMenus(menus);
    dto.setPermissions(new ArrayList<>(List.of(button)));
    return dto;
  }

  @Test
  void addMenusWithNullPermissionsKeepsButtons() {
    // 先建含 1 个按钮的菜单
    MenusButtonDto created = service.addMenus(menuWithOneButton("m1"));
    String menusId = created.getMenus().getId();
    assertFalse(menusButtonDao.findAllByMenusId(menusId).isEmpty());

    // 再用 permissions=null 编辑：不得静默删除已有按钮
    MenusButtonDto edit = new MenusButtonDto();
    MenusEntity in = new MenusEntity();
    in.setId(menusId);
    in.setTitle("m1");
    edit.setMenus(in);
    edit.setPermissions(null);

    try {
      service.addMenus(edit);
    } catch (RuntimeException expectedAfterFix) {
      // 修复后应在删除前抛出，拒绝编辑
    }
    assertFalse(menusButtonDao.findAllByMenusId(menusId).isEmpty(), "permissions=null 不得删除已有按钮");
  }

  @Test
  void editMenusUpdatesMetadata() {
    MenusButtonDto created = service.addMenus(menuWithOneButton("m2"));
    String menusId = created.getMenus().getId();

    MenusButtonDto edit = menuWithOneButton("m2-edit");
    edit.getMenus().setId(menusId);
    service.addMenus(edit);

    assertEquals("m2-edit", menusDao.findById(menusId).getTitle(), "编辑 title 必须生效");
  }
}
