package com.nip.service;

import com.nip.dao.general.telex.GeneralTelexPatDao;
import com.nip.dao.general.telex.GeneralTelexPatPageDao;
import com.nip.dto.general.GeneralTelexPatPageDetailDto;
import com.nip.dto.general.GeneralTelexPatPageDto;
import com.nip.dto.general.GeneralTelexPatPageParamDto;
import com.nip.entity.simulation.telex.GeneralTelexPatEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatPageEntity;
import com.nip.service.general.GeneralTelexPatService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Task 5.1 回归：电传 findMessageBody 此前恒返回 null，
 * `POST /api/generalTelexPat/findPage` 对客户端表现为 {code:200, data:null}。
 */
@QuarkusTest
class GeneralTelexPatMessageBodyTest {
  @Inject GeneralTelexPatService service;
  @Inject GeneralTelexPatDao trainDao;
  @Inject GeneralTelexPatPageDao trainPageDao;

  private void page(String trainId, int pageNumber, int sort, String key) {
    GeneralTelexPatPageEntity entity = new GeneralTelexPatPageEntity();
    entity.setTrainId(trainId);
    entity.setPageNumber(pageNumber);
    entity.setSort(sort);
    entity.setKey(key);
    trainPageDao.save(entity);
  }

  @Test
  void findMessageBodyReturnsRequestedPageInSortOrder() {
    GeneralTelexPatEntity train = new GeneralTelexPatEntity();
    train.setTitle("telex-body-" + UUID.randomUUID());
    train.setTrainType(1);
    train.setTotalNumber(3);
    train.setStatus(0);
    String trainId = trainDao.save(train).getId();

    // 第 1 页故意乱序落库，第 2 页用于验证只返回请求页
    page(trainId, 1, 1, "p1-b");
    page(trainId, 1, 0, "p1-a");
    page(trainId, 2, 0, "p2-a");

    GeneralTelexPatPageParamDto param = new GeneralTelexPatPageParamDto();
    param.setTrainId(trainId);
    param.setPageNumber(1);

    GeneralTelexPatPageDto dto = service.findMessageBody(param);
    assertNotNull(dto, "findMessageBody 不得再返回 null");
    List<GeneralTelexPatPageDetailDto> content = dto.getMessageContent();
    assertEquals(List.of("p1-a", "p1-b"),
        content.stream().map(GeneralTelexPatPageDetailDto::getKey).toList(),
        "只返回请求页的报底，且按 sort 升序");

    param.setPageNumber(2);
    assertEquals(List.of("p2-a"),
        service.findMessageBody(param).getMessageContent().stream()
            .map(GeneralTelexPatPageDetailDto::getKey).toList(),
        "第 2 页应返回本页报底");
  }
}
