package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;

/** 电子键结算产生的原始定位依据，不在前端根据展示文字重新判分。 */
@RegisterForReflection
public record ElectronicKeyDeductionEvidence(int pageNumber, Integer referenceGroup, Integer referenceThrough,
    Integer capturedGroup, Integer capturedThrough, String reference, String captured,
    String kind, int count, Integer partnerReferenceGroup, BigDecimal unitDeduction, BigDecimal deduction) {
  public ElectronicKeyDeductionEvidence priced(PostKeyPatTrainRuleDto rule) {
    BigDecimal unit = switch (kind) {
      case "error" -> rule.getOther().getErrorCode();
      case "lack", "more" -> rule.getOther().getMuchLessCode();
      case "lackGroup", "moreGroup" -> rule.getOther().getMuchLessGroups();
      case "lackLine", "moreLine" -> rule.getOther().getMuchLessLine();
      case "alterError" -> rule.getOther().getAlterError();
      case "bunchGroup" -> rule.getOther().getBunchGroup();
      case "lackGap" -> rule.getOther().getLessGap();
      default -> throw new IllegalArgumentException("未知电子键扣分项目: " + kind);
    };
    return new ElectronicKeyDeductionEvidence(pageNumber, referenceGroup, referenceThrough, capturedGroup,
        capturedThrough, reference, captured, kind, count, partnerReferenceGroup, unit, unit.multiply(BigDecimal.valueOf(count)));
  }
}
