package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;

/** 数据报/电传结算产生的定位依据，结算时随 deductInfo 冻结，不在前端根据当前规则重新判分。 */
@RegisterForReflection
public record TelexDeductionEvidence(Integer pageNumber, String kind, Integer referenceGroup,
    String reference, String captured, int count, BigDecimal unitDeduction, BigDecimal deduction) {
  public TelexDeductionEvidence priced(PostTelexPatTrainRuleDto rule) {
    BigDecimal unit = switch (kind) {
      case "errorCode" -> rule.getOther().getErrorCode();
      case "muchLessLine" -> rule.getOther().getMuchLessLine();
      case "muchLessGroups" -> rule.getOther().getMuchLessGroups();
      case "muchLessCode" -> rule.getOther().getMuchLessCode();
      case "lessReturnLine" -> rule.getOther().getLessReturnLine();
      case "lessPage" -> rule.getOther().getLessPage();
      case "errorPage" -> rule.getOther().getErrorPage();
      case "nonStandart" -> rule.getOther().getNonStandart();
      // 数据报改错单价只取评分规则页配置的 correctMistakes；alterError 是手键/电子键字段，数据报不读
      case "correctMistakes" -> rule.getOther().getCorrectMistakes();
      default -> throw new IllegalArgumentException("未知数据报扣分项目: " + kind);
    };
    return new TelexDeductionEvidence(pageNumber, kind, referenceGroup, reference, captured, count,
        unit, unit.multiply(BigDecimal.valueOf(count)));
  }
}
