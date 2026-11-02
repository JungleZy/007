package com.nip.service;

import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.TickerPatUtils;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 结算比较器在拍发组数超过标准报文时仍应完成边界处理。 */
class MessageComparisonBoundsTest {

  @Test
  void extraCapturedGroupsDoNotCauseLineDetectorToReadPastSources() {
    List<String> sources = Collections.nCopies(10, "0");
    List<String> captured = Collections.nCopies(21, "1");
    List<PostTelegramTrainContentAddParam> contents = Collections.nCopies(21, content());
    PostTelegramTrainScoreVO score = new PostTelegramTrainScoreVO();
    PostTelegramTrainStatisticsVO statistics = new PostTelegramTrainStatisticsVO();

    PostTelegramTrainResolverVO result = assertDoesNotThrow(() -> new MessageComparisonService().comparison(
        sources, captured, score, contents, List.of(standard()), rule(), statistics));

    assertEquals(21, result.getResolverMessage().size());
  }

  private static PostTelegramTrainContentAddParam content() {
    PostTelegramTrainContentAddParam content = new PostTelegramTrainContentAddParam();
    content.setPatKeys(JSONUtils.toJson(List.of("1")));
    content.setPatLogs("[[]]");
    content.setMoresTime("[[]]");
    content.setMoresValue("[[]]");
    return content;
  }

  private static PostTelegramTrainFinishInfoDto standard() {
    PostTelegramTrainFinishInfoDto standard = new PostTelegramTrainFinishInfoDto();
    standard.setDot(100);
    standard.setLine(300);
    standard.setCodeGap(100);
    standard.setWordGap(300);
    standard.setGroupGap(700);
    return standard;
  }

  private static PostTelegramTrainRule rule() {
    return TickerPatUtils.parseContent("""
        {"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":70,"r":2,"l":1},"skew":51,
         "code":{"dot":{"base":1000,"l":1,"r":1,"max":1},"dash":{"base":1000,"l":1,"r":1,"max":5}},
         "gap":{"little":{"base":1000,"l":1,"r":1,"max":1},"middle":{"base":1000,"l":1,"r":1,"max":1},"large":{"base":1000,"l":1,"r":1,"max":1}},
         "other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}
        """);
  }
}
