package com.nip.common.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 电子键/数据报组训教员统计共用构建器：收尾补交期间仍有未结算学员（score 为空），
 * 统计必须照常返回（与手键组训同口径跳过未结算者），不能因空成绩整体失败。
 */
class PatTrainStatisticsBuilderTest {

  private record Member(String userId, BigDecimal score) {}

  @Test
  void unsettledMembersAreSkippedInsteadOfFailingTheStatistics() {
    List<Member> members = List.of(
        new Member("settled-low", new BigDecimal("38")),
        new Member("settled-high", new BigDecimal("95")),
        new Member("unsettled", null));

    PatTrainStatisticsBuilder<Member, Object> builder = PatTrainStatisticsBuilder.<Member, Object>create(members)
        .withScoreExtractor(Member::score)
        .withUserIdExtractor(Member::userId)
        .calculateScoreDistribution()
        .buildUserTendencies(
            userId -> new PatTrainStatisticsBuilder.UserInfo(userId, userId, ""),
            (userId, createTime) -> new ArrayList<>());

    assertEquals(1, builder.getSchoolReport().getBelowStandard().getPeopleNumber());
    assertEquals(0, builder.getSchoolReport().getNice().getPeopleNumber());
    assertEquals(1, builder.getSchoolReport().getGood().getPeopleNumber());
    assertEquals(List.of("settled-low", "settled-high"),
        builder.getUserTendencies().stream().map(tendency -> tendency.getUserId()).toList(),
        "未结算学员不进入成绩趋势");
  }
}
