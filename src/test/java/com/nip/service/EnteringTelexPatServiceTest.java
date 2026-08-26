package com.nip.service;

import com.nip.dao.UserDao;
import com.nip.dto.vo.EnteringTelexPatVO;
import com.nip.dto.vo.param.EnteringTelexPatSaveParam;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 改级#16：save 创建路径查重断言方向写反（EnteringTelexPatService:49）。
 * 修复前 Assert.notNull(check, ...)：首次创建（check==null）反而抛"已存在"；
 * 真重复（check!=null）静默放行。
 */
@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class EnteringTelexPatServiceTest {
  @Inject EnteringTelexPatService service;
  @Inject UserDao userDao;

  private static EnteringTelexPatSaveParam param(Integer type) {
    EnteringTelexPatSaveParam p = new EnteringTelexPatSaveParam();
    p.setType(type);
    p.setTotalTime(60);
    p.setTotalNum(120);
    p.setTotalError(3);
    p.setMessageName("口诀练习");
    return p;
  }

  @Test
  void firstCreateOfTypeSucceeds() {
    String token = "telex-pat-first-create";
    Fixtures.user(userDao, token);

    EnteringTelexPatVO vo = service.save(token, param(0));

    assertNotNull(vo, "首次创建同类型训练必须成功");
    assertNotNull(vo.getId(), "创建成功必须返回持久化 id");
  }

  @Test
  void secondCreateOfSameTypeIsRejected() {
    String token = "telex-pat-duplicate";
    UserEntity user = Fixtures.user(userDao, token);

    service.save(token, param(1));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.save(token, param(1)), "同一用户同类型第二条创建必须被拒");
    assertEquals("您已存在相同类型的训练，不能再添加同类的训练！", ex.getMessage());
    assertNotNull(user.getId());
  }
}
